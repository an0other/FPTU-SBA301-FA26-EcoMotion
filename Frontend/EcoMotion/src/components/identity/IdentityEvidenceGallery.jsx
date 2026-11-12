import { useEffect, useState } from 'react'
import { identityVerificationService as api } from '../../services/identityVerificationService'

function Evidence({ attemptId, eventId, kind, activationToken, automatic }) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [requested, setRequested] = useState(automatic)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    if (!requested) return
    let alive = true
    let objectUrl
    const controller = new AbortController()
    queueMicrotask(() => { if (alive) { setBusy(true); setError(''); setUrl('') } })
    const request = eventId
      ? api.ownHistoryEvidence(attemptId, eventId, kind, activationToken, controller.signal)
      : activationToken ? api.downloadActivationEvidence(activationToken, attemptId, kind, controller.signal)
        : api.downloadEvidence(attemptId, kind, controller.signal)
    request.then(async data => {
      const head = new Uint8Array(await data.slice(0, 16).arrayBuffer())
      const type = kind === 'video' ? head[0] === 0x1a ? 'video/webm' : 'video/mp4' : head[0] === 0x89 ? 'image/png' : 'image/jpeg'
      if (!alive) return
      objectUrl = URL.createObjectURL(new Blob([data], { type }))
      setUrl(objectUrl)
    }).catch(err => { if (alive && err.code !== 'ERR_CANCELED') setError(err.message || 'Chưa tải được bằng chứng.') }).finally(() => { if (alive) setBusy(false) })
    return () => { alive = false; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [attemptId, eventId, kind, activationToken, requested, retry])
  const label = kind === 'front' ? 'Mặt trước CCCD' : kind === 'back' ? 'Mặt sau CCCD' : 'Video đã gửi'
  return <div className="space-y-2 rounded-xl border border-slate-200 p-3"><h4 className="text-sm font-semibold">{label}</h4>{busy && <p role="status" className="text-sm text-slate-500">Đang tải bằng chứng…</p>}{url && (kind === 'video' ? <video src={url} controls playsInline preload="metadata" className="max-h-80 w-full rounded-lg bg-slate-950" /> : <a href={url} target="_blank" rel="noreferrer" aria-label={`Mở ảnh ${label.toLowerCase()} lớn`}><img src={url} alt={`${label} của bản hồ sơ đang xem`} className="h-48 w-full rounded-lg bg-slate-50 object-contain" /><span className="mt-1 block text-xs text-brand-700">Bấm ảnh để xem lớn</span></a>)}{!requested && <button type="button" className="text-sm font-semibold text-brand-700 underline" onClick={() => setRequested(true)}>Xem {label.toLowerCase()}</button>}{error && <><p role="alert" className="text-sm text-red-700">{error}</p><button type="button" disabled={busy} className="text-sm text-brand-700 underline" onClick={() => setRetry(value => value + 1)}>Thử tải lại</button></>}</div>
}

export default function IdentityEvidenceGallery({ attemptId, eventId, activationToken, hasFront = true, hasBack = true, hasVideo = false, revision = 0, title = 'Ảnh và video đang lưu trong hồ sơ' }) {
  if (!attemptId || (!hasFront && !hasBack && !hasVideo)) return null
  const kinds = [hasFront && 'front', hasBack && 'back', hasVideo && 'video'].filter(Boolean)
  return <section className="space-y-3" aria-label={title}><h3 className="font-semibold">{title}</h3><p className="text-xs text-slate-500">Đây là bằng chứng của đúng bản hồ sơ đang xem, tải qua kết nối có kiểm tra quyền.</p><div className="grid gap-3 sm:grid-cols-2">{kinds.map(kind => <Evidence key={`${attemptId}-${eventId || revision}-${kind}`} attemptId={attemptId} eventId={eventId} activationToken={activationToken} kind={kind} automatic={kind !== 'video'} />)}</div></section>
}
