package com.qaestudos.painel.configuracao;

import static com.qaestudos.painel.configuracao.ChaveConfig.*;

import com.qaestudos.painel.configuracao.dto.ConfiguracoesDtos.AtualizarConfiguracoesRequest;
import com.qaestudos.painel.configuracao.dto.ConfiguracoesDtos.ConfiguracoesResponse;
import com.qaestudos.painel.configuracao.dto.ConfiguracoesDtos.IaResponse;
import com.qaestudos.painel.configuracao.dto.ConfiguracoesDtos.ImportacaoConfiguracoesResponse;
import com.qaestudos.painel.configuracao.dto.ConfiguracoesDtos.JiraResponse;
import com.qaestudos.painel.jira.JiraCliente;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de configurações: leitura (sem segredos), atualização, teste do Jira e importação do painel Node. */
@RestController
@RequestMapping("/api/configuracoes")
public class ConfiguracaoController {

    private final ConfiguracaoService service;
    private final ImportadorConfiguracoes importador;
    private final JiraCliente jira;

    public ConfiguracaoController(ConfiguracaoService service, ImportadorConfiguracoes importador, JiraCliente jira) {
        this.service = service;
        this.importador = importador;
        this.jira = jira;
    }

    @GetMapping
    public ConfiguracoesResponse obter() {
        String provedor = texto(IA_PROVEDOR);
        boolean anthropic = service.configurado(IA_ANTHROPIC_CHAVE);
        boolean gemini = service.configurado(IA_GEMINI_CHAVE);
        String url = texto(JIRA_URL), email = texto(JIRA_EMAIL), projeto = texto(JIRA_PROJETO);
        boolean token = service.configurado(JIRA_TOKEN);
        return new ConfiguracoesResponse(
                texto(AMBIENTE_NOME),
                new IaResponse(provedor, texto(IA_ANTHROPIC_MODELO), texto(IA_GEMINI_MODELO), anthropic, gemini,
                        "gemini".equals(provedor) ? gemini : anthropic),
                new JiraResponse(url, email, projeto, texto(JIRA_TIPO_ISSUE), token,
                        url != null && email != null && projeto != null && token));
    }

    /** POST /api/configuracoes/jira/testar → valida credenciais e projeto chamando o próprio Jira. */
    @PostMapping("/jira/testar")
    public JiraCliente.TesteConexao testarJira() {
        return jira.testarConexao();
    }

    /** PUT (e não POST): substitui/atualiza o estado de um recurso que já existe. */
    @PutMapping
    public ConfiguracoesResponse atualizar(@RequestBody AtualizarConfiguracoesRequest pedido) {
        Map<ChaveConfig, String> valores = new EnumMap<>(ChaveConfig.class);
        Set<ChaveConfig> remover = EnumSet.noneOf(ChaveConfig.class);

        comum(AMBIENTE_NOME, pedido.ambiente(), valores, remover);
        if (pedido.ia() != null) {
            comum(IA_PROVEDOR, pedido.ia().provedor(), valores, remover);
            comum(IA_ANTHROPIC_MODELO, pedido.ia().modeloAnthropic(), valores, remover);
            comum(IA_GEMINI_MODELO, pedido.ia().modeloGemini(), valores, remover);
            valores.put(IA_ANTHROPIC_CHAVE, pedido.ia().chaveAnthropic()); // segredo: vazio = manter
            valores.put(IA_GEMINI_CHAVE, pedido.ia().chaveGemini());
        }
        if (pedido.jira() != null) {
            comum(JIRA_URL, pedido.jira().url(), valores, remover);
            comum(JIRA_EMAIL, pedido.jira().email(), valores, remover);
            comum(JIRA_PROJETO, pedido.jira().projeto(), valores, remover);
            comum(JIRA_TIPO_ISSUE, pedido.jira().tipoIssue(), valores, remover);
            valores.put(JIRA_TOKEN, pedido.jira().token()); // segredo: vazio = manter
        }
        for (String nome : pedido.remover() == null ? List.<String>of() : pedido.remover()) {
            remover.add(ConfiguracaoService.chavePorNome(nome));
        }
        valores.values().removeIf(v -> v == null || v.isBlank());
        service.atualizar(valores, remover);
        return obter();
    }

    /** POST /api/configuracoes/importar-painel-node → traz ambiente e IA do painel Node. */
    @PostMapping("/importar-painel-node")
    public ImportacaoConfiguracoesResponse importarPainelNode() {
        return importador.importar();
    }

    // Campo comum: null = não mexe; "" = apaga; texto = grava.
    private static void comum(ChaveConfig chave, String valor, Map<ChaveConfig, String> valores, Set<ChaveConfig> remover) {
        if (valor == null) return;
        if (valor.isBlank()) remover.add(chave);
        else valores.put(chave, valor);
    }

    private String texto(ChaveConfig chave) {
        return service.valor(chave).orElse(null);
    }
}
