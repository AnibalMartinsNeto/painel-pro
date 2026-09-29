package com.qaestudos.painel.configuracao;

/**
 * Catálogo FECHADO das configurações que o painel aceita. Um enum em vez
 * de texto livre: não dá para gravar uma chave inexistente por engano, e
 * o próprio enum diz o que é secreto e qual o valor padrão.
 */
public enum ChaveConfig {
    AMBIENTE_NOME("ambiente.nome", false, "Homologação"),
    IA_PROVEDOR("ia.provedor", false, "gemini"),
    IA_ANTHROPIC_CHAVE("ia.anthropic.chave", true, null),
    IA_ANTHROPIC_MODELO("ia.anthropic.modelo", false, "claude-sonnet-5"),
    IA_GEMINI_CHAVE("ia.gemini.chave", true, null),
    IA_GEMINI_MODELO("ia.gemini.modelo", false, "gemini-flash-latest"),
    AZURE_ORGANIZACAO("azure.organizacao", false, null),
    AZURE_PROJETO("azure.projeto", false, null),
    AZURE_AREA_PATH("azure.area-path", false, null),
    AZURE_PAT("azure.pat", true, null);

    private final String chave;
    private final boolean secreto;
    private final String padrao;

    ChaveConfig(String chave, boolean secreto, String padrao) {
        this.chave = chave;
        this.secreto = secreto;
        this.padrao = padrao;
    }

    public String chave() { return chave; }
    public boolean secreto() { return secreto; }
    public String padrao() { return padrao; }
}
