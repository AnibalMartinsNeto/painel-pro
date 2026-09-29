// Tipos e chamadas de API da feature "projetos".
//
// Os tipos abaixo espelham os DTOs do backend Java:
//   ProjetoResumo  ↔ ProjetoResumoResponse.java
//   ProjetoDetalhe ↔ ProjetoDetalheResponse.java
// Se o contrato mudar no Java, atualize aqui — o TypeScript então aponta
// todos os pontos do front que precisam de ajuste.
import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../../api/client'

export type TipoProjeto = 'CYPRESS' | 'PLAYWRIGHT' | 'K6'

export interface ProjetoResumo {
  id: string
  nome: string
  tipo: TipoProjeto
  encontrado: boolean
  instalado: boolean
}

export interface ProjetoDetalhe extends ProjetoResumo {
  navegadores: string[]
  specs: string[]
}

export const listarProjetos = () => apiGet<ProjetoResumo[]>('/api/projetos')
export const buscarProjeto = (id: string) => apiGet<ProjetoDetalhe>(`/api/projetos/${encodeURIComponent(id)}`)

// Hooks do TanStack Query: cuidam de loading, erro, cache e nova tentativa.
// A "queryKey" identifica o dado no cache — duas telas que pedem
// ['projetos'] compartilham a mesma resposta em vez de chamar a API duas vezes.
export function useProjetos() {
  return useQuery({ queryKey: ['projetos'], queryFn: listarProjetos })
}

export function useProjeto(id: string) {
  return useQuery({ queryKey: ['projetos', id], queryFn: () => buscarProjeto(id) })
}
