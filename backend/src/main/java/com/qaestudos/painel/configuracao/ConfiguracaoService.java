package com.qaestudos.painel.configuracao;

import com.qaestudos.painel.common.RequisicaoInvalidaException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura e gravação das configurações. É o ÚNICO ponto que conhece a
 * criptografia: quem pede {@link #valor} recebe o texto já decifrado, e
 * tudo que é gravado como secreto passa pelo {@link Cifrador}.
 */
@Service
@Transactional
public class ConfiguracaoService {

    static final Set<String> PROVEDORES_IA = Set.of("gemini", "anthropic");

    private final ConfiguracaoRepository repository;
    private final Cifrador cifrador;
    private final Clock clock;

    public ConfiguracaoService(ConfiguracaoRepository repository, Cifrador cifrador, Clock clock) {
        this.repository = repository;
        this.cifrador = cifrador;
        this.clock = clock;
    }

    /** Valor efetivo (decifrado se secreto), ou o padrão do catálogo. */
    @Transactional(readOnly = true)
    public Optional<String> valor(ChaveConfig chave) {
        return repository.findById(chave.chave())
                .map(c -> c.isSecreto() ? cifrador.decifrar(c.getValor()) : c.getValor())
                .or(() -> Optional.ofNullable(chave.padrao()));
    }

    @Transactional(readOnly = true)
    public boolean configurado(ChaveConfig chave) {
        return repository.existsById(chave.chave());
    }

    /**
     * Grava um valor. Regra de "campo em branco": {@code null} ou vazio NÃO
     * altera nada — assim o formulário pode mandar a chave de API em branco
     * (o navegador nunca conhece o valor atual) sem apagá-la sem querer.
     * Para apagar de propósito, use {@link #remover}.
     */
    public void definir(ChaveConfig chave, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String limpo = valor.trim();
        if (chave == ChaveConfig.IA_PROVEDOR && !PROVEDORES_IA.contains(limpo)) {
            throw new RequisicaoInvalidaException("Provedor de IA inválido: '%s'. Use gemini ou anthropic.".formatted(limpo));
        }
        if (chave == ChaveConfig.JIRA_URL) {
            // Token em texto claro só trafega por HTTPS; e sem a barra final
            // para montar "url + /rest/api/3/..." sem barra dupla.
            if (!limpo.startsWith("https://")) {
                throw new RequisicaoInvalidaException("A URL do Jira deve começar com https:// (ex.: https://empresa.atlassian.net).");
            }
            limpo = limpo.replaceAll("/+$", "");
        }
        if (chave == ChaveConfig.JIRA_PROJETO) {
            limpo = limpo.toUpperCase(java.util.Locale.ROOT); // chaves de projeto do Jira são maiúsculas
        }
        String armazenado = chave.secreto() ? cifrador.cifrar(limpo) : limpo;
        repository.findById(chave.chave()).ifPresentOrElse(
                c -> c.atualizar(armazenado, chave.secreto(), clock.instant()),
                () -> repository.save(new Configuracao(chave.chave(), armazenado, chave.secreto(), clock.instant())));
    }

    public void remover(ChaveConfig chave) {
        repository.deleteById(chave.chave());
    }

    /**
     * Aplica várias mudanças numa ÚNICA transação: ou todas são gravadas,
     * ou nenhuma (se uma for inválida, as anteriores são desfeitas).
     */
    public void atualizar(Map<ChaveConfig, String> valores, Set<ChaveConfig> remover) {
        remover.forEach(this::remover);
        valores.forEach(this::definir);
    }

    /** Converte "ia.gemini.chave" no enum; 400 se a chave não existir no catálogo. */
    public static ChaveConfig chavePorNome(String nome) {
        return Arrays.stream(ChaveConfig.values())
                .filter(c -> c.chave().equals(nome))
                .findFirst()
                .orElseThrow(() -> new RequisicaoInvalidaException("Configuração desconhecida: " + nome));
    }
}
