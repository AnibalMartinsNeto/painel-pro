package com.qaestudos.painel.execucao;

import com.qaestudos.painel.common.RecursoNaoEncontradoException;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Serve o arquivo de uma evidência (screenshot direto no &lt;img&gt;; trace para baixar). */
@RestController
public class EvidenciaController {

    private final EvidenciaRepository evidencias;
    private final ArmazemEvidencias armazem;

    public EvidenciaController(EvidenciaRepository evidencias, ArmazemEvidencias armazem) {
        this.evidencias = evidencias;
        this.armazem = armazem;
    }

    /**
     * GET /api/evidencias/7 → o arquivo. Imagem vai "inline" (o navegador
     * mostra); o resto vai como download. Cache longo: uma evidência gravada
     * nunca muda.
     */
    @GetMapping("/api/evidencias/{id}")
    public ResponseEntity<Resource> abrir(@PathVariable Long id) {
        Evidencia e = evidencias.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evidência %d não existe.".formatted(id)));
        var arquivo = armazem.abrir(e.getArquivo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("O arquivo da evidência %d não está mais na pasta de evidências.".formatted(id)));
        ContentDisposition disposicao = (e.imagem() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(e.getNome()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.getTipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(new FileSystemResource(arquivo));
    }
}
