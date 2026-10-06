package com.qaestudos.painel.execucao.dto;

import com.qaestudos.painel.execucao.executor.SolicitacaoExecucao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Corpo JSON de {@code POST /api/execucoes}.
 *
 * <p>As anotações do Bean Validation são checadas ANTES de o Controller
 * rodar (graças ao {@code @Valid}). Se algo violar — specs vazio,
 * retentativas = 9 — a API responde 400 listando os campos, e nenhuma
 * linha de regra de negócio é executada.
 */
public record NovaExecucaoRequest(
        @NotBlank String projeto,
        String script,
        @NotEmpty(message = "selecione ao menos um spec") List<@NotBlank String> specs,
        String navegador,
        // Integer/Boolean (e não int/boolean): campos OPCIONAIS. Com
        // primitivos, o Jackson 3 recusa o JSON quando o campo não vem.
        @Min(0) @Max(3) Integer retentativas,
        Boolean abrirNavegador,
        // true = execução de teste ("dev"): fica fora das métricas.
        Boolean dev) {

    public NovaExecucaoRequest {
        retentativas = retentativas == null ? 0 : retentativas;
        abrirNavegador = abrirNavegador != null && abrirNavegador;
        dev = dev != null && dev;
    }

    public SolicitacaoExecucao paraSolicitacao() {
        return new SolicitacaoExecucao(projeto, script, specs, navegador, retentativas, abrirNavegador, dev);
    }
}
