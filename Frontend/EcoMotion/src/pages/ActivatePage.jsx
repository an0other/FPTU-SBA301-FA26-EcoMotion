import { Fragment, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { isBlank, isValidEmail, isValidVnPhone } from '../utils/validation'
import {
  clearPendingActivation,
  getPendingActivation,
  setPendingActivation,
} from '../utils/pendingActivation'
import { FieldError, INPUT_CLASS, LABEL_CLASS } from '../components/AuthLayout'
import {
  IconArrowRight,
  IconCheckCircle,
  IconIdCard,
  IconMail,
  IconPhone,
  IconShield,
  IconUser,
} from '../components/icons/Icons'

const PRIMARY_BUTTON =
  'group flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-65'

const OUTLINE_BUTTON =
  'group flex w-full items-center justify-center gap-2 rounded-lg border border-brand-600 bg-white px-4 py-3 text-sm font-semibold text-brand-700 transition-all hover:bg-brand-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400'

const BACK_BUTTON =
  'group flex flex-1 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-600 transition-all hover:border-slate-300 hover:bg-slate-100'

const NEXT_BUTTON =
  'group flex flex-1 items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-md shadow-brand-600/25 transition-all hover:bg-brand-700 active:bg-brand-800'

const PLAIN_INPUT =
  'w-full rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-3 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:cursor-not-allowed disabled:opacity-60'

const STEPS = [
  { n: 1, label: 'Số điện thoại' },
  { n: 2, label: 'CCCD' },
  { n: 3, label: 'Khuôn mặt' },
]

const EMPTY_FORM = { fullName: '', cccdNo: '', dateOfBirth: '', gender: '', address: '' }
const GENDERS = ['Nam', 'Nữ', 'Khác']

function FilePicker({ id, label, hint, file, onChange, disabled, icon: Icon = IconIdCard }) {
  return (
    <div>
      <label className={LABEL_CLASS} htmlFor={id}>
        {label}
      </label>
      <label
        htmlFor={id}
        className={`flex cursor-pointer items-center gap-3 rounded-lg border border-dashed px-3.5 py-3 text-sm transition-colors ${
          file
            ? 'border-brand-400 bg-brand-50 text-brand-800'
            : 'border-slate-300 bg-slate-50 text-slate-500 hover:border-brand-400 hover:bg-white'
        }`}
      >
        <Icon className="h-5 w-5 shrink-0 text-brand-600" />
        <span className="truncate">{file ? file.name : hint}</span>
      </label>
      <input
        id={id}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => onChange(e.target.files?.[0] || null)}
        disabled={disabled}
      />
    </div>
  )
}

function Stepper({ current, done, canGo, onSelect }) {
  return (
    <div className="flex items-center gap-2">
      {STEPS.map((s, i) => {
        const isDone = done.includes(s.n)
        const isCurrent = current === s.n
        const reachable = canGo(s.n)
        return (
          <Fragment key={s.n}>
            {i > 0 && (
              <span
                aria-hidden="true"
                className={`h-0.5 flex-1 rounded-full ${isDone || isCurrent ? 'bg-brand-500' : 'bg-slate-200'}`}
              />
            )}
            <button
              type="button"
              onClick={() => reachable && onSelect(s.n)}
              disabled={!reachable}
              aria-current={isCurrent ? 'step' : undefined}
              className={`flex shrink-0 items-center gap-2 rounded-full border px-2.5 py-1.5 text-[12px] font-semibold transition-colors ${
                isDone
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                  : isCurrent
                    ? 'border-brand-300 bg-brand-50 text-brand-700'
                    : 'border-slate-200 bg-white text-slate-400'
              } ${reachable ? 'cursor-pointer hover:border-brand-400' : 'cursor-not-allowed'}`}
            >
              <span
                className={`flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold text-white ${
                  isDone ? 'bg-emerald-500' : isCurrent ? 'bg-brand-600' : 'bg-slate-300'
                }`}
              >
                {isDone ? <IconCheckCircle className="h-3.5 w-3.5" /> : s.n}
              </span>
              <span className="hidden sm:inline">{s.label}</span>
            </button>
          </Fragment>
        )
      })}
    </div>
  )
}

function StepCard({ step, title, hint, children }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-start gap-2.5">
        <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-600 text-xs font-bold text-white">
          {step}
        </span>
        <div>
          <h3 className="text-sm font-bold text-slate-900">{title}</h3>
          {hint && <p className="mt-0.5 text-[12px] leading-snug text-slate-500">{hint}</p>}
        </div>
      </div>
      <div className="flex flex-col gap-3">{children}</div>
    </section>
  )
}

function SuccessNote({ children }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-[13px] font-medium text-emerald-800">
      <IconShield className="h-4 w-4 shrink-0" />
      {children}
    </div>
  )
}

function Shell({ title, subtitle, children }) {
  const navigate = useNavigate()
  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex w-full max-w-3xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <button
            type="button"
            onClick={() => navigate('/me')}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-[13px] font-semibold text-slate-700 transition-colors hover:border-slate-300 hover:bg-slate-100"
          >
            ← Trang của tôi
          </button>
          <span className="flex items-center gap-2 text-base font-extrabold tracking-tight text-slate-900">
            <IconShield className="h-4 w-4 text-brand-600" />
            EcoMotion <span className="text-[11px] font-semibold text-brand-600">VN</span>
          </span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        </div>
        {children}
      </main>
    </div>
  )
}

export default function ActivatePage() {
  const {
    activateSendPhoneOtp,
    activateVerifyPhoneOtp,
    activateStatus,
    activateScanCccd,
    activateFaceMatch,
    activateResumeOtp,
    activateResume,
  } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [session, setSession] = useState(() => getPendingActivation())

  const [loadingStatus, setLoadingStatus] = useState(true)
  const [step, setStep] = useState(1)
  const [activated, setActivated] = useState(false)
  const [result, setResult] = useState(null)

  const [phoneVerified, setPhoneVerified] = useState(false)
  const [cccdScanned, setCccdScanned] = useState(false)

  const [cccdConfirmed, setCccdConfirmed] = useState(false)

  const [resumeEmail, setResumeEmail] = useState(() => location.state?.email || '')
  const [resumeOtp, setResumeOtp] = useState('')
  const [resumeOtpSent, setResumeOtpSent] = useState(false)
  const [resumeMockOtp, setResumeMockOtp] = useState('')
  const [resumeCooldown, setResumeCooldown] = useState(0)

  // Step 1 — phone
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [otpSent, setOtpSent] = useState(false)
  const [mockOtp, setMockOtp] = useState('')
  const [cooldown, setCooldown] = useState(0)

  // Step 2 — CCCD
  const [cccdFront, setCccdFront] = useState(null)
  const [cccdBack, setCccdBack] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)

  // Step 3 — face
  const [portrait, setPortrait] = useState(null)
  const [faceResult, setFaceResult] = useState(null)

  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')

  const timerRef = useRef(null)
  useEffect(() => () => clearInterval(timerRef.current), [])

  useEffect(() => {
    let active = true
    async function bootstrap() {
      if (!session?.token) {
        setLoadingStatus(false)
        return
      }
      setLoadingStatus(true)
      try {
        const status = await activateStatus(session.token)
        if (!active) return
        setPhoneVerified(Boolean(status.phoneVerified))
        setCccdScanned(Boolean(status.cccdScanned))
        if (status.cccdScanned) {
          setForm({
            fullName: status.fullName || '',
            cccdNo: status.cccdNo || '',
            dateOfBirth: status.dateOfBirth || '',
            gender: status.gender || '',
            address: status.address || '',
          })
        }
        if (status.status === 'ACTIVE') {
          setActivated(true)
          setResult({ fullName: status.fullName })
          clearPendingActivation()
        } else if (!status.phoneVerified) {
          setStep(1)
        } else if (!status.cccdScanned) {
          setStep(2)
        } else {
          setCccdConfirmed(false)
          setStep(2)
        }
      } catch {
        // A stale/expired token: the resume gate below takes over.
      } finally {
        if (active) setLoadingStatus(false)
      }
    }
    bootstrap()
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.token])

  function startCooldown(seconds, setter = setCooldown) {
    clearInterval(timerRef.current)
    setter(seconds)
    timerRef.current = setInterval(() => {
      setter((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current)
          return 0
        }
        return prev - 1
      })
    }, 1000)
  }

  async function handlePhoneOtpClick() {
    setError('')
    if (isBlank(phone)) {
      setError('Vui lòng nhập số điện thoại.')
      return
    }
    if (!isValidVnPhone(phone)) {
      setError('Số điện thoại không hợp lệ.')
      return
    }

    setBusy('phone')
    try {
      const data = await activateSendPhoneOtp(session.token, phone.trim())
      setMockOtp(data.otp || '')
      setOtpSent(true)
      startCooldown(data.resendCooldownSeconds || 60)
    } catch (err) {
      setError(err.message || 'Không gửi được mã OTP. Vui lòng thử lại.')
    } finally {
      setBusy('')
    }
  }

  async function handleVerifyPhoneOtp() {
    setError('')
    if (isBlank(otp)) {
      setError('Vui lòng nhập mã OTP.')
      return
    }

    setBusy('otp')
    try {
      await activateVerifyPhoneOtp(session.token, otp.trim())
      setMockOtp('')
      setOtp('')
      setOtpSent(false)
      setPhoneVerified(true)
      setStep(2)
    } catch (err) {
      setError(err.message || 'Mã OTP không hợp lệ hoặc đã hết hạn.')
    } finally {
      setBusy('')
    }
  }

  async function handleScanCccd() {
    setError('')
    if (!cccdFront || !cccdBack) {
      setError('Vui lòng chọn ảnh cả 2 mặt CCCD.')
      return
    }

    setBusy('scan')
    try {
      const data = await activateScanCccd(session.token, { cccdFront, cccdBack })
      setForm({
        fullName: data.fullName || '',
        cccdNo: data.cccdNo || '',
        dateOfBirth: data.dateOfBirth || '',
        gender: data.gender || '',
        address: data.address || '',
      })
      setFaceResult(null)
      setCccdScanned(true)
      setCccdConfirmed(false)
    } catch (err) {
      setError(err.message || 'Không quét được CCCD. Vui lòng kiểm tra lại ảnh và thử lại.')
    } finally {
      setBusy('')
    }
  }

  function handleRescanCccd() {
    setError('')
    setCccdScanned(false)
    setCccdConfirmed(false)
    setCccdFront(null)
    setCccdBack(null)
    setForm(EMPTY_FORM)
    setFaceResult(null)
  }

  async function handleFaceMatch() {
    setError('')
    if (!cccdScanned) {
      setError('Vui lòng quét CCCD trước khi xác thực khuôn mặt.')
      return
    }
    if (!portrait) {
      setError('Vui lòng chọn ảnh chân dung.')
      return
    }

    setBusy('face')
    try {
      const data = await activateFaceMatch(session.token, portrait)
      if (data.matched && data.activated) {
        setResult({ fullName: form.fullName })
        setActivated(true)
        clearPendingActivation()
      } else {
        setFaceResult(data)
      }
    } catch (err) {
      setError(err.message || 'Xác thực khuôn mặt thất bại. Vui lòng thử lại.')
    } finally {
      setBusy('')
    }
  }

  async function handleResumeSendOtp() {
    setError('')
    if (isBlank(resumeEmail)) {
      setError('Vui lòng nhập email.')
      return
    }
    if (!isValidEmail(resumeEmail)) {
      setError('Email không hợp lệ.')
      return
    }

    setBusy('resume-send')
    try {
      const data = await activateResumeOtp(resumeEmail.trim())
      setResumeOtpSent(true)
      setResumeMockOtp(data.otp || '')
      startCooldown(data.resendCooldownSeconds || 60, setResumeCooldown)
    } catch (err) {
      setError(err.message || 'Không gửi được mã OTP. Vui lòng thử lại.')
    } finally {
      setBusy('')
    }
  }

  async function handleResumeVerify() {
    setError('')
    if (isBlank(resumeOtp)) {
      setError('Vui lòng nhập mã OTP.')
      return
    }

    setBusy('resume-verify')
    try {
      const data = await activateResume(resumeEmail.trim(), resumeOtp.trim())
      setPendingActivation({ token: data.activationToken, email: data.email })
      setResumeOtp('')
      setResumeMockOtp('')
      setResumeOtpSent(false)
      setSession({ token: data.activationToken, email: data.email })
    } catch (err) {
      setError(err.message || 'Mã OTP không hợp lệ hoặc đã hết hạn.')
    } finally {
      setBusy('')
    }
  }

  if (!session?.token) {
    return (
      <Shell
        title="Kích hoạt tài khoản"
        subtitle="Nhập email đã đăng ký để tiếp tục kích hoạt tài khoản của bạn."
      >
        <div className="flex flex-col gap-4">
          {location.state?.notice && (
            <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-[13px] font-medium text-amber-800">
              {location.state.notice}
            </div>
          )}

          <div className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-[13px] text-slate-600">
            <IconShield className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />
            <span>
              Bạn có thể quay lại trang này bất cứ lúc nào để kích hoạt. Chúng tôi sẽ gửi một mã OTP tới email của
              bạn để xác nhận.
            </span>
          </div>

          <div>
            <label className={LABEL_CLASS} htmlFor="resume-email">
              Email
            </label>
            <div className="relative">
              <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                <IconMail className="h-5 w-5" />
              </span>
              <input
                id="resume-email"
                type="email"
                autoComplete="off"
                className={INPUT_CLASS}
                placeholder="ban@example.com"
                value={resumeEmail}
                onChange={(e) => setResumeEmail(e.target.value)}
                disabled={busy === 'resume-send' || busy === 'resume-verify'}
              />
            </div>
          </div>

          <button
            type="button"
            onClick={handleResumeSendOtp}
            disabled={busy === 'resume-send' || busy === 'resume-verify'}
            className={PRIMARY_BUTTON}
          >
            <IconShield className="h-4 w-4" />
            <span>{busy === 'resume-send' ? 'Đang gửi mã...' : 'Gửi mã xác thực'}</span>
          </button>

          {resumeOtpSent && (
            <>
              {resumeMockOtp && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-3 text-[13px] text-amber-800">
                  <span className="font-semibold">OTP (mock):</span>{' '}
                  <span className="font-mono text-base tracking-widest">{resumeMockOtp}</span>
                </div>
              )}

              <input
                inputMode="numeric"
                maxLength={6}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-xl tracking-[0.5em] text-slate-900 placeholder-slate-300 focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:opacity-60"
                placeholder="••••••"
                value={resumeOtp}
                onChange={(e) => setResumeOtp(e.target.value.replace(/\D/g, ''))}
                disabled={busy === 'resume-verify'}
              />

              <button
                type="button"
                onClick={handleResumeVerify}
                disabled={busy === 'resume-verify'}
                className={PRIMARY_BUTTON}
              >
                <span>{busy === 'resume-verify' ? 'Đang xác thực...' : 'Xác nhận & tiếp tục'}</span>
                {busy !== 'resume-verify' && (
                  <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                )}
              </button>

              <button
                type="button"
                onClick={handleResumeSendOtp}
                disabled={resumeCooldown > 0 || busy === 'resume-verify'}
                className="self-start text-xs font-semibold text-brand-600 hover:text-brand-700 hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline"
              >
                {resumeCooldown > 0 ? `Gửi lại mã sau ${resumeCooldown}s` : 'Gửi lại mã'}
              </button>
            </>
          )}

          <FieldError>{error}</FieldError>
        </div>
      </Shell>
    )
  }

  const phoneOtpBusy = busy === 'phone' || busy === 'otp'
  const phoneOtpLabel = busy === 'phone'
    ? 'Đang gửi mã...'
    : !otpSent
      ? 'Xác thực số điện thoại'
      : cooldown > 0
        ? `Gửi lại sau ${cooldown}s`
        : 'Gửi lại mã'

  const doneSteps = [...(phoneVerified ? [1] : []), ...(cccdConfirmed ? [2] : [])]
  const maxReached = !phoneVerified ? 1 : !cccdConfirmed ? 2 : 3
  const canGo = (n) => n <= maxReached

  return (
    <Shell
      title="Kích hoạt tài khoản"
      subtitle="Hoàn tất lần lượt từng bước xác thực dưới đây. Mỗi bước được lưu ngay khi hoàn thành."
    >
      <div className="flex flex-col gap-4">
        <Stepper
          current={activated ? 3 : step}
          done={activated ? [1, 2, 3] : doneSteps}
          canGo={(n) => !activated && canGo(n)}
          onSelect={(n) => {
            setError('')
            setStep(n)
          }}
        />

        {loadingStatus && <p className="text-sm text-slate-500">Đang tải trạng thái…</p>}

        {!loadingStatus && activated && (
          <>
            <SuccessNote>
              Tài khoản đã được kích hoạt{result?.fullName ? ` cho ${result.fullName}` : ''}. Bạn có thể đăng nhập
              ngay.
            </SuccessNote>
            <button
              type="button"
              onClick={() =>
                navigate('/login', { state: { notice: 'Tài khoản đã được kích hoạt. Vui lòng đăng nhập.' } })
              }
              className={PRIMARY_BUTTON}
            >
              <span>Đăng nhập</span>
              <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </button>
          </>
        )}

        {!loadingStatus && !activated && step === 1 && (
          <StepCard
            step={1}
            title="Xác thực số điện thoại"
            hint="Nhập số điện thoại và bấm xác thực. Hệ thống trả về một mã OTP (mock) để bạn nhập."
          >
            {phoneVerified ? (
              <>
                <SuccessNote>Số điện thoại đã được xác thực.</SuccessNote>
                <button type="button" onClick={() => setStep(2)} className={PRIMARY_BUTTON}>
                  <span>Tiếp tục</span>
                  <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                </button>
              </>
            ) : (
              <>
                <div className="relative">
                  <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                    <IconPhone className="h-5 w-5" />
                  </span>
                  <input
                    id="activate-phone"
                    inputMode="numeric"
                    className={INPUT_CLASS}
                    placeholder="0901234567"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    disabled={phoneOtpBusy}
                  />
                </div>

                <button
                  type="button"
                  onClick={handlePhoneOtpClick}
                  disabled={phoneOtpBusy || (otpSent && cooldown > 0)}
                  className={PRIMARY_BUTTON}
                >
                  <IconShield className="h-4 w-4" />
                  <span>{phoneOtpLabel}</span>
                </button>

                {otpSent && (
                  <>
                    {mockOtp && (
                      <div className="rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-3 text-[13px] text-amber-800">
                        <span className="font-semibold">OTP (mock):</span>{' '}
                        <span className="font-mono text-base tracking-widest">{mockOtp}</span>
                      </div>
                    )}

                    <input
                      id="activate-otp"
                      inputMode="numeric"
                      maxLength={6}
                      className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-xl tracking-[0.5em] text-slate-900 placeholder-slate-300 focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:opacity-60"
                      placeholder="••••••"
                      value={otp}
                      onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                      disabled={busy === 'otp'}
                    />

                    <button
                      type="button"
                      onClick={handleVerifyPhoneOtp}
                      disabled={busy === 'otp'}
                      className={PRIMARY_BUTTON}
                    >
                      <span>{busy === 'otp' ? 'Đang xác thực...' : 'Xác nhận OTP'}</span>
                      {busy !== 'otp' && (
                        <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                      )}
                    </button>
                  </>
                )}
              </>
            )}
          </StepCard>
        )}

        {!loadingStatus && !activated && step === 2 && (
          <StepCard
            step={2}
            title="Thông tin CCCD"
            hint="Tải lên 2 mặt CCCD rồi bấm quét. Hệ thống đọc thông tin và lưu ngay vào hồ sơ — hãy kiểm tra lại rồi xác nhận để sang bước tiếp theo."
          >
            {cccdScanned ? (
              <SuccessNote>
                Đã quét CCCD và lưu thông tin vào hồ sơ. Vui lòng kiểm tra lại thông tin bên dưới trước khi
                tiếp tục.
              </SuccessNote>
            ) : (
              <>
                <FilePicker
                  id="cccd-front"
                  label="CCCD — mặt trước"
                  hint="Chọn ảnh mặt trước CCCD"
                  file={cccdFront}
                  onChange={setCccdFront}
                  disabled={busy === 'scan'}
                />
                <FilePicker
                  id="cccd-back"
                  label="CCCD — mặt sau"
                  hint="Chọn ảnh mặt sau CCCD"
                  file={cccdBack}
                  onChange={setCccdBack}
                  disabled={busy === 'scan'}
                />

                <button
                  type="button"
                  onClick={handleScanCccd}
                  disabled={busy === 'scan' || !cccdFront || !cccdBack}
                  className={OUTLINE_BUTTON}
                >
                  <IconIdCard className="h-4 w-4" />
                  <span>{busy === 'scan' ? 'Đang quét CCCD...' : 'Quét CCCD & lưu thông tin'}</span>
                </button>
              </>
            )}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <label className={LABEL_CLASS} htmlFor="kyc-fullname">
                  Họ và tên
                </label>
                <input
                  id="kyc-fullname"
                  className={PLAIN_INPUT}
                  value={form.fullName}
                  placeholder="Nguyễn Văn A"
                  disabled
                />
              </div>
              <div>
                <label className={LABEL_CLASS} htmlFor="kyc-cccd">
                  Số CCCD
                </label>
                <input
                  id="kyc-cccd"
                  inputMode="numeric"
                  className={PLAIN_INPUT}
                  value={form.cccdNo}
                  placeholder="079123456789"
                  disabled
                />
              </div>
              <div>
                <label className={LABEL_CLASS} htmlFor="kyc-dob">
                  Ngày sinh
                </label>
                <input id="kyc-dob" type="date" className={PLAIN_INPUT} value={form.dateOfBirth} disabled />
              </div>
              <div>
                <label className={LABEL_CLASS} htmlFor="kyc-gender">
                  Giới tính
                </label>
                <select id="kyc-gender" className={PLAIN_INPUT} value={form.gender} disabled>
                  <option value="">— Chọn —</option>
                  {GENDERS.map((g) => (
                    <option key={g} value={g}>
                      {g}
                    </option>
                  ))}
                </select>
              </div>
              <div className="sm:col-span-2">
                <label className={LABEL_CLASS} htmlFor="kyc-address">
                  Địa chỉ
                </label>
                <input
                  id="kyc-address"
                  className={PLAIN_INPUT}
                  value={form.address}
                  placeholder="Số nhà, đường, phường, quận, tỉnh/thành"
                  disabled
                />
              </div>
            </div>

            <p className="text-[11px] leading-snug text-slate-500">
              Thông tin trên được đọc tự động từ CCCD nên không thể chỉnh sửa. Nếu có thông tin sai, hãy quét
              lại với ảnh rõ hơn.
            </p>

            <div className="flex flex-col gap-3 sm:flex-row">
              <button type="button" onClick={cccdScanned ? handleRescanCccd : () => setStep(1)} className={BACK_BUTTON}>
                {cccdScanned ? 'Quét lại' : '← Quay lại'}
              </button>
              {cccdScanned && (
                <button
                  type="button"
                  onClick={() => {
                    setError('')
                    setCccdConfirmed(true)
                    setStep(3)
                  }}
                  className={NEXT_BUTTON}
                >
                  <span>Thông tin đúng — Tiếp tục</span>
                  <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                </button>
              )}
            </div>
          </StepCard>
        )}

        {!loadingStatus && !activated && step === 3 && (
          <StepCard
            step={3}
            title="Xác thực khuôn mặt"
            hint="Tải lên ảnh chân dung rồi bấm xác thực. Ảnh sẽ được đối chiếu với ảnh trên CCCD mặt trước — đây là bước cuối, khớp là tài khoản được kích hoạt ngay."
          >
            <FilePicker
              id="portrait"
              label="Ảnh chân dung"
              hint="Chọn ảnh selfie của bạn"
              file={portrait}
              onChange={(f) => {
                setPortrait(f)
                setFaceResult(null)
              }}
              disabled={busy === 'face'}
              icon={IconUser}
            />

            <button
              type="button"
              onClick={handleFaceMatch}
              disabled={busy === 'face' || !portrait}
              className={OUTLINE_BUTTON}
            >
              <IconUser className="h-4 w-4" />
              <span>{busy === 'face' ? 'Đang xác thực...' : 'Xác thực khuôn mặt'}</span>
            </button>

            {faceResult && (
              <div
                className={`rounded-lg border px-3.5 py-3 text-[13px] ${
                  faceResult.matched
                    ? 'border-amber-200 bg-amber-50 text-amber-800'
                    : 'border-red-200 bg-red-50 text-red-700'
                }`}
              >
                {faceResult.matched ? (
                  <span className="font-medium">
                    Khuôn mặt khớp ({Number(faceResult.similarity).toFixed(1)}%) nhưng tài khoản chưa được kích hoạt.
                    Vui lòng thử lại.
                  </span>
                ) : (
                  <span className="font-medium">
                    Khuôn mặt không khớp ({Number(faceResult.similarity).toFixed(1)}%). Vui lòng thử lại với ảnh rõ
                    hơn.
                  </span>
                )}
              </div>
            )}

            <div className="flex flex-col gap-3 sm:flex-row">
              <button type="button" onClick={() => setStep(2)} className={BACK_BUTTON}>
                ← Quay lại
              </button>
            </div>
          </StepCard>
        )}

        <FieldError>{error}</FieldError>
      </div>
    </Shell>
  )
}
