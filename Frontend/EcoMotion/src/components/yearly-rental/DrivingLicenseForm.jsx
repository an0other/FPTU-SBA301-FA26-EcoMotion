import { useEffect, useRef, useState } from 'react'
import { yearlyRentalService } from '../../services/yearlyRentalService'
import { BookingErrorDialog } from '../booking/BookingErrorDialog'

const MAX_IMAGE_BYTES = 8 * 1024 * 1024

function useObjectUrl(file) {
  const [preview, setPreview] = useState(null)

  useEffect(() => {
    if (!file) {
      return undefined
    }

    const reader = new FileReader()
    reader.onload = () => setPreview({ file, url: String(reader.result || '') })
    reader.readAsDataURL(file)
    return () => reader.abort()
  }, [file])

  return preview?.file === file ? preview.url : ''
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(`${value}T00:00:00+07:00`)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' }).format(date)
}

function LicenseImagePicker({ side, label, file, savedUrl, onChange, disabled }) {
  const previewUrl = useObjectUrl(file)
  const imageUrl = previewUrl || savedUrl

  return (
    <label className="block rounded-xl border border-slate-200 p-4">
      <span className="text-sm font-semibold text-slate-800">{label}</span>
      {imageUrl ? (
        <img
          src={imageUrl}
          alt={`Ảnh mặt ${side} GPLX`}
          className="mt-3 h-40 w-full rounded-lg bg-slate-100 object-contain"
        />
      ) : (
        <span className="mt-3 flex h-40 items-center justify-center rounded-lg bg-slate-50 text-sm text-slate-500">
          Chưa có ảnh
        </span>
      )}
      <input
        type="file"
        accept="image/jpeg,image/png"
        disabled={disabled}
        onChange={(event) => onChange(event.target.files?.[0] ?? null)}
        className="mt-3 block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-2 file:font-semibold file:text-brand-700"
      />
      <span className="mt-1 block text-xs text-slate-500">
        {file?.name || 'Chọn ảnh JPG hoặc PNG từ thư viện hoặc chụp trực tiếp · tối đa 8 MB'}
      </span>
    </label>
  )
}

export default function DrivingLicenseForm({ license, onSaved }) {
  const [frontFile, setFrontFile] = useState(null)
  const [backFile, setBackFile] = useState(null)
  const [savedImageState, setSavedImageState] = useState({ key: '', images: {}, error: '' })
  const [supportedClasses, setSupportedClasses] = useState([])
  const [classesError, setClassesError] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [requestError, setRequestError] = useState('')
  const submitting = useRef(false)

  useEffect(() => {
    let active = true

    yearlyRentalService.getSupportedLicenseClasses()
      .then((classes) => {
        if (active) setSupportedClasses(classes)
      })
      .catch((loadError) => {
        if (active) setClassesError(loadError.message || 'Không tải được danh sách hạng GPLX.')
      })

    return () => {
      active = false
    }
  }, [])

  const imageKey = `${license.frontImageAvailable}:${license.backImageAvailable}`

  useEffect(() => {
    let active = true
    const urls = []
    const sides = ['front', 'back'].filter((side) =>
      side === 'front' ? license.frontImageAvailable : license.backImageAvailable,
    )

    Promise.allSettled(sides.map(async (side) => {
      const blob = await yearlyRentalService.getDrivingLicenseImage(side)
      return [side, URL.createObjectURL(blob)]
    })).then((results) => {
      if (!active) {
        results.forEach((result) => {
          if (result.status === 'fulfilled') URL.revokeObjectURL(result.value[1])
        })
        return
      }

      const nextImages = {}
      let nextError = ''
      results.forEach((result) => {
        if (result.status === 'fulfilled') {
          urls.push(result.value[1])
          nextImages[result.value[0]] = result.value[1]
        } else {
          nextError = result.reason?.message || 'Không tải được ảnh GPLX đã lưu.'
        }
      })
      setSavedImageState({ key: imageKey, images: nextImages, error: nextError })
    })

    return () => {
      active = false
      urls.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [imageKey, license.backImageAvailable, license.frontImageAvailable])

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting.current) return

    if (!frontFile || !backFile) {
      setError('Vui lòng chọn ảnh cả mặt trước và mặt sau GPLX.')
      return
    }

    for (const file of [frontFile, backFile]) {
      if (!['image/jpeg', 'image/png'].includes(file.type)) {
        setError('Ảnh GPLX chỉ hỗ trợ định dạng JPG hoặc PNG.')
        return
      }
      if (file.size > MAX_IMAGE_BYTES) {
        setError('Mỗi ảnh GPLX không được vượt quá 8 MB.')
        return
      }
    }

    submitting.current = true
    setSaving(true)
    setError('')

    try {
      await yearlyRentalService.uploadDrivingLicense(frontFile, backFile)
      setFrontFile(null)
      setBackFile(null)
      onSaved()
    } catch (uploadError) {
      setRequestError(uploadError.message || 'Không đọc được GPLX. Vui lòng kiểm tra ảnh và thử lại.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  const supportedClass = supportedClasses.find(
    (item) => item.code === license.licenseClass,
  )
  const savedImages = savedImageState.key === imageKey ? savedImageState.images : {}
  const imageError = savedImageState.key === imageKey ? savedImageState.error : ''

  return (
    <section id="driving-license" className="scroll-mt-24 rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
      <BookingErrorDialog
        title="Chưa đọc được GPLX"
        message={requestError}
        onClose={() => setRequestError('')}
      />

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-900">GPLX cho gói năm tự lái</h2>
          <p className="mt-1 text-sm leading-relaxed text-slate-500">
            Tải ảnh hai mặt. DocumentAI đọc OCR/QR và điền dữ liệu; bạn có thể chọn ảnh có sẵn trong thư viện.
          </p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
          license.yearlyBookingEligible
            ? 'bg-brand-50 text-brand-800'
            : license.licenseVerified
              ? 'bg-amber-50 text-amber-800'
              : 'bg-slate-100 text-slate-600'
        }`}>
          {license.yearlyBookingEligible
            ? 'Đủ điều kiện sơ bộ'
            : license.licenseVerified
              ? 'Đã đọc · chưa đủ điều kiện'
              : license.licenseNo
                ? 'Cần tải ảnh để đọc lại'
                : 'Chưa có GPLX'}
        </span>
      </div>

      {license.licenseNo && (
        <div className="mt-5 rounded-xl bg-slate-50 p-4">
          <h3 className="font-semibold text-slate-900">Thông tin đọc được</h3>
          <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Số GPLX</dt>
              <dd className="font-medium text-slate-900">{license.licenseNo}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Họ tên</dt>
              <dd className="font-medium text-slate-900">{license.licenseFullName || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Ngày sinh</dt>
              <dd className="font-medium text-slate-900">{formatDate(license.licenseDateOfBirth)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Hạng / ngày cấp</dt>
              <dd className="font-medium text-slate-900">
                {license.licenseClass || '—'} / {formatDate(license.licenseIssueDate)}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Thời hạn</dt>
              <dd className="font-medium text-slate-900">
                {license.licenseExpiryUnlimited ? 'Không thời hạn' : formatDate(license.licenseExpiry)}
              </dd>
            </div>
          </dl>
          <p className={`mt-3 text-sm ${license.yearlyBookingEligible ? 'text-brand-800' : 'text-amber-800'}`}>
            {license.yearlyBookingEligible
              ? 'GPLX phù hợp với chính sách demo. Điều kiện về ngày hết hạn và tuổi vẫn được kiểm tra lại theo thời gian thuê.'
              : license.yearlyBookingIneligibleReason || 'GPLX chưa đáp ứng chính sách đặt gói năm.'}
          </p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-5 space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <LicenseImagePicker
            side="trước"
            label="Mặt trước"
            file={frontFile}
            savedUrl={savedImages.front}
            onChange={setFrontFile}
            disabled={saving}
          />
          <LicenseImagePicker
            side="sau"
            label="Mặt sau"
            file={backFile}
            savedUrl={savedImages.back}
            onChange={setBackFile}
            disabled={saving}
          />
        </div>

        {(error || imageError || classesError) && (
          <div role="alert" className="space-y-1 text-sm text-on-error-container">
            {error && <p>{error}</p>}
            {imageError && <p>{imageError}</p>}
            {classesError && <p>{classesError}</p>}
          </div>
        )}

        <button
          type="submit"
          disabled={saving}
          className="rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {saving ? 'Đang đọc và lưu GPLX…' : 'Đọc và lưu GPLX'}
        </button>
      </form>

      <div className="mt-5 rounded-lg border border-slate-200 p-4">
        <h3 className="text-sm font-semibold text-slate-900">Hạng trong chính sách demo</h3>
        {supportedClass && (
          <p className="mt-2 text-sm text-brand-800">
            Đọc được hạng {supportedClass.code}: {supportedClass.note}
          </p>
        )}
        <ul className="mt-2 space-y-2 text-sm text-slate-600">
          {supportedClasses.map((item) => (
            <li key={item.code}>
              <span className="font-semibold text-slate-800">{item.label}:</span> {item.note}
            </li>
          ))}
        </ul>
        {!supportedClasses.length && !classesError && (
          <p className="mt-2 text-sm text-slate-500">Đang tải chính sách hạng GPLX…</p>
        )}
      </div>

      <p className="mt-4 rounded-lg bg-amber-50 p-3 text-xs leading-relaxed text-amber-900">
        Kết quả OCR/QR chỉ cho biết dữ liệu đọc được và đối chiếu nội bộ khớp; không xác minh với cơ quan nhà nước,
        không chứng minh thẻ là thật hoặc thuộc về tài khoản này. Ảnh được lưu riêng tư và chỉ tài khoản của bạn
        xem được; việc khớp GPLX với CCCD chưa nằm trong bản demo.
      </p>
    </section>
  )
}
