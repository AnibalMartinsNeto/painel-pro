import { NavLink, Outlet } from 'react-router'
import { ApiStatus } from './ApiStatus'

// Itens do menu. "etapa" marca o que ainda será construído no PainelPro.
const NAV = [
  { to: '/projetos', label: 'Projetos' },
  { to: '/execucoes', label: 'Execuções', etapa: 4 },
  { to: '/triagem', label: 'Triagem IA', etapa: 6 },
  { to: '/azure', label: 'Azure DevOps', etapa: 6 },
  { to: '/relatorios', label: 'Relatórios', etapa: 4 },
]

/**
 * Moldura de todas as telas: barra lateral fixa + área de conteúdo.
 * O <Outlet /> é onde o React Router desenha a página da rota atual.
 */
export function Layout() {
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
              <span>QA Panel <small>Pro</small></span>
            </div>
            <nav className="nav" aria-label="Navegação principal">
              {NAV.map((item) => (
                <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                  {item.label}
                  {item.etapa && <span className="nav-tag">etapa {item.etapa}</span>}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="side-status">
            <ApiStatus />
          </div>
        </aside>
        <main className="main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
