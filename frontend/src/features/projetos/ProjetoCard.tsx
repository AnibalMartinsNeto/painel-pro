import { Link } from 'react-router'
import { Badge } from '../../components/Badge'
import type { ProjetoResumo } from './api'

const DESCRICAO: Record<ProjetoResumo['tipo'], string> = {
  CYPRESS: 'Testes E2E no navegador com Cypress',
  PLAYWRIGHT: 'Testes E2E multi-navegador com Playwright',
  K6: 'Testes de desempenho e carga com k6',
}

/** Um card da lista de projetos. Recebe os dados por "props" e só os exibe. */
export function ProjetoCard({ projeto }: { projeto: ProjetoResumo }) {
  const status = !projeto.encontrado ? (
    <Badge tom="bad">Pasta não encontrada</Badge>
  ) : projeto.instalado ? (
    <Badge tom="ok">Pronto para rodar</Badge>
  ) : (
    <Badge tom="warn">Dependências não instaladas</Badge>
  )

  return (
    <Link to={`/projetos/${projeto.id}`} className="feature" aria-label={`Abrir projeto ${projeto.nome}`}>
      <div className="feature-icon" aria-hidden="true">
        {projeto.tipo === 'K6' ? '⚡' : projeto.tipo === 'PLAYWRIGHT' ? '🎭' : '🌲'}
      </div>
      <div style={{ flex: 1 }}>
        <h3>
          {projeto.nome}
          {status}
        </h3>
        <p>{DESCRICAO[projeto.tipo]}</p>
      </div>
    </Link>
  )
}
