import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { providerOnboardingService as api } from '../../services/providerOnboardingService'
import { identityVerificationService as identity } from '../../services/identityVerificationService'
import PhoneVerification from '../../components/identity/PhoneVerification'
import IdentityVerificationPanel from '../../components/identity/IdentityVerificationPanel'
import { PickupMap } from '../../components/booking/PickupMap'
import { buttonClass, secondaryClass, cardClass, inputClass, identityMessage, statusLabels } from '../../components/identity/identityUi'

const emptyForm = { displayName: '', hotline: '', depotAddress: '', latitude: '', longitude: '', bankCode: '', bankAccountNo: '', bankAccountHolder: '' }

export default function ProviderOnboardingPage() {
  const { user, logout, refreshUser, refreshSession } = useAuth()
  const navigate = useNavigate()
  const [application, setApplication] = useState(null)
  const [summary, setSummary] = useState(null)
  const [banks, setBanks] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [revision, setRevision] = useState(0)
  const [step, setStep] = useState(0)
  const [showMap, setShowMap] = useState(false)
  const [consent, setConsent] = useState(false)
  const mapError = useCallback(value => setError(value), [])
  const load = useCallback(async () => {
    const [app, state] = await Promise.all([api.get(), identity.status()])
    setApplication(app); setSummary(state)
    if (!dirty) setForm({ ...emptyForm, ...Object.fromEntries(Object.entries(app.profile || {}).filter(([, value]) => value !== null)) })
    return app
  }, [dirty])
  useEffect(() => {
    let alive = true
    Promise.all([api.get(), identity.status(), api.options()]).then(([app, state, options]) => {
      if (!alive) return
      setApplication(app); setSummary(state); setBanks(options)
      setStep(['SUBMITTED', 'APPROVED', 'REJECTED', 'NEEDS_CHANGES'].includes(app.status) ? 3 : app.profile ? 2 : state.phoneVerified && (app.identityVerified || (state.attemptStatus === 'SUBMITTED' || state.attemptStatus === 'READY_TO_SUBMIT' && app.nextAction !== 'VERIFY_IDENTITY')) ? 1 : 0)
      setForm({ ...emptyForm, ...Object.fromEntries(Object.entries(app.profile || {}).filter(([, value]) => value !== null)) })
    }).catch(err => { if (alive) setError(identityMessage(err)) })
    return () => { alive = false }
  }, [])

  async function perform(label, action) {
    setBusy(label); setError(''); setMessage('')
    try { await action() } catch (err) {
      const raw = err.original?.response?.data?.message || ''
      const fields = { latitude: 'Vĩ độ', longitude: 'Kinh độ', displayName: 'Tên hiển thị', hotline: 'Hotline', depotAddress: 'Địa chỉ giao nhận', bankCode: 'Ngân hàng', bankAccountNo: 'Số tài khoản', bankAccountHolder: 'Chủ tài khoản', version: 'Phiên bản hồ sơ' }
      const invalid = Object.entries(fields).filter(([key]) => new RegExp(`\\b${key}\\b`).test(raw)).map(([, label]) => label)
      setError(invalid.length ? `Vui lòng kiểm tra: ${invalid.join(', ')}. ${/latitude|longitude/.test(raw) ? 'Tọa độ phải đúng phạm vi và tối đa 6 chữ số thập phân.' : ''}` : identityMessage(err))
      if (err.status === 409) { try { await load() } catch { } }
    }
    finally { setBusy('') }
  }
  const update = (key, value) => { setForm(previous => ({ ...previous, [key]: value, ...(key === 'bankCode' && !value ? { bankAccountNo: '', bankAccountHolder: '' } : {}) })); setDirty(true); setConsent(false) }
  const canEdit = application?.canEdit && !busy
  const hasPoint = form.latitude !== '' && form.longitude !== '' && form.latitude != null && form.longitude != null
  const identitySent = application?.identityVerified || (summary?.attemptStatus === 'SUBMITTED' || summary?.attemptStatus === 'READY_TO_SUBMIT' && application?.nextAction !== 'VERIFY_IDENTITY')
  function go(next) { setStep(next); setError(''); setMessage(''); setConsent(false) }

  return <div className="min-h-screen bg-slate-50 text-slate-900">
    <header className="border-b border-slate-200 bg-white"><div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-4"><Link className="text-xl font-extrabold text-brand-700" to="/">EcoMotion</Link><span className="break-all text-sm text-slate-600">{user.email}</span><button className={secondaryClass} onClick={async () => { await logout(); navigate('/login') }}>Đăng xuất</button></div></header>
    <main className="mx-auto max-w-4xl space-y-5 px-4 py-8">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-2xl font-bold">Hoàn thiện hồ sơ Provider cá nhân</h1><p className="mt-2 text-sm text-slate-600">Xác minh danh tính và gửi hồ sơ hoạt động. Sau khi được duyệt, bạn có thể quản lý xe và nhận đơn.</p></div><button className={secondaryClass} disabled={!!busy} onClick={() => perform('Đang cập nhật…', async () => { await load(); await refreshUser(); setRevision(value => value + 1) })}>Cập nhật trạng thái</button></div>
      <nav aria-label="Các bước đăng ký Provider"><ol className="grid grid-cols-2 gap-2 sm:grid-cols-4">{['Xác minh', 'Thông tin dịch vụ', 'Kiểm tra hồ sơ', 'Theo dõi xét duyệt'].map((label, index) => <li key={label}><button type="button" aria-current={step === index ? 'step' : undefined} disabled={!application || !!busy || (index === 2 && (!application.profile || dirty))} onClick={() => go(index)} className={`flex h-full w-full items-center gap-2 rounded-xl border p-3 text-left text-sm font-semibold disabled:opacity-40 ${step === index ? 'border-emerald-500 bg-emerald-50 text-emerald-800' : 'border-slate-200 bg-white text-slate-500'}`}><span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${step === index ? 'bg-emerald-600 text-white' : 'bg-slate-100'}`}>{index + 1}</span>{label}</button></li>)}</ol></nav>
      {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{error}</p>}{message && <p role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-800">{message}</p>}{busy && <p role="status" className="text-sm text-brand-700">{busy}</p>}
      {!application && !error && <p role="status" className={cardClass}>Đang tải hồ sơ…</p>}
      {application && step === 3 && <div className="grid gap-3 sm:grid-cols-3">{[['Điện thoại', summary?.phoneVerified ? 'Đã xác nhận' : 'Chưa xác nhận'], ['Danh tính', application.identityVerified ? 'Admin đã duyệt' : statusLabels[summary?.attemptStatus] || 'Chưa hoàn tất'], ['Hồ sơ Provider', statusLabels[application.status] || application.status]].map(([label, value]) => <div key={label} className={cardClass}><p className="text-xs font-semibold uppercase text-slate-500">{label}</p><p className="mt-2 font-bold">{value}</p></div>)}</div>}
      {step === 3 && application?.status === 'APPROVED' && <div className={`${cardClass} space-y-3 border-emerald-300`}><h2 className="font-bold text-emerald-800">Hồ sơ Provider đã được duyệt</h2><button className={buttonClass} disabled={!!busy} onClick={() => perform('Đang mở tài khoản…', async () => { const me = await refreshSession(); if (me.sessionScope !== 'FULL') throw new Error('Tài khoản vẫn bị hạn chế. Vui lòng liên hệ hỗ trợ.'); navigate('/provider', { replace: true }) })}>Cập nhật quyền và vào dashboard</button></div>}
      {application?.reviewNote && <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Admin hướng dẫn: {application.reviewNote}</p>}
      {application?.reasonCode && <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Lý do phản hồi: {identityMessage(application.reasonCode)}</p>}
      {step === 3 && application?.status === 'REJECTED' && <p className="text-sm text-slate-600">Hồ sơ bị từ chối. Bạn có thể sửa thông tin, bổ sung giấy tờ và gửi lại.</p>}
      {step === 0 && summary && !summary.phoneVerified && <section className={cardClass}><PhoneVerification verified={summary.phoneVerified} onVerified={async () => { await load(); await refreshUser() }} /></section>}
      {step === 0 && summary && !identitySent && <>
        {summary.phoneVerified && <IdentityVerificationPanel key={revision} onPrepared={() => go(1)} onChanged={async () => { await load(); await refreshUser() }} />}
      </>}
      {step === 0 && identitySent && <section className={`${cardClass} space-y-3`}><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-bold">Xác minh danh tính</h2><span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800">{application.identityVerified ? 'Đã được duyệt' : 'CCCD/video đã chuẩn bị'}</span></div><p className="text-sm text-slate-500">Hoàn thiện thông tin dịch vụ rồi gửi chung với CCCD/video cho Admin duyệt một lần.</p><details><summary className="cursor-pointer text-sm font-semibold text-brand-700">Xem chi tiết xác minh</summary><div className="mt-4"><IdentityVerificationPanel key={revision} onPrepared={() => go(1)} onChanged={load} /></div></details></section>}
      {step === 0 && application && <div className="flex justify-end"><button className={buttonClass} disabled={!!busy} onClick={() => go(1)}>Tiếp tục: thông tin dịch vụ →</button></div>}
      {application && step === 1 && <section className={`${cardClass} space-y-4`}><h2 className="text-lg font-bold">Thông tin cung cấp dịch vụ</h2><p className="text-sm text-slate-500">Thông tin liên hệ và điểm giao nhận xe chính của bạn.</p>
        <form className="grid gap-4 sm:grid-cols-2" onInvalid={event => { const details = event.target.closest('details'); if (details) details.open = true }} onSubmit={event => { event.preventDefault(); const next = event.nativeEvent.submitter?.value === 'continue'; perform('Đang lưu hồ sơ…', async () => {
          if (!hasPoint) throw new Error('Vui lòng chọn vị trí giao nhận.');
          const data = await api.save({ version: application.version, displayName: form.displayName.trim(), hotline: form.hotline.trim(), depotAddress: form.depotAddress.trim(), latitude: Number(form.latitude), longitude: Number(form.longitude), bankCode: form.bankCode || null, bankAccountNo: form.bankAccountNo.trim() || null, bankAccountHolder: form.bankAccountHolder.trim() || null })
          setApplication(data); setDirty(false); setConsent(false); if (next) setStep(2); setMessage('Đã lưu hồ sơ.')
        }) }}>
          <label className="text-sm">Tên hiển thị *<input className={`${inputClass} mt-1`} required maxLength={150} value={form.displayName} disabled={!canEdit} onChange={event => update('displayName', event.target.value)} /></label>
          <label className="text-sm">Hotline *<input className={`${inputClass} mt-1`} type="tel" pattern="0[0-9]{9,10}" required value={form.hotline} disabled={!canEdit} onChange={event => update('hotline', event.target.value)} /></label>
          <label className="text-sm sm:col-span-2">Địa chỉ giao nhận *<input className={`${inputClass} mt-1`} required maxLength={500} value={form.depotAddress} disabled={!canEdit} onChange={event => { update('depotAddress', event.target.value); setForm(previous => ({ ...previous, latitude: '', longitude: '' })) }} /></label>
          <div className="space-y-3 sm:col-span-2"><div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 p-3"><span className="text-sm text-slate-500">{hasPoint ? 'Đã chọn vị trí giao nhận' : 'Chưa chọn vị trí trên bản đồ'}</span><button type="button" className={secondaryClass} disabled={!canEdit} onClick={() => setShowMap(value => !value)}>{showMap ? 'Đóng bản đồ' : 'Chọn trên bản đồ'}</button></div>
          {showMap && <div className="space-y-3"><PickupMap readOnly={!canEdit} onError={mapError} value={{ address: form.depotAddress, latitude: hasPoint ? Number(form.latitude) : null, longitude: hasPoint ? Number(form.longitude) : null }} onChange={point => { setForm(previous => ({ ...previous, depotAddress: point.address || '', latitude: point.latitude ?? '', longitude: point.longitude ?? '' })); setDirty(true); setConsent(false) }} /><button type="button" className={secondaryClass} disabled={!hasPoint} onClick={() => setShowMap(false)}>Xác nhận vị trí</button></div>}
          <details><summary className="cursor-pointer text-xs text-slate-500">Nhập tọa độ thủ công</summary><div className="mt-3 grid grid-cols-2 gap-3">{[['Vĩ độ', 'latitude', 90], ['Kinh độ', 'longitude', 180]].map(([label, key, max]) => <label key={key} className="text-sm">{label}<input className={inputClass} type="number" min={-max} max={max} step="any" value={form[key]} disabled={!canEdit} onChange={event => update(key, event.target.value)} /></label>)}</div></details></div>
          <details className="rounded-xl border border-slate-200 p-4 sm:col-span-2"><summary className="cursor-pointer text-sm font-semibold">Thông tin thanh toán <span className="font-normal text-slate-500">· Có thể bổ sung sau</span></summary><div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="text-sm">Ngân hàng (có thể bổ sung sau)<select className={`${inputClass} mt-1`} value={form.bankCode} disabled={!canEdit} onChange={event => update('bankCode', event.target.value)}><option value="">Chưa khai báo</option>{banks.map(bank => <option key={bank.code} value={bank.code}>{bank.name}</option>)}</select></label>
          <label className="text-sm">Số tài khoản<input className={`${inputClass} mt-1`} inputMode="numeric" required={!!form.bankCode} pattern="[0-9]{6,30}" value={form.bankAccountNo} disabled={!canEdit} onChange={event => update('bankAccountNo', event.target.value)} /></label>
          <label className="text-sm sm:col-span-2">Chủ tài khoản<input className={`${inputClass} mt-1`} required={!!form.bankCode} maxLength={150} value={form.bankAccountHolder} disabled={!canEdit} onChange={event => update('bankAccountHolder', event.target.value)} /></label>
          </div></details>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4 sm:col-span-2"><button type="button" className={secondaryClass} disabled={!!busy} onClick={() => go(0)}>← Xác minh</button><div className="flex gap-2">{application.canEdit ? <><button type="submit" className={secondaryClass} disabled={!!busy}>Lưu thông tin</button><button type="submit" value="continue" className={buttonClass} disabled={!!busy}>Lưu và tiếp tục →</button></> : <button type="button" className={buttonClass} onClick={() => go(2)}>Xem hồ sơ →</button>}</div></div>
          <p className="text-xs text-slate-500 sm:col-span-2">{dirty ? 'Có thay đổi chưa lưu.' : application.profile ? 'Hồ sơ đã được lưu.' : 'Các trường có dấu * là bắt buộc.'}</p>
        </form>

      </section>}
      {application && step === 2 && <section className={`${cardClass} space-y-5`}><div><h2 className="text-lg font-bold">Kiểm tra trước khi gửi</h2><p className="mt-1 text-sm text-slate-500">Kiểm tra thông tin sẽ được gửi Admin xét duyệt.</p></div>
        {[['Danh tính', application.identityVerified ? 'Đã xác minh' : identitySent ? 'CCCD/video đã chuẩn bị' : 'Chưa hoàn tất CCCD/video', 0], ['Thông tin dịch vụ', `${form.displayName || 'Chưa khai báo'} · ${form.hotline || 'Chưa có hotline'}`, 1], ['Điểm giao nhận', form.depotAddress || 'Chưa khai báo', 1], ['Thanh toán', form.bankCode ? `${banks.find(bank => bank.code === form.bankCode)?.name || form.bankCode} · ${form.bankAccountNo} · ${form.bankAccountHolder}` : 'Chưa khai báo — có thể bổ sung sau', 1]].map(([label, value, target]) => <div key={label} className="flex items-start justify-between gap-4 rounded-xl bg-slate-50 p-4"><div className="min-w-0"><p className="text-xs text-slate-500">{label}</p><p className="mt-1 break-words text-sm font-semibold">{value}</p></div><button className="shrink-0 text-xs font-semibold text-brand-700" disabled={!!busy} onClick={() => go(target)}>{application.canEdit ? 'Chỉnh sửa' : 'Xem'}</button></div>)}
        {application.canEdit ? <>{!application.canSubmit && !identitySent && <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Hãy hoàn tất kiểm tra CCCD/video trước khi gửi hồ sơ. <button className="font-semibold underline" onClick={() => go(0)}>Xem xác minh</button></p>}{identitySent && !application.canSubmit && <p className="text-sm text-amber-800">Hồ sơ chưa đủ điều kiện gửi. Vui lòng kiểm tra xác minh tài khoản và thông tin dịch vụ.</p>}<label className="flex items-start gap-3 text-sm leading-6"><input type="checkbox" className="mt-1 accent-emerald-600" checked={consent} disabled={!!busy || !application.canSubmit} onChange={event => setConsent(event.target.checked)} />Tôi xác nhận thông tin dịch vụ chính xác và đồng ý gửi hồ sơ xét duyệt.</label><div className="flex flex-wrap justify-between gap-3 border-t border-slate-100 pt-4"><button className={secondaryClass} disabled={!!busy} onClick={() => go(1)}>← Thông tin dịch vụ</button><button className={buttonClass} disabled={!!busy || dirty || !application.canSubmit || !consent} onClick={() => perform('Đang gửi hồ sơ…', async () => { setApplication(await api.submit(application.version)); setConsent(false); setStep(3); setMessage('Đã gửi hồ sơ Provider.') })}>Gửi hồ sơ xét duyệt</button></div></> : <button className={buttonClass} onClick={() => go(3)}>Theo dõi xét duyệt →</button>}
      </section>}
      {application && step === 3 && application.status !== 'APPROVED' && <section className={`${cardClass} space-y-4`}><h2 className="text-lg font-bold">{statusLabels[application.status] || application.status}</h2><p className="text-sm leading-6 text-slate-600">{application.status === 'SUBMITTED' ? 'Hồ sơ đã được gửi thành công. Thông tin được giữ nguyên trong thời gian Admin xét duyệt.' : application.status === 'NEEDS_CHANGES' ? 'Admin yêu cầu bổ sung hồ sơ. Kiểm tra phản hồi và chỉnh sửa trước khi gửi lại.' : application.status === 'REJECTED' ? 'Bạn có thể sửa thông tin, bổ sung CCCD/video theo phản hồi và gửi lại hồ sơ.' : 'Hồ sơ chưa được gửi. Hoàn thiện xác minh và thông tin dịch vụ để tiếp tục.'}</p>{application.submittedAt && <p className="text-xs text-slate-500">Ngày gửi: {new Date(application.submittedAt).toLocaleString('vi-VN')}</p>}<div className="flex flex-wrap gap-3"><button className={buttonClass} disabled={!!busy} onClick={() => go(application.canEdit ? 1 : 2)}>{application.canEdit ? 'Tiếp tục hoàn thiện hồ sơ' : 'Xem hồ sơ đã gửi'}</button>{!application.identityVerified && <button className={secondaryClass} disabled={!!busy} onClick={() => go(0)}>Kiểm tra danh tính</button>}</div></section>}
    </main>
  </div>
}
