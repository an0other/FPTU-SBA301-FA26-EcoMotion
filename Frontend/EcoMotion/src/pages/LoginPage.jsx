import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { isBlank, isValidEmail } from '../utils/validation'
import { homeRouteForRole } from '../utils/roleHome'
import {
  IconArrowRight,
  IconEye,
  IconEyeOff,
  IconLock,
  IconMail,
  IconSparkles,
} from '../components/icons/Icons'

const INPUT_CLASS =
  'w-full rounded-lg border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:opacity-60'

const LABEL_CLASS =
  'mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-slate-700'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '', rememberMe: false })
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

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
      const user = await login(form.email.trim(), form.password, form.rememberMe)
      const from = location.state?.from?.pathname
      navigate(from || homeRouteForRole(user.role), { replace: true })
    } catch (err) {
      setError(err.message || 'Đăng nhập thất bại. Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <div className="flex w-full flex-1">
        <aside className="relative hidden flex-col justify-between overflow-hidden bg-ink p-12 text-white lg:flex lg:w-1/2">
          <div
            className="absolute inset-0 z-0 bg-cover bg-center transition-transform duration-700 hover:scale-105"
            style={{
              backgroundImage:
                "url('https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1600&q=80')",
            }}
          >
            <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/75 to-slate-900/60 backdrop-blur-[1px]" />
          </div>

          <div className="relative z-10 flex items-center">
            <Link
              to="/"
              className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/10 px-3 py-1.5 text-xs font-medium text-slate-200 backdrop-blur-md transition-all hover:bg-white/20"
            >
              ← Về trang chủ
            </Link>
          </div>

          <div className="relative z-10 my-auto max-w-lg py-10">
            <span className="mb-5 block text-4xl font-extrabold leading-tight tracking-tight text-white">
              EcoMotion
            </span>
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-brand-500/30 bg-brand-500/20 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-brand-300">
              <IconSparkles className="h-3.5 w-3.5 text-brand-400" />
              Chuẩn mực thuê xe kỷ nguyên mới
            </div>
            <h1 className="text-4xl font-bold leading-tight tracking-tight text-white">
              Trải nghiệm thuê xe <span className="font-extrabold text-brand-400">Minh Bạch, An Toàn</span> &amp;
              Tiện Lợi Nhất.
            </h1>
          </div>

          <div />
        </aside>

        <main className="flex w-full items-center justify-center overflow-y-auto bg-white px-4 py-10 sm:px-8 lg:w-1/2 lg:px-12 lg:py-16">
          <div className="mx-auto w-full max-w-md">
            <div className="mb-8 flex items-center justify-between border-b border-slate-100 pb-4 lg:hidden">
              <span className="text-xl font-extrabold tracking-tight text-slate-900">
                EcoMotion <span className="text-[11px] font-semibold text-brand-600">VN</span>
              </span>
              <Link to="/" className="text-xs font-medium text-slate-500 hover:text-brand-600">
                Trang chủ →
              </Link>
            </div>

            <div className="mb-6">
              <h2 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-[28px]">
                Chào mừng bạn quay lại!
              </h2>
              <p className="mt-1.5 text-sm text-slate-500">
                Nhập thông tin xác thực để quản lý chuyến đi và đội xe của bạn.
              </p>
            </div>

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
                <span className="select-none text-xs text-slate-600">
                  Ghi nhớ đăng nhập trên thiết bị này
                </span>
              </label>

              <button
                type="submit"
                disabled={submitting}
                className="group mt-2 flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3.5 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-65"
              >
                <span>{submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}</span>
                {!submitting && (
                  <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                )}
              </button>

              {error && (
                <div
                  role="alert"
                  className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-[13px] font-medium text-on-error-container"
                >
                  {error}
                </div>
              )}
            </form>
          </div>
        </main>
      </div>
    </div>
  )
}
