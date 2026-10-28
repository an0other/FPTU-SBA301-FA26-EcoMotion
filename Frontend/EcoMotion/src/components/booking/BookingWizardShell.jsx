import { Link } from 'react-router-dom'
import { BookingStepper } from './BookingStepper'
import { formatCurrency } from '../../utils/formatCurrency'

export function BookingWizardShell({
  vehicleId,
  vehicleName,
  vehicleImage,
  plateNumber,
  planTitle,
  timeRangeLabel,
  totalLabel,
  depositAmount,
  currentStep,
  onStepSelect,
  contentWidth = 'wide',
  children,
}) {
  return (
    <div className="mx-auto w-full max-w-7xl space-y-5 px-4 py-4 sm:px-6 lg:px-8">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs text-[#64748B] sm:text-sm">
        <Link to="/" className="transition-colors hover:text-brand-700">Trang chủ</Link>
        <svg className="h-3 w-3 text-[#64748B]/50" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clipRule="evenodd" />
        </svg>
        <Link to={`/vehicles/${vehicleId}`} className="transition-colors hover:text-brand-700">
          {vehicleName}
        </Link>
        <svg className="h-3 w-3 text-[#64748B]/50" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clipRule="evenodd" />
        </svg>
        <span className="font-medium text-[#14221A]">Gửi yêu cầu đặt xe</span>
      </nav>

      {/* Vehicle summary header */}
      <section className="overflow-hidden rounded-xl border border-[#E6EBE8] bg-white shadow-xs">
        <div className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
          <div className="flex min-w-0 items-center gap-4">
            {vehicleImage ? (
              <img
                src={vehicleImage}
                alt=""
                className="h-[4.5rem] w-[6.75rem] shrink-0 rounded-lg object-cover"
              />
            ) : (
              <div className="flex h-[4.5rem] w-[6.75rem] shrink-0 items-center justify-center rounded-lg bg-[#F1F5F3]">
                <svg className="h-6 w-6 text-[#14221A]/30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 18.75a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0H21M3.375 14.25h-.375m0 0V10.5m0 3.75h.375m0 0h4.875m4.875 0h4.875m-4.875 0V10.5m4.875 3.75h.375m0 0V10.5m0 0l-1.5-5.25h-9l-1.5 5.25" />
                </svg>
              </div>
            )}
            <div className="min-w-0">
              <p className="truncate text-base font-bold text-[#14221A]">{vehicleName}</p>
              <p className="mt-0.5 text-xs font-medium text-[#14221A]/60">{plateNumber}</p>
              {planTitle ? (
                <p className="mt-1 truncate text-sm font-medium text-brand-700">{planTitle}</p>
              ) : null}
            </div>
          </div>

          <div className="flex shrink-0 flex-col items-start gap-0.5 border-t border-[#E6EBE8] pt-3 sm:items-end sm:border-t-0 sm:pt-0">
            {timeRangeLabel ? (
              <p className="text-xs text-[#14221A]/60">{timeRangeLabel}</p>
            ) : (
              <p className="text-xs text-[#14221A]/40">Chưa chọn thời gian</p>
            )}
            {totalLabel ? (
              <p className="text-lg font-bold tabular-nums text-brand-800">{totalLabel}</p>
            ) : null}
            {depositAmount != null && depositAmount > 0 ? (
              <p className="text-[11px] font-medium text-[#14221A]/55">
                Cọc: {formatCurrency(depositAmount)}
              </p>
            ) : null}
          </div>
        </div>
      </section>

      {/* Stepper */}
      <BookingStepper current={currentStep} onStepSelect={onStepSelect} />

      {/* Step content */}
      <div className={contentWidth === 'form' ? 'mx-auto w-full max-w-3xl' : 'w-full'}>
        {children}
      </div>
    </div>
  )
}
