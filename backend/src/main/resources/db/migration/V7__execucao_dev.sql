-- V7: execução de teste ("dev").
-- Rodar para depurar não deve sujar os números: execuções dev ficam no
-- histórico, mas ficam FORA das métricas, dos relatórios, da fila de triagem
-- e da previsão de tempo.
ALTER TABLE execucao ADD COLUMN dev BOOLEAN NOT NULL DEFAULT FALSE;
