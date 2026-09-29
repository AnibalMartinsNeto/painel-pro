import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { ProjetoAtualProvider } from '../features/projetos/ProjetoAtual'

/**
 * Renderiza com os mesmos provedores da aplicação real (cache de dados,
 * roteador, projeto selecionado), mas isolado: cache novo a cada teste,
 * sem novas tentativas em erro e rota inicial controlada pelo teste.
 */
export function renderComApp(ui: ReactElement, { rota = '/' } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[rota]}>
        <ProjetoAtualProvider>{ui}</ProjetoAtualProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/**
 * API falsa: mapeia URL → corpo JSON (ou [status, corpo]). URLs não
 * mapeadas devolvem 404. Substitui o fetch global durante o teste.
 */
export function apiFalsa(rotas: Record<string, unknown>) {
  const fetchFalso = vi.fn((url: string) => {
    const resposta = rotas[url]
    const [status, corpo] = resposta === undefined ? [404, { status: 404, detail: `sem mock para ${url}` }]
      : Array.isArray(resposta) && typeof resposta[0] === 'number' ? resposta : [200, resposta]
    if (status === 204) return Promise.resolve(new Response(null, { status }))
    return Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }))
  })
  vi.stubGlobal('fetch', fetchFalso)
  return fetchFalso
}
