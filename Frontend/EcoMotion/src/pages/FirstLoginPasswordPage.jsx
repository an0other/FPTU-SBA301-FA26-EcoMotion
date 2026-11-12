import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import AuthLayout, { FieldError, INPUT_CLASS, LABEL_CLASS } from '../components/AuthLayout'
import { authService } from '../services/authService'
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  validateFirstLoginForm,
} from '../utils/validation'
import {
  IconArrowRight,
  IconCheckCircle,
  IconEye,
  IconEyeOff,
  IconLock,
  IconMail,
  IconShield,
} from '../components/icons/Icons'

const INVALID_INPUT_RING = 'border-red-400 bg-red-50/40 focus:border-red-500 focus:ring-red-500/20'

function RuleBadge({ met, invalid, label }) {
  const toneClass = invalid
    ? 'text-red-600 font-medium'
    : met
      ? 'text-emerald-700 font-medium'
      : 'text-slate-500'

  return (
    <li className={`flex items-center gap-1.5 text-xs ${toneClass}`}>
      <IconCheckCircle
        className={`h-3.5 w-3.5 shrink-0 ${
          invalid ? 'text-red-500' : met ? 'text-emerald-600' : 'text-slate-300'
        }`}
      />
      <span>{label}</span>
    </li>
  )
}

export default function FirstLoginPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  const initialEmail = searchParams.get('email') || location.state?.email || ''
  const [email, setEmail] = useState(initialEmail)
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [showTemporaryPassword, setShowTemporaryPassword] = useState(false)
  const [newPassword, setNewPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const [touched, setTouched] = useState({
    email: false,
    temporaryPassword: false,
    newPassword: false,
    confirmPassword: false,
  })
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const initialNotice =
    location.state?.notice
    || 'Nhập mật khẩu tạm thời 8 ký tự trong email tài xế và đặt mật khẩu mới để bắt đầu đăng nhập.'

  const validation = validateFirstLoginForm({
    email,
    temporaryPassword,
    newPassword,
    confirmPassword,
  })
  const { fieldErrors, rules } = validation

  const visibleErrorFor = (field) =>
    (submitted || touched[field]) ? fieldErrors[field] : ''

  const markTouched = (field) => {
    setTouched((prev) => (prev[field] ? prev : { ...prev, [field]: true }))
  }

  const clearApiError = () => {
    if (error) setError('')
  }

  const emailError = visibleErrorFor('email')
  const tempPasswordError = visibleErrorFor('temporaryPassword')
  const newPasswordError = visibleErrorFor('newPassword')
  const confirmPasswordError =
    confirmPassword.length > 0 && confirmPassword !== newPassword
      ? 'Xác nhận mật khẩu mới không khớp.'
      : visibleErrorFor('confirmPassword')

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitted(true)
    setError('')

    if (!validation.valid) {
      setError(validation.summaryError)
      return
    }

    const cleanEmail = email.trim()
    const cleanTempPass = temporaryPassword.trim()

    setBusy(true)
    try {
      const result = await authService.changeTemporaryPassword({
        email: cleanEmail,
        temporaryPassword: cleanTempPass,
        newPassword,
      })
      navigate('/login', {
        replace: true,
        state: {
          notice: result?.message || 'Đổi mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.',
        },
      })
    } catch (err) {
      const raw = err?.original?.response?.data?.message || ''
      if (err?.status === 403 && /pending/i.test(raw)) {
        navigate('/activate', {
          state: {
            email: cleanEmail,
            notice: 'Tài khoản chưa được kích hoạt. Vui lòng hoàn tất xác thực để tiếp tục.',
          },
        })
        return
      }
      if (err?.status === 401) {
        setError('Email hoặc mật khẩu tạm thời không đúng (hoặc tài khoản đã hoàn tất đổi mật khẩu lần đầu trước đó).')
        return
      }
      setError(err?.message || 'Không thể đổi mật khẩu. Vui lòng kiểm tra lại thông tin và thử lại.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout
      title="Đổi mật khẩu lần đầu"
      subtitle="Vì lý do bảo mật, bạn phải đổi mật khẩu tạm thời trước khi đăng nhập hệ thống."
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate aria-busy={busy}>
        <div
          role="status"
          className="flex items-start gap-2.5 rounded-lg border border-sky-200 bg-sky-50 px-3.5 py-2.5 text-[13px] font-medium text-sky-900"
        >
          <IconShield className="mt-0.5 h-4 w-4 shrink-0 text-sky-600" />
          <span>{initialNotice}</span>
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor="first-login-email">
            Email đăng nhập
          </label>
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <IconMail className="h-5 w-5" />
            </span>
            <input
              id="first-login-email"
              type="email"
              autoComplete="email"
              maxLength={254}
              value={email}
              disabled={busy}
              aria-invalid={Boolean(emailError)}
              onChange={(e) => {
                clearApiError()
                setEmail(e.target.value)
              }}
              onBlur={() => markTouched('email')}
              className={`${INPUT_CLASS} ${emailError ? INVALID_INPUT_RING : ''}`}
              placeholder="driver@ecomotion.vn"
            />
          </div>
          {emailError && (
            <p role="alert" className="mt-1.5 text-xs font-medium text-red-600">
              {emailError}
            </p>
          )}
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor="temp-password">
            Mật khẩu tạm thời (nhận từ email)
          </label>
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <IconLock className="h-5 w-5" />
            </span>
            <input
              id="temp-password"
              type={showTemporaryPassword ? 'text' : 'password'}
              autoComplete="current-password"
              maxLength={PASSWORD_MAX_LENGTH}
              value={temporaryPassword}
              disabled={busy}
              aria-invalid={Boolean(tempPasswordError)}
              onChange={(e) => {
                clearApiError()
                setTemporaryPassword(e.target.value)
              }}
              onBlur={() => markTouched('temporaryPassword')}
              className={`${INPUT_CLASS} pr-11 ${tempPasswordError ? INVALID_INPUT_RING : ''}`}
              placeholder="Nhập mã 8 ký tự từ email"
            />
            <button
              type="button"
              disabled={busy}
              onClick={() => setShowTemporaryPassword((v) => !v)}
              aria-label={showTemporaryPassword ? 'Ẩn mật khẩu tạm' : 'Hiện mật khẩu tạm'}
              className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-slate-600"
              tabIndex={-1}
            >
              {showTemporaryPassword ? <IconEyeOff className="h-5 w-5" /> : <IconEye className="h-5 w-5" />}
            </button>
          </div>
          {tempPasswordError ? (
            <p role="alert" className="mt-1.5 text-xs font-medium text-red-600">
              {tempPasswordError}
            </p>
          ) : (
            <p className="mt-1.5 text-[11px] text-slate-500">
              Kiểm tra hộp thư đến hoặc thư rác (Spam) để lấy mã 8 ký tự phân biệt chữ hoa/thường.
            </p>
          )}
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor="first-new-password">
            Mật khẩu mới
          </label>
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <IconLock className="h-5 w-5" />
            </span>
            <input
              id="first-new-password"
              type={showNewPassword ? 'text' : 'password'}
              autoComplete="new-password"
              maxLength={PASSWORD_MAX_LENGTH}
              value={newPassword}
              disabled={busy}
              aria-invalid={Boolean(newPasswordError)}
              onChange={(e) => {
                clearApiError()
                setNewPassword(e.target.value)
              }}
              onBlur={() => markTouched('newPassword')}
              className={`${INPUT_CLASS} pr-11 ${newPasswordError ? INVALID_INPUT_RING : ''}`}
              placeholder={`Tối thiểu ${PASSWORD_MIN_LENGTH} ký tự`}
            />
            <button
              type="button"
              disabled={busy}
              onClick={() => setShowNewPassword((v) => !v)}
              aria-label={showNewPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'}
              className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-slate-600"
              tabIndex={-1}
            >
              {showNewPassword ? <IconEyeOff className="h-5 w-5" /> : <IconEye className="h-5 w-5" />}
            </button>
          </div>
          {newPasswordError && (
            <p role="alert" className="mt-1.5 text-xs font-medium text-red-600">
              {newPasswordError}
            </p>
          )}
          <ul className="mt-2 space-y-1 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
            <RuleBadge
              met={rules.minLength && rules.maxLength}
              invalid={newPassword.length > 0 && !rules.maxLength}
              label={`Ít nhất ${PASSWORD_MIN_LENGTH} ký tự`}
            />
            <RuleBadge
              met={rules.notBlank}
              invalid={newPassword.length > 0 && !rules.notBlank}
              label="Không để trống hoặc chỉ gồm dấu cách"
            />
            <RuleBadge
              met={Boolean(temporaryPassword.trim()) && rules.differentFromTemp}
              invalid={rules.sameAsTemp}
              label={rules.sameAsTemp ? 'Không được trùng với mật khẩu tạm thời' : 'Khác mật khẩu tạm thời'}
            />
          </ul>
        </div>

        <div>
          <label className={LABEL_CLASS} htmlFor="first-confirm-password">
            Xác nhận mật khẩu mới
          </label>
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
              <IconLock className="h-5 w-5" />
            </span>
            <input
              id="first-confirm-password"
              type={showConfirmPassword ? 'text' : 'password'}
              autoComplete="new-password"
              maxLength={PASSWORD_MAX_LENGTH}
              value={confirmPassword}
              disabled={busy}
              aria-invalid={Boolean(confirmPasswordError)}
              onChange={(e) => {
                clearApiError()
                setConfirmPassword(e.target.value)
              }}
              onBlur={() => markTouched('confirmPassword')}
              className={`${INPUT_CLASS} pr-11 ${confirmPasswordError ? INVALID_INPUT_RING : ''}`}
              placeholder="Nhập lại mật khẩu mới"
            />
            <button
              type="button"
              disabled={busy}
              onClick={() => setShowConfirmPassword((v) => !v)}
              aria-label={showConfirmPassword ? 'Ẩn xác nhận mật khẩu' : 'Hiện xác nhận mật khẩu'}
              className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-slate-600"
              tabIndex={-1}
            >
              {showConfirmPassword ? <IconEyeOff className="h-5 w-5" /> : <IconEye className="h-5 w-5" />}
            </button>
          </div>
          {confirmPasswordError && (
            <p role="alert" className="mt-1.5 text-xs font-medium text-red-600">
              {confirmPasswordError}
            </p>
          )}
          {!confirmPasswordError && rules.confirmMatches && (
            <p className="mt-1.5 flex items-center gap-1.5 text-xs font-medium text-emerald-700">
              <IconCheckCircle className="h-3.5 w-3.5 shrink-0 text-emerald-600" />
              <span>Mật khẩu xác nhận khớp</span>
            </p>
          )}
        </div>

        <FieldError>{error}</FieldError>

        <button
          type="submit"
          disabled={busy}
          className="group mt-1 flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3.5 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-65"
        >
          <span>{busy ? 'Đang cập nhật...' : 'Xác nhận đổi mật khẩu'}</span>
          {!busy && <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />}
        </button>

        <div className="pt-1 text-center">
          <Link to="/login" className="text-xs font-semibold text-brand-600 hover:text-brand-700 hover:underline">
            Quay lại trang đăng nhập
          </Link>
        </div>
      </form>
    </AuthLayout>
  )
}
