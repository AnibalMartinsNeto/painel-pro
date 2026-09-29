import { ApiError } from '../api/client'
import { useProjetoAtual } from '../features/projetos/ProjetoAtual'

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

/**
 * Aviso no topo das páginas quando o projeto selecionado não está pronto
 * (pasta ausente, dependências não instaladas ou API fora do ar).
 */
export function AvisoProjeto() {
  const { detalhe } = useProjetoAtual()
  if (detalhe.error) return <ErroApi erro={detalhe.error} />
  const p = detalhe.data
  if (!p) return null
  if (!p.encontrado) return <div className="note">Projeto {p.nome} não encontrado. Verifique o diretório no application.yml do backend.</div>
  if (!p.instalado)
    return (
      <div className="note">
        Dependências do {p.nome} não instaladas.{' '}
        {p.tipo === 'K6' ? (
          <>Instale com <code>winget install GrafanaLabs.k6</code>.</>
        ) : (
          <>Rode <code>npm install</code> na pasta do projeto.</>
        )}
      </div>
    )
  return null
}

/** Nota padrão das funcionalidades que ainda serão construídas. */
export function NotaEtapa({ etapa, children }: { etapa: number; children: React.ReactNode }) {
  return (
    <div className="note">
      <b style={{ color: 'var(--amber)' }}>Etapa {etapa}:</b> {children}
    </div>
  )
}
