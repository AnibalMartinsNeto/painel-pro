-- V5: publicação da triagem como issue no Jira.
-- jira_issue: chave do bug criado (ex.: DEV-2); demanda: a issue que o
-- teste valida (ex.: DEV-1), à qual o bug fica ligado.
ALTER TABLE triagem
    ADD COLUMN jira_issue     VARCHAR(50),
    ADD COLUMN jira_url       TEXT,
    ADD COLUMN demanda        VARCHAR(50),
    ADD COLUMN publicada_em   TIMESTAMPTZ;
