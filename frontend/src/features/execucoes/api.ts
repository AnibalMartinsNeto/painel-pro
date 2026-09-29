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

export interface ResultadoImportacao {
  importadas: number
  ignoradas: number
  erros: string[]
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

/**
 * useMutation: para chamadas que ALTERAM dados (POST/PUT/DELETE). Ao
 * terminar, invalida o cache de ['execucoes'] — todas as telas que mostram
 * execuções buscam de novo e se atualizam sozinhas.
 */
export function useImportarPainelNode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiPost<ResultadoImportacao>('/api/importacoes/painel-node'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['execucoes'] }),
  })
}
