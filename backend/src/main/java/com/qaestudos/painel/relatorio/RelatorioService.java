package com.qaestudos.painel.relatorio;

import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ExecucaoRepository;
import com.qaestudos.painel.execucao.ExecucaoService;
import com.qaestudos.painel.execucao.HistoricoTeste;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.ResultadoTesteRepository;
import com.qaestudos.painel.execucao.StatusExecucao;
import com.qaestudos.painel.projeto.ProjetoService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Números da tela Relatórios: todo o histórico do projeto (não só o mês,
 * como a Visão geral), os testes que mais falham e os instáveis.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    private final ExecucaoRepository execucoes;
    private final ResultadoTesteRepository resultados;
    private final ProjetoService projetos;

    public RelatorioService(ExecucaoRepository execucoes, ResultadoTesteRepository resultados, ProjetoService projetos) {
        this.execucoes = execucoes;
        this.resultados = resultados;
        this.projetos = projetos;
    }

    /**
     * Um teste que já falhou ao menos uma vez (ou é instável).
     *
     * @param ultimoResultadoId resultado da falha mais recente — abre a triagem dele
     */
    public record TesteComFalha(
            String chave, String spec, String titulo, String modulo, String tipoErro, String ultimaMensagem,
            long falhas, long execucoes, boolean instavel, Instant ultimaFalha, Long ultimoResultadoId) {}

    /** @param aprovacaoPorExecucao últimas execuções concluídas, da mais antiga para a mais recente */
    public record Relatorio(
            long execucoes, long testesUnicos, long jaFalharam, long instaveis, long tempoTotalMs,
            List<Execucao> aprovacaoPorExecucao, List<TesteComFalha> testesComFalha) {}

    public Relatorio gerar(String projetoId) {
        projetos.buscar(projetoId); // 404 se o projeto não existir

        List<HistoricoTeste> historico = resultados.historicoPorTeste(projetoId);

        // Falha mais recente de cada teste: a consulta já vem ordenada, então
        // o primeiro resultado de cada chave é o mais novo.
        Map<String, ResultadoTeste> ultimaFalha = new LinkedHashMap<>();
        for (ResultadoTeste r : resultados.falhasRecentes(projetoId)) {
            ultimaFalha.putIfAbsent(r.getChave(), r);
        }

        List<TesteComFalha> comFalha = new ArrayList<>();
        for (HistoricoTeste h : historico) {
            if (h.falhas() == 0) continue;
            ResultadoTeste ultima = ultimaFalha.get(h.chave());
            comFalha.add(new TesteComFalha(
                    h.chave(), h.spec(), h.titulo(), ExecucaoService.moduloDe(h.spec()),
                    ultima == null ? null : ultima.getTipoErro(),
                    ultima == null ? null : ultima.getMensagemErro(),
                    h.falhas(), h.execucoes(), h.instavel(),
                    ultima == null ? null : ultima.getExecucao().getIniciadaEm(),
                    ultima == null ? null : ultima.getId()));
        }
        comFalha.sort(Comparator.comparingLong(TesteComFalha::falhas).reversed()
                .thenComparing(TesteComFalha::ultimaFalha, Comparator.nullsLast(Comparator.reverseOrder())));

        List<Execucao> ultimas = new ArrayList<>(execucoes.findTop24ByProjetoIdAndStatusInOrderByIniciadaEmDesc(
                projetoId, EnumSet.of(StatusExecucao.PASSOU, StatusExecucao.FALHOU)));
        ultimas.sort(Comparator.comparing(Execucao::getIniciadaEm)); // gráfico lê da esquerda (antiga) para a direita

        return new Relatorio(
                execucoes.countByProjetoId(projetoId),
                historico.size(),
                comFalha.size(),
                historico.stream().filter(HistoricoTeste::instavel).count(),
                execucoes.somarDuracao(projetoId),
                ultimas,
                comFalha);
    }

    /** Todas as execuções do projeto em CSV (separador ";" e BOM, para o Excel abrir com acentos). */
    public String exportarCsv(String projetoId) {
        projetos.buscar(projetoId);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("id;inicio;fim;status;script;navegador;testes;passaram;falharam;pulados;duracao_ms;importada\n");
        for (Execucao e : execucoes.findByProjetoIdOrderByIniciadaEmDesc(projetoId)) {
            csv.append(String.join(";",
                    String.valueOf(e.getId()), texto(e.getIniciadaEm()), texto(e.getFinalizadaEm()), e.getStatus().name(),
                    celula(e.getScript()), celula(e.getNavegador()), String.valueOf(e.getTotal()),
                    String.valueOf(e.getAprovados()), String.valueOf(e.getReprovados()), String.valueOf(e.getPulados()),
                    texto(e.getDuracaoMs()), e.getOrigem() != null ? "sim" : "nao"))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String texto(Object v) {
        return v == null ? "" : v.toString();
    }

    /** Texto livre no CSV: entre aspas se tiver ";" , aspas ou quebra de linha. */
    private static String celula(String v) {
        if (v == null) return "";
        return v.matches("(?s).*[;\"\\n\\r].*") ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }
}
