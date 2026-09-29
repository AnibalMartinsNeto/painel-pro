// Cliente HTTP da aplicação: único lugar que sabe fazer chamadas à API.
// Os componentes nunca chamam fetch diretamente — usam as funções de
// cada feature (ex.: features/projetos/api.ts), que usam este cliente.

/** Formato de erro devolvido pelo backend (Problem Details, RFC 9457). */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
}

/** Erro de API com o status HTTP e a mensagem vinda do backend. */
export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export const apiGet = <T>(path: string) => requisitar<T>('GET', path)
export const apiPost = <T>(path: string, corpo?: unknown) => requisitar<T>('POST', path, corpo)

async function requisitar<T>(method: string, path: string, corpo?: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      method,
      headers: { Accept: 'application/json', ...(corpo !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    })
  } catch {
    // fetch só rejeita quando nem chegou a resposta: servidor fora do ar.
    throw new ApiError(0, 'Não foi possível conectar à API. O backend está rodando?')
  }
  if (!res.ok) {
    const problem = (await res.json().catch(() => undefined)) as ProblemDetail | undefined
    throw new ApiError(res.status, problem?.detail ?? `Erro ${res.status} ao chamar ${path}`, problem)
  }
  return (await res.json()) as T
}
