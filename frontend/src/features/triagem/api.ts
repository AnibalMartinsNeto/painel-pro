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
  /** Já tem bug publicado e voltou a falhar depois disso. */
  recorrente: boolean
  /** O que o painel já fez no Jira por este teste (mais recente primeiro). */
  vinculos: VinculoJira[]
}

export interface VinculoJira {
  chave: string
  url: string | null
  acao: 'CRIADO' | 'COMENTADO'
  em: string
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

/** Precisa de atenção: ainda não triada, ou recorrente (o bug existe, mas a falha voltou). */
export const pendente = (f: FalhaTriagem) => !f.triagem?.classificacao || f.recorrente

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
    mutationFn: ({ resultadoId, demanda, novoBug = false }: { resultadoId: number; demanda: string; novoBug?: boolean }) =>
      apiPost<Publicacao>(`/api/triagem/${resultadoId}/publicar`, { demanda: demanda || null, novoBug }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['triagem', projeto] })
      queryClient.invalidateQueries({ queryKey: ['jira-bugs', projeto] })
      queryClient.invalidateQueries({ queryKey: ['jira-historico'] })
    },
  })
}

/** Falha recorrente: comenta a nova ocorrência no bug que já existe (em vez de criar outro). */
export function useComentarOcorrencia(projeto: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (resultadoId: number) => apiPost<{ chave: string; url: string }>(`/api/triagem/${resultadoId}/comentar`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['triagem', projeto] }),
  })
}

/** Tira esta ocorrência da fila sem publicar. Se o teste falhar de novo, ela volta. */
export function useIgnorar(projeto: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (resultadoId: number) => apiPost<void>(`/api/triagem/${resultadoId}/ignorar`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['triagem', projeto] }),
  })
}

/** "Ignorar pendentes": tira da fila todas as falhas ainda não triadas. */
export function useIgnorarPendentes(projeto: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiPost<{ ignoradas: number }>(`/api/triagem/ignorar-pendentes?projeto=${encodeURIComponent(projeto)}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['triagem', projeto] }),
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
