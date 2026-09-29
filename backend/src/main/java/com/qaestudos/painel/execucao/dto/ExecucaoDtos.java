package com.qaestudos.painel.execucao.dto;

import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.ResumoProjeto;
import com.qaestudos.painel.execucao.StatusExecucao;
import com.qaestudos.painel.execucao.StatusTeste;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DTOs da API de execuções, agrupados num arquivo por serem pequenos.
 *
 * <p>Por que não devolver a entidade {@link Execucao} direto? Porque ela
 * carrega a relação com os resultados (que pode disparar consultas extras
 * ao ser convertida em JSON) e expõe o formato interno do banco. O DTO
 * escolhe exatamente o que sai.
 */
public final class ExecucaoDtos {

    private ExecucaoDtos() {}

    public record ExecucaoResumoResponse(
            Long id, String projetoId, String script, String navegador, StatusExecucao status,
            Instant iniciadaEm, Instant finalizadaEm, int total, int aprovados, int reprovados, int pulados,
            Long duracaoMs, boolean importada) {

        public static ExecucaoResumoResponse de(Execucao e) {
            return new ExecucaoResumoResponse(e.getId(), e.getProjetoId(), e.getScript(), e.getNavegador(), e.getStatus(),
                    e.getIniciadaEm(), e.getFinalizadaEm(), e.getTotal(), e.getAprovados(), e.getReprovados(),
                    e.getPulados(), e.getDuracaoMs(), e.getOrigem() != null);
        }
    }

    public record ResultadoResponse(
            Long id, String spec, String titulo, StatusTeste status, Long duracaoMs, String mensagemErro, String tipoErro) {

        public static ResultadoResponse de(ResultadoTeste r) {
            return new ResultadoResponse(r.getId(), r.getSpec(), r.getTitulo(), r.getStatus(), r.getDuracaoMs(),
                    r.getMensagemErro(), r.getTipoErro());
        }
    }

    public record ExecucaoDetalheResponse(
            ExecucaoResumoResponse execucao, String versaoFerramenta, String erro, List<ResultadoResponse> resultados) {

        public static ExecucaoDetalheResponse de(Execucao e) {
            return new ExecucaoDetalheResponse(ExecucaoResumoResponse.de(e), e.getVersaoFerramenta(), e.getErro(),
                    e.getResultados().stream().map(ResultadoResponse::de).toList());
        }
    }

    public record ResumoProjetoResponse(
            String mes, long execucoes, long testes, long aprovados, long reprovados, Double aprovacao,
            List<ResumoProjeto.FalhasModulo> falhasPorModulo,
            ExecucaoResumoResponse ultimaExecucao,
            Map<String, ExecucaoResumoResponse> ultimaPorScript) {

        public static ResumoProjetoResponse de(ResumoProjeto r) {
            Map<String, ExecucaoResumoResponse> porScript = new LinkedHashMap<>();
            r.ultimaPorScript().forEach((nome, e) -> porScript.put(nome, ExecucaoResumoResponse.de(e)));
            return new ResumoProjetoResponse(
                    r.mes().toString(), r.periodo().execucoes(), r.periodo().testes(), r.periodo().aprovados(),
                    r.periodo().reprovados(), r.periodo().aprovacao(), r.falhasPorModulo(),
                    r.ultimaExecucao() == null ? null : ExecucaoResumoResponse.de(r.ultimaExecucao()),
                    porScript);
        }
    }
}
