import { NavLink, Outlet, useLocation } from 'react-router'
import { useConfiguracoes } from '../features/configuracoes/api'
import { useEmAndamento } from '../features/execucoes/api'
import { useProjetoAtual } from '../features/projetos/ProjetoAtual'
import { pendente, useFilaTriagem } from '../features/triagem/api'
import { ApiStatus } from './ApiStatus'
import { Funcionalidades } from './Funcionalidades'

const NAV = [
  { to: '/', label: 'Visão geral' },
  { to: '/execucoes', label: 'Execuções' },
  { to: '/triagem', label: 'Triagem IA' },
  { to: '/jira', label: 'Jira' },
  { to: '/relatorios', label: 'Relatórios' },
  { to: '/configuracoes', label: 'Configurações' },
]

/**
 * Moldura de todas as telas, igual ao painel Node: barra lateral com
 * marca, seletor de projeto, menu e status; área de conteúdo à direita.
 * O <Outlet /> é onde o React Router desenha a página da rota atual.
 */
export function Layout() {
  const { pathname } = useLocation()
  const { data: emAndamento } = useEmAndamento()
  const { id: projeto } = useProjetoAtual()
  const { data: fila } = useFilaTriagem(projeto)
  const pendentes = fila?.filter(pendente).length ?? 0

  return (
    <div className="shell">
      <div className="frame">
        <aside className="sidebar">
          <div>
            <div className="brand">
              <div className="brand-logo" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                </svg>
              </div>
              <span>QA Panel</span>
            </div>
            <SeletorProjeto />
            <nav className="nav" aria-label="Navegação principal">
              {NAV.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                  {item.label}
                  {item.to === '/execucoes' && emAndamento && <span className="nav-live" title="Execução em andamento" />}
                  {item.to === '/triagem' && pendentes > 0 && (
                    <span className="nav-count" title="Falhas aguardando triagem">{pendentes}</span>
                  )}
                </NavLink>
              ))}
            </nav>
          </div>
          <StatusLateral />
        </aside>
        <main className="main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      {pathname === '/' && <Funcionalidades />}
    </div>
  )
}

function SeletorProjeto() {
  const { id, selecionar, projetos } = useProjetoAtual()
  return (
    <div className="project-pick">
      <label htmlFor="projectSelect" className="side-label">
        Projeto de testes
      </label>
      <select id="projectSelect" className="input" value={id} onChange={(e) => selecionar(e.target.value)}>
        {(projetos.data ?? [{ id, nome: id, encontrado: true, instalado: true }]).map((p) => (
          <option key={p.id} value={p.id}>
            {p.nome}
            {!p.encontrado ? ' (não encontrado)' : !p.instalado ? ' (sem npm install)' : ''}
          </option>
        ))}
      </select>
    </div>
  )
}

function StatusLateral() {
  const { detalhe } = useProjetoAtual()
  const { data: config } = useConfiguracoes()
  const baseUrl = detalhe.data?.baseUrl?.replace(/^https?:\/\//, '')
  const nomeProvedor = config?.ia.provedor === 'anthropic' ? 'Claude' : 'Gemini'

  return (
    <div className="side-status">
      <div>
        <span className="side-label">Ambiente</span>
        <div className={`status-line ${detalhe.data?.encontrado === false ? 'off' : ''}`}>
          <span className="dot pulse" aria-hidden="true" />
          <span>{config?.ambiente ?? 'Homologação'}</span>
        </div>
        {baseUrl && <div className="side-sub">{baseUrl}</div>}
      </div>
      <ApiStatus />
      <div>
        <span className="side-label">Jira</span>
        <div className={`status-line ${config?.jira.configurado ? '' : 'off'}`}>
          <span className="dot" aria-hidden="true" />
          <span>{config?.jira.configurado ? `Projeto ${config.jira.projeto}` : 'Não configurado'}</span>
        </div>
      </div>
      <div>
        <span className="side-label">Triagem IA</span>
        <div className={`status-line ${config?.ia.ativa ? '' : 'warn'}`}>
          <span className="dot" aria-hidden="true" />
          <span>{config?.ia.ativa ? `${nomeProvedor} ativo` : 'Sem chave'}</span>
        </div>
      </div>
    </div>
  )
}
