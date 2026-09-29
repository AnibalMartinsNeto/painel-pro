// Tipos e chamadas da API de execuções (espelham ExecucaoDtos.java).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
