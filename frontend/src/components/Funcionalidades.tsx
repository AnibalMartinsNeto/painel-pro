import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Badge } from './Badge'

// Ícones em SVG inline (mesmos do painel Node).
const Icone = ({ children }: { children: ReactNode }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
)

const ITENS = [
  {
    to: '/execucoes',
    titulo: 'Execução remota',
    texto: 'Roda os scripts do projeto ou uma seleção de specs com 1 clique, direto do navegador, com log ao vivo.',
    icone: <polygon points="5 3 19 12 5 21 5 3" />,
  },
  {
    to: '/relatorios',
    titulo: 'Telemetria',
    texto: 'Taxa de aprovação, falhas por módulo, testes instáveis e histórico de cada execução.',
    icone: (
      <>
        <line x1="18" x2="18" y1="20" y2="10" />
        <line x1="12" x2="12" y1="20" y2="4" />
        <line x1="6" x2="6" y1="20" y2="14" />
      </>
    ),
  },
  {
    to: '/triagem',
    titulo: 'Triagem com IA',
    texto: 'Teste falhou? A IA analisa o erro e o código do spec e preenche o bug: esperado, encontrado e passos.',
    icone: <path d="M12 3l1.912 5.885L20 9l-4.5 4.5 1.5 6.5L12 16.8 6.999 20l1.5-6.5L4 9l6.088-.115L12 3z" />,
    destaque: true,
  },
  {
    to: '/azure',
    titulo: 'Azure DevOps',
    texto: 'Encontra os testes pelo número da demanda e publica o bug direto no board.',
    icone: (
      <>
        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
      </>
    ),
  },
]

/** Seção "O que o painel faz", exibida abaixo do quadro na Visão geral. */
export function Funcionalidades() {
  return (
    <section aria-label="Funcionalidades">
      <h2 className="features-title">O que o painel faz</h2>
      <div className="grid2" style={{ gap: 16 }}>
        {ITENS.map((f) => (
          <Link key={f.to} to={f.to} className={`feature ${f.destaque ? 'hl' : ''}`}>
            <div className="feature-icon">
              <Icone>{f.icone}</Icone>
            </div>
            <div style={{ flex: 1 }}>
              <h3>
                {f.titulo}
                {f.destaque && <Badge tom="ok">Destaque</Badge>}
              </h3>
              <p>{f.texto}</p>
            </div>
          </Link>
        ))}
      </div>
    </section>
  )
}
