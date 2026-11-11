import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import PhoneVerification from '../components/identity/PhoneVerification'
import IdentityVerificationPanel from '../components/identity/IdentityVerificationPanel'
import AccountLinkPanel from '../components/identity/AccountLinkPanel'
import { authService } from '../services/authService'
import { identityVerificationService as identity } from '../services/identityVerificationService'
import { clearPendingActivation, getPendingActivation, setPendingActivation } from '../utils/pendingActivation'
import { buttonClass, secondaryClass, inputClass, identityMessage } from '../components/identity/identityUi'

export default function ActivatePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [token, setToken] = useState(() => getPendingActivation()?.token || '')
  const [email, setEmail] = useState(() => getPendingActivation()?.email || location.state?.email || '')
  const [otp, setOtp] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [retryAt, setRetryAt] = useState(0)
  const [revision, setRevision] = useState(0)
  const completed = useCallback(() => {
    clearPendingActivation()
    navigate('/login', { replace: true, state: { email, notice: 'Xác minh đã hoàn tất. Bạn có thể đăng nhập để sử dụng tài khoản.' } })
  }, [email, navigate])
  const expired = useCallback(() => { clearPendingActivation(); setToken(''); setSummary(null); setError('Phiên kích hoạt đã hết hiệu lực. Hãy xác nhận email để tiếp tục hoặc đăng nhập nếu tài khoản đã được kích hoạt.') }, [])
  const reload = useCallback(async () => {
    if (!token) return
    try { setSummary(await identity.activationStatus(token)) }
    catch (err) { if ([401, 410].includes(err.status)) expired(); else setError(identityMessage(err)) }
  }, [token, expired])
  useEffect(() => { if (token) { let alive = true; identity.activationStatus(token).then(data => { if (alive) setSummary(data) }).catch(err => { if (alive) { if ([401, 410].includes(err.status)) expired(); else setError(identityMessage(err)) } }); return () => { alive = false } } }, [token, expired])
  async function perform(action) {
    setBusy(true); setError('')
    try { await action() } catch (err) { setError(identityMessage(err)) } finally { setBusy(false) }
  }
  return <AuthLayout title="Hoàn tất xác minh tài khoản" subtitle="Xác nhận điện thoại, CCCD và video khuôn mặt để tiếp tục.">
    <div className="space-y-5">
      {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {!token && <form className="space-y-3" onSubmit={event => { event.preventDefault(); perform(async () => { const result = await authService.activateResume(email.trim(), otp.trim()); setPendingActivation({ token: result.activationToken, email: email.trim() }); setToken(result.activationToken); setOtp('') }) }}>
        <p className="text-sm text-slate-600">Nhập email tài khoản đang kích hoạt để lấy lại phiên. Nếu đã hoàn tất, hãy đăng nhập.</p>
        <label className="block text-sm">Email<input className={`${inputClass} mt-1`} type="email" required value={email} onChange={event => { setEmail(event.target.value); setSent(false) }} disabled={busy} /></label>
        <button type="button" className={secondaryClass} disabled={busy || !email.trim()} onClick={() => perform(async () => { if (Date.now() < retryAt) { setError('Vui lòng đợi trước khi gửi lại OTP.'); return } const result = await authService.activateResumeOtp(email.trim()); setSent(true); setRetryAt(Date.now() + (result.resendCooldownSeconds || 60) * 1000) })}>{sent ? 'Gửi lại OTP email' : 'Gửi OTP email'}</button>
        {sent && <><label className="block text-sm">OTP email<input className={`${inputClass} mt-1`} inputMode="numeric" autoComplete="one-time-code" required value={otp} onChange={event => setOtp(event.target.value)} disabled={busy} /></label><button className={buttonClass} disabled={busy || !otp.trim()}>Tiếp tục kích hoạt</button></>}
      </form>}
      {token && !summary && <p role="status">Đang lấy trạng thái… <button className="underline" onClick={reload}>Thử lại</button></p>}
      {token && summary && <>
        <PhoneVerification activationToken={token} verified={summary.phoneVerified} onExpired={expired} onVerified={async () => { if (summary.identityId && summary.role === 'RENTER') completed(); else await reload() }} />
        <AccountLinkPanel activationToken={token} userId={summary.userId} onChanged={async result => { if (result.targetAccountStatus === 'ACTIVE') completed(); else { await reload(); setRevision(value => value + 1) } }} />
        {summary.phoneVerified && <IdentityVerificationPanel key={revision} activationToken={token} onComplete={completed} onChanged={reload} onExpired={expired} />}
        {summary.role === 'PROVIDER' && <p className="text-sm text-slate-600">Provider có thể <Link className="font-semibold text-brand-700 underline" to="/login">đăng nhập</Link> để tiếp tục nhập hồ sơ và theo dõi xét duyệt.</p>}
      </>}
      <div className="flex flex-wrap gap-4 text-sm"><Link className="font-semibold text-brand-700" to="/login">Đăng nhập</Link><Link to="/register">Tạo tài khoản</Link>{token && <button className="text-brand-700 underline" onClick={() => { clearPendingActivation(); setToken(''); setSummary(null); setSent(false); setError(''); setOtp('') }}>Khôi phục phiên qua email</button>}</div>
    </div>
  </AuthLayout>
}
