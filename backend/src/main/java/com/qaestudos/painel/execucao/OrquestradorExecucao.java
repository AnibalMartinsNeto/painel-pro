package com.qaestudos.painel.execucao;

import com.qaestudos.painel.common.ConflitoException;
import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import com.qaestudos.painel.common.RequisicaoInvalidaException;
import com.qaestudos.painel.execucao.executor.ExecutorFerramenta;
import com.qaestudos.painel.execucao.executor.ExecutorFerramenta.Etapa;
import com.qaestudos.painel.execucao.executor.ExecutorFerramenta.Leitura;
import com.qaestudos.painel.execucao.executor.SolicitacaoExecucao;
import com.qaestudos.painel.projeto.Projeto;
import com.qaestudos.painel.projeto.ProjetoService;
import com.qaestudos.painel.projeto.TipoProjeto;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Coordena uma execução de testes do início ao fim:
 *
 * <ol>
 *   <li>valida o pedido e grava a execução como EM_ANDAMENTO;
 *   <li>numa thread própria (a requisição HTTP já respondeu), inicia os
 *       processos da ferramenta e repassa cada linha de saída ao log ao vivo;
 *   <li>ao terminar, pede à estratégia que leia os relatórios e grava os
 *       resultados, o status final e o log completo.
 * </ol>
 *
 * <p>Uma execução por vez: rodar dois navegadores de teste em paralelo na
 * mesma máquina deixaria os testes lentos e instáveis.
 */
@Service
public class OrquestradorExecucao {

    private static final Logger log = LoggerFactory.getLogger(OrquestradorExecucao.class);
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");

    /** Execução corrente: id, log, processo ativo e pedido de cancelamento. */
    static final class EmAndamento {
        final Long id;
        final LogAoVivo log = new LogAoVivo();
        volatile Process processo;
        volatile boolean cancelada;

        EmAndamento(Long id) {
            this.id = id;
        }
    }

    private final ProjetoService projetoService;
    private final ExecucaoGravacao gravacao;
    private final Map<TipoProjeto, ExecutorFerramenta> executores = new EnumMap<>(TipoProjeto.class);
    private final AtomicReference<EmAndamento> atual = new AtomicReference<>();
    private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> new Thread(r, "execucao-testes"));

    // O Spring injeta TODAS as implementações de ExecutorFerramenta numa lista.
    public OrquestradorExecucao(ProjetoService projetoService, ExecucaoGravacao gravacao, List<ExecutorFerramenta> estrategias) {
        this.projetoService = projetoService;
        this.gravacao = gravacao;
        estrategias.forEach(e -> executores.put(e.tipo(), e));
    }

    public Execucao iniciar(SolicitacaoExecucao s) {
        Projeto projeto = projetoService.buscar(s.projetoId());
        validar(projeto, s);

        EmAndamento reserva = new EmAndamento(null);
        // compareAndSet: só um pedido consegue "pegar a vez", mesmo com cliques simultâneos.
        if (!atual.compareAndSet(null, reserva)) {
            throw new ConflitoException("Já existe uma execução em andamento. Aguarde ou cancele antes de iniciar outra.");
        }
        try {
            Execucao execucao = gravacao.criar(s);
            EmAndamento emAndamento = new EmAndamento(execucao.getId());
            atual.set(emAndamento);
            thread.submit(() -> executar(emAndamento, projeto, s));
            return execucao;
        } catch (RuntimeException e) {
            atual.set(null);
            throw e;
        }
    }

    private void validar(Projeto projeto, SolicitacaoExecucao s) {
        if (!projetoService.status(projeto).instalado()) {
            throw new RequisicaoInvalidaException("O projeto %s não está pronto para rodar (dependências não instaladas).".formatted(projeto.nome()));
        }
        var disponiveis = new HashSet<>(projetoService.listarSpecs(projeto));
        List<String> invalidos = s.specs().stream().filter(sp -> !disponiveis.contains(sp)).toList();
        if (!invalidos.isEmpty()) {
            throw new RequisicaoInvalidaException("Specs inexistentes no projeto: " + String.join(", ", invalidos));
        }
        if (s.navegador() != null && !projeto.navegadores().isEmpty() && !projeto.navegadores().contains(s.navegador())) {
            throw new RequisicaoInvalidaException("Navegador '%s' não suportado. Use um de: %s".formatted(s.navegador(), projeto.navegadores()));
        }
    }

    private void executar(EmAndamento em, Projeto projeto, SolicitacaoExecucao s) {
        ExecutorFerramenta executor = executores.get(projeto.tipo());
        Path pasta = null;
        StatusExecucao status = StatusExecucao.ERRO;
        try {
            pasta = Files.createTempDirectory("qapanel-execucao-" + em.id + "-");
            for (Etapa etapa : executor.etapas(projeto, s, pasta)) {
                if (em.cancelada) break;
                rodarProcesso(em, projeto, etapa);
            }
            if (em.cancelada) {
                em.log.adicionar("[painel] Execução cancelada pelo usuário.");
                status = StatusExecucao.CANCELADA;
                gravacao.concluir(em.id, status, List.of(), null, "Execução cancelada pelo usuário.", em.log.texto());
                return;
            }
            Leitura leitura = executor.ler(projeto, s, pasta);
            status = leitura.resultados().isEmpty() ? StatusExecucao.ERRO
                    : leitura.resultados().stream().anyMatch(r -> r.getStatus() == StatusTeste.FALHOU) ? StatusExecucao.FALHOU
                    : StatusExecucao.PASSOU;
            String erro = leitura.erro() != null ? leitura.erro()
                    : leitura.resultados().isEmpty() ? "A execução terminou sem nenhum resultado de teste." : null;
            gravacao.concluir(em.id, status, leitura.resultados(), leitura.versaoFerramenta(), erro, em.log.texto());
        } catch (Exception e) {
            log.error("Falha na execução {}", em.id, e);
            em.log.adicionar("[painel] Erro inesperado: " + e.getMessage());
            status = StatusExecucao.ERRO;
            gravacao.concluir(em.id, status, List.of(), null, "Erro ao executar: " + e.getMessage(), em.log.texto());
        } finally {
            em.log.encerrar(status);
            atual.compareAndSet(em, null);
            apagar(pasta);
        }
    }

    private void rodarProcesso(EmAndamento em, Projeto projeto, Etapa etapa) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(etapa.comando())
                .directory(projeto.diretorio().toFile())
                .redirectErrorStream(true); // junta stderr no stdout: um único fluxo de log
        pb.environment().put("FORCE_COLOR", "0");
        pb.environment().putAll(etapa.ambiente());

        em.log.adicionar("[painel] ▶ " + String.join(" ", etapa.comando()));
        Process processo;
        try {
            processo = pb.start();
        } catch (IOException e) {
            em.log.adicionar("[painel] Não foi possível iniciar '" + etapa.comando().getFirst() + "': " + e.getMessage());
            return;
        }
        em.processo = processo;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = r.readLine()) != null) {
                em.log.adicionar(ANSI.matcher(linha).replaceAll(""));
            }
        }
        int codigo = processo.waitFor();
        em.log.adicionar("[painel] processo terminou com código " + codigo);
    }

    public void cancelar(Long id) {
        EmAndamento em = atual.get();
        if (em == null || !id.equals(em.id)) {
            throw new ConflitoException("A execução %d não está em andamento.".formatted(id));
        }
        em.cancelada = true;
        Process p = em.processo;
        if (p != null) {
            // Mata a árvore inteira: o Cypress/Playwright abre navegadores como processos filhos.
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    public Optional<Long> emAndamento() {
        return Optional.ofNullable(atual.get()).map(e -> e.id);
    }

    /** Log ao vivo (SSE) da execução corrente; 404 se ela não estiver rodando. */
    public SseEmitter assinarLog(Long id) {
        EmAndamento em = atual.get();
        if (em == null || !id.equals(em.id)) {
            throw new RecursoNaoEncontradoException("A execução %d não está em andamento; use o log gravado.".formatted(id));
        }
        return em.log.assinar();
    }

    private static void apagar(Path pasta) {
        if (pasta == null) return;
        try (Stream<Path> s = Files.walk(pasta)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.debug("Não foi possível apagar {}", pasta, e);
        }
    }

    @PreDestroy
    void desligar() {
        EmAndamento em = atual.get();
        if (em != null) cancelar(em.id);
        thread.shutdownNow();
    }
}
