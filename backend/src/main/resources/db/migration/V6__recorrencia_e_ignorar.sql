-- V6: recorrência e "ignorar" na triagem.
--
-- ignorado_resultado_id: a OCORRÊNCIA que o QA tirou da fila sem publicar.
--   Se o teste falhar de novo (outro resultado), ele volta para a fila.
-- comentado_resultado_id: a ocorrência já comentada no bug existente
--   (evita comentar duas vezes a mesma falha recorrente).
ALTER TABLE triagem
    ADD COLUMN ignorado_resultado_id  BIGINT,
    ADD COLUMN ignorado_em            TIMESTAMPTZ,
    ADD COLUMN comentado_resultado_id BIGINT;

-- Histórico de tudo que o painel fez no Jira por teste: bug criado e
-- comentários de nova ocorrência. É o que permite dizer "🔗 já reportado".
-- Sem chave estrangeira para resultado_teste: o histórico vale mesmo se a
-- execução for apagada.
CREATE TABLE jira_vinculo (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    projeto_id   VARCHAR(50) NOT NULL,
    chave_teste  TEXT        NOT NULL,
    resultado_id BIGINT,
    jira_issue   VARCHAR(50) NOT NULL,
    jira_url     TEXT,
    acao         VARCHAR(20) NOT NULL CHECK (acao IN ('CRIADO', 'COMENTADO')),
    criado_em    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_jira_vinculo_teste ON jira_vinculo (projeto_id, chave_teste);

-- Bugs já publicados antes desta migração entram no histórico como CRIADO.
INSERT INTO jira_vinculo (projeto_id, chave_teste, resultado_id, jira_issue, jira_url, acao, criado_em)
SELECT projeto_id, chave_teste, resultado_id, jira_issue, jira_url, 'CRIADO', publicada_em
FROM triagem
WHERE jira_issue IS NOT NULL AND publicada_em IS NOT NULL;

-- Reserva da publicação: marcada ANTES de chamar o Jira por um UPDATE
-- condicional (atômico). Dois cliques/pedidos simultâneos não criam dois
-- bugs: o segundo encontra a reserva e recebe 409.
ALTER TABLE triagem ADD COLUMN publicando_em TIMESTAMPTZ;
