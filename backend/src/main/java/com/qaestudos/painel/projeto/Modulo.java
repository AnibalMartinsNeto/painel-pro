package com.qaestudos.painel.projeto;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Um módulo do sistema testado (ex.: "Usuários"), configurado por projeto.
 *
 * <p>O {@code termo} é procurado no nome do arquivo do spec, sem acento,
 * maiúscula nem separador: {@code usuario} cobre {@code admin-usuarios.spec.js}
 * e {@code cadastro-usuario.cy.js}. O módulo serve para três coisas: agrupar
 * reprovações (Dashboard e Relatórios), filtrar specs na tela de Execuções e
 * escolher a SEÇÃO certa das regras de negócio que vai para a IA.
 *
 * @param secaoRegras título da seção ("## ...") do arquivo de regras deste módulo (opcional)
 */
public record Modulo(String rotulo, String termo, String secaoRegras) {

    public boolean cobre(String spec) {
        if (spec == null || termo == null || termo.isBlank()) return false;
        String arquivo = spec.substring(Math.max(spec.lastIndexOf('/'), spec.lastIndexOf('\\')) + 1);
        return normalizar(arquivo).contains(normalizar(termo));
    }

    /** "Admin-Usuários.spec.js" → "adminusuariosspecjs". */
    static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
