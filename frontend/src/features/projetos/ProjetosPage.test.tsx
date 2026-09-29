import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderComApp, respostaApi } from '../../test/render'
import { ProjetoDetalhePage } from './ProjetoDetalhePage'
import { ProjetosPage } from './ProjetosPage'

// Testes de componente: o React renderiza de verdade (num DOM simulado),
// mas a API é falsa — o fetch devolve o que cada teste definir. Assim o
// front é testado sem depender do backend estar no ar.
afterEach(() => vi.unstubAllGlobals())

describe('ProjetosPage', () => {
  it('mostra um card por projeto com o status de instalação', async () => {
    vi.stubGlobal('fetch', vi.fn(() =>
      respostaApi(200, [
        { id: 'cypress', nome: 'Cypress', tipo: 'CYPRESS', encontrado: true, instalado: true },
        { id: 'k6', nome: 'k6 (desempenho)', tipo: 'K6', encontrado: true, instalado: false },
      ]),
    ))

    renderComApp(<ProjetosPage />)

    expect(await screen.findByRole('link', { name: /Abrir projeto Cypress/ })).toHaveTextContent('Pronto para rodar')
    expect(screen.getByRole('link', { name: /Abrir projeto k6/ })).toHaveTextContent('Dependências não instaladas')
    expect(screen.getByText('1 de 2 prontos')).toBeInTheDocument()
  })

  it('mostra mensagem clara quando o backend está fora do ar', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))

    renderComApp(<ProjetosPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('O backend está rodando?')
  })
})

describe('ProjetoDetalhePage', () => {
  it('lista os specs do projeto da URL', async () => {
    const fetchFalso = vi.fn(() =>
      respostaApi(200, {
        id: 'playwright', nome: 'Playwright', tipo: 'PLAYWRIGHT', encontrado: true, instalado: true,
        navegadores: ['chromium'], specs: ['tests/login.spec.js', 'tests/checkout.spec.js'],
      }),
    )
    vi.stubGlobal('fetch', fetchFalso)

    renderComApp(<ProjetoDetalhePage />, { rota: '/projetos/playwright', caminho: '/projetos/:id' })

    expect(await screen.findByRole('heading', { name: 'Playwright' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Specs' }).children).toHaveLength(2)
    expect(fetchFalso).toHaveBeenCalledWith('/api/projetos/playwright', expect.anything())
  })

  it('mostra a mensagem do Problem Detail quando o projeto não existe', async () => {
    vi.stubGlobal('fetch', vi.fn(() =>
      respostaApi(404, { status: 404, title: 'Recurso não encontrado', detail: "Projeto 'xyz' não existe." }),
    ))

    renderComApp(<ProjetoDetalhePage />, { rota: '/projetos/xyz', caminho: '/projetos/:id' })

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Não encontrado')
    expect(alerta).toHaveTextContent("Projeto 'xyz' não existe.")
  })
})
