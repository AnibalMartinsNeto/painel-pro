-- V4: triagem das falhas.
--
-- Uma triagem por TESTE (projeto + chave "spec › título"), não por
-- execução: se o mesmo teste falhar de novo amanhã, a análise continua
-- valendo. resultado_id aponta a ocorrência que foi analisada por último.
CREATE TABLE triagem (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    projeto_id             VARCHAR(50)  NOT NULL,
    chave_teste            TEXT         NOT NULL,
    resultado_id           BIGINT       REFERENCES resultado_teste (id) ON DELETE SET NULL,
    -- NULL = pendente (ninguém classificou ainda)
    classificacao          VARCHAR(30)
                           CHECK (classificacao IN ('BUG_APLICACAO', 'FALHA_AUTOMACAO', 'AMBIENTE', 'INSTAVEL', 'BUG_CONHECIDO')),
    classificacao_sugerida VARCHAR(30),
    severidade             VARCHAR(10)  CHECK (severidade IN ('CRITICA', 'ALTA', 'MEDIA', 'BAIXA')),
    titulo                 TEXT,
    esperado               TEXT,
    encontrado             TEXT,
    passos                 TEXT,          -- um passo por linha
    analise                TEXT,
    origem_rascunho        VARCHAR(20),   -- IA ou HEURISTICA
    modelo                 VARCHAR(100),  -- modelo de IA que gerou o rascunho
    observacoes            TEXT,
    atualizada_em          TIMESTAMPTZ  NOT NULL,
    UNIQUE (projeto_id, chave_teste)
);
