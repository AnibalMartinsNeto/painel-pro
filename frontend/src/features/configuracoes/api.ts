// Tipos e chamadas da API de configurações (espelham ConfiguracoesDtos.java).
// Segredos são SOMENTE ESCRITA: a resposta só diz se estão configurados.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost, apiPut } from '../../api/client'

export type ProvedorIa = 'gemini' | 'anthropic'

export interface Configuracoes {
  ambiente: string | null
  ia: {
    provedor: ProvedorIa
    modeloAnthropic: string | null
    modeloGemini: string | null
    anthropicConfigurada: boolean
    geminiConfigurada: boolean
    ativa: boolean
  }
  azure: {
    organizacao: string | null
    projeto: string | null
    areaPath: string | null
    patConfigurado: boolean
    configurado: boolean
  }
}

export interface AtualizarConfiguracoes {
  ambiente?: string
  ia?: { provedor?: string; modeloAnthropic?: string; modeloGemini?: string; chaveAnthropic?: string; chaveGemini?: string }
  azure?: { organizacao?: string; projeto?: string; areaPath?: string; pat?: string }
  remover?: string[]
}

const CHAVE = ['configuracoes']

export function useConfiguracoes() {
  return useQuery({ queryKey: CHAVE, queryFn: () => apiGet<Configuracoes>('/api/configuracoes') })
}

export function useSalvarConfiguracoes() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pedido: AtualizarConfiguracoes) => apiPut<Configuracoes>('/api/configuracoes', pedido),
    // O PUT já devolve o estado novo: grava direto no cache, sem outra ida à API.
    onSuccess: (novo) => queryClient.setQueryData(CHAVE, novo),
  })
}

export function useImportarConfiguracoes() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiPost<{ importadas: string[]; aviso: string | null }>('/api/configuracoes/importar-painel-node'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CHAVE }),
  })
}
