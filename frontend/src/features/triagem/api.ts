// Tipos e chamadas da API de triagem (espelham TriagemController.java).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost, apiPut } from '../../api/client'

export type Classificacao = 'BUG_APLICACAO' | 'FALHA_AUTOMACAO' | 'AMBIENTE' | 'INSTAVEL' | 'BUG_CONHECIDO'
export type Severidade = 'CRITICA' | 'ALTA' | 'MEDIA' | 'BAIXA'

export const CLASSIFICACOES: Record<Classificacao, { rotulo: string; tom: 'bad' | 'warn' | 'info' | 'idle' }> = {
  BUG_APLICACAO: { rotulo: 'Bug da aplicação', tom: 'bad' },
  FALHA_AUTOMACAO: { rotulo: 'Falha de automação', tom: 'warn' },
  AMBIENTE: { rotulo: 'Ambiente', tom: 'info' },
  INSTAVEL: { rotulo: 'Instável (flaky)', tom: 'warn' },
  BUG_CONHECIDO: { rotulo: 'Bug conhecido', tom: 'idle' },
}

export const SEVERIDADES: Record<Severidade, string> = { CRITICA: 'Crítica', ALTA: 'Alta', MEDIA: 'Média', BAIXA: 'Baixa' }

export interface Triagem {
  classificacao: Classificacao | null
  classificacaoSugerida: Classificacao | null
  severidade: Severidade | null
  titulo: string | null
  esperado: string | null
  encontrado: string | null
  passos: string[]
  analise: string | null
  origemRascunho: 'IA' | 'HEURISTICA' | null
  modelo: string | null
  observacoes: string | null
  atualizadaEm: string
  jiraIssue: string | null
  jiraUrl: string | null
  demanda: string | null
}

export interface Publicacao {
  chave: string
  url: string
  demanda: string | null
  aviso: string | null
}

export interface FalhaTriagem {
  resultadoId: number
  execucaoId: number
  spec: string
  titulo: string
  chave: string
  mensagemErro: string | null
  tipoErro: string | null
  ocorridaEm: string
  navegador: string | null
  triagem: Triagem | null
}

export interface Revisao {
  classificacao: Classificacao | null
  severidade: Severidade | null
  titulo: string
  esperado: string
  encontrado: string
  passos: string[]
  observacoes: string
}

export const pendente = (f: FalhaTriagem) => !f.triagem?.classificacao

export function useFilaTriagem(projeto: string) {
  return useQuery({
    queryKey: ['triagem', projeto],
    queryFn: () => apiGet<FalhaTriagem[]>(`/api/triagem?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}

export function useAnalisar(projeto: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (resultadoId: number) => apiPost<Triagem>(`/api/triagem/${resultadoId}/analisar`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['triagem', projeto] }),
  })
}

export function usePublicarNoJira(projeto: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ resultadoId, demanda }: { resultadoId: number; demanda: string }) =>
      apiPost<Publicacao>(`/api/triagem/${resultadoId}/publicar`, { demanda: demanda || null }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['triagem', projeto] })
      queryClient.invalidateQueries({ queryKey: ['jira-bugs', projeto] })
    },
  })
}

export function useSalvarTriagem(projeto: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ resultadoId, revisao }: { resultadoId: number; revisao: Revisao }) =>
      apiPut<Triagem>(`/api/triagem/${resultadoId}`, revisao),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['triagem', projeto] }),
  })
}
