import { useQuery } from '@tanstack/react-query'
import { apiGet } from '../api/client'

/**
 * Indicador na barra lateral: consulta o /actuator/health do backend a
 * cada 10s. Deixa visível a ligação front → API em tempo real.
 */
export function ApiStatus() {
  const { data, isError, isPending } = useQuery({
    queryKey: ['health'],
    queryFn: () => apiGet<{ status: string }>('/actuator/health'),
    refetchInterval: 10_000,
    retry: false,
  })

  const online = !isError && data?.status === 'UP'
  const texto = isPending ? 'Verificando…' : online ? 'Online' : 'Fora do ar'

  return (
    <div>
      <span className="side-label">API (Spring Boot)</span>
      <div className={`status-line ${isPending ? 'off' : online ? '' : 'bad'}`} role="status">
        <span className="dot" aria-hidden="true" />
        <span>{texto}</span>
      </div>
      <div className="side-sub">localhost:8080</div>
    </div>
  )
}
