import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import useRecaptcha from '../hooks/useRecaptcha'
import { PASSWORD_MIN_LENGTH, isBlank, isValidEmail } from '../utils/validation'
import { setPendingActivation } from '../utils/pendingActivation'
import AuthLayout, { FieldError, INPUT_CLASS, LABEL_CLASS } from '../components/AuthLayout'
import {
  IconArrowRight,
  IconBuilding,
  IconEye,
  IconEyeOff,
  IconLock,
  IconMail,
  IconShield,
  IconUser,
} from '../components/icons/Icons'

const ROLE_OPTIONS = [
  {
    value: 'RENTER',
    title: 'Tôi muốn thuê xe',
    description: 'Tìm và đặt xe điện, xe xăng từ các chủ xe trên EcoMotion.',
    available: true,
  },
  {
    value: 'PROVIDER',
    title: 'Tôi là chủ xe / Garage',
    description: 'Đăng xe cho thuê và quản lý đội xe, tài xế của bạn.',
    available: false,
  },
]

const EMPTY_FORM = {
  email: '',
  password: '',
  confirmPassword: '',
  agreeTerms: true,
}

export default function RegisterPage() {
  const { register, sendRegisterOtp, verifyRegisterOtp } = useAuth()
  const navigate = useNavigate()
  const recaptcha = useRecaptcha()

  const [role, setRole] = useState('RENTER')
  const [form, setForm] = useState(EMPTY_FORM)
  const [showPassword, setShowPassword] = useState(false)

  const [otp, setOtp] = useState('')
  const [otpSent, setOtpSent] = useState(false)
  const [cooldown, setCooldown] = useState(0)

  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const timerRef = useRef(null)
  useEffect(() => () => clearInterval(timerRef.current), [])

  const updateField = (key, value) => setForm((prev) => ({ ...prev, [key]: value }))

  function validateDetails() {
    if (isBlank(form.email)) return 'Vui lòng nhập email.'
    if (!isValidEmail(form.email)) return 'Email không hợp lệ.'
    if (isBlank(form.password)) return 'Vui lòng nhập mật khẩu.'
    if (form.password.length < PASSWORD_MIN_LENGTH) {
      return `Mật khẩu phải có ít nhất ${PASSWORD_MIN_LENGTH} ký tự.`
    }
    if (form.password !== form.confirmPassword) return 'Mật khẩu xác nhận không khớp.'
    if (!form.agreeTerms) return 'Bạn cần đồng ý với điều khoản để tiếp tục.'
    return ''
  }

  function startCooldown(seconds) {
    clearInterval(timerRef.current)
    setCooldown(seconds)
    timerRef.current = setInterval(() => {
      setCooldown((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current)
          return 0
        }
        return prev - 1
      })
    }, 1000)
  }

  async function handleSendOtp() {
    setError('')
    const validationError = validateDetails()
    if (validationError) {
      setError(validationError)
      return
    }

    setSubmitting(true)
    try {
      const data = await sendRegisterOtp(form.email.trim())
      setOtpSent(true)
      startCooldown(data.resendCooldownSeconds || 60)
    } catch (err) {
      setError(err.message || 'Không gửi được mã OTP. Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleVerifyAndRegister() {
    setError('')
    if (isBlank(otp)) {
      setError('Vui lòng nhập mã OTP.')
      return
    }

    setSubmitting(true)
    try {
      const verified = await verifyRegisterOtp(form.email.trim(), otp.trim())
      const captchaToken = await recaptcha.execute('register')
      const data = await register({
        registrationToken: verified.registrationToken,
        password: form.password,
        role,
        agreeTerms: form.agreeTerms,
        captchaToken,
      })

      // Account is PENDING: no session yet. Hand the activation token to the activation page in
      // memory only — it is never written to web storage.
      setPendingActivation({ token: data.activationToken, email: form.email.trim() })
      navigate('/activate', { replace: true })
    } catch (err) {
      setError(err.message || 'Không tạo được tài khoản. Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Tạo tài khoản EcoMotion"
      subtitle="Chỉ cần email và mật khẩu — bạn sẽ hoàn tất hồ sơ ở bước kế tiếp."
    >
      <div className="flex flex-col gap-5">
        {!otpSent && (
          <>
            <div>
              <span className={LABEL_CLASS}>Bạn tham gia với vai trò</span>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {ROLE_OPTIONS.map((option) => {
                  const active = role === option.value
                  const disabled = !option.available
                  return (
                    <button
                      key={option.value}
                      type="button"
                      disabled={disabled}
                      onClick={() => !disabled && setRole(option.value)}
                      className={`relative rounded-xl border p-3 text-left transition-all ${
                        disabled
                          ? 'cursor-not-allowed border-slate-200 bg-slate-50 opacity-70'
                          : active
                            ? 'border-brand-600 bg-brand-50 ring-2 ring-brand-500/20'
                            : 'border-slate-200 bg-white hover:border-slate-300'
                      }`}
                    >
                      {disabled && (
                        <span className="absolute right-2 top-2 rounded-full bg-slate-200 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
                          Sắp ra mắt
                        </span>
                      )}
                      <span className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                        {option.value === 'RENTER' ? (
                          <IconUser className="h-4 w-4 text-brand-600" />
                        ) : (
                          <IconBuilding className="h-4 w-4 text-brand-600" />
                        )}
                        {option.title}
                      </span>
                      <span className="mt-1 block text-[11px] leading-snug text-slate-500">
                        {option.description}
                      </span>
                    </button>
                  )
                })}
              </div>
            </div>

            <div>
              <label className={LABEL_CLASS} htmlFor="register-email">
                Email
              </label>
              <div className="relative">
                <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <IconMail className="h-5 w-5" />
                </span>
                <input
                  id="register-email"
                  type="email"
                  autoComplete="off"
                  className={INPUT_CLASS}
                  placeholder="ban@example.com"
                  value={form.email}
                  onChange={(e) => updateField('email', e.target.value)}
                  disabled={submitting}
                />
              </div>
              <p className="mt-1.5 text-[11px] text-slate-500">
                Mã xác thực sẽ được gửi tới email này để đảm bảo hộp thư tồn tại.
              </p>
            </div>

            <div>
              <label className={LABEL_CLASS} htmlFor="register-password">
                Mật khẩu
              </label>
              <div className="relative">
                <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <IconLock className="h-5 w-5" />
                </span>
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  className={`${INPUT_CLASS} pr-11`}
                  placeholder={`Tối thiểu ${PASSWORD_MIN_LENGTH} ký tự`}
                  value={form.password}
                  onChange={(e) => updateField('password', e.target.value)}
                  disabled={submitting}
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-slate-600"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  tabIndex={-1}
                >
                  {showPassword ? <IconEyeOff className="h-5 w-5" /> : <IconEye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div>
              <label className={LABEL_CLASS} htmlFor="register-confirm">
                Xác nhận mật khẩu
              </label>
              <div className="relative">
                <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <IconLock className="h-5 w-5" />
                </span>
                <input
                  id="register-confirm"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  className={INPUT_CLASS}
                  placeholder="Nhập lại mật khẩu"
                  value={form.confirmPassword}
                  onChange={(e) => updateField('confirmPassword', e.target.value)}
                  disabled={submitting}
                />
              </div>
            </div>

            <label className="flex cursor-pointer items-start gap-2.5">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 accent-brand-600 focus:ring-brand-500"
                checked={form.agreeTerms}
                onChange={(e) => updateField('agreeTerms', e.target.checked)}
                disabled={submitting}
              />
              <span className="select-none text-xs leading-relaxed text-slate-600">
                Tôi đồng ý với <span className="font-semibold text-brand-600">Điều khoản dịch vụ</span> và{' '}
                <span className="font-semibold text-brand-600">Chính sách bảo mật</span> của EcoMotion.
              </span>
            </label>

            <button
              type="button"
              onClick={handleSendOtp}
              disabled={submitting}
              className="group flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3.5 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-65"
            >
              <IconShield className="h-4 w-4" />
              <span>{submitting ? 'Đang gửi mã...' : 'Nhận mã xác thực qua email'}</span>
            </button>
          </>
        )}

        {otpSent && (
          <>
            <div className="flex items-center gap-2 rounded-lg border border-brand-200 bg-brand-50 px-3.5 py-2.5 text-[13px] text-brand-800">
              <IconShield className="h-4 w-4 shrink-0" />
              <span>
                Mã xác thực đã được gửi tới <span className="font-semibold">{form.email}</span>. Vui lòng kiểm tra
                hộp thư của bạn.
              </span>
            </div>

            <div>
              <label className={LABEL_CLASS} htmlFor="register-otp">
                Mã xác thực (6 chữ số)
              </label>
              <input
                id="register-otp"
                inputMode="numeric"
                maxLength={6}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-xl tracking-[0.5em] text-slate-900 placeholder-slate-300 focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:opacity-60"
                placeholder="••••••"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                disabled={submitting}
              />
            </div>

            <button
              type="button"
              onClick={handleVerifyAndRegister}
              disabled={submitting}
              className="group flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3.5 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-65"
            >
              <span>{submitting ? 'Đang tạo tài khoản...' : 'Xác thực & tạo tài khoản'}</span>
              {!submitting && <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />}
            </button>

            <div className="flex items-center justify-between text-xs">
              <button
                type="button"
                onClick={handleSendOtp}
                disabled={cooldown > 0 || submitting}
                className="font-semibold text-brand-600 hover:text-brand-700 hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline"
              >
                {cooldown > 0 ? `Gửi lại mã sau ${cooldown}s` : 'Gửi lại mã'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setOtpSent(false)
                  setOtp('')
                  setCooldown(0)
                  clearInterval(timerRef.current)
                }}
                className="font-medium text-slate-500 hover:text-slate-700"
              >
                ← Sửa thông tin
              </button>
            </div>
          </>
        )}

        <FieldError>{error}</FieldError>

        <p className="text-center text-[11px] leading-relaxed text-slate-400">
          Trang này được bảo vệ bởi reCAPTCHA. Áp dụng{' '}
          <a
            href="https://policies.google.com/privacy"
            target="_blank"
            rel="noreferrer"
            className="underline hover:text-slate-600"
          >
            Chính sách bảo mật
          </a>{' '}
          và{' '}
          <a
            href="https://policies.google.com/terms"
            target="_blank"
            rel="noreferrer"
            className="underline hover:text-slate-600"
          >
            Điều khoản
          </a>{' '}
          của Google.
        </p>

        <p className="text-center text-xs text-slate-500">
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline">
            Đăng nhập
          </Link>
        </p>
      </div>
    </AuthLayout>
  )
}
