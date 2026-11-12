import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import DrivingLicenseForm from '../components/yearly-rental/DrivingLicenseForm'
import YearlyWorkflowPanel from '../components/yearly-rental/YearlyWorkflowPanel'
import YearlyBookingFlow from '../components/yearly-rental/YearlyBookingFlow'
import { useYearlyRental } from '../hooks/useYearlyRental'
import { BookingErrorDialog } from '../components/booking/BookingErrorDialog'

function ErrorPanel({ onRetry }) {
  return (
    <div className="rounded-xl border border-error-container bg-white p-6">
      <p className="text-sm text-on-error-container">Chưa tải được thông tin. Vui lòng thử lại.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold"
      >
        Thử lại
      </button>
    </div>
  )
}

export default function YearlyRentalPage() {
  const { bookingCode } = useParams()
  const [searchParams] = useSearchParams()
  const vehicleId = searchParams.get('vehicleId')
  const planId = searchParams.get('planId')
  const { licenseState, contractState, reloadLicense, reloadContract } =
    useYearlyRental(bookingCode)
  const [message, setMessage] = useState('')
  const [dismissedError, setDismissedError] = useState('')
  const loadError = [licenseState.error, contractState.error].filter(Boolean).join('\n')
  const retryLoading = () => {
    setDismissedError('')
    reloadLicense()
    if (bookingCode) reloadContract()
  }

  return (
    <div className="bg-surface">
      <BookingErrorDialog title="Không tải được hồ sơ thuê năm"
        message={loadError === dismissedError ? '' : loadError}
        onClose={() => setDismissedError(loadError)} onRetry={retryLoading} />
      <div className="mx-auto max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
        <nav aria-label="Đường dẫn trang" className="flex flex-wrap gap-2 text-xs">
          <Link to="/" className="text-slate-500 hover:text-brand-700">
            Trang chủ
          </Link>
          <span aria-hidden="true" className="text-slate-300">/</span>
          <span aria-current="page" className="font-semibold text-slate-800">
            Hồ sơ gói năm
          </span>
        </nav>

        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Hồ sơ thuê xe theo năm
          </h1>
          <p className="mt-2 max-w-3xl text-sm leading-relaxed text-slate-500">
            Gói năm là hình thức tự lái. Tải hai mặt GPLX để đọc dữ liệu, gửi yêu cầu thuê,
            chờ nhà cung cấp chấp nhận rồi tải hợp đồng đã ký và thanh toán khoản trả trước.
          </p>
        </div>

        {message && (
          <p role="status" className="rounded-xl bg-brand-50 p-4 text-sm text-brand-900">
            {message}
          </p>
        )}

        <div className="space-y-6">
          <details id="driving-license" className="rounded-xl border border-slate-200 bg-white p-4" open={!bookingCode && !licenseState.data?.yearlyBookingEligible}>
            <summary className="cursor-pointer font-semibold">Hồ sơ GPLX · {licenseState.data?.yearlyBookingEligible ? 'Đủ điều kiện sơ bộ — xem hoặc cập nhật' : 'Kiểm tra điều kiện trước khi gửi yêu cầu'}</summary>
            <div className="mt-4">
            {licenseState.loading ? (
              <p role="status" className="rounded-xl bg-white p-6 text-sm text-slate-500">
                Đang tải GPLX…
              </p>
            ) : licenseState.error ? (
              <ErrorPanel onRetry={() => { setDismissedError(''); reloadLicense() }} />
            ) : licenseState.data ? (
              <DrivingLicenseForm
                key={JSON.stringify(licenseState.data)}
                license={licenseState.data}
                onSaved={() => {
                  setMessage('Đã cập nhật hồ sơ GPLX. Bản nộp mới chỉ có hiệu lực sau khi Admin chấp nhận.')
                  reloadLicense()
                }}
              />
            ) : null}
            </div>
          </details>

          <div>
            {!bookingCode && vehicleId && planId ? (
              <YearlyBookingFlow
                vehicleId={vehicleId}
                planId={planId}
                license={licenseState.data}
                licenseLoading={licenseState.loading}
              />
            ) : !bookingCode ? (
              <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-card">
                <h2 className="text-lg font-bold text-slate-900">
                  Bắt đầu thuê năm
                </h2>
                <p className="mt-3 text-sm leading-relaxed text-slate-500">
                  Chọn một xe có gói năm ở trang danh sách hoặc chi tiết xe. Bạn sẽ cần GPLX hạng được hỗ trợ,
                  còn hạn đến hết kỳ thuê.
                </p>
                <Link
                  to="/"
                  className="mt-5 inline-block rounded-lg border border-brand-200 px-4 py-2.5 text-sm font-semibold text-brand-700 hover:bg-brand-50"
                >
                  Khám phá xe
                </Link>
              </section>
            ) : contractState.loading ? (
              <p role="status" className="rounded-xl bg-white p-6 text-sm text-slate-500">
                Đang tải thông tin hợp đồng…
              </p>
            ) : contractState.error ? (
              <ErrorPanel onRetry={() => { setDismissedError(''); reloadContract() }} />
            ) : contractState.data ? (
              <YearlyWorkflowPanel
                key={bookingCode}
                role="renter"
                bookingCode={bookingCode}
                bookingStatus={contractState.data.bookingStatus}
              />
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
