-- V8: evidências dos testes (screenshot, trace) copiadas para fora do
-- projeto de testes — a próxima execução sobrescreveria os originais.
-- O arquivo fica em disco (pasta de evidências do painel); aqui só os dados.
CREATE TABLE evidencia (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resultado_id BIGINT       NOT NULL REFERENCES resultado_teste (id) ON DELETE CASCADE,
    nome         TEXT         NOT NULL,   -- nome original do arquivo
    tipo         VARCHAR(100) NOT NULL,   -- content type: image/png, application/zip...
    arquivo      TEXT         NOT NULL,   -- caminho relativo à pasta de evidências
    tamanho      BIGINT       NOT NULL
);

CREATE INDEX idx_evidencia_resultado ON evidencia (resultado_id);
