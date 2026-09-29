package com.qaestudos.painel.execucao.executor;

import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.TipoProjeto;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * PADRÃO STRATEGY: cada ferramenta de teste sabe (1) quais processos
 * iniciar e (2) como ler o relatório que eles geram. O orquestrador não
 * conhece Cypress, Playwright nem k6 — só esta interface. Suportar uma
 * ferramenta nova (Selenium, JMeter...) é criar mais uma implementação,
 * sem tocar no orquestrador.
 */
public interface ExecutorFerramenta {

    TipoProjeto tipo();

    /**
     * Processos a executar, em sequência (o k6, por exemplo, roda um
     * processo por script). {@code pasta} é uma pasta temporária exclusiva
     * da execução, onde as ferramentas gravam seus relatórios.
     */
    List<Etapa> etapas(Projeto projeto, SolicitacaoExecucao solicitacao, Path pasta);

    /** Lê os relatórios gravados em {@code pasta} e converte em resultados de teste. */
    Leitura ler(Projeto projeto, SolicitacaoExecucao solicitacao, Path pasta);

    /** Um processo a iniciar: linha de comando + variáveis de ambiente extras. */
    record Etapa(List<String> comando, Map<String, String> ambiente) {}

    /**
     * Resultado da leitura dos relatórios.
     *
     * @param erro falha da execução em si (ferramenta não subiu, relatório
     *             ausente...) — diferente de teste que falhou
     */
    record Leitura(List<ResultadoTeste> resultados, String versaoFerramenta, String erro) {

        public static Leitura falha(String erro) {
            return new Leitura(List.of(), null, erro);
        }
    }
}
