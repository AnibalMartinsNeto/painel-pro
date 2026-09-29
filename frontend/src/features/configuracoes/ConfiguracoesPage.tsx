import { useState, type FormEvent } from 'react'
import { Badge } from '../../components/Badge'
import { Carregando, ErroApi } from '../../components/Estado'
import {
  useConfiguracoes,
  useImportarConfiguracoes,
  useSalvarConfiguracoes,
  useTestarJira,
  type Configuracoes,
  type ProvedorIa,
} from './api'

export function ConfiguracoesPage() {
  const { data, isPending, error } = useConfiguracoes()
  const importar = useImportarConfiguracoes()

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Configurações</h1>
          <p>Ambiente, inteligência artificial e Jira. Segredos são gravados criptografados e nunca voltam para o navegador.</p>
        </div>
        <div className="btn-row">
          {importar.data && (
            <span className="hint" role="status">
              {importar.data.aviso ?? (importar.data.importadas.length ? `${importar.data.importadas.length} importadas` : 'Nada novo para importar')}
            </span>
          )}
          <button className="btn sm" type="button" onClick={() => importar.mutate()} disabled={importar.isPending}>
            Importar do painel Node
          </button>
        </div>
      </header>
      {isPending && <Carregando />}
      {error && <ErroApi erro={error} />}
      {/* key: quando os dados mudam (ex.: importação), o formulário recomeça com os valores novos */}
      {data && <Formulario key={JSON.stringify(data)} config={data} />}
    </>
  )
}

/** Campo de segredo: vazio = manter o atual. O valor atual nunca é mostrado. */
function CampoSegredo(props: { id: string; rotulo: string; configurado: boolean; valor: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <div className="field">
      <label htmlFor={props.id}>
        {props.rotulo} {props.configurado ? <Badge tom="ok">Configurado</Badge> : <Badge tom="idle">Não configurado</Badge>}
      </label>
      <input
        id={props.id}
        type="password"
        className="input"
        autoComplete="off"
        value={props.valor}
        onChange={(e) => props.onChange(e.target.value)}
        placeholder={props.configurado ? '•••••• (deixe em branco para manter)' : props.placeholder}
      />
    </div>
  )
}

function Formulario({ config }: { config: Configuracoes }) {
  const salvar = useSalvarConfiguracoes()
  const [ambiente, setAmbiente] = useState(config.ambiente ?? '')
  const [provedor, setProvedor] = useState<ProvedorIa>(config.ia.provedor)
  const [modeloGemini, setModeloGemini] = useState(config.ia.modeloGemini ?? '')
  const [modeloAnthropic, setModeloAnthropic] = useState(config.ia.modeloAnthropic ?? '')
  const [chaveGemini, setChaveGemini] = useState('')
  const [chaveAnthropic, setChaveAnthropic] = useState('')
  const [jiraUrl, setJiraUrl] = useState(config.jira.url ?? '')
  const [jiraEmail, setJiraEmail] = useState(config.jira.email ?? '')
  const [jiraProjeto, setJiraProjeto] = useState(config.jira.projeto ?? '')
  const [jiraTipo, setJiraTipo] = useState(config.jira.tipoIssue ?? 'Bug')
  const [jiraToken, setJiraToken] = useState('')
  const testarJira = useTestarJira()

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    salvar.mutate({
      ambiente,
      ia: { provedor, modeloGemini, modeloAnthropic, chaveGemini, chaveAnthropic },
      jira: { url: jiraUrl, email: jiraEmail, projeto: jiraProjeto, tipoIssue: jiraTipo, token: jiraToken },
    })
  }
  const removerSegredo = (chave: string) => salvar.mutate({ remover: [chave] })

  return (
    <form className="stack" onSubmit={enviar}>
      <section className="card">
        <span className="eyebrow" style={{ display: 'block', marginBottom: 12 }}>
          Ambiente
        </span>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="cfgAmbiente">Nome do ambiente testado</label>
            <input id="cfgAmbiente" className="input" value={ambiente} onChange={(e) => setAmbiente(e.target.value)} />
          </div>
        </div>
      </section>

      <section className="card ai-box">
        <div className="card-head">
          <span className="eyebrow" style={{ color: 'var(--green)' }}>
            Inteligência artificial (triagem)
          </span>
          {config.ia.ativa ? <Badge tom="ok">IA ativa</Badge> : <Badge tom="warn">Sem chave para o provedor escolhido</Badge>}
        </div>
        <div className="form-grid">
          <div className="field full">
            <label htmlFor="cfgProvedor">Provedor</label>
            <select id="cfgProvedor" className="input" value={provedor} onChange={(e) => setProvedor(e.target.value as ProvedorIa)}>
              <option value="gemini">Google Gemini</option>
              <option value="anthropic">Anthropic (Claude)</option>
            </select>
          </div>
          {provedor === 'gemini' ? (
            <>
              <CampoSegredo id="cfgChaveGemini" rotulo="Chave da API Gemini" configurado={config.ia.geminiConfigurada} valor={chaveGemini} onChange={setChaveGemini} placeholder="AIza… / AQ.…" />
              <div className="field">
                <label htmlFor="cfgModeloGemini">Modelo</label>
                <input id="cfgModeloGemini" className="input" value={modeloGemini} onChange={(e) => setModeloGemini(e.target.value)} />
              </div>
            </>
          ) : (
            <>
              <CampoSegredo id="cfgChaveAnthropic" rotulo="Chave da API Anthropic" configurado={config.ia.anthropicConfigurada} valor={chaveAnthropic} onChange={setChaveAnthropic} placeholder="sk-ant-…" />
              <div className="field">
                <label htmlFor="cfgModeloAnthropic">Modelo</label>
                <input id="cfgModeloAnthropic" className="input" value={modeloAnthropic} onChange={(e) => setModeloAnthropic(e.target.value)} />
              </div>
            </>
          )}
        </div>
        {(provedor === 'gemini' ? config.ia.geminiConfigurada : config.ia.anthropicConfigurada) && (
          <button className="btn ghost sm" type="button" style={{ marginTop: 10 }}
            onClick={() => removerSegredo(provedor === 'gemini' ? 'ia.gemini.chave' : 'ia.anthropic.chave')}>
            Remover chave
          </button>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <span className="eyebrow">Jira</span>
          {config.jira.configurado ? <Badge tom="ok">Configurado</Badge> : <Badge tom="idle">Não configurado</Badge>}
        </div>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="cfgJiraUrl">URL do Jira</label>
            <input id="cfgJiraUrl" className="input" placeholder="https://empresa.atlassian.net" value={jiraUrl} onChange={(e) => setJiraUrl(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="cfgJiraEmail">E-mail da conta Atlassian</label>
            <input id="cfgJiraEmail" className="input" type="email" placeholder="voce@empresa.com" value={jiraEmail} onChange={(e) => setJiraEmail(e.target.value)} />
          </div>
          <CampoSegredo id="cfgJiraToken" rotulo="API token" configurado={config.jira.tokenConfigurado} valor={jiraToken} onChange={setJiraToken} placeholder="ATATT3x… (id.atlassian.com → Security → API tokens)" />
          <div className="field">
            <label htmlFor="cfgJiraProjeto">Chave do projeto</label>
            <input id="cfgJiraProjeto" className="input" placeholder="QA (prefixo das issues, ex.: QA-123)" value={jiraProjeto} onChange={(e) => setJiraProjeto(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="cfgJiraTipo">Tipo das issues criadas</label>
            <input id="cfgJiraTipo" className="input" value={jiraTipo} onChange={(e) => setJiraTipo(e.target.value)} />
          </div>
        </div>
        <p className="hint" style={{ margin: '10px 0 0' }}>
          O Jira autentica com <b>e-mail + token juntos</b>. Salve antes de testar a conexão.
        </p>
        <div className="btn-row" style={{ marginTop: 10 }}>
          <button className="btn sm" type="button" onClick={() => testarJira.mutate()} disabled={testarJira.isPending || !config.jira.configurado}>
            {testarJira.isPending ? 'Testando…' : 'Testar conexão'}
          </button>
          {config.jira.tokenConfigurado && (
            <button className="btn ghost sm" type="button" onClick={() => removerSegredo('jira.token')}>
              Remover token
            </button>
          )}
          {testarJira.data && (
            <span className="hint" role="status" style={{ color: 'var(--green)' }}>
              Conectado como {testarJira.data.usuario} ao projeto {testarJira.data.projeto} ({testarJira.data.nomeProjeto})
            </span>
          )}
          {testarJira.error && (
            <span className="hint" role="alert" style={{ color: 'var(--red)' }}>
              {testarJira.error.message}
            </span>
          )}
        </div>
      </section>

      <div className="btn-row">
        <button className="btn primary" type="submit" disabled={salvar.isPending}>
          {salvar.isPending ? 'Salvando…' : 'Salvar configurações'}
        </button>
        {salvar.isSuccess && <span className="hint" role="status">Configurações salvas.</span>}
        {salvar.error && (
          <span className="hint" role="alert" style={{ color: 'var(--red)' }}>
            {salvar.error.message}
          </span>
        )}
      </div>
    </form>
  )
}
