package com.qaestudos.painel.configuracao;

import static com.qaestudos.painel.configuracao.ChaveConfig.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.qaestudos.painel.configuracao.dto.ConfiguracoesDtos.ImportacaoConfiguracoesResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traz as configurações de ambiente e IA do painel Node
 * (Painel/data/settings.json), onde os segredos estavam em TEXTO PURO,
 * para o banco — agora criptografados.
 * Só importa o que ainda não está configurado aqui (não sobrescreve).
 */
@Service
public class ImportadorConfiguracoes {

    private final ConfiguracaoService service;
    private final JsonMapper json;
    private final Path arquivo;

    public ImportadorConfiguracoes(
            ConfiguracaoService service,
            JsonMapper json,
            @Value("${painel.importacao.configuracoes-painel-node:../../Painel/data/settings.json}") String arquivo) {
        this.service = service;
        this.json = json;
        this.arquivo = Path.of(arquivo).toAbsolutePath().normalize();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SettingsNode(String envName, Ai ai) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ai(String provider, String apiKey, String model, String geminiKey, String geminiModel) {}

    @Transactional
    public ImportacaoConfiguracoesResponse importar() {
        if (!Files.isRegularFile(arquivo)) {
            return new ImportacaoConfiguracoesResponse(List.of(), "Arquivo não encontrado: " + arquivo);
        }
        SettingsNode s;
        try {
            s = json.readValue(Files.readString(arquivo), SettingsNode.class);
        } catch (IOException | RuntimeException e) {
            return new ImportacaoConfiguracoesResponse(List.of(), "Não foi possível ler " + arquivo.getFileName());
        }
        List<String> importadas = new ArrayList<>(); // só os NOMES, nunca os valores
        importar(AMBIENTE_NOME, s.envName(), importadas);
        if (s.ai() != null) {
            importar(IA_PROVEDOR, s.ai().provider(), importadas);
            importar(IA_ANTHROPIC_CHAVE, s.ai().apiKey(), importadas);
            importar(IA_ANTHROPIC_MODELO, s.ai().model(), importadas);
            importar(IA_GEMINI_CHAVE, s.ai().geminiKey(), importadas);
            importar(IA_GEMINI_MODELO, s.ai().geminiModel(), importadas);
        }
        // O painel Node usava Azure DevOps; o PainelPro usa Jira, então não há o que importar dessa parte.
        return new ImportacaoConfiguracoesResponse(importadas, null);
    }

    private void importar(ChaveConfig chave, String valor, List<String> importadas) {
        if (valor != null && !valor.isBlank() && !service.configurado(chave)) {
            service.definir(chave, valor);
            importadas.add(chave.chave());
        }
    }
}
