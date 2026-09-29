// Carregado antes de cada arquivo de teste (ver vite.config.ts).
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Adiciona matchers de DOM ao expect: toBeInTheDocument, toHaveTextContent...
import '@testing-library/jest-dom/vitest'

// Desmonta o que cada teste renderizou. Sem isso, a tela de um teste
// "vaza" para o próximo — foi o que fez o teste de 404 encontrar o
// alerta deixado pelo teste de "backend fora do ar".
afterEach(() => cleanup())
