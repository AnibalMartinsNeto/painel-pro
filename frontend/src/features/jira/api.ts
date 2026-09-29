// Chamadas da tela Jira (espelham JiraController.java).
import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../../api/client'

export interface IssueJira {
  chave: string
  resumo: string
  tipo: string
  status: string | null
  url: string
}

export interface Demanda {
  chave: string
  issue: IssueJira | null
  erro: string | null // Jira indisponível: ainda assim vêm os specs
  specs: string[]
}

export interface BugPublicado {
  chave: string
  url: string
  titulo: string
  chaveTeste: string
  demanda: string | null
  publicadaEm: string
}

/** Busca só quando há uma chave para buscar (enabled). */
export function useDemanda(projeto: string, chave: string) {
  return useQuery({
    queryKey: ['demanda', projeto, chave],
    queryFn: () => apiGet<Demanda>(`/api/jira/demandas/${encodeURIComponent(chave)}?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto && !!chave,
    retry: false,
  })
}

export function useBugsPublicados(projeto: string) {
  return useQuery({
    queryKey: ['jira-bugs', projeto],
    queryFn: () => apiGet<BugPublicado[]>(`/api/jira/bugs?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}
