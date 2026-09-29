import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'

/**
 * Renderiza um componente com os mesmos provedores da aplicação real
 * (cache de dados + roteador), mas isolado: cache novo a cada teste,
 * sem novas tentativas em erro, e rota inicial controlada pelo teste.
 */
export function renderComApp(ui: ReactElement, { rota = '/', caminho = '*' } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[rota]}>
        <Routes>
          <Route path={caminho} element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** Substitui o fetch global por uma resposta falsa da API. */
export function respostaApi(status: number, corpo: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } }),
  )
}
