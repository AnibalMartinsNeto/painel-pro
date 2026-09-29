import { Link, useParams } from 'react-router'
import { Badge } from '../../components/Badge'
import { Carregando, ErroApi } from '../../components/Estado'
import { useProjeto } from './api'

/** Tela /projetos/:id — o ":id" da URL chega aqui via useParams(). */
export function ProjetoDetalhePage() {
  const { id = '' } = useParams()
  const { data: projeto, isPending, error } = useProjeto(id)

  return (
    <>
      <Link className="btn ghost sm" to="/projetos">
        ← Projetos
      </Link>
      {isPending && <Carregando />}
      {error && <ErroApi erro={error} />}
      {projeto && (
        <>
          <header className="page-head">
            <div>
              <h1>{projeto.nome}</h1>
              <p>
                <code>GET /api/projetos/{projeto.id}</code>
              </p>
            </div>
            {projeto.instalado ? <Badge tom="ok">Pronto para rodar</Badge> : <Badge tom="warn">Não instalado</Badge>}
          </header>

          <div className="stat-strip">
            <div className="stat">
              <b>{projeto.specs.length}</b>
              <span>Specs</span>
            </div>
            <div className="stat">
              <b>{projeto.navegadores.length || '—'}</b>
              <span>Navegadores</span>
            </div>
            <div className="stat">
              <b>{projeto.tipo}</b>
              <span>Ferramenta</span>
            </div>
          </div>

          <section className="card">
            <span className="eyebrow">Specs encontrados</span>
            {projeto.specs.length ? (
              <ul className="spec-list" aria-label="Specs">
                {projeto.specs.map((s) => (
                  <li key={s} className="check">
                    {s}
                  </li>
                ))}
              </ul>
            ) : (
              <div className="empty">Nenhum spec encontrado.</div>
            )}
          </section>

          {projeto.navegadores.length > 0 && (
            <section className="card">
              <span className="eyebrow">Navegadores suportados</span>
              <div className="btn-row" style={{ marginTop: 12 }}>
                {projeto.navegadores.map((n) => (
                  <Badge key={n} tom="idle">
                    {n}
                  </Badge>
                ))}
              </div>
            </section>
          )}

          <p className="hint">A execução dos specs a partir desta tela chega na etapa 4.</p>
        </>
      )}
    </>
  )
}
