import { useEffect, useState } from 'react'
import {
  countdownUrgency,
  formatCountdown,
  holdProgress,
  remainingMs,
} from '../../utils/paymentDisplay'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`} aria-hidden="true">{name}</span>
}

export function HoldCountdown({ expiresAt, compact = false, className = '' }) {
  const [now, setNow] = useState(() => Date.now())
  const ms = remainingMs(expiresAt, now)
  const urgency = countdownUrgency(ms)

  useEffect(() => {
    if (!expiresAt) return undefined
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [expiresAt])

  if (ms == null) return null

  const label = urgency === 'expired' ? 'Hết thời gian giữ chỗ' : 'Giữ chỗ còn'
  const tone = {
    ok: 'border-brand-200 bg-brand-50 text-brand-900',
    urgent: 'border-amber-300 bg-amber-50 text-amber-950 motion-safe:animate-pulse motion-reduce:animate-none',
    expired: 'border-red-200 bg-red-50 text-red-800',
    none: 'border-slate-200 bg-slate-50 text-slate-700',
  }[urgency]

  const spoken =
    urgency === 'expired'
      ? 'Hết thời gian giữ chỗ'
      : `${label} ${formatCountdown(ms)}`

  if (compact) {
    return (
      <span
        role="status"
        aria-atomic="true"
        className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 font-mono text-[11px] font-bold ${tone} ${className}`}
      >
        <Icon name="timer" className="text-sm" />
        {spoken}
      </span>
    )
  }

  return (
    <div
      role="status"
      aria-atomic="true"
      className={`min-w-[11rem] rounded-xl border px-3 py-2 ${tone} ${className}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] font-semibold uppercase tracking-wide">{label}</span>
        <Icon name="timer" className="text-base" />
      </div>
      <p className="mt-0.5 font-mono text-xl font-bold tabular-nums">
        {formatCountdown(ms)}
      </p>
      <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-black/10">
        <div
          className="h-full rounded-full bg-current transition-[width] duration-1000 ease-linear motion-reduce:transition-none"
          style={{ width: `${holdProgress(ms)}%` }}
        />
      </div>
    </div>
  )
}
