import { NotaEtapa } from '../../components/Estado'

// Telas das etapas futuras, com o mesmo cabeçalho e estados vazios do
// painel Node. Cada uma ganha sua pasta em features/ quando for construída.

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
