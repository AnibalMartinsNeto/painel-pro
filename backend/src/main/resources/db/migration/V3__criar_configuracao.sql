-- V3: configurações do painel (ambiente, IA, Azure DevOps).
--
-- Tabela chave → valor. Valores SECRETOS (chaves de API, PAT) são gravados
-- CRIPTOGRAFADOS pela aplicação (AES-256-GCM): quem ler esta tabela vê só
-- "v1:<base64>", nunca o segredo. A chave que decifra fica FORA do banco.
CREATE TABLE configuracao (
    chave         VARCHAR(100) PRIMARY KEY,
    valor         TEXT         NOT NULL,
    secreto       BOOLEAN      NOT NULL DEFAULT FALSE,
    atualizado_em TIMESTAMPTZ  NOT NULL
);
