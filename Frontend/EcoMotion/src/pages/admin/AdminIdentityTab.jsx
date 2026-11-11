import { useCallback, useEffect, useState } from 'react'
import { identityVerificationService as identity } from '../../services/identityVerificationService'
import { providerOnboardingService as provider } from '../../services/providerOnboardingService'
import { buttonClass, secondaryClass, cardClass, inputClass, identityMessage, statusLabels, humanDate, downloadBlob } from '../../components/identity/identityUi'

const reasons = { DOCUMENT_UNREADABLE: 'Giấy tờ chưa rõ', FACE_UNCLEAR: 'Khuôn mặt/video chưa rõ', DETAILS_INCONSISTENT: 'Thông tin chưa nhất quán', ADDRESS_REQUIRED: 'Cần bổ sung địa chỉ/vị trí', BANK_FIELDS_REQUIRED: 'Cần bổ sung thông tin ngân hàng', OTHER_REVIEW_REQUIRED: 'Cần kiểm tra, bổ sung thêm', APPEAL_ACCEPTED: 'Chấp nhận mở lại hồ sơ' }

function PrivateEvidence({ id, kind, reviewId }) {
  const [blob, setBlob] = useState(null)
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (!blob) return; const value = URL.createObjectURL(blob); queueMicrotask(() => setUrl(value)); return () => URL.revokeObjectURL(value) }, [blob])
  return <div className="space-y-2 rounded-xl border border-slate-200 p-3"><button className={secondaryClass} disabled={busy} onClick={async () => {
    setBusy(true); setError('')
    try {
      const data = await (reviewId ? provider.historyEvidence(id, reviewId, kind) : identity.downloadProviderIdentityEvidence(id, kind))
      const header = new Uint8Array(await data.slice(0, 12).arrayBuffer())
      const type = kind === 'video' ? header[0] === 0x1a ? 'video/webm' : 'video/mp4' : header[0] === 0x89 ? 'image/png' : 'image/jpeg'
      setBlob(new Blob([data], { type }))
    } catch (err) { setError(identityMessage(err)) } finally { setBusy(false) }
  }}>{busy ? 'Đang tải…' : `Xem ${kind === 'front' ? 'mặt trước CCCD' : kind === 'back' ? 'mặt sau CCCD' : 'video'}`}</button>
    {url && (kind === 'video' ? <video className="max-h-80 w-full rounded-lg bg-slate-950" src={url} controls playsInline /> : <img className="max-h-96 w-full rounded-lg object-contain" src={url} alt={kind === 'front' ? 'Mặt trước CCCD của hồ sơ đang xem' : 'Mặt sau CCCD của hồ sơ đang xem'} />)}
    {blob && <button className="block text-sm font-medium text-brand-700 underline" onClick={() => downloadBlob(blob, `bang-chung-${kind}.${blob.type.includes('webm') ? 'webm' : blob.type.includes('mp4') ? 'mp4' : blob.type.includes('png') ? 'png' : 'jpg'}`)}>Tải bằng chứng</button>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
  </div>
}

function ProfileDetails({ data }) {
  if (!data?.profile) return <p className="text-sm text-slate-500">Chưa có form Provider được lưu.</p>
  const profile = data.profile
  return <dl className="grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">{[['Tên hiển thị', profile.displayName], ['Hotline', profile.hotline], ['Điểm giao nhận', profile.depotAddress], ['Tọa độ', `${profile.latitude}, ${profile.longitude}`], ['Ngân hàng', profile.bankCode], ['Số tài khoản', profile.bankAccountNo], ['Chủ tài khoản', profile.bankAccountHolder]].map(([label, value]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd className="break-words font-medium">{value || 'Chưa khai báo'}</dd></div>)}</dl>
}

function SubmittedIdentity({ attemptId, userId, reviewId }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let alive = true
    const request = reviewId ? provider.historyIdentity(userId, reviewId) : identity.providerIdentityDetail(attemptId)
    request.then(result => { if (alive) setData(result) }).catch(err => { if (alive) setError(identityMessage(err)) })
    return () => { alive = false }
  }, [attemptId, userId, reviewId])
  return <div className="space-y-3"><h3 className="font-bold">CCCD và video kèm hồ sơ</h3>{error && <p role="alert" className="text-sm text-red-700">{error}</p>}{!data && !error && <p role="status">Đang tải bằng chứng…</p>}{data && <><dl className="grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">{[['Họ tên CCCD', data.document?.fullName], ['Số CCCD', data.document?.documentNumber], ['Ngày sinh', data.document?.dateOfBirth], ['Ngày hết hạn', data.document?.expiryDate], ['Địa chỉ', data.document?.address]].map(([label, value]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd>{value || 'Chưa đọc được'}</dd></div>)}</dl><div className="rounded-xl bg-blue-50 p-4 text-sm"><p>Động tác: {data.video?.motionChallenge?.status === 'PASS' ? 'Đã hoàn thành' : 'Chưa hoàn thành'}</p><p>Số frame chấm được: {data.video?.faceMatching?.scoredFrames ?? '—'}</p><p>Điểm đối chiếu trung vị: {data.video?.faceMatching?.median?.toFixed(4) ?? '—'} (không phải phần trăm)</p></div><div className="grid gap-3">{['front', 'back', 'video'].map(kind => <PrivateEvidence key={kind} id={reviewId ? userId : attemptId} reviewId={reviewId} kind={kind} />)}</div></>}</div>
}

function ReviewDetail({ mode, id, onReviewed, onClose }) {
  const [data, setData] = useState(null)
  const [related, setRelated] = useState(null)
  const [history, setHistory] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [decision, setDecision] = useState('APPROVE')
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')
  const [checked, setChecked] = useState(false)
  const load = useCallback(async () => { const result = mode === 'identity' ? await identity.providerIdentityDetail(id) : await provider.detail(id); setData(result); setChecked(false); setDecision(result.status === 'REJECTED' ? 'REOPEN' : 'APPROVE') }, [mode, id])
  useEffect(() => { let alive = true; (mode === 'identity' ? identity.providerIdentityDetail(id) : provider.detail(id)).then(result => { if (alive) { setData(result); setDecision(result.status === 'REJECTED' ? 'REOPEN' : 'APPROVE') } }).catch(err => { if (alive) setError(identityMessage(err)) }); return () => { alive = false } }, [mode, id])

  async function perform(action) {
    setBusy(true); setError('')
    try { await action() } catch (err) { setError(identityMessage(err)); if (err.status === 409) { try { await load() } catch { } } }
    finally { setBusy(false) }
  }
  return <section className={`${cardClass} space-y-4`}>
    <div className="flex items-center justify-between gap-2"><h2 className="text-lg font-bold">Chi tiết {mode === 'identity' ? 'danh tính' : 'hồ sơ Provider'}</h2><button className={secondaryClass} onClick={onClose} disabled={busy}>Đóng</button></div>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {!data && <p role="status">Đang lấy hồ sơ…</p>}
    {data && <>
      <p className="break-all text-sm font-semibold">{data.fullName || data.document?.fullName || 'Chưa có họ tên'} · {data.email}</p><p className="text-sm">{statusLabels[data.status] || data.status} · Gửi lúc {humanDate(data.submittedAt)}</p>
      {data.decisionReason || data.reasonCode ? <p className="text-sm text-amber-800">Phản hồi hiện tại: {identityMessage(data.decisionReason || data.reasonCode)}</p> : null}
      {mode === 'identity' ? <>
        <dl className="grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">{[['Họ tên CCCD', data.document?.fullName], ['Số CCCD', data.document?.documentNumber], ['Ngày sinh', data.document?.dateOfBirth], ['Ngày hết hạn', data.document?.expiryDate], ['Địa chỉ', data.document?.address]].map(([label, value]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd>{value || 'Chưa đọc được'}</dd></div>)}</dl>
        <div className="rounded-xl bg-blue-50 p-4 text-sm"><p>Động tác: {data.video?.motionChallenge?.status === 'PASS' ? 'Đã hoàn thành' : 'Chưa hoàn thành'}</p><p>Số frame chấm được: {data.video?.faceMatching?.scoredFrames ?? '—'}</p><p>Điểm đối chiếu trung vị: {data.video?.faceMatching?.median?.toFixed(4) ?? '—'} (không phải phần trăm)</p><p className="mt-2">Đối chiếu giấy tờ và video trước khi quyết định. Kết quả AI chỉ hỗ trợ xét duyệt.</p></div>
        <div className="grid gap-3">{['front', 'back', 'video'].map(kind => <PrivateEvidence key={kind} id={id} kind={kind} />)}</div>
        <button className={secondaryClass} disabled={busy} onClick={() => perform(async () => setRelated(await provider.detail(data.userId)))}>Xem form Provider đã lưu</button>{related && <ProfileDetails data={related} />}
      </> : <>{data.identityAttemptId && <SubmittedIdentity key={data.identityAttemptId} attemptId={data.identityAttemptId} />}<ProfileDetails data={data} /><p className="text-sm">Danh tính: {data.identityVerified ? 'Đã xác minh' : 'Sẽ xác minh cùng khi chấp nhận hồ sơ'}</p><button className={secondaryClass} disabled={busy} onClick={() => perform(async () => setHistory(await provider.history(id)))}>Xem lịch sử xét duyệt</button>{history && <ol className="space-y-2 text-sm">{history.map((entry, index) => <li key={`${entry.version}-${index}`} className="rounded-xl bg-slate-50 p-3">{humanDate(entry.reviewedAt)} · {entry.decision} · {entry.reasonCode || 'Đã chấp nhận'} · Admin {entry.adminId}<p className="mt-1">{entry.note}</p><details className="mt-2"><summary className="cursor-pointer font-semibold">Xem hồ sơ tại lần duyệt này</summary>{entry.profileSnapshotJson && <ProfileDetails data={{ profile: JSON.parse(entry.profileSnapshotJson) }} />}{entry.hasIdentitySnapshot && <SubmittedIdentity userId={id} reviewId={entry.id} />}</details></li>)}</ol>}</>}
      {data.status === 'SUBMITTED' && <form className="space-y-3 border-t border-slate-200 pt-4" onSubmit={event => { event.preventDefault(); perform(async () => {
        if (mode === 'identity') await identity.reviewProviderIdentity(id, data.version, decision, decision === 'APPROVE' ? null : reason)
        else await provider.review(id, data.version, decision, decision === 'APPROVE' ? null : reason, decision === 'APPROVE' ? null : note)
        await onReviewed()
      }) }}>
        <label className="block text-sm">Quyết định<select className={`${inputClass} mt-1`} disabled={busy} value={decision} onChange={event => { setDecision(event.target.value); setChecked(false) }}>{(data.status === 'REJECTED' ? [['REOPEN', 'Mở lại để bổ sung']] : [['APPROVE', 'Chấp nhận'], ['NEEDS_CHANGES', 'Yêu cầu bổ sung'], ['REJECT', 'Từ chối']]).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        {decision !== 'APPROVE' && <label className="block text-sm">Lý do bắt buộc<select className={`${inputClass} mt-1`} required value={reason} disabled={busy} onChange={event => setReason(event.target.value)}><option value="">Chọn lý do</option>{Object.entries(reasons).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>}
        {decision !== 'APPROVE' && <label className="block text-sm">Hướng dẫn bổ sung / lý do cụ thể<textarea className={`${inputClass} mt-1`} required maxLength={1000} value={note} disabled={busy} onChange={event => setNote(event.target.value)} /></label>}
        <label className="flex gap-2 text-sm"><input type="checkbox" checked={checked} disabled={busy} onChange={event => setChecked(event.target.checked)} />Tôi đã kiểm tra thông tin và bằng chứng liên quan, xác nhận quyết định này.</label>
        <button className={buttonClass} disabled={busy || !checked || decision !== 'APPROVE' && !reason}>{busy ? 'Đang lưu quyết định…' : 'Xác nhận quyết định'}</button>
      </form>}
    </>}
  </section>
}

export default function AdminIdentityTab() {
  const mode = 'application'
  const [status, setStatus] = useState('SUBMITTED')
  const [page, setPage] = useState(0)
  const [items, setItems] = useState([])
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [revision, setRevision] = useState(0)
  const [notice, setNotice] = useState('')
  useEffect(() => {
    let alive = true
    queueMicrotask(() => { if (alive) { setLoading(true); setError('') } })
    const promise = mode === 'identity' ? identity.pendingProviderIdentities(page, status) : provider.pending(page, status)
    promise.then(data => { if (alive) setItems(data) }).catch(err => { if (alive) { setError(identityMessage(err)); setItems([]) } }).finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [mode, status, page, revision])
  return <div className="space-y-5"><div><h1 className="text-2xl font-bold">Xác minh & onboarding Provider</h1><p className="mt-2 text-sm text-slate-500">Xem CCCD/video cùng thông tin dịch vụ và duyệt một lần để kích hoạt Provider.</p></div>
    <div className="flex flex-wrap gap-2"><select aria-label="Trạng thái hồ sơ" className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm" value={status} onChange={event => { setStatus(event.target.value); setPage(0); setSelected(null) }}>{['SUBMITTED', 'APPROVED', 'NEEDS_CHANGES', 'REJECTED'].map(value => <option key={value} value={value}>{statusLabels[value]}</option>)}</select><button className={secondaryClass} disabled={loading} onClick={() => { setSelected(null); setRevision(value => value + 1) }}>Cập nhật</button></div>
    {notice && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}{error && <p role="alert" className="text-red-700">{error}</p>}
    {loading ? <p role="status">Đang lấy danh sách…</p> : <div className="grid gap-3 md:grid-cols-2">{items.map(item => <button key={item.attemptId || item.userId} className={`${cardClass} text-left hover:border-brand-500`} onClick={() => setSelected(mode === 'identity' ? item.attemptId : item.userId)}><p className="break-all font-semibold">{item.fullName || item.profile?.displayName || item.email || `Tài khoản ${item.userId}`}</p><p className="mt-1 break-all text-sm text-slate-500">{item.email}</p><p className="mt-2 text-sm">{statusLabels[item.status]} · {humanDate(item.submittedAt)}</p><span className="mt-3 block text-sm font-semibold text-brand-700">Xem và xét duyệt →</span></button>)}{!items.length && <p className="text-sm text-slate-500">Không có hồ sơ ở trạng thái này.</p>}</div>}
    <div className="flex gap-3"><button className={secondaryClass} disabled={loading || page === 0} onClick={() => { setPage(value => value - 1); setSelected(null) }}>Trang trước</button><span className="self-center text-sm">Trang {page + 1}</span><button className={secondaryClass} disabled={loading || items.length < 20} onClick={() => { setPage(value => value + 1); setSelected(null) }}>Trang sau</button></div>
    {selected && <ReviewDetail key={`${mode}-${selected}`} mode={mode} id={selected} onClose={() => setSelected(null)} onReviewed={async () => { setSelected(null); setNotice('Đã lưu quyết định.'); setRevision(value => value + 1) }} />}
  </div>
}
