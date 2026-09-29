import { ApiError } from '../api/client'

/** Estados de tela reaproveitados em todas as páginas: carregando e erro. */
export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return (
    <div className="card empty" role="status">
      <span className="spinner" aria-hidden="true" /> {texto}
    </div>
  )
}

export function ErroApi({ erro }: { erro: Error }) {
  const titulo = erro instanceof ApiError && erro.status === 404 ? 'Não encontrado' : 'Erro ao carregar'
  return (
    <div className="card empty" role="alert">
      <b>{titulo}</b>
      {erro.message}
    </div>
  )
}

export function EmBreve({ titulo, etapa, descricao }: { titulo: string; etapa: number; descricao: string }) {
  return (
    <>
      <header className="page-head">
        <h1>{titulo}</h1>
        <span className="pill idle">Etapa {etapa}</span>
      </header>
      <div className="card empty">
        <b>Em construção</b>
        {descricao}
      </div>
    </>
  )
}
