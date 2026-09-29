import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App } from './App'
import './styles/global.css'

// Ponto de entrada do front: monta o React dentro da <div id="root"> do
// index.html e "embrulha" a aplicação com os provedores globais:
//  - QueryClientProvider: cache de dados vindos da API (TanStack Query)
//  - BrowserRouter: navegação por URL (React Router)
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 5_000 } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
