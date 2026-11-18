<#
.SYNOPSIS
    Popula uma base de DEMONSTRACAO do cadeopila: 3 usuarios com ~51 transacoes cada,
    espalhadas por 2026, com mistura de APROVADAS e REJEITADAS e transferencias entre eles.

.DESCRIPTION
    Fluxo (idempotente - pode rodar de novo sem duplicar):
      1. Confere a saude do MS1 (:8081) e MS2 (:8082).
      2. Cadastra 3 usuarios demo via API publica do MS1 (POST /api/v1/users); se ja existem (409),
         recupera o id listando os usuarios autenticado.
      3. Cria as contas desses 3 usuarios na MockAPI (saldo + limites), lendo ACCOUNT_API_BASE_URL do .env.
      4. Apaga qualquer seed anterior desses 3 usuarios (DELETE scoped por user_id).
      5. Insere ~51 transacoes por usuario direto no transactions_db (docker exec psql), com snapshot
         de cambio USD/EUR. As datas sao espalhadas por 2026 (a API so grava "agora", por isso o insert direto).

    Pre-requisitos: Docker Compose no ar (docker compose up --build), .env preenchido na raiz, servicos UP.
    Para base limpa do zero: docker compose down -v  (apaga os volumes) e suba de novo antes de rodar.

.NOTES
    Senha de todos os usuarios demo: Senha@Teste123
    Rode a partir de qualquer lugar: pwsh ./infra/seed/Seed-Demo.ps1  (ou powershell -File ...).
#>

[CmdletBinding()]
param(
    [string]$Ms1BaseUrl = 'http://localhost:8081',
    [string]$Ms2BaseUrl = 'http://localhost:8082',
    [string]$PostgresContainer = 'cadeopila-postgres',
    [string]$TransactionsDb = 'transactions_db',
    [decimal]$AccountBalance = 3000,
    [decimal]$AccountDailyLimit = 5000,
    [decimal]$AccountTransactionLimit = 5000
)

$ErrorActionPreference = 'Stop'
# PS 5.1 nao negocia TLS 1.2 por padrao; a MockAPI exige https moderno.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$Password = 'Senha@Teste123'

# ----------------------------------------------------------------------------------------------------
# Localiza a raiz do repo (este script vive em infra/seed/) e carrega o .env.
# ----------------------------------------------------------------------------------------------------
$ScriptDir = Split-Path -Parent $PSCommandPath
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$EnvFile = Join-Path $RepoRoot '.env'

function Read-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { throw "Arquivo .env nao encontrado em $Path" }
    $map = @{}
    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $val = $trimmed.Substring($idx + 1).Trim()
        $map[$key] = $val
    }
    return $map
}

$dotenv = Read-DotEnv -Path $EnvFile
$AccountBase = $dotenv['ACCOUNT_API_BASE_URL']
$DbUser = $dotenv['TRANSACTIONS_DB_USER']
$DbPass = $dotenv['TRANSACTIONS_DB_PASSWORD']
if ([string]::IsNullOrWhiteSpace($AccountBase)) { throw 'ACCOUNT_API_BASE_URL ausente no .env' }
if ([string]::IsNullOrWhiteSpace($DbUser))     { throw 'TRANSACTIONS_DB_USER ausente no .env' }
if ([string]::IsNullOrWhiteSpace($DbPass))     { throw 'TRANSACTIONS_DB_PASSWORD ausente no .env' }
$AccountBase = $AccountBase.TrimEnd('/')

# ----------------------------------------------------------------------------------------------------
# Usuarios demo
# ----------------------------------------------------------------------------------------------------
$Users = @(
    [ordered]@{ Name = 'Ana Souza';   Email = 'ana.souza@email.com';   Cpf = '11122233344'; Id = $null }
    [ordered]@{ Name = 'Bruno Lima';  Email = 'bruno.lima@email.com';  Cpf = '55566677788'; Id = $null }
    [ordered]@{ Name = 'Carol Dias';  Email = 'carol.dias@email.com';  Cpf = '33344455566'; Id = $null }
)

# ----------------------------------------------------------------------------------------------------
# Helpers HTTP
# ----------------------------------------------------------------------------------------------------
function Get-BasicAuthHeader {
    param([string]$Email, [string]$Pass)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("${Email}:${Pass}")
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($bytes) }
}

function Test-ServiceUp {
    param([string]$Name, [string]$Url)
    try {
        $health = Invoke-RestMethod -Method Get -Uri "$Url/actuator/health" -TimeoutSec 10
        if ($health.status -ne 'UP') { throw "status=$($health.status)" }
        Write-Host "  [ok] $Name UP ($Url)" -ForegroundColor Green
    } catch {
        throw "$Name nao esta UP em $Url. Suba o ambiente (docker compose up --build) e aguarde o health. Detalhe: $($_.Exception.Message)"
    }
}

# ----------------------------------------------------------------------------------------------------
# 1) Saude dos servicos
# ----------------------------------------------------------------------------------------------------
Write-Host "`n[1/5] Conferindo saude dos servicos..." -ForegroundColor Cyan
Test-ServiceUp -Name 'MS1 user-service' -Url $Ms1BaseUrl
Test-ServiceUp -Name 'MS2 transaction-service' -Url $Ms2BaseUrl

# ----------------------------------------------------------------------------------------------------
# 2) Cadastra usuarios (tolera 409) e captura UUIDs listando autenticado
# ----------------------------------------------------------------------------------------------------
Write-Host "`n[2/5] Cadastrando usuarios demo no MS1..." -ForegroundColor Cyan
foreach ($u in $Users) {
    $body = @{ name = $u.Name; email = $u.Email; cpf = $u.Cpf; password = $Password } | ConvertTo-Json
    try {
        $created = Invoke-RestMethod -Method Post -Uri "$Ms1BaseUrl/api/v1/users" -ContentType 'application/json' -Body $body
        $u.Id = $created.id
        Write-Host "  [novo] $($u.Email) -> $($u.Id)" -ForegroundColor Green
    } catch {
        $status = $null
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        if ($status -eq 409) {
            Write-Host "  [existe] $($u.Email) ja cadastrado (409); recupero o id ao listar" -ForegroundColor Yellow
        } else {
            throw "Falha ao cadastrar $($u.Email) (HTTP $status): $($_.Exception.Message)"
        }
    }
}

# Lista autenticado (a rota GET /users exige Basic Auth; a Ana ja existe aqui) e resolve os ids faltantes.
$authHeader = Get-BasicAuthHeader -Email $Users[0].Email -Pass $Password
$page = Invoke-RestMethod -Method Get -Uri "$Ms1BaseUrl/api/v1/users?page=0&size=100" -Headers $authHeader
foreach ($u in $Users) {
    if ($null -ne $u.Id) { continue }
    $match = $page.content | Where-Object { $_.email -eq $u.Email } | Select-Object -First 1
    if ($null -eq $match) { throw "Nao encontrei o usuario $($u.Email) na listagem; verifique a senha/base." }
    $u.Id = $match.id
    Write-Host "  [id] $($u.Email) -> $($u.Id)" -ForegroundColor Green
}

# ----------------------------------------------------------------------------------------------------
# 3) Cria/garante as contas na MockAPI (saldo + limites), chaveadas pelo userId
# ----------------------------------------------------------------------------------------------------
Write-Host "`n[3/5] Garantindo contas na MockAPI..." -ForegroundColor Cyan
foreach ($u in $Users) {
    $existing = $null
    try {
        $existing = Invoke-RestMethod -Method Get -Uri "$AccountBase/accounts?userId=$($u.Id)" -TimeoutSec 15
    } catch {
        # 404 (nenhuma conta) cai aqui em alguns mocks; tratamos como "nao existe".
        $existing = $null
    }
    $hasAccount = $false
    if ($existing) {
        $hasAccount = @($existing | Where-Object { $_.userId -eq $u.Id }).Count -gt 0
    }
    if ($hasAccount) {
        Write-Host "  [existe] conta de $($u.Email) ja na MockAPI" -ForegroundColor Yellow
        continue
    }
    $acc = @{
        userId           = $u.Id
        balance          = $AccountBalance
        dailyLimit       = $AccountDailyLimit
        transactionLimit = $AccountTransactionLimit
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$AccountBase/accounts" -ContentType 'application/json' -Body $acc -TimeoutSec 15 | Out-Null
    Write-Host "  [novo] conta de $($u.Email) criada (saldo=$AccountBalance, limites=$AccountDailyLimit/$AccountTransactionLimit)" -ForegroundColor Green
}

# ----------------------------------------------------------------------------------------------------
# 4) Dataset: ~51 transacoes por usuario (template relativo; transfers resolvem para os outros 2)
# ----------------------------------------------------------------------------------------------------
# Campos: Mon,Day,Type,Amount(string invariant),Desc,Cat,Status,Reason,L1,L2,Fixed,Rec,Dest(0|1|$null)
function New-Template {
    @(
        # --- 11 DEPOSITOS (receita mensal) ---
        @{M=1;  D=5;  T='DEPOSIT';    A='1500.00'; Desc='Salario de Janeiro';   Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=2;  D=5;  T='DEPOSIT';    A='1500.00'; Desc='Salario de Fevereiro'; Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=3;  D=5;  T='DEPOSIT';    A='1600.00'; Desc='Salario de Marco';     Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=4;  D=5;  T='DEPOSIT';    A='1600.00'; Desc='Salario de Abril';     Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=5;  D=5;  T='DEPOSIT';    A='1500.00'; Desc='Salario de Maio';      Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=6;  D=5;  T='DEPOSIT';    A='1700.00'; Desc='Salario de Junho';     Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=7;  D=5;  T='DEPOSIT';    A='1500.00'; Desc='Salario de Julho';     Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=8;  D=5;  T='DEPOSIT';    A='1500.00'; Desc='Salario de Agosto';    Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=9;  D=5;  T='DEPOSIT';    A='1800.00'; Desc='Salario de Setembro';  Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=10; D=5;  T='DEPOSIT';    A='1600.00'; Desc='Salario de Outubro';   Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}
        @{M=11; D=5;  T='DEPOSIT';    A='1600.00'; Desc='Salario de Novembro';  Cat='SALARY'; S='APPROVED'; R=$null; L1='SAUDE_FINANCEIRA'; L2=$null; F=$false; Rec=$true;  Dest=$null}

        # --- 26 COMPRAS APROVADAS (variedade de categorias/niveis; algumas fixas/recorrentes) ---
        @{M=1;  D=12; T='PURCHASE'; A='180.00';  Desc='Compras no mercado';       Cat='FOOD';      S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Mercado';     F=$false; Rec=$false; Dest=$null}
        @{M=1;  D=20; T='PURCHASE'; A='39.90';   Desc='Assinatura de streaming';  Cat='LEISURE';   S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Streaming';   F=$true;  Rec=$true;  Dest=$null}
        @{M=2;  D=10; T='PURCHASE'; A='1200.00'; Desc='Aluguel do apartamento';   Cat='HOUSING';   S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Aluguel';     F=$true;  Rec=$true;  Dest=$null}
        @{M=2;  D=18; T='PURCHASE'; A='45.00';   Desc='Corrida de aplicativo';    Cat='TRANSPORT'; S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Uber';        F=$false; Rec=$false; Dest=$null}
        @{M=3;  D=11; T='PURCHASE'; A='90.00';   Desc='Pedido de delivery';       Cat='LEISURE';   S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Delivery';    F=$false; Rec=$false; Dest=$null}
        @{M=3;  D=24; T='PURCHASE'; A='70.00';   Desc='Remedios na farmacia';     Cat='HEALTH';    S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Farmacia';    F=$false; Rec=$false; Dest=$null}
        @{M=4;  D=9;  T='PURCHASE'; A='160.00';  Desc='Compras no mercado';       Cat='FOOD';      S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Mercado';     F=$false; Rec=$false; Dest=$null}
        @{M=4;  D=15; T='PURCHASE'; A='250.00';  Desc='Roupas novas';             Cat='SHOPPING';  S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Roupas';      F=$false; Rec=$false; Dest=$null}
        @{M=4;  D=28; T='PURCHASE'; A='160.00';  Desc='Conta de energia';         Cat='BILLS';     S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Energia';     F=$true;  Rec=$true;  Dest=$null}
        @{M=5;  D=8;  T='PURCHASE'; A='300.00';  Desc='Mensalidade do curso';     Cat='EDUCATION'; S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Curso';       F=$true;  Rec=$true;  Dest=$null}
        @{M=5;  D=22; T='PURCHASE'; A='120.00';  Desc='Jantar no restaurante';    Cat='LEISURE';   S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Restaurante'; F=$false; Rec=$false; Dest=$null}
        @{M=6;  D=10; T='PURCHASE'; A='200.00';  Desc='Compras no mercado';       Cat='FOOD';      S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Mercado';     F=$false; Rec=$false; Dest=$null}
        @{M=6;  D=21; T='PURCHASE'; A='180.00';  Desc='Combustivel do carro';     Cat='TRANSPORT'; S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Combustivel'; F=$false; Rec=$false; Dest=$null}
        @{M=7;  D=12; T='PURCHASE'; A='39.90';   Desc='Assinatura de streaming';  Cat='LEISURE';   S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Streaming';   F=$true;  Rec=$true;  Dest=$null}
        @{M=7;  D=26; T='PURCHASE'; A='350.00';  Desc='Fone de ouvido novo';      Cat='SHOPPING';  S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Eletronicos'; F=$false; Rec=$false; Dest=$null}
        # Agosto: concentrando "OUTROS" para o alerta do buraco negro disparar nesse mes
        @{M=8;  D=9;  T='PURCHASE'; A='220.00';  Desc='Compra diversa nao identificada'; Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='OUTROS'; F=$false; Rec=$false; Dest=$null}
        @{M=8;  D=16; T='PURCHASE'; A='150.00';  Desc='Gasto avulso sem categoria';      Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='OUTROS'; F=$false; Rec=$false; Dest=$null}
        @{M=8;  D=23; T='PURCHASE'; A='80.00';   Desc='Compras no mercado';       Cat='FOOD';      S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Mercado';     F=$false; Rec=$false; Dest=$null}
        @{M=9;  D=10; T='PURCHASE'; A='1200.00'; Desc='Aluguel do apartamento';   Cat='HOUSING';   S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Aluguel';     F=$true;  Rec=$true;  Dest=$null}
        @{M=9;  D=19; T='PURCHASE'; A='75.00';   Desc='Pedido de delivery';       Cat='LEISURE';   S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Delivery';    F=$false; Rec=$false; Dest=$null}
        @{M=10; D=11; T='PURCHASE'; A='60.00';   Desc='Remedios na farmacia';     Cat='HEALTH';    S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Farmacia';    F=$false; Rec=$false; Dest=$null}
        @{M=10; D=17; T='PURCHASE'; A='180.00';  Desc='Roupas novas';             Cat='SHOPPING';  S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Roupas';      F=$false; Rec=$false; Dest=$null}
        @{M=10; D=27; T='PURCHASE'; A='120.00';  Desc='Internet residencial';     Cat='BILLS';     S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Internet';    F=$true;  Rec=$true;  Dest=$null}
        @{M=11; D=8;  T='PURCHASE'; A='210.00';  Desc='Compras no mercado';       Cat='FOOD';      S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Mercado';     F=$false; Rec=$false; Dest=$null}
        @{M=11; D=12; T='PURCHASE'; A='39.90';   Desc='Assinatura de streaming';  Cat='LEISURE';   S='APPROVED'; R=$null; L1='ESTILO_DE_VIDA';   L2='Streaming';   F=$true;  Rec=$true;  Dest=$null}
        @{M=11; D=16; T='PURCHASE'; A='50.00';   Desc='Corrida de aplicativo';    Cat='TRANSPORT'; S='APPROVED'; R=$null; L1='ESSENCIAL';        L2='Uber';        F=$false; Rec=$false; Dest=$null}

        # --- 4 TRANSFERENCIAS APROVADAS (entre os usuarios demo) ---
        @{M=3;  D=20; T='TRANSFER'; A='200.00'; Desc='Divisao da conta';     Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Transferencia'; F=$false; Rec=$false; Dest=0}
        @{M=6;  D=18; T='TRANSFER'; A='150.00'; Desc='Reembolso do almoco';  Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Transferencia'; F=$false; Rec=$false; Dest=1}
        @{M=9;  D=22; T='TRANSFER'; A='300.00'; Desc='Presente de aniversario'; Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Transferencia'; F=$false; Rec=$false; Dest=0}
        @{M=11; D=10; T='TRANSFER'; A='120.00'; Desc='Rachar a corrida';     Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Transferencia'; F=$false; Rec=$false; Dest=1}

        # --- 3 SAQUES APROVADOS ---
        @{M=4;  D=12; T='WITHDRAWAL'; A='200.00'; Desc='Saque no caixa'; Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Saque'; F=$false; Rec=$false; Dest=$null}
        @{M=7;  D=15; T='WITHDRAWAL'; A='150.00'; Desc='Saque em dinheiro'; Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Saque'; F=$false; Rec=$false; Dest=$null}
        @{M=10; D=20; T='WITHDRAWAL'; A='100.00'; Desc='Saque rapido'; Cat='OTHER'; S='APPROVED'; R=$null; L1='DIVIDAS_IMPREVISTOS'; L2='Saque'; F=$false; Rec=$false; Dest=$null}

        # --- 7 REJEITADAS (motivos variados; ficam no historico com o motivo) ---
        @{M=2;  D=22; T='PURCHASE';   A='999999.00'; Desc='TV de 120 polegadas';          Cat='SHOPPING'; S='REJECTED'; R='TRANSACTION_LIMIT_EXCEEDED'; L1='ESTILO_DE_VIDA';     L2='Eletronicos'; F=$false; Rec=$false; Dest=$null}
        @{M=5;  D=14; T='WITHDRAWAL'; A='50000.00';  Desc='Saque muito alto';             Cat='OTHER';    S='REJECTED'; R='INSUFFICIENT_BALANCE';       L1='DIVIDAS_IMPREVISTOS'; L2='Saque';     F=$false; Rec=$false; Dest=$null}
        @{M=6;  D=25; T='PURCHASE';   A='999999.00'; Desc='Carro zero quilometro';        Cat='SHOPPING'; S='REJECTED'; R='TRANSACTION_LIMIT_EXCEEDED'; L1='ESTILO_DE_VIDA';     L2='Veiculo';   F=$false; Rec=$false; Dest=$null}
        @{M=8;  D=19; T='TRANSFER';   A='80000.00';  Desc='Emprestimo grande';            Cat='OTHER';    S='REJECTED'; R='INSUFFICIENT_BALANCE';       L1='DIVIDAS_IMPREVISTOS'; L2='Transferencia'; F=$false; Rec=$false; Dest=0}
        @{M=9;  D=28; T='WITHDRAWAL'; A='50000.00';  Desc='Saque gigante';                Cat='OTHER';    S='REJECTED'; R='INSUFFICIENT_BALANCE';       L1='DIVIDAS_IMPREVISTOS'; L2='Saque';     F=$false; Rec=$false; Dest=$null}
        @{M=10; D=6;  T='WITHDRAWAL'; A='2500.00';   Desc='Saque estourou o limite diario'; Cat='OTHER';  S='REJECTED'; R='DAILY_LIMIT_EXCEEDED';       L1='DIVIDAS_IMPREVISTOS'; L2='Saque';     F=$false; Rec=$false; Dest=$null}
        @{M=11; D=14; T='WITHDRAWAL'; A='1800.00';   Desc='Outro saque no mesmo dia';     Cat='OTHER';    S='REJECTED'; R='DAILY_LIMIT_EXCEEDED';       L1='DIVIDAS_IMPREVISTOS'; L2='Saque';     F=$false; Rec=$false; Dest=$null}
    )
}

# ----------------------------------------------------------------------------------------------------
# Geracao de SQL
# ----------------------------------------------------------------------------------------------------
$inv = [System.Globalization.CultureInfo]::InvariantCulture

function SqlStr {
    param($Value)
    if ($null -eq $Value) { return 'NULL' }
    return "'" + ($Value -replace "'", "''") + "'"
}

function Convert-Amount {
    param([string]$Amount, [double]$Rate)
    $a = [decimal]::Parse($Amount, $inv)
    $converted = [math]::Round($a / [decimal]$Rate, 2)
    return $converted.ToString($inv)
}

function New-TransactionSql {
    param([hashtable]$Row, [string]$UserId, [string[]]$OtherIds)

    $txId = [guid]::NewGuid().ToString()
    $occurred = [datetime]::new(2026, $Row.M, $Row.D, 10, 30, 0)
    $occurredStr = $occurred.ToString('yyyy-MM-dd HH:mm:ss', $inv) + '-03'
    $quotedStr = $occurred.AddDays(-1).ToString('yyyy-MM-dd HH:mm:ss', $inv) + '-03'

    $dest = 'NULL'
    if ($null -ne $Row.Dest) { $dest = SqlStr $OtherIds[$Row.Dest] }

    $reason = if ($null -eq $Row.R) { 'NULL' } else { SqlStr $Row.R }
    $l1 = if ($null -eq $Row.L1) { 'NULL' } else { SqlStr $Row.L1 }
    $l2 = if ($null -eq $Row.L2) { 'NULL' } else { SqlStr $Row.L2 }
    $fixed = if ($Row.F) { 'TRUE' } else { 'FALSE' }
    $rec = if ($Row.Rec) { 'TRUE' } else { 'FALSE' }

    $usd = Convert-Amount -Amount $Row.A -Rate 5.40
    $eur = Convert-Amount -Amount $Row.A -Rate 5.90

    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine("INSERT INTO transactions (id, user_id, destination_user_id, type, amount, description, category, status, rejection_reason, occurred_at, created_at, updated_at, deleted_at, version, category_level_1, category_level_2, is_fixed, is_recurring) VALUES (")
    [void]$sb.AppendLine("  '$txId', '$UserId', $dest, '$($Row.T)', $($Row.A), $(SqlStr $Row.Desc), '$($Row.Cat)', '$($Row.S)', $reason, '$occurredStr', '$occurredStr', '$occurredStr', NULL, 0, $l1, $l2, $fixed, $rec);")
    [void]$sb.AppendLine("INSERT INTO transaction_exchange_quotes (transaction_id, currency, rate, converted_amount, quoted_at, source) VALUES ('$txId', 'USD', 5.400000, $usd, '$quotedStr', 'BrasilAPI');")
    [void]$sb.AppendLine("INSERT INTO transaction_exchange_quotes (transaction_id, currency, rate, converted_amount, quoted_at, source) VALUES ('$txId', 'EUR', 5.900000, $eur, '$quotedStr', 'BrasilAPI');")
    return $sb.ToString()
}

Write-Host "`n[4/5] Montando o dataset (~51 transacoes por usuario)..." -ForegroundColor Cyan
$template = New-Template
$sql = [System.Text.StringBuilder]::new()
[void]$sql.AppendLine('SET client_encoding = ' + "'UTF8';")
[void]$sql.AppendLine('BEGIN;')

# Idempotencia: apaga o seed anterior desses 3 usuarios (cascade limpa as cotacoes).
$idList = ($Users | ForEach-Object { "'" + $_.Id + "'" }) -join ', '
[void]$sql.AppendLine("DELETE FROM transactions WHERE user_id IN ($idList);")

$counts = @{}
foreach ($u in $Users) {
    $others = @($Users | Where-Object { $_.Id -ne $u.Id } | ForEach-Object { $_.Id })
    $approved = 0; $rejected = 0; $transfers = 0
    foreach ($row in $template) {
        [void]$sql.Append((New-TransactionSql -Row $row -UserId $u.Id -OtherIds $others))
        if ($row.S -eq 'APPROVED') { $approved++ } else { $rejected++ }
        if ($row.T -eq 'TRANSFER') { $transfers++ }
    }
    $counts[$u.Email] = [ordered]@{ Total = $template.Count; Approved = $approved; Rejected = $rejected; Transfers = $transfers }
}
[void]$sql.AppendLine('COMMIT;')

# ----------------------------------------------------------------------------------------------------
# 5) Aplica o SQL no Postgres (via docker cp + psql, evitando problemas de encoding em stdin)
# ----------------------------------------------------------------------------------------------------
Write-Host "`n[5/5] Inserindo no $TransactionsDb (container $PostgresContainer)..." -ForegroundColor Cyan
$tmpFile = Join-Path ([System.IO.Path]::GetTempPath()) 'cadeopila-seed.sql'
[System.IO.File]::WriteAllText($tmpFile, $sql.ToString(), (New-Object System.Text.UTF8Encoding($false)))

docker cp $tmpFile "${PostgresContainer}:/tmp/cadeopila-seed.sql" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "docker cp falhou. O container $PostgresContainer esta no ar? (docker compose up)" }

# -h 127.0.0.1 forca conexao TCP (auth por senha via PGPASSWORD), em vez do socket local.
docker exec -e PGPASSWORD=$DbPass $PostgresContainer psql -h 127.0.0.1 -U $DbUser -d $TransactionsDb -v ON_ERROR_STOP=1 -q -f /tmp/cadeopila-seed.sql
if ($LASTEXITCODE -ne 0) { throw "psql retornou erro ao aplicar o seed." }

docker exec $PostgresContainer rm -f /tmp/cadeopila-seed.sql | Out-Null
Remove-Item $tmpFile -ErrorAction SilentlyContinue

# ----------------------------------------------------------------------------------------------------
# Resumo
# ----------------------------------------------------------------------------------------------------
Write-Host "`n==================== SEED CONCLUIDO ====================" -ForegroundColor Green
Write-Host "Senha de todos os usuarios demo: $Password`n"
foreach ($u in $Users) {
    $c = $counts[$u.Email]
    Write-Host ("{0,-22} {1}" -f $u.Name, $u.Id)
    Write-Host ("  email: {0}" -f $u.Email)
    Write-Host ("  transacoes: {0} (aprovadas={1}, rejeitadas={2}, transferencias={3})`n" -f $c.Total, $c.Approved, $c.Rejected, $c.Transfers)
}
Write-Host "Swagger MS2: $Ms2BaseUrl/swagger-ui.html  (clique Authorize com email + senha acima)" -ForegroundColor Cyan
Write-Host "Dicas de teste:" -ForegroundColor Cyan
Write-Host "  - Analise  : GET /api/v1/analytics/expenses/summary?month=08-2026  (alerta do buraco negro)"
Write-Host "  - Relatorio: GET /api/v1/transactions/report?format=pdf&from=2026-01-01&to=2026-12-31"
Write-Host "=======================================================" -ForegroundColor Green
