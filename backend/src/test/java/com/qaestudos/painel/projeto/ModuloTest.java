package com.qaestudos.painel.projeto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModuloTest {

    private final Modulo usuarios = new Modulo("Usuários", "usuario", "Usuários");

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "tests/admin-usuarios.spec.js,       true",
            "cypress/e2e/cadastro-usuario.cy.js, true",
            "cypress\\e2e\\Cadastro_Usuário.cy.js, true",  // Windows, maiúscula, acento e _
            "tests/admin-produtos.spec.js,       false",
            "tests/usuario/login.spec.js,        false",   // só o NOME do arquivo conta, não a pasta
    })
    void cobrePeloNomeDoArquivoSemAcentoNemSeparador(String spec, boolean esperado) {
        assertThat(usuarios.cobre(spec)).isEqualTo(esperado);
    }

    @Test
    void termoVazioNaoCobreNada() {
        assertThat(new Modulo("X", "", null).cobre("tests/x.spec.js")).isFalse();
        assertThat(usuarios.cobre(null)).isFalse();
    }
}
