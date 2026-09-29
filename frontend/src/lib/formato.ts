// Formatação de valores para exibição (datas, durações, plurais).

export function fmtDuracao(ms: number | null | undefined): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  const s = Math.round(ms / 1000)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  return m < 60 ? `${m}min${s % 60 ? ` ${s % 60}s` : ''}` : `${Math.floor(m / 60)}h ${m % 60}min`
}

export function fmtData(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export const plural = (n: number, um: string, varios: string) => `${n} ${n === 1 ? um : varios}`

export const fmtPct = (v: number | null | undefined) => (v == null ? '—' : `${String(v).replace('.', ',')}%`)
