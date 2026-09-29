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

beforeEach(() => {
  localStorage.clear()
  apiFalsa({
    '/actuator/health': { status: 'UP' },
    '/api/projetos': projetos,
    '/api/projetos/cypress': cypress,
    '/api/projetos/k6': k6,
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

  it('"limpar" desmarca tudo e pede ao menos um spec', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'limpar' }))

    expect(screen.getByText('Selecione ao menos um spec')).toBeInTheDocument()
  })
})
