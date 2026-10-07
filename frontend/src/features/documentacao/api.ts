// Chamadas da aba Documentação (espelham DocumentoController.java).
import { useQuery } from '@tanstack/react-query'
import { apiGet, apiGetTexto } from '../../api/client'

export interface Documento {
  id: string // "serverest-qa/README.md"
  titulo: string
  pasta: string
}

export function useDocumentos(projeto: string) {
  return useQuery({
    queryKey: ['documentos', projeto],
    queryFn: () => apiGet<Documento[]>(`/api/documentos?projeto=${encodeURIComponent(projeto)}`),
    enabled: !!projeto,
  })
}

export function useConteudoDocumento(projeto: string, id: string | null) {
  return useQuery({
    queryKey: ['documentos', projeto, id],
    queryFn: () => apiGetTexto(`/api/documentos/conteudo?projeto=${encodeURIComponent(projeto)}&id=${encodeURIComponent(id!)}`),
    enabled: !!projeto && !!id,
  })
}
