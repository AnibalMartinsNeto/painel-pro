package com.qaestudos.painel.triagem.ia;

/**
 * Um serviço de IA generativa (Strategy). Recebe um prompt e devolve o
 * texto gerado. Quem monta o prompt e interpreta a resposta é o
 * {@link com.qaestudos.painel.triagem.AssistenteTriagem}; o provedor só
 * sabe falar o "dialeto" HTTP da sua API.
 */
public interface ProvedorIa {

    /** "gemini" ou "anthropic" — o mesmo valor da configuração ia.provedor. */
    String id();

    /** @return texto gerado e o nome do modelo que respondeu */
    Resposta gerar(String prompt, String modelo, String chave);

    record Resposta(String texto, String modelo) {}
}
