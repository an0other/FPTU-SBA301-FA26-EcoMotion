import { useCallback, useEffect, useState } from 'react'
import { identityVerificationService as api } from '../../services/identityVerificationService'
import { buttonClass, secondaryClass, inputClass, cardClass, identityMessage, humanDate } from './identityUi'

export default function AccountLinkPanel({ userId, activationToken, canInitiate = false, onChanged }) {
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmId, setConfirmId] = useState(null)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => { const data = activationToken ? await api.activationLinks(activationToken, page) : await api.links(page); setItems(data) }, [activationToken, page])
  useEffect(() => { let alive = true; (activationToken ? api.activationLinks(activationToken, page) : api.links(page)).then(data => { if (alive) setItems(data) }).catch(err => { if (alive) setError(identityMessage(err)) }); return () => { alive = false } }, [activationToken, page])
  async function perform(action) {
    setBusy(true); setError(''); setMessage('')
    try { await action() } catch (err) { setError(identityMessage(err)); if (err.status === 409) { try { await load() } catch { } } }
    finally { setBusy(false); setPassword(''); setConfirmPassword('') }
  }
  return <section className={`${cardClass} space-y-4`}>
    <div className="flex flex-wrap justify-between gap-2"><h2 className="text-lg font-bold">Liên kết tài khoản Renter và Provider</h2><button className={secondaryClass} disabled={busy} onClick={() => perform(load)}>Cập nhật yêu cầu</button></div>
    <p className="text-sm text-slate-600">Hai tài khoản vẫn có email và mật khẩu riêng. Tài khoản đã xác minh tạo yêu cầu, tài khoản còn lại xác nhận để dùng chung danh tính. Provider vẫn cần Admin duyệt.</p>
    {!canInitiate && <p className="text-sm text-slate-500">Nếu đã có tài khoản vai trò khác được xác minh, hãy đăng nhập tài khoản đó, vào “Liên kết tài khoản” và nhập email của tài khoản này.</p>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}{message && <p role="status" className="text-sm text-emerald-700">{message}</p>}
    {canInitiate && <form className="grid gap-3 sm:grid-cols-2" onSubmit={event => { event.preventDefault(); perform(async () => { await api.requestLink(email.trim(), password); setMessage('Đã tạo yêu cầu. Đăng nhập tài khoản đích để xác nhận trước khi yêu cầu hết hạn.'); setEmail(''); await load() }) }}>
      <label className="text-sm">Email tài khoản vai trò còn lại<input type="email" required className={`${inputClass} mt-1`} value={email} onChange={event => setEmail(event.target.value)} disabled={busy} /></label>
      <label className="text-sm">Mật khẩu tài khoản hiện tại<input type="password" required autoComplete="current-password" className={`${inputClass} mt-1`} value={password} onChange={event => setPassword(event.target.value)} disabled={busy} /></label><button className={buttonClass} disabled={busy}>Tạo yêu cầu liên kết</button>
    </form>}
    {!items.length && <p className="text-sm text-slate-500">Chưa có yêu cầu liên kết.</p>}
    {items.map(item => <article key={item.id} className="space-y-2 rounded-xl border border-slate-200 p-4 text-sm">
      <p className="break-all font-semibold">{item.sourceEmail} → {item.targetEmail}</p><p>{({ PENDING: 'Đang chờ xác nhận', CONFIRMED: 'Đã liên kết', CANCELLED: 'Đã hủy', EXPIRED: 'Đã hết hạn' })[item.status] || item.status} · Hết hạn: {humanDate(item.expiresAt)}</p>
      {item.status === 'PENDING' && <div className="flex flex-wrap gap-2">{item.targetUserId === userId && <button className={buttonClass} disabled={busy} onClick={() => { setConfirmId(item.id); setConfirmPassword('') }}>Xác nhận bằng tài khoản này</button>}<button className={secondaryClass} disabled={busy} onClick={() => perform(async () => { if (activationToken) await api.cancelActivationLink(activationToken, item.id, item.version); else await api.cancelLink(item.id, item.version); setConfirmId(null); await load() })}>Hủy yêu cầu</button></div>}
      {item.status === 'PENDING' && item.id === confirmId && <form className="space-y-2" onSubmit={event => { event.preventDefault(); perform(async () => {
        const result = activationToken ? await api.confirmActivationLink(activationToken, item.id, item.version, confirmPassword) : await api.confirmLink(item.id, item.version, confirmPassword)
        setConfirmId(null); setMessage('Đã liên kết. Hãy hoàn tất OTP điện thoại và các bước còn lại.')
        if (activationToken && result.targetAccountStatus === 'ACTIVE') { onChanged?.(result); return }
        await load(); await onChanged?.(result)
      }) }}><label className="block">Mật khẩu của {item.targetEmail}<input className={`${inputClass} mt-1`} type="password" required autoComplete="current-password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} disabled={busy} /></label><button className={buttonClass} disabled={busy}>Đồng ý liên kết danh tính</button></form>}
    </article>)}
    <div className="flex gap-2"><button className={secondaryClass} disabled={busy || page === 0} onClick={() => setPage(value => value - 1)}>Trang trước</button><span className="self-center text-sm">Trang {page + 1}</span><button className={secondaryClass} disabled={busy || items.length < 20} onClick={() => setPage(value => value + 1)}>Trang sau</button></div>
  </section>
}
