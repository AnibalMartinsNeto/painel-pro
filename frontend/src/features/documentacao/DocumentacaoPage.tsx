import { useEffect } from 'react'
import { useSearchParams } from 'react-router'
import { Carregando, ErroApi } from '../../components/Estado'
import { Markdown } from '../../lib/markdown'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import { useConteudoDocumento, useDocumentos } from './api'

/**
 * Tela /documentacao: os .md do projeto de testes (README, regras de
 * negócio...) lidos dentro do painel. O documento aberto fica na URL
 * (?doc=...), então dá para mandar o link.
 */
export function DocumentacaoPage() {
  const { id: projeto, detalhe } = useProjetoAtual()
  const documentos = useDocumentos(projeto)
  const [params, setParams] = useSearchParams()
  const aberto = params.get('doc') ?? documentos.data?.[0]?.id ?? null
  const conteudo = useConteudoDocumento(projeto, aberto)

  // Trocou de projeto e o documento aberto não existe nele: volta para o primeiro.
  useEffect(() => {
    if (documentos.data && aberto && !documentos.data.some((d) => d.id === aberto)) setParams({}, { replace: true })
  }, [documentos.data, aberto, setParams])

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Documentação</h1>
          <p>Os documentos de {detalhe.data?.nome ?? projeto}: README, regras de negócio e o que mais estiver em Markdown no projeto.</p>
        </div>
      </header>
      {documentos.isPending && <Carregando />}
      {documentos.error && <ErroApi erro={documentos.error} />}
      {documentos.data?.length === 0 && <div className="card empty">Nenhum arquivo .md na pasta do projeto.</div>}
      {!!documentos.data?.length && (
        <div className="split">
          <section className="card flat" aria-label="Documentos">
            <div className="list">
              {documentos.data.map((d) => (
                <button
                  key={d.id}
                  type="button"
                  className={`list-item ${d.id === aberto ? 'sel' : ''}`}
                  style={{ textAlign: 'left', background: 'none', border: 0, borderTop: '1px solid var(--card-border)', width: '100%', color: 'inherit' }}
                  onClick={() => setParams({ doc: d.id })}
                >
                  <div className="li-main">
                    <div className="li-title">{d.titulo}</div>
                    <div className="li-sub">{d.id}</div>
                  </div>
                </button>
              ))}
            </div>
          </section>
          <article className="card" aria-label="Documento">
            {conteudo.isPending && aberto && <Carregando />}
            {conteudo.error && <ErroApi erro={conteudo.error} />}
            {conteudo.data && <Markdown texto={conteudo.data} />}
          </article>
        </div>
      )}
    </>
  )
}
