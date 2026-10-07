/** Uma evidência de teste (screenshot, trace) servida pela API. */
export interface Evidencia {
  id: number
  nome: string
  tipo: string
  url: string
}

/**
 * Evidências de um teste: imagens aparecem em miniatura (clicar abre em tamanho
 * real numa aba nova); o resto (ex.: trace do Playwright) vira link de download.
 */
export function Evidencias({ evidencias }: { evidencias?: Evidencia[] }) {
  if (!evidencias?.length) return null
  const imagens = evidencias.filter((e) => e.tipo.startsWith('image/'))
  const outras = evidencias.filter((e) => !e.tipo.startsWith('image/'))
  return (
    <div className="evidencias" aria-label="Evidências">
      {imagens.map((e) => (
        <a key={e.id} href={e.url} target="_blank" rel="noopener" title={`${e.nome} (abrir em tamanho real)`}>
          <img src={e.url} alt={`Screenshot: ${e.nome}`} loading="lazy" />
        </a>
      ))}
      {outras.map((e) => (
        <a key={e.id} className="btn ghost sm" href={e.url} download={e.nome}>
          ⬇ {e.nome}
          {e.nome.endsWith('.zip') && <span className="hint"> (abra em trace.playwright.dev)</span>}
        </a>
      ))}
    </div>
  )
}
