package com.qaestudos.painel.triagem;

import static com.qaestudos.painel.configuracao.ChaveConfig.*;

import com.qaestudos.painel.configuracao.ConfiguracaoService;
import com.qaestudos.painel.triagem.ia.IaIndisponivelException;
import com.qaestudos.painel.triagem.ia.ProvedorIa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transforma uma falha num rascunho de bug:
 *
 * <ol>
 *   <li>lê nas Configurações qual provedor usar e a chave (já decifrada);
 *   <li>sem chave → rascunho HEURÍSTICO, montado da própria mensagem de erro;
 *   <li>com chave → monta o prompt, chama a IA e interpreta o JSON devolvido.
 * </ol>
 */
@Service
public class AssistenteTriagem {

    private static final int LIMITE_CODIGO = 12_000; // caracteres do spec enviados à IA
    private static final int LIMITE_ERRO = 4_000;
    private static final int LIMITE_REGRAS = 15_000; // caracteres do .md de regras de negócio
    private static final int LIMITE_SISTEMA = BuscadorCodigo.LIMITE_TOTAL + 1_000;

    private final ConfiguracaoService config;
    private final List<ProvedorIa> provedores;
    private final JsonMapper json;

    public AssistenteTriagem(ConfiguracaoService config, List<ProvedorIa> provedores, JsonMapper json) {
        this.config = config;
        this.provedores = List.copyOf(provedores);
        this.json = json;
    }

    /** Procura o provedor NA HORA do uso (não na inicialização) e falha com mensagem clara se não existir. */
    private ProvedorIa provedor(String id) {
        return provedores.stream().filter(p -> id.equals(p.id())).findFirst()
                .orElseThrow(() -> new IaIndisponivelException("Provedor de IA '%s' não está disponível.".formatted(id)));
    }

    public RascunhoBug gerarRascunho(ContextoFalha falha) {
        String provedor = config.valor(IA_PROVEDOR).orElse("gemini");
        boolean gemini = "gemini".equals(provedor);
        var chave = config.valor(gemini ? IA_GEMINI_CHAVE : IA_ANTHROPIC_CHAVE).filter(c -> !c.isBlank());
        if (chave.isEmpty()) {
            return heuristica(falha);
        }
        String modelo = config.valor(gemini ? IA_GEMINI_MODELO : IA_ANTHROPIC_MODELO).orElseThrow();
        ProvedorIa.Resposta resposta = provedor(provedor).gerar(prompt(falha), modelo, chave.get());
        return interpretar(resposta.texto(), resposta.modelo());
    }

    /**
     * O prompt pede um JSON com formato fixo. Pedir SAÍDA ESTRUTURADA é o
     * que permite ao código ler a resposta da IA com segurança, em vez de
     * tentar entender texto livre.
     */
    static String prompt(ContextoFalha f) {
        return """
                Você é um analista de QA sênior. Um teste automatizado %s falhou.
                Analise a falha e escreva o bug em português do Brasil.

                Spec: %s
                Teste: %s
                Categoria do erro (heurística): %s
                Navegador: %s

                Mensagem de erro:
                \"\"\"
                %s
                \"\"\"

                Código-fonte do spec:
                \"\"\"
                %s
                \"\"\"
                %s%s
                Responda SOMENTE com um JSON válido, sem markdown, no formato:
                {
                  "titulo": "título curto e objetivo do bug",
                  "classificacao": "BUG_APLICACAO" | "FALHA_AUTOMACAO" | "AMBIENTE" | "INSTAVEL",
                  "severidade": "CRITICA" | "ALTA" | "MEDIA" | "BAIXA",
                  "esperado": "comportamento esperado",
                  "encontrado": "comportamento encontrado",
                  "passos": ["passo 1", "passo 2"],
                  "analise": "1 a 3 frases: causa provável, se parece defeito do app ou do teste e, se houver, o código da regra de negócio envolvida"
                }
                """.formatted(
                f.ferramenta(), f.spec(), f.titulo(), valor(f.tipoErro()), valor(f.navegador()),
                cortar(f.mensagemErro(), LIMITE_ERRO), cortar(ResumoSpec.paraPrompt(f.codigoSpec(), f.titulo()), LIMITE_CODIGO), secaoRegras(f.regrasNegocio()),
                secaoSistema(f.codigoSistema()));
    }

    /**
     * Trechos do código do sistema testado achados pelo BuscadorCodigo (rotas,
     * data-testid e textos do teste). Com eles a IA aponta ONDE está o defeito.
     */
    static String secaoSistema(String trechos) {
        if (trechos == null || trechos.isBlank()) return "";
        return """

                Trechos do código-fonte do SISTEMA TESTADO ligados a esta falha (linhas numeradas):
                \"\"\"
                %s
                \"\"\"

                Se os trechos mostrarem a causa, cite o arquivo e a linha na análise (ex.: showUsers.js:42).
                Não invente código que não foi mostrado.

                """.formatted(cortar(trechos, LIMITE_SISTEMA));
    }

    /**
     * Regras de negócio do sistema testado: a "fonte da verdade" para decidir
     * de quem é o defeito. Sem elas, a IA só tem o teste para saber o que é
     * esperado, e não consegue dizer se o próprio teste está errado.
     */
    static String secaoRegras(String regras) {
        if (regras == null || regras.isBlank()) return "";
        return """

                Regras de negócio do sistema testado (fonte da verdade sobre o comportamento esperado):
                \"\"\"
                %s
                \"\"\"

                Use as regras para classificar:
                - a aplicação viola uma regra → BUG_APLICACAO, e cite o código da regra (ex.: USU-09) na análise;
                - o teste espera algo que contradiz as regras ou que elas não exigem → FALHA_AUTOMACAO;
                - a falha vem do ambiente (rede, 429, serviço fora) → AMBIENTE.

                """.formatted(cortar(regras, LIMITE_REGRAS));
    }

    /** Lê o JSON da IA de forma TOLERANTE: ignora texto/markdown em volta e valores fora do esperado. */
    RascunhoBug interpretar(String texto, String modelo) {
        int ini = texto.indexOf('{'), fim = texto.lastIndexOf('}');
        if (ini < 0 || fim <= ini) {
            throw new IaIndisponivelException("A IA não devolveu um JSON. Tente novamente.");
        }
        JsonNode j;
        try {
            j = json.readTree(texto.substring(ini, fim + 1));
        } catch (RuntimeException e) {
            throw new IaIndisponivelException("A IA devolveu um JSON inválido. Tente novamente.");
        }
        List<String> passos = new ArrayList<>();
        j.path("passos").forEach(p -> passos.add(p.asString("")));
        return new RascunhoBug(
                j.path("titulo").asString(null),
                enumOuNulo(Classificacao.class, j.path("classificacao").asString(null)),
                enumOuNulo(Severidade.class, j.path("severidade").asString(null)),
                j.path("esperado").asString(null),
                j.path("encontrado").asString(null),
                passos,
                j.path("analise").asString(null),
                "IA",
                modelo);
    }

    /** Rascunho sem IA: aproveita o que dá para extrair da mensagem de erro. */
    static RascunhoBug heuristica(ContextoFalha f) {
        String erro = f.mensagemErro() == null ? "" : f.mensagemErro();
        String primeiraLinha = erro.lines().filter(l -> !l.isBlank()).findFirst().orElse("Falha sem mensagem").trim();
        List<String> partes = Arrays.asList(f.titulo().split(" › "));
        String arquivo = f.spec().substring(f.spec().lastIndexOf('/') + 1).replaceFirst("\\.(cy|spec|test)?\\.?[jt]sx?$", "");
        Classificacao sugestao = switch (f.tipoErro() == null ? "" : f.tipoErro()) {
            case "Rede / Ambiente" -> Classificacao.AMBIENTE;
            case "Erro no código do teste", "Elemento / Seletor" -> Classificacao.FALHA_AUTOMACAO;
            case "Timeout" -> Classificacao.INSTAVEL;
            default -> Classificacao.BUG_APLICACAO;
        };
        List<String> passos = new ArrayList<>();
        for (int i = 0; i < partes.size(); i++) {
            passos.add((i == 0 ? "Acessar o fluxo: " : "Executar: ") + partes.get(i));
        }
        passos.add("Observar o resultado");
        return new RascunhoBug(
                "[%s] %s falhou".formatted(arquivo, partes.getLast()),
                sugestao,
                Severidade.MEDIA,
                "O cenário \"%s\" deveria passar.".formatted(f.titulo()),
                primeiraLinha,
                passos,
                "Rascunho gerado sem IA, a partir da mensagem de erro (categoria: %s). Configure uma chave de IA em Configurações para uma análise completa."
                        .formatted(valor(f.tipoErro())),
                "HEURISTICA",
                null);
    }

    private static <E extends Enum<E>> E enumOuNulo(Class<E> tipo, String valor) {
        if (valor == null) return null;
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // a IA inventou um valor fora da lista: o QA escolhe na tela
        }
    }

    private static String cortar(String s, int limite) {
        if (s == null || s.isBlank()) return "(indisponível)";
        return s.length() <= limite ? s : s.substring(0, limite) + "\n[... cortado ...]";
    }

    private static String valor(String s) {
        return s == null ? "n/d" : s;
    }
}
