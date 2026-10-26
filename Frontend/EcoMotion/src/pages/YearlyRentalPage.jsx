import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DrivingLicenseForm from '../components/yearly-rental/DrivingLicenseForm'
import YearlyContractPanel from '../components/yearly-rental/YearlyContractPanel'
import { useYearlyRental } from '../hooks/useYearlyRental'

function ErrorPanel({ message, onRetry }) {
  return (
    <div role="alert" className="rounded-xl border border-error-container bg-white p-6">
      <p className="text-sm text-on-error-container">{message}</p>
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
  const { licenseState, contractState, reloadLicense, reloadContract } =
    useYearlyRental(bookingCode)
  const [message, setMessage] = useState('')

  return (
    <div className="bg-surface">
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
            Gói năm là hình thức tự lái, cần GPLX hợp lệ được duyệt và hợp đồng
            trước khi xác nhận nhận xe.
          </p>
        </div>

        {message && (
          <p role="status" className="rounded-xl bg-brand-50 p-4 text-sm text-brand-900">
            {message}
          </p>
        )}

        <div className="grid items-start gap-6 lg:grid-cols-2">
          <div>
            {licenseState.loading ? (
              <p role="status" className="rounded-xl bg-white p-6 text-sm text-slate-500">
                Đang tải GPLX…
              </p>
            ) : licenseState.error ? (
              <ErrorPanel message={licenseState.error} onRetry={reloadLicense} />
            ) : licenseState.data ? (
              <DrivingLicenseForm
                key={JSON.stringify(licenseState.data)}
                license={licenseState.data}
                onSaved={() => {
                  setMessage('Đã lưu thông tin GPLX.')
                  reloadLicense()
                }}
              />
            ) : null}
          </div>

          <div>
            {!bookingCode ? (
              <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-card">
                <h2 className="text-lg font-bold text-slate-900">
                  Hợp đồng của đơn thuê
                </h2>
                <p className="mt-3 text-sm leading-relaxed text-slate-500">
                  Sau khi gửi yêu cầu thuê năm, mở hồ sơ của đơn để tải hợp đồng.
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
              <ErrorPanel message={contractState.error} onRetry={reloadContract} />
            ) : contractState.data ? (
              <YearlyContractPanel
                key={bookingCode}
                contract={contractState.data}
                onUploaded={() => {
                  setMessage('Đã lưu hợp đồng.')
                  reloadContract()
                }}
              />
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}