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
    private final ResultadoTesteRepository resultados;
    private final ProjetoService projetos;
    private final AssistenteTriagem assistente;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TriagemService(TriagemRepository triagens, ResultadoTesteRepository resultados, ProjetoService projetos,
                          AssistenteTriagem assistente, TransactionTemplate tx, Clock clock) {
        this.triagens = triagens;
        this.resultados = resultados;
        this.projetos = projetos;
        this.assistente = assistente;
        this.tx = tx;
        this.clock = clock;
    }

    public record ItemFila(FalhaEmAberto falha, Triagem triagem) {}

    public List<ItemFila> listar(String projetoId) {
        projetos.buscar(projetoId); // 404 se não existir
        return tx.execute(status -> {
            List<FalhaEmAberto> falhas = triagens.listarFalhasEmAberto(projetoId);
            Map<String, Triagem> porChave = triagens
                    .findByProjetoIdAndChaveTesteIn(projetoId, falhas.stream().map(FalhaEmAberto::getChave).toList())
                    .stream().collect(Collectors.toMap(Triagem::getChaveTeste, Function.identity()));
            return falhas.stream().map(f -> new ItemFila(f, porChave.get(f.getChave()))).toList();
        });
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
