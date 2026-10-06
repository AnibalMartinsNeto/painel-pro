package com.qaestudos.painel.triagem;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import com.qaestudos.painel.execucao.Execucao;
import com.qaestudos.painel.execucao.ResultadoTeste;
import com.qaestudos.painel.execucao.ResultadoTesteRepository;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.triagem.TriagemRepository.FalhaEmAberto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fila de triagem, análise por IA e revisão do QA.
 *
 * <p>Transações com {@link TransactionTemplate} (e não {@code @Transactional}
 * na classe): a chamada à IA pode levar dezenas de segundos e NÃO deve
 * acontecer com uma conexão do banco presa. O fluxo de {@link #analisar}
 * é: lê (transação curta) → chama a IA (sem banco) → grava (transação curta).
 */
@Service
public class TriagemService {

    private final TriagemRepository triagens;
    private final JiraVinculoRepository vinculos;
    private final ResultadoTesteRepository resultados;
    private final ProjetoService projetos;
    private final AssistenteTriagem assistente;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TriagemService(TriagemRepository triagens, JiraVinculoRepository vinculos, ResultadoTesteRepository resultados,
                          ProjetoService projetos, AssistenteTriagem assistente, TransactionTemplate tx, Clock clock) {
        this.triagens = triagens;
        this.vinculos = vinculos;
        this.resultados = resultados;
        this.projetos = projetos;
        this.assistente = assistente;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Um item da fila.
     *
     * @param recorrente o teste já tem bug publicado e voltou a falhar depois disso
     * @param vinculos   o que o painel já fez no Jira por este teste (mais recente primeiro)
     */
    public record ItemFila(FalhaEmAberto falha, Triagem triagem, boolean recorrente, List<JiraVinculo> vinculos) {}

    /**
     * Fila de triagem: falhas da execução mais recente, menos as ocorrências
     * ignoradas e as de specs que NÃO EXISTEM MAIS no projeto (apagados ou
     * renomeados): esses testes nunca mais rodam, então nunca sairiam da fila.
     */
    public List<ItemFila> listar(String projetoId) {
        Projeto projeto = projetos.buscar(projetoId); // 404 se não existir
        // Pasta de specs ausente (ex.: CI sem o projeto de testes): não dá para saber, não filtra.
        Set<String> specsAtuais = Set.copyOf(projetos.listarSpecs(projeto));
        return tx.execute(status -> {
            List<FalhaEmAberto> falhas = triagens.listarFalhasEmAberto(projetoId).stream()
                    .filter(f -> specsAtuais.isEmpty() || specsAtuais.contains(f.getSpec()))
                    .toList();
            List<String> chaves = falhas.stream().map(FalhaEmAberto::getChave).toList();
            Map<String, Triagem> porChave = triagens.findByProjetoIdAndChaveTesteIn(projetoId, chaves)
                    .stream().collect(Collectors.toMap(Triagem::getChaveTeste, Function.identity()));
            Map<String, List<JiraVinculo>> historico = vinculos.findByProjetoIdAndChaveTesteInOrderByCriadoEmDesc(projetoId, chaves)
                    .stream().collect(Collectors.groupingBy(JiraVinculo::getChaveTeste));
            return falhas.stream()
                    .filter(f -> { Triagem t = porChave.get(f.getChave()); return t == null || !t.ignorada(f.getResultadoId()); })
                    .map(f -> {
                        Triagem t = porChave.get(f.getChave());
                        boolean recorrente = t != null && t.recorrente(f.getResultadoId(), f.getOcorridaEm());
                        return new ItemFila(f, t, recorrente, historico.getOrDefault(f.getChave(), List.of()));
                    })
                    .toList();
        });
    }

    /** Tira ESTA ocorrência da fila sem publicar nada. Se o teste falhar de novo, ele volta. */
    public void ignorar(Long resultadoId) {
        tx.executeWithoutResult(status -> {
            ResultadoTeste r = resultados.buscarComExecucao(resultadoId).orElseThrow(() -> naoEncontrado(resultadoId));
            Triagem t = obterOuCriar(r.getExecucao().getProjetoId(), r.getChave());
            t.ignorar(resultadoId, clock.instant());
            triagens.save(t);
        });
    }

    /**
     * "Ignorar todas": tira da fila as falhas ainda NÃO triadas (sem
     * classificação) e não recorrentes. As já triadas ficam, porque têm decisão do QA.
     *
     * @return quantas ocorrências foram ignoradas
     */
    public int ignorarPendentes(String projetoId) {
        List<ItemFila> pendentes = listar(projetoId).stream()
                .filter(i -> !i.recorrente() && (i.triagem() == null || i.triagem().getClassificacao() == null))
                .toList();
        pendentes.forEach(i -> ignorar(i.falha().getResultadoId()));
        return pendentes.size();
    }

    /** Dados da falha lidos do banco antes de falar com a IA. */
    private record Alvo(Long resultadoId, String projetoId, String chave, ContextoFalha contexto) {}

    public Triagem analisar(Long resultadoId) {
        Alvo alvo = tx.execute(status -> carregar(resultadoId));             // 1. lê
        RascunhoBug rascunho = assistente.gerarRascunho(alvo.contexto());    // 2. IA, sem banco
        return tx.execute(status -> {                                        // 3. grava
            Triagem t = obterOuCriar(alvo.projetoId(), alvo.chave());
            t.aplicarRascunho(rascunho, alvo.resultadoId(), clock.instant());
            return triagens.save(t);
        });
    }

    public Triagem salvar(Long resultadoId, Revisao r) {
        return tx.execute(status -> {
            ResultadoTeste res = resultados.buscarComExecucao(resultadoId).orElseThrow(() -> naoEncontrado(resultadoId));
            Triagem t = obterOuCriar(res.getExecucao().getProjetoId(), res.getChave());
            t.revisar(resultadoId, r.classificacao(), r.severidade(), r.titulo(), r.esperado(), r.encontrado(), r.passos(),
                    r.observacoes(), clock.instant());
            return triagens.save(t);
        });
    }

    /** O que o QA revisou na tela. */
    public record Revisao(Classificacao classificacao, Severidade severidade, String titulo, String esperado,
                          String encontrado, List<String> passos, String observacoes) {}

    private Alvo carregar(Long resultadoId) {
        ResultadoTeste r = resultados.buscarComExecucao(resultadoId).orElseThrow(() -> naoEncontrado(resultadoId));
        Execucao e = r.getExecucao();
        Projeto projeto = projetos.buscar(e.getProjetoId());
        String codigoSpec = lerCodigo(projeto.diretorio().resolve(r.getSpec()));
        var contexto = new ContextoFalha(projeto.nome(), r.getSpec(), r.getTitulo(), r.getMensagemErro(), r.getTipoErro(),
                e.getNavegador(), codigoSpec, projetos.regras(projeto).orElse(null),
                trechosDoSistema(projeto, r.getSpec(), codigoSpec, r.getTitulo(), r.getMensagemErro()));
        return new Alvo(r.getId(), e.getProjetoId(), r.getChave(), contexto);
    }

    private Triagem obterOuCriar(String projetoId, String chave) {
        return triagens.findByProjetoIdAndChaveTeste(projetoId, chave).orElseGet(() -> new Triagem(projetoId, chave));
    }

    /** Trechos do código do sistema testado ligados à falha, ou null (sem pastas configuradas, nada achado ou erro). */
    private static String trechosDoSistema(Projeto projeto, String spec, String codigoSpec, String titulo, String mensagemErro) {
        try {
            var achados = BuscadorCodigo.buscar(projeto.diretorio(), spec, codigoSpec, titulo, mensagemErro, projeto.codigoSistema());
            return achados.vazio() ? null : achados.formatar();
        } catch (RuntimeException e) {
            return null; // a busca é um bônus: se falhar, a triagem segue sem ela
        }
    }

    private static String lerCodigo(Path arquivo) {
        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            return null; // spec renomeado/apagado: a IA analisa só pela mensagem
        }
    }

    private static RecursoNaoEncontradoException naoEncontrado(Long id) {
        return new RecursoNaoEncontradoException("Resultado de teste %d não existe.".formatted(id));
    }
}
