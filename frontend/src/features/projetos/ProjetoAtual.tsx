import { createContext, useContext, useState, type ReactNode } from 'react'
import { useProjeto, useProjetos } from './api'

// Projeto escolhido no seletor da barra lateral (Cypress, Playwright, k6).
//
// Várias telas precisam dele. Em vez de passá-lo de componente em
// componente por props, ele fica num CONTEXTO: qualquer componente dentro
// do <ProjetoAtualProvider> lê com useProjetoAtual().
const CHAVE = 'qa-panel-pro:projeto'

function lerSalvo(): string {
  try {
    return localStorage.getItem(CHAVE) ?? 'cypress'
  } catch {
    return 'cypress'
  }
}

type ProjetoAtualValor = {
  id: string
  selecionar: (id: string) => void
  projetos: ReturnType<typeof useProjetos>
  detalhe: ReturnType<typeof useProjeto>
}

const ProjetoAtualContext = createContext<ProjetoAtualValor | null>(null)

export function ProjetoAtualProvider({ children }: { children: ReactNode }) {
  const [id, setId] = useState(lerSalvo)
  const projetos = useProjetos()
  const detalhe = useProjeto(id)

  const selecionar = (novo: string) => {
    setId(novo)
    try {
      localStorage.setItem(CHAVE, novo) // lembra a escolha ao recarregar
    } catch {
      /* navegação privada: segue sem lembrar */
    }
  }

  return <ProjetoAtualContext value={{ id, selecionar, projetos, detalhe }}>{children}</ProjetoAtualContext>
}

export function useProjetoAtual() {
  const valor = useContext(ProjetoAtualContext)
  if (!valor) throw new Error('useProjetoAtual precisa estar dentro de <ProjetoAtualProvider>')
  return valor
}
