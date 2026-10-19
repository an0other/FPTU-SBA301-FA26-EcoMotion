import { useRef, useState } from 'react'
import { yearlyRentalService } from '../../services/yearlyRentalService'

function todayInVietnam() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())

  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

export default function DrivingLicenseForm({ license, onSaved }) {
  const [form, setForm] = useState({
    licenseNo: license.licenseNo ?? '',
    licenseClass: license.licenseClass ?? '',
    licenseExpiry: license.licenseExpiry ?? '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const submitting = useRef(false)

  const expired = Boolean(
    license.licenseExpiry && license.licenseExpiry < todayInVietnam(),
  )

  function updateField(event) {
    const { name, value } = event.target
    setForm((previous) => ({ ...previous, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting.current) return

    const request = {
      licenseNo: form.licenseNo.trim(),
      licenseClass: form.licenseClass.trim(),
      licenseExpiry: form.licenseExpiry,
    }

    if (!request.licenseNo || !request.licenseClass || !request.licenseExpiry) {
      setError('Vui lòng nhập đầy đủ thông tin GPLX.')
      return
    }

    submitting.current = true
    setSaving(true)
    setError('')

    try {
      await yearlyRentalService.updateDrivingLicense(request)
      onSaved()
    } catch (error) {
      setError(error.message || 'Không lưu được GPLX.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  const inputClass =
    'mt-2 w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60'

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Giấy phép lái xe</h2>
          <p className="mt-1 text-sm text-slate-500">
            Dành cho gói năm tự lái. Gói giờ, ngày và tháng không yêu cầu GPLX.
          </p>
        </div>

        <span
          className={`rounded-full px-3 py-1 text-xs font-semibold ${
            expired
              ? 'bg-red-50 text-red-700'
              : license.licenseVerified
                ? 'bg-brand-50 text-brand-800'
                : 'bg-amber-50 text-amber-800'
          }`}
        >
          {expired
            ? 'Đã hết hạn'
            : license.licenseVerified
              ? 'Đã duyệt'
              : license.licenseNo
                ? 'Chưa được duyệt'
                : 'Chưa khai báo'}
        </span>
      </div>

      <form onSubmit={handleSubmit} className="mt-6 space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-semibold text-slate-700">
            Số GPLX
            <input
              name="licenseNo"
              value={form.licenseNo}
              onChange={updateField}
              maxLength={20}
              required
              disabled={saving}
              className={inputClass}
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Hạng GPLX
            <input
              name="licenseClass"
              value={form.licenseClass}
              onChange={updateField}
              maxLength={10}
              required
              disabled={saving}
              placeholder="Ví dụ: B"
              className={inputClass}
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Ngày hết hạn
            <input
              type="date"
              name="licenseExpiry"
              value={form.licenseExpiry}
              onChange={updateField}
              min={todayInVietnam()}
              required
              disabled={saving}
              className={inputClass}
            />
          </label>
        </div>

        <p className="rounded-lg bg-slate-50 p-3 text-xs leading-relaxed text-slate-600">
          Nếu thay đổi thông tin GPLX đã được duyệt, hồ sơ sẽ cần Admin duyệt lại.
          Lưu thông tin không đồng nghĩa đã đủ điều kiện đặt gói năm.
        </p>

        {error && (
          <p role="alert" className="text-sm text-on-error-container">{error}</p>
        )}

        <button
          type="submit"
          disabled={saving}
          className="rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {saving ? 'Đang lưu…' : 'Lưu thông tin GPLX'}
        </button>
      </form>
    </section>
  )
}