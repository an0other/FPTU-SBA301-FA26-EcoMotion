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
import './LoginPage.css'

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
    <div className="login-page">
      <div className="login-page__body">
        {/* LEFT: hero brand panel (desktop only) */}
        <aside className="login-hero">
          <div
            className="login-hero__bg"
            style={{
              backgroundImage:
                "url('https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1600&q=80')",
            }}
          >
            <div className="login-hero__overlay" />
          </div>

          <div className="login-hero__top">
            <Link to="/" className="login-hero__home">
              ← Về trang chủ
            </Link>
          </div>

          <div className="login-hero__mid">
            <span className="login-hero__name">EcoMotion</span>
            <div className="login-hero__pill">
              <IconSparkles />
              Chuẩn mực thuê xe kỷ nguyên mới
            </div>
            <h1 className="login-hero__headline">
              Trải nghiệm thuê xe <span>Minh Bạch, An Toàn</span> &amp; Tiện Lợi Nhất.
            </h1>
          </div>

          <div />
        </aside>

        {/* RIGHT: authentication form */}
        <main className="login-form-col">
          <div className="login-card">
            <div className="login-card__mobile-header">
              <span className="login-card__mobile-name">
                EcoMotion <span>VN</span>
              </span>
              <Link to="/" className="login-card__mobile-home">
                Trang chủ →
              </Link>
            </div>

            <div className="login-card__title">
              <h2>Chào mừng bạn quay lại!</h2>
              <p>Nhập thông tin xác thực để quản lý chuyến đi và đội xe của bạn.</p>
            </div>

            <form className="login-form" onSubmit={handleSubmit} noValidate>
              <div className="form-field">
                <label className="form-field__label" htmlFor="login-email">
                  Email
                </label>
                <div className="input-wrap">
                  <span className="input-wrap__icon">
                    <IconMail />
                  </span>
                  <input
                    id="login-email"
                    type="email"
                    autoComplete="off"
                    className="form-input"
                    placeholder="Nhập email"
                    value={form.email}
                    onChange={(e) => updateField('email', e.target.value)}
                    disabled={submitting}
                  />
                </div>
              </div>

              <div className="form-field">
                <label className="form-field__label" htmlFor="login-password">
                  Mật khẩu
                </label>
                <div className="input-wrap">
                  <span className="input-wrap__icon">
                    <IconLock />
                  </span>
                  <input
                    id="login-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    className="form-input"
                    placeholder="Nhập mật khẩu"
                    value={form.password}
                    onChange={(e) => updateField('password', e.target.value)}
                    disabled={submitting}
                  />
                  <button
                    type="button"
                    className="input-wrap__toggle"
                    onClick={() => setShowPassword((prev) => !prev)}
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    tabIndex={-1}
                  >
                    {showPassword ? <IconEyeOff /> : <IconEye />}
                  </button>
                </div>
                <div className="form-field__below">
                  <a className="form-field__link" href="#forgot">
                    Quên mật khẩu?
                  </a>
                </div>
              </div>

              <label className="form-remember">
                <input
                  type="checkbox"
                  checked={form.rememberMe}
                  onChange={(e) => updateField('rememberMe', e.target.checked)}
                  disabled={submitting}
                />
                <span>Ghi nhớ đăng nhập trên thiết bị này</span>
              </label>

              <button type="submit" className="login-submit" disabled={submitting}>
                <span>{submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}</span>
                {!submitting && <IconArrowRight />}
              </button>

              {error && (
                <div className="form-error" role="alert">
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
