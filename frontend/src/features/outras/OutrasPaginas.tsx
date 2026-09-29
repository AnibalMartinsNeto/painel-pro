import { Link } from 'react-router'
import { NotaEtapa } from '../../components/Estado'
import { useConfiguracoes } from '../configuracoes/api'

// Telas das etapas futuras, com o mesmo cabeçalho e estados vazios do
// painel Node. Cada uma ganha sua pasta em features/ quando for construída.

export function TriagemPage() {
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Triagem IA</h1>
          <p>Falhas mais recentes de cada teste. A IA lê o erro e o código do spec e sugere o bug.</p>
        </div>
        <span className="pill idle">Não configurado</span>
      </header>
      <div className="card empty">
        <b>Nenhuma falha registrada</b>Quando um teste falhar ele aparece aqui para triagem.
      </div>
      <NotaEtapa etapa={6}>análise das falhas com IA (Gemini ou Claude) e rascunho do bug editável.</NotaEtapa>
    </>
  )
}

export function JiraPage() {
  const { data: config } = useConfiguracoes()
  const projeto = config?.jira.projeto ?? 'QA'
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Jira</h1>
          <p>Busque testes pela chave da issue e acompanhe os bugs publicados pelo painel.</p>
        </div>
        {config?.jira.configurado ? (
          <span className="pill ok">Projeto {config.jira.projeto}</span>
        ) : (
          <Link className="pill idle" to="/configuracoes">Não configurado · configurar</Link>
        )}
      </header>
      <section className="card">
        <span className="eyebrow" style={{ display: 'block', marginBottom: 12 }}>
          Testes por issue
        </span>
        <div className="btn-row">
          <input className="input" style={{ maxWidth: 200 }} placeholder={`${projeto}-123`} disabled />
          <button className="btn primary" disabled>
            Buscar
          </button>
        </div>
        <p className="hint" style={{ margin: '10px 0 0' }}>
          O painel procura a chave da issue (ex.: <code>{projeto}-123</code>) no título ou no código dos specs.
        </p>
      </section>
      <section className="card flat">
        <div className="card-head" style={{ padding: '16px 16px 0' }}>
          <span className="eyebrow">Bugs publicados</span>
          <span className="hint">0</span>
        </div>
        <div className="empty">Nenhum bug publicado ainda.</div>
      </section>
      <NotaEtapa etapa={6}>busca de specs pela issue e criação do bug no Jira, ligado à história testada.</NotaEtapa>
    </>
  )
}

export function RelatoriosPage() {
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Relatórios</h1>
          <p>Histórico completo das execuções registradas pelo painel.</p>
        </div>
        <div className="btn-row">
          <button className="btn sm" disabled>
            Exportar CSV
          </button>
          <button className="btn sm" disabled>
            Exportar falhas (JSON)
          </button>
        </div>
      </header>
      <div className="stat-strip">
        {['Execuções', 'Testes únicos', 'Já falharam', 'Instáveis', 'Tempo total'].map((r, i) => (
          <div key={r} className={`stat ${i === 2 ? 'r' : i === 3 ? 'a' : ''}`}>
            <b>{i === 4 ? '—' : 0}</b>
            <span>{r}</span>
          </div>
        ))}
      </div>
      <section className="card">
        <div className="card-head">
          <span className="eyebrow">Aprovação por execução</span>
        </div>
        <div className="empty">Sem execuções concluídas.</div>
      </section>
      <NotaEtapa etapa={4}>os números vêm das execuções gravadas no PostgreSQL.</NotaEtapa>
    </>
  )
}
