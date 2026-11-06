import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthLayout, { FieldError } from '../components/AuthLayout'
import { authService } from '../services/authService'
import { IconEye, IconEyeOff } from '../components/icons/Icons'

const inputClass = 'mt-2 w-full rounded-lg border border-slate-300 bg-slate-50 px-4 py-3 text-base text-slate-900 focus-visible:outline-2 focus-visible:outline-brand-700 disabled:opacity-60'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const otpInput = useRef(null)
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [otp, setOtp] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [cooldown, setCooldown] = useState(0)

  useEffect(() => {
    if (sent) otpInput.current?.focus()
  }, [sent])
  useEffect(() => {
    if (cooldown <= 0) return undefined
    const timer = setTimeout(() => setCooldown(value => value - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  async function sendCode(event) {
    event?.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const result = await authService.forgotPassword(email.trim())
      setNotice(result.message)
      setCooldown(result.resendCooldownSeconds)
      setSent(true)
      setOtp('')
    } catch (err) {
      setError(err.message || 'Chưa gửi được mã. Vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  async function resetPassword(event) {
    event.preventDefault()
    if (password !== confirmation) return
    setBusy(true)
    setError('')
    try {
      const result = await authService.resetPassword({ email: email.trim(), otp: otp.trim(), password })
      navigate('/login', { replace: true, state: { notice: result.message } })
    } catch (err) {
      setError(err.message || 'Chưa thể đặt lại mật khẩu. Vui lòng kiểm tra mã xác thực.')
    } finally {
      setBusy(false)
    }
  }

  return <AuthLayout title="Quên mật khẩu?" subtitle="Nhận mã xác thực qua email để đặt lại mật khẩu của bạn.">
    <form onSubmit={sent ? resetPassword : sendCode} className="space-y-5" aria-busy={busy}>
      <div>
        <label htmlFor="reset-email" className="text-sm font-semibold text-slate-700">Email đăng ký</label>
        <input id="reset-email" type="email" autoComplete="email" required maxLength={254}
          value={email} disabled={busy || sent} onChange={event => setEmail(event.target.value)} className={inputClass} />
      </div>
      <p role="status" className="text-sm leading-6 text-brand-800">{notice}</p>
      {sent && <>
        <div>
          <label htmlFor="reset-otp" className="text-sm font-semibold text-slate-700">Mã xác thực</label>
          <input ref={otpInput} id="reset-otp" autoComplete="one-time-code" inputMode="numeric" required maxLength={12}
            value={otp} disabled={busy} onChange={event => setOtp(event.target.value)} className={inputClass}
            aria-describedby="reset-otp-help" />
          <p id="reset-otp-help" className="mt-2 text-sm text-slate-600">Kiểm tra cả thư rác. Nếu mã hết hạn, hãy yêu cầu mã mới.</p>
        </div>
        <div>
          <label htmlFor="reset-password" className="text-sm font-semibold text-slate-700">Mật khẩu mới</label>
          <div className="relative">
          <input id="reset-password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" required minLength={8} maxLength={128}
            value={password} disabled={busy} onChange={event => setPassword(event.target.value)} className={`${inputClass} pr-14`}
            aria-describedby="reset-password-help" />
          <button type="button" disabled={busy} onClick={() => setShowPassword(value => !value)}
            aria-label={showPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'} aria-controls="reset-password"
            className="absolute inset-y-0 right-1 mt-2 flex min-w-11 items-center justify-center rounded-lg text-slate-600 hover:text-brand-700 focus-visible:outline-2 focus-visible:outline-brand-700">
            {showPassword ? <IconEyeOff className="h-5 w-5" /> : <IconEye className="h-5 w-5" />}
          </button>
          </div>
          <p id="reset-password-help" className="mt-2 text-sm text-slate-600">Dùng ít nhất 8 ký tự để bảo vệ tài khoản.</p>
        </div>
        <div>
          <label htmlFor="reset-confirm" className="text-sm font-semibold text-slate-700">Nhập lại mật khẩu mới</label>
          <div className="relative">
          <input id="reset-confirm" type={showConfirmation ? 'text' : 'password'} autoComplete="new-password" required maxLength={128}
            value={confirmation} disabled={busy} onChange={event => setConfirmation(event.target.value)} className={`${inputClass} pr-14`}
            aria-invalid={Boolean(confirmation && confirmation !== password)} aria-describedby="reset-confirm-error" />
          <button type="button" disabled={busy} onClick={() => setShowConfirmation(value => !value)}
            aria-label={showConfirmation ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'} aria-controls="reset-confirm"
            className="absolute inset-y-0 right-1 mt-2 flex min-w-11 items-center justify-center rounded-lg text-slate-600 hover:text-brand-700 focus-visible:outline-2 focus-visible:outline-brand-700">
            {showConfirmation ? <IconEyeOff className="h-5 w-5" /> : <IconEye className="h-5 w-5" />}
          </button>
          </div>
          <p id="reset-confirm-error" className="mt-2 text-sm text-red-700">{confirmation && confirmation !== password ? 'Mật khẩu nhập lại chưa khớp.' : ''}</p>
        </div>
      </>}
      {error && <FieldError>{error}</FieldError>}
      <button type="submit" disabled={busy || (sent && password !== confirmation)}
        className="min-h-11 w-full rounded-lg bg-brand-700 px-4 py-3 font-semibold text-white hover:bg-brand-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700 disabled:cursor-not-allowed disabled:opacity-60">
        {busy ? 'Đang xử lý…' : sent ? 'Đặt lại mật khẩu' : 'Gửi mã xác thực'}
      </button>
      {sent && <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <button type="button" disabled={busy || cooldown > 0} onClick={() => sendCode()}
          className="min-h-11 font-semibold text-brand-700 hover:underline disabled:text-slate-500 focus-visible:outline-2">
          {cooldown > 0 ? `Gửi lại mã sau ${cooldown}s` : 'Gửi lại mã'}
        </button>
        <button type="button" disabled={busy} onClick={() => { setSent(false); setError(''); setNotice(''); setOtp(''); setPassword(''); setConfirmation(''); setShowPassword(false); setShowConfirmation(false) }}
          className="min-h-11 font-semibold text-brand-700 hover:underline focus-visible:outline-2">Đổi email</button>
      </div>}
      <Link to="/login" className="block py-2 text-center text-sm font-semibold text-brand-700 hover:underline">Quay lại đăng nhập</Link>
    </form>
  </AuthLayout>
}
