package com.qaestudos.painel.execucao.executor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * Teste PARAMETRIZADO: o mesmo teste roda para cada linha da tabela — a
 * versão JUnit do "data-driven" que a matriz de usuários faz no Cypress.
 */
class ClassificadorErroTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            AssertionError: expected true to equal false                                  | Asserção
            Error: expect(received).toBe(expected)                                        | Asserção
            Timed out retrying after 4000ms: Expected to find element: .btn, but never found it | Elemento / Seletor
            Error: expect(locator).toBeVisible() failed — element(s) not found           | Elemento / Seletor
            locator.click: Timeout 30000ms exceeded.                                      | Timeout
            Test timeout of 60000ms exceeded.                                             | Timeout
            cy.visit() failed trying to load: https://app                                 | Rede / Ambiente
            page.goto: net::ERR_NAME_NOT_RESOLVED                                         | Rede / Ambiente
            The following error originated from your application code                     | Exceção da aplicação
            TypeError: Cannot read properties of undefined                                | Erro no código do teste
            algo completamente diferente                                                  | Outro
            """)
    void classificaPelaMensagem(String mensagem, String categoria) {
        assertThat(ClassificadorErro.classificar(mensagem)).isEqualTo(categoria);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void semMensagemNaoTemCategoria(String mensagem) {
        assertThat(ClassificadorErro.classificar(mensagem)).isNull();
    }
}
