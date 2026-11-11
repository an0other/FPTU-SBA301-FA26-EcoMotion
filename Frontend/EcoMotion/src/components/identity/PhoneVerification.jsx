import { useState } from 'react'
import { authService } from '../../services/authService'
import { providerOnboardingService as provider } from '../../services/providerOnboardingService'
import { buttonClass, secondaryClass, inputClass, identityMessage } from './identityUi'

export default function PhoneVerification({ activationToken, verified, onVerified, onExpired }) {
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [retryAt, setRetryAt] = useState(0)

  async function perform(action) {
    setBusy(true); setError('')
    try { await action() } catch (err) { setError(identityMessage(err)); if (activationToken && [401, 410].includes(err.status)) onExpired?.() } finally { setBusy(false) }
  }

  if (verified) return <p className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800">Số điện thoại đã được xác nhận.</p>
  return <div className="space-y-3"><h2 className="font-bold">Xác nhận số điện thoại</h2>
    <label className="block text-sm">Số điện thoại<input className={`${inputClass} mt-1`} value={phone} onChange={event => { setPhone(event.target.value); setSent(false); setOtp('') }} type="tel" autoComplete="tel" disabled={busy} placeholder="09xxxxxxxx" /></label>
    <button className={secondaryClass} disabled={busy || !/^0\d{9,10}$/.test(phone)} onClick={() => perform(async () => {
      if (Date.now() < retryAt) { setError('Vui lòng đợi hết thời gian gửi lại OTP.'); return }
      const result = activationToken ? await authService.activateSendPhoneOtp(activationToken, phone) : await provider.sendPhoneOtp(phone)
      setSent(true); setRetryAt(Date.now() + (result.resendCooldownSeconds || 60) * 1000)
    })}>{sent ? 'Gửi lại OTP' : 'Gửi OTP điện thoại'}</button>
    {sent && <><p className="text-sm text-slate-500">Nhập mã xác nhận số điện thoại.</p><label className="block text-sm">Mã OTP<input className={`${inputClass} mt-1`} inputMode="numeric" autoComplete="one-time-code" value={otp} onChange={event => setOtp(event.target.value)} disabled={busy} /></label><button className={buttonClass} disabled={busy || !otp.trim()} onClick={() => perform(async () => { const result = activationToken ? await authService.activateVerifyPhoneOtp(activationToken, otp.trim()) : await provider.verifyPhoneOtp(otp.trim()); if (!result.phoneVerified) throw new Error('Số điện thoại chưa được xác nhận.'); setOtp(''); await onVerified() })}>{busy ? 'Đang xác nhận…' : 'Xác nhận OTP'}</button></>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
  </div>
}
