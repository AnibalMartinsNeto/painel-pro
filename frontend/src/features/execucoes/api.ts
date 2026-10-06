// Tipos e chamadas da API de execuções (espelham ExecucaoDtos.java).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { apiGet, apiPost } from '../../api/client'

export type StatusExecucao = 'EM_ANDAMENTO' | 'PASSOU' | 'FALHOU' | 'ERRO' | 'CANCELADA'
export type StatusTeste = 'PASSOU' | 'FALHOU' | 'PENDENTE' | 'PULADO'

export interface ExecucaoResumo {
  id: number
  projetoId: string
  script: string | null
  navegador: string | null
  status: StatusExecucao
  iniciadaEm: string // datas chegam como texto ISO-8601 no JSON
  finalizadaEm: string | null
  total: number
  aprovados: number
  reprovados: number
  pulados: number
  duracaoMs: number | null
  importada: boolean
  /** Execução de teste: fica no histórico, mas fora das métricas, relatórios e triagem. */
  dev?: boolean
}

export interface ResultadoTeste {
  id: number
  spec: string
  titulo: string
  status: StatusTeste
  duracaoMs: number | null
  mensagemErro: string | null
  tipoErro: string | null
}

export interface ExecucaoDetalhe {
  execucao: ExecucaoResumo
  versaoFerramenta: string | null
  erro: string | null
  resultados: ResultadoTeste[]
}

export interface ResumoProjeto {
  mes: string
  execucoes: number
  testes: number
  aprovados: number
  reprovados: number
  aprovacao: number | null
  falhasPorModulo: { modulo: string; falhas: number; percentual: number }[]
  ultimaExecucao: ExecucaoResumo | null
  ultimaPorScript: Record<string, ExecucaoResumo>
}

export function useExecucoes(projeto: string) {
  return useQuery({
    queryKey: ['execucoes', projeto],
    queryFn: () => apiGet<ExecucaoResumo[]>(`/api/execucoes?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}

export function useResumo(projeto: string) {
  return useQuery({
    queryKey: ['execucoes', projeto, 'resumo'],
    queryFn: () => apiGet<ResumoProjeto>(`/api/execucoes/resumo?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}

export function useExecucao(id: string) {
  return useQuery({
    queryKey: ['execucao', id],
    queryFn: () => apiGet<ExecucaoDetalhe>(`/api/execucoes/${encodeURIComponent(id)}`),
  })
}

export interface NovaExecucao {
  projeto: string
  script: string | null
  specs: string[]
  navegador: string | null
  retentativas: number
  abrirNavegador: boolean
  dev?: boolean
}

/** Base da previsão de tempo: média de cada spec nas execuções reais + tempo fixo de uma execução. */
export interface Duracoes {
  tempoFixoMs: number
  mediaPorSpecMs: Record<string, number>
}

export function useDuracoes(projeto: string) {
  return useQuery({
    queryKey: ['execucoes', projeto, 'duracoes'],
    queryFn: () => apiGet<Duracoes>(`/api/execucoes/duracoes?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}

/**
 * Previsão para um conjunto de specs: eles rodam em SEQUÊNCIA, então é a
 * soma das médias + o tempo fixo. Só há previsão se TODOS tiverem histórico
 * (uma soma parcial subestimaria o tempo sem avisar).
 */
export function preverDuracao(d: Duracoes | undefined, specs: string[]): { ms: number } | { semHistorico: number } | null {
  if (!d || specs.length === 0) return null
  const semHistorico = specs.filter((s) => d.mediaPorSpecMs[s] == null).length
  if (semHistorico > 0) return { semHistorico }
  return { ms: d.tempoFixoMs + specs.reduce((total, s) => total + d.mediaPorSpecMs[s], 0) }
}

/**
 * Execução rodando agora (ou null). Consulta a cada 3s: é assim que todas
 * as telas — inclusive em outra aba — ficam sabendo que algo começou.
 */
export function useEmAndamento() {
  return useQuery({
    queryKey: ['em-andamento'],
    queryFn: () => apiGet<ExecucaoResumo | null>('/api/execucoes/em-andamento'),
    refetchInterval: 3_000,
  })
}

export function useIniciarExecucao() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pedido: NovaExecucao) => apiPost<ExecucaoResumo>('/api/execucoes', pedido),
    onSuccess: (execucao) => queryClient.setQueryData(['em-andamento'], execucao),
  })
}

export function useCancelarExecucao() {
  return useMutation({ mutationFn: (id: number) => apiPost<void>(`/api/execucoes/${id}/cancelar`) })
}

/**
 * Log ao vivo via SSE. O EventSource é a API do navegador para Server-Sent
 * Events: abre a conexão, recebe cada evento "linha" e, no "fim", fecha e
 * manda atualizar histórico, resumo e "em andamento".
 */
export function useLogAoVivo(id: number | null) {
  const queryClient = useQueryClient()
  const [linhas, setLinhas] = useState<string[]>([])
  const [statusFinal, setStatusFinal] = useState<StatusExecucao | null>(null)

  useEffect(() => {
    if (id == null || typeof EventSource === 'undefined') return
    setLinhas([])
    setStatusFinal(null)
    const fonte = new EventSource(`/api/execucoes/${id}/log`)
    fonte.addEventListener('linha', (e) => setLinhas((atual) => [...atual, (e as MessageEvent).data]))
    fonte.addEventListener('fim', (e) => {
      setStatusFinal((e as MessageEvent).data as StatusExecucao)
      fonte.close()
      queryClient.invalidateQueries({ queryKey: ['execucoes'] })
      queryClient.invalidateQueries({ queryKey: ['em-andamento'] })
    })
    fonte.onerror = () => fonte.close() // execução já terminou ou backend caiu
    // "Cleanup": ao trocar de execução ou sair da tela, fecha a conexão.
    return () => fonte.close()
  }, [id, queryClient])

  return { linhas, statusFinal }
}

