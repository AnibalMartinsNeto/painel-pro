-- V2: guarda a saída completa (console) de cada execução.
-- Exemplo de evolução de esquema: a V1 não é editada; esta migração nova
-- altera a tabela existente, e o Flyway aplica só o que ainda não rodou.
ALTER TABLE execucao ADD COLUMN log TEXT;
