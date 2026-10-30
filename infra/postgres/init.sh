#!/usr/bin/env bash
# Cria os dois bancos isolados e seus respectivos usuários.
# Executado automaticamente pelo entrypoint do postgres:17.11 via /docker-entrypoint-initdb.d/
set -e

# ── users_db ────────────────────────────────────────────────────────────
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    CREATE USER "${USERS_DB_USER}" WITH ENCRYPTED PASSWORD '${USERS_DB_PASSWORD}';
    CREATE DATABASE users_db OWNER "${USERS_DB_USER}";
    GRANT ALL PRIVILEGES ON DATABASE users_db TO "${USERS_DB_USER}";
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "users_db" <<-EOSQL
    GRANT ALL ON SCHEMA public TO "${USERS_DB_USER}";
EOSQL

# ── transactions_db ──────────────────────────────────────────────────────
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    CREATE USER "${TRANSACTIONS_DB_USER}" WITH ENCRYPTED PASSWORD '${TRANSACTIONS_DB_PASSWORD}';
    CREATE DATABASE transactions_db OWNER "${TRANSACTIONS_DB_USER}";
    GRANT ALL PRIVILEGES ON DATABASE transactions_db TO "${TRANSACTIONS_DB_USER}";
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "transactions_db" <<-EOSQL
    GRANT ALL ON SCHEMA public TO "${TRANSACTIONS_DB_USER}";
EOSQL
