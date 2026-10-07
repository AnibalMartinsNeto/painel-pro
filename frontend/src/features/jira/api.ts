// Chamadas da tela Jira (espelham JiraController.java).
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiGet, apiPost } from '../../api/client'

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
  origem: OrigemBug | null // a chave é um bug publicado pelo painel
}

/** O teste que encontrou um bug publicado pelo painel. */
export interface OrigemBug {
  spec: string
  teste: string
  demanda: string | null
  publicadaEm: string | null
  specExiste: boolean // false: o arquivo do teste foi apagado/renomeado
}

export interface BugPublicado {
  chave: string
  url: string
  titulo: string
  chaveTeste: string
  demanda: string | null
  publicadaEm: string
}

export interface IssueHistorico {
  chave: string
  resumo: string
  tipo: string | null
  status: string | null
  categoriaStatus: 'new' | 'indeterminate' | 'done' | null
  prioridade: string | null
  criadaEm: string | null
  doPainel: boolean // etiqueta "qa-panel"
  url: string
}

/** Últimas issues do projeto no Jira — consulta o Jira na hora, então só busca com o Jira configurado. */
export function useHistoricoJira(configurado: boolean) {
  return useQuery({
    queryKey: ['jira-historico'],
    queryFn: () => apiGet<IssueHistorico[]>('/api/jira/issues?maximo=50'),
    enabled: configurado,
    retry: false,
  })
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

export type SituacaoRequisito = 'COBERTO' | 'PARCIAL' | 'SEM_TESTE'

export interface CoberturaDemanda {
  chave: string
  titulo: string
  url: string
  resumo: string | null
  requisitos: { requisito: string; situacao: SituacaoRequisito; evidencias: string[]; cenarioSugerido: string | null }[]
  specsAnalisados: string[]
  criterioSpecs: string
  modelo: string
}

/** Mapa de cobertura (IA). Mutation, e não query: só roda quando o QA clica (custa uma chamada à IA). */
export function useCoberturaDemanda(projeto: string) {
  return useMutation({
    mutationFn: (chave: string) =>
      apiPost<CoberturaDemanda>(`/api/demandas/${encodeURIComponent(chave)}/cobertura?projeto=${encodeURIComponent(projeto)}`),
  })
}
