/** Medidor circular de aprovação (mesmo SVG do painel Node). null = sem dados. */
export function Gauge({ pct }: { pct: number | null }) {
  const C = 2 * Math.PI * 48
  const valor = pct ?? 0
  const cor = pct == null || pct >= 90 ? '' : pct >= 70 ? 'amber' : 'red'
  return (
    <div className="gauge" role="img" aria-label={pct == null ? 'Aprovação sem dados' : `Aprovação ${pct}%`}>
      <svg viewBox="0 0 120 120">
        <circle className="track" cx="60" cy="60" r="48" fill="none" strokeWidth="9" />
        <circle
          className={`bar ${cor}`}
          cx="60" cy="60" r="48" fill="none" strokeWidth="9"
          strokeDasharray={C}
          strokeDashoffset={C * (1 - valor / 100)}
          style={pct == null ? { opacity: 0 } : undefined}
        />
      </svg>
      <div className="gauge-center">
        <b>{pct == null ? '—' : `${String(pct).replace('.', ',')}%`}</b>
        <span>dos testes</span>
      </div>
    </div>
  )
}
