-- V1: tabelas de histórico de execuções.
--
-- Migrações do Flyway são IMUTÁVEIS: depois de aplicada, esta versão
-- nunca é editada. Qualquer mudança futura vira um novo arquivo
-- (V2__..., V3__...). O Flyway registra o que já rodou na tabela
-- flyway_schema_history, então cada banco evolui de forma previsível.

-- Uma execução = um "run" (ex.: npm run test:diagnostics no Cypress).
CREATE TABLE execucao (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    projeto_id        VARCHAR(50)  NOT NULL,
    script            VARCHAR(100),               -- nome do script ou null (seleção manual)
    navegador         VARCHAR(50),
    status            VARCHAR(20)  NOT NULL
                      CHECK (status IN ('EM_ANDAMENTO', 'PASSOU', 'FALHOU', 'ERRO', 'CANCELADA')),
    iniciada_em       TIMESTAMPTZ  NOT NULL,
    finalizada_em     TIMESTAMPTZ,
    total             INT          NOT NULL DEFAULT 0,
    aprovados         INT          NOT NULL DEFAULT 0,
    reprovados        INT          NOT NULL DEFAULT 0,
    pulados           INT          NOT NULL DEFAULT 0,
    duracao_ms        BIGINT,
    versao_ferramenta VARCHAR(50),
    erro              TEXT,                       -- falha da execução em si (não dos testes)
    origem            VARCHAR(100) UNIQUE         -- id no painel Node, quando importada
);

-- Consulta mais comum: "últimas execuções do projeto X".
CREATE INDEX idx_execucao_projeto_data ON execucao (projeto_id, iniciada_em DESC);

-- Um resultado = um teste dentro de uma execução.
CREATE TABLE resultado_teste (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execucao_id   BIGINT       NOT NULL REFERENCES execucao (id) ON DELETE CASCADE,
    spec          VARCHAR(300) NOT NULL,
    titulo        TEXT         NOT NULL,          -- "describe › it"
    chave         TEXT         NOT NULL,          -- spec › titulo: identifica o teste entre execuções
    status        VARCHAR(20)  NOT NULL
                  CHECK (status IN ('PASSOU', 'FALHOU', 'PENDENTE', 'PULADO')),
    duracao_ms    BIGINT,
    mensagem_erro TEXT,
    tipo_erro     VARCHAR(50)                     -- Asserção, Timeout, Elemento/Seletor...
);

CREATE INDEX idx_resultado_execucao ON resultado_teste (execucao_id);
-- Histórico de um teste ("falhou nas últimas 3 execuções?") busca pela chave.
CREATE INDEX idx_resultado_chave ON resultado_teste (chave);
