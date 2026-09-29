import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { apiFalsa, renderComApp } from '../test/render'

// Testes de componente: o React renderiza de verdade (num DOM simulado),
// mas a API é falsa. O front é testado sem depender do backend no ar.

const projetos = [
  { id: 'cypress', nome: 'Cypress', tipo: 'CYPRESS', encontrado: true, instalado: true },
  { id: 'k6', nome: 'k6 (desempenho)', tipo: 'K6', encontrado: true, instalado: false },
]
const cypress = {
  ...projetos[0],
  baseUrl: 'https://www.saucedemo.com',
  navegadores: ['electron', 'chrome'],
  specs: ['cypress/e2e/checkout.cy.js', 'cypress/e2e/login.cy.js', 'cypress/e2e/user-behavior-matrix.cy.js'],
  scripts: [
    { nome: 'test', comando: 'cypress run --spec ...', specs: ['cypress/e2e/checkout.cy.js', 'cypress/e2e/login.cy.js'], navegador: null },
    { nome: 'test:diagnostics', comando: 'cypress run --spec ...', specs: ['cypress/e2e/user-behavior-matrix.cy.js'], navegador: null },
  ],
}
const k6 = { ...projetos[1], baseUrl: null, navegadores: [], specs: ['tests/smoke.js'], scripts: [] }

const execucao = {
  id: 7, projetoId: 'cypress', script: 'test:diagnostics', navegador: 'electron', status: 'FALHOU',
  iniciadaEm: '2026-09-28T23:50:58Z', finalizadaEm: '2026-09-28T23:51:35Z',
  total: 2, aprovados: 1, reprovados: 1, pulados: 0, duracaoMs: 37036, importada: true,
}
const resumo = {
  mes: '2026-09', execucoes: 3, testes: 70, aprovados: 58, reprovados: 12, aprovacao: 82.9,
  falhasPorModulo: [{ modulo: 'Matriz de usuários', falhas: 12, percentual: 100 }],
  ultimaExecucao: execucao,
  ultimaPorScript: { 'test:diagnostics': execucao },
}
const vazio = { ...resumo, execucoes: 0, testes: 0, aprovados: 0, reprovados: 0, aprovacao: null, falhasPorModulo: [], ultimaExecucao: null, ultimaPorScript: {} }

let fetchFalso: ReturnType<typeof apiFalsa>

beforeEach(() => {
  localStorage.clear()
  fetchFalso = apiFalsa({
    '/actuator/health': { status: 'UP' },
    '/api/projetos': projetos,
    '/api/projetos/cypress': cypress,
    '/api/projetos/k6': k6,
    '/api/execucoes/resumo?projeto=cypress': resumo,
    '/api/execucoes/resumo?projeto=k6': vazio,
    '/api/execucoes?projeto=cypress': [execucao],
    '/api/execucoes?projeto=k6': [],
    '/api/execucoes/7': {
      execucao, versaoFerramenta: 'Cypress 15.19.0', erro: null,
      resultados: [
        { id: 1, spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › standard_user › login', status: 'PASSOU', duracaoMs: 2000, mensagemErro: null, tipoErro: null },
        { id: 2, spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › problem_user › imagens', status: 'FALHOU', duracaoMs: 66, mensagemErro: 'AssertionError: imagens duplicadas', tipoErro: 'Asserção' },
      ],
    },
    '/api/importacoes/painel-node': { importadas: 5, ignoradas: 0, erros: [] },
  })
})
afterEach(() => vi.unstubAllGlobals())

describe('Visão geral', () => {
  it('mostra uma linha de execução rápida por script do package.json', async () => {
    renderComApp(<App />)

    const linhas = await screen.findByRole('region', { name: 'Execuções rápidas' })
    expect(await within(linhas).findByText('npm run test')).toBeInTheDocument()
    expect(within(linhas).getByText('npm run test:diagnostics')).toBeInTheDocument()
    expect(within(linhas).getByText('2 specs')).toBeInTheDocument()
  })

  it('mostra os números do resumo vindos do banco', async () => {
    renderComApp(<App />)

    expect(await screen.findByText('82,9%', { selector: '.kpi-value' })).toBeInTheDocument()
    expect(screen.getByText('Suíte instável')).toBeInTheDocument()
    expect(screen.getByText('Matriz de usuários')).toBeInTheDocument()
    // A linha do script mostra o resultado da última execução dele.
    const linhas = screen.getByRole('region', { name: 'Execuções rápidas' })
    expect(within(linhas).getByText('1 falha')).toBeInTheDocument()
    expect(within(linhas).getByText('Nunca executado')).toBeInTheDocument() // "test" nunca rodou
  })

  it('mostra a URL do ambiente e a API online na barra lateral', async () => {
    renderComApp(<App />)

    expect(await screen.findByText('www.saucedemo.com')).toBeInTheDocument()
    expect(await screen.findByText('Online')).toBeInTheDocument()
  })
})

describe('Seletor de projeto', () => {
  it('troca o projeto, avisa que o k6 não está instalado e lembra a escolha', async () => {
    const user = userEvent.setup()
    renderComApp(<App />)

    // Espera a lista chegar da API antes de escolher (senão só existe a opção atual).
    await screen.findByRole('option', { name: /k6/ })
    await user.selectOptions(screen.getByLabelText('Projeto de testes'), 'k6')

    expect(await screen.findByText(/Dependências do k6 \(desempenho\) não instaladas/)).toBeInTheDocument()
    expect(localStorage.getItem('qa-panel-pro:projeto')).toBe('k6')
  })
})

describe('Execuções', () => {
  it('a seleção rápida marca os specs do script escolhido', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'test:diagnostics' }))

    expect(screen.getByRole('checkbox', { name: /user-behavior-matrix/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /login/ })).not.toBeChecked()
    expect(screen.getByText('1 spec selecionado')).toBeInTheDocument()
  })

  it('lista o histórico e importa do painel Node com POST', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    expect(await screen.findByRole('link', { name: /test:diagnostics/ })).toHaveTextContent('importada do painel Node')

    await user.click(screen.getByRole('button', { name: 'Importar do painel Node' }))

    expect(await screen.findByText(/5 importadas/)).toBeInTheDocument()
    expect(fetchFalso).toHaveBeenCalledWith('/api/importacoes/painel-node', expect.objectContaining({ method: 'POST' }))
  })

  it('detalhe agrupa por spec e mostra o erro da falha', async () => {
    renderComApp(<App />, { rota: '/execucoes/7' })

    expect(await screen.findByRole('heading', { name: 'test:diagnostics' })).toBeInTheDocument()
    expect(screen.getByText('AssertionError: imagens duplicadas')).toBeInTheDocument()
    expect(screen.getByText('Asserção')).toBeInTheDocument()
    expect(screen.getByText('1 falha')).toBeInTheDocument()
  })

  it('"limpar" desmarca tudo e pede ao menos um spec', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'limpar' }))

    expect(screen.getByText('Selecione ao menos um spec')).toBeInTheDocument()
  })
})
