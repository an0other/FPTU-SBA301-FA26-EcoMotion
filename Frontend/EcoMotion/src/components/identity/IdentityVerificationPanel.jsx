import { useCallback, useEffect, useRef, useState } from 'react'
import { identityVerificationService as api } from '../../services/identityVerificationService'
import VideoCapture from './VideoCapture'
import { buttonClass, secondaryClass, cardClass, identityMessage, statusLabels } from './identityUi'

function DocumentUpload({ label, value, onChange, disabled }) {
  const previewRef = useRef(null)
  useEffect(() => {
    if (!value) return
    const url = URL.createObjectURL(value)
    if (previewRef.current) previewRef.current.src = url
    return () => URL.revokeObjectURL(url)
  }, [value])
  return <label className={`relative flex min-h-40 flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed p-4 text-center transition ${value ? 'border-emerald-400 bg-emerald-50/50' : 'border-slate-200 bg-slate-50 hover:border-emerald-400'} ${disabled ? 'opacity-60' : 'cursor-pointer'}`}>
    {value ? <img ref={previewRef} alt={`Ảnh ${label.toLowerCase()} đã chọn`} className="h-24 w-full rounded-lg object-contain" /> : <span aria-hidden="true" className="text-3xl text-slate-400">＋</span>}
    <span className="text-sm font-semibold text-slate-800">{label}</span>
    <span className="max-w-full truncate text-xs text-slate-500">{value ? value.name : 'Chọn ảnh JPEG hoặc PNG'}</span>
    <input className="absolute inset-0 h-full w-full cursor-pointer opacity-0" aria-label={`Tải ảnh ${label.toLowerCase()}`} type="file" accept="image/jpeg,image/png" disabled={disabled} onChange={event => onChange(event.target.files?.[0] || null)} />
  </label>
}

export default function IdentityVerificationPanel({ activationToken, onComplete, onChanged, onExpired, onPrepared }) {
  const [attempt, setAttempt] = useState(null)
  const [constraints, setConstraints] = useState(null)
  const [front, setFront] = useState(null)
  const [back, setBack] = useState(null)
  const [video, setVideo] = useState(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [capturing, setCapturing] = useState(false)
  const [selectedStep, setSelectedStep] = useState(null)
  const aliveRef = useRef(true)
  useEffect(() => { aliveRef.current = true; return () => { aliveRef.current = false } }, [])

  const fetchAttempt = useCallback(async () => {
    const summary = activationToken ? await api.activationStatus(activationToken) : await api.status()
    if (summary.attemptId) {
      const result = activationToken ? await api.activationAttempt(activationToken, '', summary.attemptId) : await api.getAttempt(summary.attemptId)
      return result
    }
    return null
  }, [activationToken])
  const load = useCallback(async () => setAttempt(await fetchAttempt()), [fetchAttempt])

  useEffect(() => {
    let alive = true
    Promise.allSettled([api.constraints(), fetchAttempt()]).then(results => {
      if (!alive) return
      if (results[0].status === 'fulfilled') setConstraints(results[0].value)
      else setError('Chưa lấy được giới hạn từ dịch vụ xác minh. Hãy thử cập nhật lại sau.')
      if (results[1].status === 'rejected') setError(identityMessage(results[1].reason))
      else setAttempt(results[1].value)
      setLoading(false)
    })
    return () => { alive = false }
  }, [fetchAttempt])

  async function perform(label, action) {
    setBusy(label)
    setError('')
    try { await action() }
    catch (err) {
      setError(identityMessage(err))
      if ([401, 410].includes(err.status) && activationToken && aliveRef.current) onExpired?.()
      try {
        const latest = await fetchAttempt()
        if (aliveRef.current) {
          setAttempt(latest)
          setConfirmed(false)
          if (latest?.attemptId !== attempt?.attemptId || latest?.documentRevision !== attempt?.documentRevision) {
            setVideo(null)
            setFront(null)
            setBack(null)
            setSelectedStep(null)
          }
        }
      } catch { }
    } finally { setBusy('') }
  }

  async function create() {
    const result = activationToken ? await api.activationAttempt(activationToken) : await api.createAttempt()
    setAttempt(result)
    setConfirmed(false)
    setSelectedStep(0)
  }

  async function documents(event) {
    event.preventDefault()
    if (!front || !back) { setError('Hãy chọn đủ ảnh mặt trước và mặt sau CCCD.'); return }
    if ([front, back].some(file => file.size > constraints.maxImageBytes)) { setError('Ảnh CCCD vượt dung lượng cho phép.'); return }
    await perform('Đang đọc hai mặt CCCD…', async () => {
      const result = activationToken
        ? await api.uploadActivationDocuments(activationToken, attempt.attemptId, attempt.version, { cccdFront: front, cccdBack: back })
        : await api.uploadDocuments(attempt.attemptId, attempt.version, { cccdFront: front, cccdBack: back })
      setAttempt(result)
      setVideo(null)
      setFront(null)
      setBack(null)
      setConfirmed(false)
      setSelectedStep(0)
    })
  }

  async function uploadVideo() {
    if (!video) return
    await perform('Đang phân tích video và đối chiếu khuôn mặt. Vui lòng giữ trang này mở…', async () => {
      const latest = await fetchAttempt()
      setAttempt(latest)
      if (latest?.attemptId !== attempt.attemptId || latest?.documentRevision !== attempt.documentRevision) {
        setVideo(null)
        setSelectedStep(null)
        throw new Error('CCCD đã thay đổi. Hãy kiểm tra lại thông tin trước khi gửi video.')
      }
      const result = activationToken ? await api.uploadActivationVideo(activationToken, latest.attemptId, latest.version, video)
        : await api.uploadVideo(latest.attemptId, latest.version, video)
      setAttempt(result)
      setVideo(null)
      setConfirmed(false)
      setSelectedStep(result.status === 'READY_TO_SUBMIT' ? 2 : 1)
    })
  }

  async function submit() {
    await perform('Đang gửi xác nhận…', async () => {
      const result = activationToken ? await api.submitActivationAttempt(activationToken, attempt.attemptId, attempt.version, confirmed)
        : await api.submitAttempt(attempt.attemptId, attempt.version, confirmed)
      if (result.accountActivated) { if (aliveRef.current) onComplete?.(result); return }
      await load()
      if (aliveRef.current) onChanged?.()
      if (aliveRef.current && result.status === 'READY_TO_SUBMIT') onPrepared?.()
    })
  }

  const terminal = ['CANCELLED', 'EXPIRED', 'REJECTED', 'APPROVED'].includes(attempt?.status)
  const editable = attempt && !terminal && !['SUBMITTED', 'PROCESSING'].includes(attempt.status)
  const writable = constraints?.verificationEnabled && !busy && !loading && !capturing
  const availableStep = attempt?.status === 'READY_TO_SUBMIT' ? 2 : attempt?.document?.requiredFieldsReadable ? 1 : 0
  const resumeStep = attempt?.decisionReason === 'DOCUMENT_UNREADABLE' && !attempt.canSubmit ? 0
    : attempt?.decisionReason === 'FACE_UNCLEAR' && !attempt.canSubmit ? 1 : availableStep
  const step = Math.min(selectedStep ?? resumeStep, availableStep)
  return <section className={`${cardClass} space-y-5`}>
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-bold">Xác minh CCCD và khuôn mặt</h2><p className="mt-1 text-sm text-slate-500">Đọc hai mặt CCCD, quay video và xác nhận gửi.</p></div><button className={secondaryClass} disabled={!!busy || capturing} onClick={() => perform('Đang cập nhật…', async () => { const limits = await api.constraints(); setConstraints(limits); await load(); onChanged?.() })}>Cập nhật trạng thái</button></div>
    {loading && <p role="status">Đang tải phiên xác minh…</p>}
    {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {busy && <p role="status" className="rounded-xl bg-blue-50 p-3 text-sm text-blue-800">{busy}</p>}
    {constraints && (!constraints.acceptanceConfigured || !constraints.verificationEnabled) && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Hệ thống chưa sẵn sàng hoàn tất xác minh. Vui lòng liên hệ quản trị viên.</p>}
    {editable && <nav aria-label="Các bước xác minh"><ol className="grid grid-cols-3 gap-2">{['CCCD', 'Video khuôn mặt', 'Xác nhận'].map((label, index) => <li key={label}><button type="button" aria-current={step === index ? 'step' : undefined} disabled={!writable || index > availableStep} onClick={() => { setSelectedStep(index); setError('') }} className={`flex w-full flex-col items-center gap-2 rounded-xl border px-2 py-4 text-center text-xs font-semibold transition sm:text-sm ${step === index ? 'border-emerald-500 bg-emerald-50 text-emerald-800' : 'border-slate-200 text-slate-500'} disabled:cursor-not-allowed`}><span className={`flex h-7 w-7 items-center justify-center rounded-full ${step === index ? 'bg-emerald-600 text-white' : 'bg-slate-100'}`}>{index < availableStep ? '✓' : index + 1}</span>{label}</button></li>)}</ol></nav>}
    {attempt && (!editable || attempt.decisionReason || (attempt.lastErrorCode && !error)) && <div className="rounded-xl bg-slate-50 p-3 text-sm"><strong>{statusLabels[attempt.status] || attempt.status}</strong>
      {(attempt.decisionReason || attempt.lastErrorCode) && <p className="mt-1">{identityMessage(attempt.decisionReason || attempt.lastErrorCode)}</p>}
      {attempt.status === 'SUBMITTED' && <p className="mt-1">Danh tính đang chờ Admin xem xét. Bạn có thể tiếp tục nhập hồ sơ Provider.</p>}
      {attempt.status === 'APPROVED' && <p className="mt-1">Đã xác minh danh tính. Provider tiếp tục hoàn thiện và gửi hồ sơ hoạt động.</p>}
      {attempt.status === 'PROCESSING' && <p className="mt-1">Yêu cầu đang được xử lý. Hãy cập nhật trạng thái sau ít phút; không cần tạo phiên mới.</p>}
    </div>}
    {(!attempt || ['CANCELLED', 'EXPIRED', 'REJECTED'].includes(attempt.status)) && <button className={buttonClass} disabled={!writable} onClick={() => perform('Đang tạo phiên…', create)}>Bắt đầu xác minh</button>}
    {editable && step === 0 && <form onSubmit={documents} className="space-y-4">
      <h3 className="font-semibold">1. Hai mặt CCCD</h3>
      <p className="text-sm text-slate-500">JPEG/PNG, tối đa {((constraints?.maxImageBytes || 0) / 1048576).toFixed(0)} MB mỗi ảnh. Đặt thẻ ngay ngắn, rõ chữ và không lóa.</p>
      <div className="grid gap-3 sm:grid-cols-2">{[['Mặt trước', front, setFront], ['Mặt sau', back, setBack]].map(([label, value, setter]) => <DocumentUpload key={`${label}-${attempt.documentRevision}`} label={label} value={value} disabled={!writable} onChange={file => { setter(file); setConfirmed(false) }} />)}</div>
      <button type="submit" className={secondaryClass} disabled={!writable || !front || !back}>{attempt.document ? 'Đọc lại CCCD (sẽ xóa kết quả video cũ)' : 'Đọc thông tin CCCD'}</button>
    </form>}
    {attempt?.document && (step === 0 || step === 2) && <dl className="grid gap-2 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">
      <div><dt className="text-slate-500">Họ tên</dt><dd className="font-semibold">{attempt.document.fullName || 'Chưa đọc được'}</dd></div><div><dt className="text-slate-500">Số CCCD</dt><dd>{attempt.document.documentNumber || 'Chưa đọc được'}</dd></div><div><dt className="text-slate-500">Ngày sinh</dt><dd>{attempt.document.dateOfBirth || 'Chưa đọc được'}</dd></div><div><dt className="text-slate-500">Ngày hết hạn</dt><dd>{attempt.document.expiryDate || 'Chưa đọc được'}</dd></div>
    </dl>}
    {editable && step === 0 && availableStep >= 1 && <button className={buttonClass} disabled={!writable || !!front || !!back} onClick={() => setSelectedStep(1)}>Thông tin đúng, tiếp tục →</button>}
    {editable && step === 1 && attempt.document?.requiredFieldsReadable && constraints && <div className="space-y-4">
      <h3 className="font-semibold">2. Video khuôn mặt</h3><p className="text-sm text-slate-500">Tối đa {constraints.maxVideoSeconds} giây và {(constraints.maxVideoBytes / 1048576).toFixed(0)} MB.</p>
      <VideoCapture value={video} onChange={file => { setVideo(file); setConfirmed(false) }} constraints={constraints} disabled={!constraints.verificationEnabled || !!busy} onCaptureChange={setCapturing} />
      <button className={buttonClass} disabled={!writable || !video} onClick={uploadVideo}>Gửi video để kiểm tra</button>
      {availableStep === 2 && !video && <button className={secondaryClass} disabled={!writable} onClick={() => setSelectedStep(2)}>Tiếp tục xác nhận →</button>}
    </div>}
    {editable && attempt?.video && step !== 0 && <div className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-900"><p>Động tác: {attempt.video.motionChallenge?.status === 'PASS' ? 'Đã hoàn thành' : 'Chưa hoàn thành đủ hướng dẫn'}</p><p className="mt-1">{attempt.role === 'PROVIDER' ? 'CCCD/video sẽ được gửi cùng thông tin dịch vụ ở bước cuối.' : 'Hệ thống sẽ xác minh danh tính sau khi bạn xác nhận gửi.'}</p></div>}
    {step === 2 && attempt?.status === 'READY_TO_SUBMIT' && <div className="space-y-4">{(video || front || back) && <p className="text-sm text-amber-800">Bạn đã chọn dữ liệu mới. Hãy quay lại bước tương ứng và gửi để kiểm tra trước khi xác nhận.</p>}<label className="flex items-start gap-3 rounded-xl border border-slate-200 p-4 text-sm leading-6"><input className="mt-1 accent-emerald-600" type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={!!busy || capturing} />Tôi xác nhận CCCD và người trong video là của chính tôi.</label><button className={buttonClass} disabled={!!busy || capturing || !!video || !!front || !!back || !confirmed || !attempt.canSubmit} onClick={submit}>{attempt.role === 'PROVIDER' ? 'Xác nhận và tiếp tục hồ sơ' : 'Xác nhận và gửi hồ sơ'}</button></div>}
    {attempt && !terminal && attempt.status !== 'SUBMITTED' && <button className="text-sm font-medium text-red-700 underline disabled:opacity-50" disabled={!!busy || capturing} onClick={() => perform('Đang hủy phiên…', async () => { const result = activationToken ? await api.cancelActivationAttempt(activationToken, attempt.attemptId, attempt.version) : await api.cancelAttempt(attempt.attemptId, attempt.version); setAttempt(result); setVideo(null); setFront(null); setBack(null) })}>Hủy phiên hiện tại</button>}
  </section>
}
