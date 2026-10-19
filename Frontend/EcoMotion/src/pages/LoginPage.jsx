import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import useRecaptcha from '../hooks/useRecaptcha'
import { isBlank, isValidEmail } from '../utils/validation'
import { homeRouteForRole } from '../utils/roleHome'
import AuthLayout, { FieldError, INPUT_CLASS, LABEL_CLASS } from '../components/AuthLayout'
import { IconArrowRight, IconEye, IconEyeOff, IconLock, IconMail } from '../components/icons/Icons'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const recaptcha = useRecaptcha()

  const [form, setForm] = useState({ email: '', password: '', rememberMe: false })
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const notice = location.state?.notice

  const updateField = (key, value) => setForm((prev) => ({ ...prev, [key]: value }))

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')

    if (isBlank(form.email)) {
      setError('Vui lòng nhập email.')
      return
    }
    if (!isValidEmail(form.email)) {
      setError('Email không hợp lệ.')
      return
    }
    if (isBlank(form.password)) {
      setError('Vui lòng nhập mật khẩu.')
      return
    }

    setSubmitting(true)
    try {
      const captchaToken = await recaptcha.execute('login')
      const user = await login(form.email.trim(), form.password, form.rememberMe, captchaToken)
      const from = location.state?.from
      const redirectTo = from
        ? `${from.pathname ?? ''}${from.search ?? ''}${from.hash ?? ''}`
        : ''
      navigate(redirectTo || homeRouteForRole(user.role), { replace: true })
    } catch (err) {
      const raw = err?.original?.response?.data?.message || ''
      if (err?.status === 403 && /pending/i.test(raw)) {
        navigate('/activate', {
          state: {
            email: form.email.trim(),
            notice: 'Tài khoản chưa được kích hoạt. Vui lòng hoàn tất xác thực để tiếp tục.',
          },
        })
        return
      }
      setError(err.message || 'Đăng nhập thất bại. Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Chào mừng bạn quay lại!"
      subtitle="Nhập thông tin xác thực để quản lý chuyến đi và đội xe của bạn."
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <div>
          <label className={LABEL_CLASS} htmlFor="login-email">
            Email
          </label>
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <IconMail className="h-5 w-5" />
            </span>
            <input
              id="login-email"
              type="email"
              autoComplete="off"
              className={INPUT_CLASS}
              placeholder="Nhập email"
              value={form.email}
              onChange={(e) => updateField('email', e.target.value)}
              disabled={submitting}
            />
          </div>
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor="login-password">
            Mật khẩu
          </label>
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <IconLock className="h-5 w-5" />
            </span>
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              className={`${INPUT_CLASS} pr-11`}
              placeholder="Nhập mật khẩu"
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
          <div className="mt-1.5 flex justify-end">
            <a href="#forgot" className="text-xs font-semibold text-brand-600 hover:text-brand-700 hover:underline">
              Quên mật khẩu?
            </a>
          </div>
        </div>

        <label className="flex cursor-pointer items-center gap-2.5 pt-1">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300 text-brand-600 accent-brand-600 focus:ring-brand-500"
            checked={form.rememberMe}
            onChange={(e) => updateField('rememberMe', e.target.checked)}
            disabled={submitting}
          />
          <span className="select-none text-xs text-slate-600">Ghi nhớ đăng nhập trên thiết bị này</span>
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="group mt-2 flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3.5 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-65"
        >
          <span>{submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}</span>
          {!submitting && <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />}
        </button>

        {notice && (
          <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-[13px] font-medium text-emerald-800">
            {notice}
          </div>
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
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline">
            Đăng ký ngay
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
