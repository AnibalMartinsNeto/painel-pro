import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Markdown } from './markdown'

describe('Markdown', () => {
  it('desenha títulos, listas, tabela, código e formatação inline', () => {
    const { container } = render(
      <Markdown
        texto={[
          '# ServeRest QA',
          '',
          'Texto com **negrito**, *itálico*, `codigo` e [link](https://serverest.dev).',
          '',
          '## Regras',
          '- **USU-03** – e-mail único',
          '- **PRO-04** – preço inteiro',
          '',
          '| Comando | O que faz |',
          '|---|---|',
          '| `npm test` | Todos os specs |',
          '',
          '```bash',
          'docker compose up -d',
          '```',
          '',
          '> Dica: use a API local.',
        ].join('\n')}
      />,
    )

    expect(screen.getByRole('heading', { name: 'ServeRest QA' }).tagName).toBe('H2') // h1 é o título da página
    expect(screen.getByRole('heading', { name: 'Regras' })).toBeInTheDocument()
    expect(screen.getByText('negrito').tagName).toBe('STRONG')
    expect(screen.getByText('itálico').tagName).toBe('EM')
    expect(screen.getByRole('link', { name: 'link' })).toHaveAttribute('href', 'https://serverest.dev')
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('table')).toHaveTextContent('Todos os specs')
    expect(container.querySelector('pre')).toHaveTextContent('docker compose up -d')
    expect(container.querySelector('blockquote')).toHaveTextContent('Dica: use a API local.')
  })

  it('nunca transforma o conteúdo em HTML ou link perigoso', () => {
    const { container } = render(
      <Markdown texto={'<script>alert(1)</script> <img src=x onerror=alert(1)>\n\n[clique](javascript:alert(1))'} />,
    )

    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect(container.textContent).toContain('<script>alert(1)</script>') // aparece como TEXTO
    expect(screen.queryByRole('link')).toBeNull() // link javascript: vira só o rótulo
    expect(container.textContent).toContain('clique')
  })
})
