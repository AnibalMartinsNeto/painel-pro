import type { ReactNode } from 'react'

type Tom = 'ok' | 'warn' | 'bad' | 'idle' | 'info'

/** Selo colorido (ex.: "Instalado", "Não encontrado"). */
export function Badge({ tom, children }: { tom: Tom; children: ReactNode }) {
  return <span className={`badge ${tom}`}>{children}</span>
}
