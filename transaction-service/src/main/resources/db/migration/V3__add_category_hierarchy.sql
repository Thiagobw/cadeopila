-- Pedido 5 (Análise de Despesas): hierarquia de categorias e flags de previsibilidade.
-- Nível 1 = grau de necessidade (ENUM estrito); nível 2 = subcategoria livre; is_fixed/is_recurring.
-- Statements separados por portabilidade entre PostgreSQL (produção) e H2 (testes).

ALTER TABLE transactions ADD COLUMN category_level_1 VARCHAR(20);
ALTER TABLE transactions ADD COLUMN category_level_2 VARCHAR(100);
ALTER TABLE transactions ADD COLUMN is_fixed     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN is_recurring BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill do nível 1 a partir da categoria existente, para que o histórico já entre coerente na
-- análise (espelha CategoryLevel1.defaultFor no domínio).
UPDATE transactions SET category_level_1 = CASE category
    WHEN 'FOOD'      THEN 'ESSENCIAL'
    WHEN 'TRANSPORT' THEN 'ESSENCIAL'
    WHEN 'HOUSING'   THEN 'ESSENCIAL'
    WHEN 'HEALTH'    THEN 'ESSENCIAL'
    WHEN 'EDUCATION' THEN 'ESSENCIAL'
    WHEN 'BILLS'     THEN 'ESSENCIAL'
    WHEN 'LEISURE'   THEN 'ESTILO_DE_VIDA'
    WHEN 'SHOPPING'  THEN 'ESTILO_DE_VIDA'
    WHEN 'SALARY'    THEN 'SAUDE_FINANCEIRA'
    ELSE 'DIVIDAS_IMPREVISTOS'
END
WHERE category_level_1 IS NULL;

ALTER TABLE transactions ADD CONSTRAINT ck_tx_category_level_1 CHECK (category_level_1 IN
    ('ESSENCIAL', 'ESTILO_DE_VIDA', 'SAUDE_FINANCEIRA', 'DIVIDAS_IMPREVISTOS'));
