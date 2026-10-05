// Chamadas da tela Relatórios (espelham RelatorioController.java).
import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../../api/client'
import type { ExecucaoResumo } from '../execucoes/api'

export interface TesteComFalha {
  chave: string
  spec: string
  titulo: string
  modulo: string
  tipoErro: string | null
  ultimaMensagem: string | null
  falhas: number
  execucoes: number
  instavel: boolean
  ultimaFalha: string | null
  ultimoResultadoId: number | null
}

export interface Relatorio {
  execucoes: number
  testesUnicos: number
  jaFalharam: number
  instaveis: number
  tempoTotalMs: number
  aprovacaoPorExecucao: ExecucaoResumo[] // da mais antiga para a mais recente
  testesComFalha: TesteComFalha[]
}

export function useRelatorio(projeto: string) {
  return useQuery({
    // Começa com 'execucoes': quando uma execução termina, o invalidate de
    // ['execucoes'] (log ao vivo) também atualiza o relatório.
    queryKey: ['execucoes', projeto, 'relatorio'],
    queryFn: () => apiGet<Relatorio>(`/api/relatorios?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}

/** URL de download do CSV: o próprio backend manda o arquivo (Content-Disposition). */
export const urlCsv = (projeto: string) => `/api/relatorios/execucoes.csv?projeto=${encodeURIComponent(projeto)}`
