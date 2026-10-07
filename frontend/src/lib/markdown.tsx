// Markdown → elementos React, sem biblioteca e sem HTML cru.
//
// Por que não "dangerouslySetInnerHTML"? Porque o texto vem de arquivos e
// viraria HTML executável (XSS). Aqui cada pedaço vira um elemento React:
// o React escapa todo texto, então nada do arquivo é interpretado como HTML.
//
// Cobre o que os READMEs do projeto usam: títulos, parágrafos, listas,
// tabelas, blocos de código, citações, linha horizontal, **negrito**,
// *itálico*, `código` e [links](url).
import type { ReactNode } from 'react'

type Bloco =
  | { tipo: 'titulo'; nivel: number; texto: string }
  | { tipo: 'paragrafo'; texto: string }
  | { tipo: 'lista'; ordenada: boolean; itens: string[] }
  | { tipo: 'tabela'; cabecalho: string[]; linhas: string[][] }
  | { tipo: 'codigo'; texto: string }
  | { tipo: 'citacao'; texto: string }
  | { tipo: 'linha' }

const celulas = (linha: string) =>
  linha.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim())

export function blocos(md: string): Bloco[] {
  const linhas = md.replace(/\r\n?/g, '\n').split('\n')
  const saida: Bloco[] = []
  let i = 0
  while (i < linhas.length) {
    const l = linhas[i]
    if (!l.trim()) { i++; continue }
    if (l.trimStart().startsWith('```')) {
      const codigo: string[] = []
      i++
      while (i < linhas.length && !linhas[i].trimStart().startsWith('```')) codigo.push(linhas[i++])
      i++ // fecha o bloco
      saida.push({ tipo: 'codigo', texto: codigo.join('\n') })
      continue
    }
    const titulo = /^(#{1,6})\s+(.*)$/.exec(l)
    if (titulo) { saida.push({ tipo: 'titulo', nivel: titulo[1].length, texto: titulo[2] }); i++; continue }
    if (/^\s*(-{3,}|\*{3,})\s*$/.test(l)) { saida.push({ tipo: 'linha' }); i++; continue }
    if (l.includes('|') && i + 1 < linhas.length && /^\s*\|?\s*:?-{3,}/.test(linhas[i + 1])) {
      const cabecalho = celulas(l)
      i += 2
      const corpo: string[][] = []
      while (i < linhas.length && linhas[i].includes('|') && linhas[i].trim()) corpo.push(celulas(linhas[i++]))
      saida.push({ tipo: 'tabela', cabecalho, linhas: corpo })
      continue
    }
    if (/^\s*>/.test(l)) {
      const texto: string[] = []
      while (i < linhas.length && /^\s*>/.test(linhas[i])) texto.push(linhas[i++].replace(/^\s*>\s?/, ''))
      saida.push({ tipo: 'citacao', texto: texto.join(' ') })
      continue
    }
    const item = /^\s*([-*+]|\d+\.)\s+(.*)$/.exec(l)
    if (item) {
      const ordenada = /\d/.test(item[1])
      const itens: string[] = []
      while (i < linhas.length) {
        const m = /^\s*([-*+]|\d+\.)\s+(.*)$/.exec(linhas[i])
        if (m) { itens.push(m[2]); i++; continue }
        // continuação de item (linha indentada)
        if (linhas[i].trim() && /^\s{2,}/.test(linhas[i]) && itens.length) { itens[itens.length - 1] += ' ' + linhas[i].trim(); i++; continue }
        break
      }
      saida.push({ tipo: 'lista', ordenada, itens })
      continue
    }
    const paragrafo: string[] = []
    while (i < linhas.length && linhas[i].trim() && !/^(#{1,6}\s|```|\s*>|\s*([-*+]|\d+\.)\s)/.test(linhas[i])) paragrafo.push(linhas[i++].trim())
    if (!paragrafo.length) { paragrafo.push(l.trim()); i++ }
    saida.push({ tipo: 'paragrafo', texto: paragrafo.join(' ') })
  }
  return saida
}

/** Links só para http(s), âncoras e caminhos relativos: nunca "javascript:". */
const linkSeguro = (url: string) => /^(https?:\/\/|#|\.{0,2}\/|[\w-]+(\.[\w-]+)*(\/|$))/i.test(url) && !/^javascript:/i.test(url)

/** Negrito, itálico, código e links dentro de uma linha. */
export function inline(texto: string, chave = 'i'): ReactNode[] {
  const partes: ReactNode[] = []
  const padrao = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\[[^\]]+\]\([^)\s]+\))|(\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g
  let ultimo = 0
  let m: RegExpExecArray | null
  let n = 0
  while ((m = padrao.exec(texto))) {
    if (m.index > ultimo) partes.push(texto.slice(ultimo, m.index))
    const k = `${chave}-${n++}`
    const t = m[0]
    if (m[1]) partes.push(<code key={k}>{t.slice(1, -1)}</code>)
    else if (m[2]) partes.push(<strong key={k}>{inline(t.slice(2, -2), k)}</strong>)
    else if (m[3]) {
      const [, rotulo, url] = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(t)!
      partes.push(linkSeguro(url)
        ? <a key={k} href={url} target={url.startsWith('#') ? undefined : '_blank'} rel="noopener">{inline(rotulo, k)}</a>
        : rotulo)
    } else partes.push(<em key={k}>{t.slice(1, -1)}</em>)
    ultimo = m.index + t.length
  }
  if (ultimo < texto.length) partes.push(texto.slice(ultimo))
  return partes
}

export function Markdown({ texto }: { texto: string }) {
  return (
    <div className="markdown">
      {blocos(texto).map((b, i) => {
        switch (b.tipo) {
          case 'titulo': {
            const Tag = `h${Math.min(b.nivel + 1, 6)}` as 'h2' // h1 é o título da página
            return <Tag key={i}>{inline(b.texto, `t${i}`)}</Tag>
          }
          case 'paragrafo':
            return <p key={i}>{inline(b.texto, `p${i}`)}</p>
          case 'lista': {
            const Tag = b.ordenada ? 'ol' : 'ul'
            return <Tag key={i}>{b.itens.map((it, j) => <li key={j}>{inline(it, `l${i}-${j}`)}</li>)}</Tag>
          }
          case 'tabela':
            return (
              <div key={i} className="tbl-wrap">
                <table className="tbl">
                  <thead><tr>{b.cabecalho.map((c, j) => <th key={j}>{inline(c, `h${i}-${j}`)}</th>)}</tr></thead>
                  <tbody>
                    {b.linhas.map((linha, j) => (
                      <tr key={j}>{linha.map((c, k) => <td key={k}>{inline(c, `c${i}-${j}-${k}`)}</td>)}</tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          case 'codigo':
            return <pre key={i}><code>{b.texto}</code></pre>
          case 'citacao':
            return <blockquote key={i}>{inline(b.texto, `q${i}`)}</blockquote>
          case 'linha':
            return <hr key={i} />
        }
      })}
    </div>
  )
}
