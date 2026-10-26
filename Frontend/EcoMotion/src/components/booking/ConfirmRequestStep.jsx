import { formatCurrency } from '../../utils/formatCurrency'
import { DEPOSIT_HOLD_COPY } from '../../utils/bookingCopy'

function SectionHeader({ icon, children, accent }) {
  return (
    <div className={`flex items-center gap-2.5 border-b px-5 py-3.5 sm:px-6 ${
      accent
        ? 'border-brand-300 bg-brand-50'
        : 'border-[#D9DFD9] bg-[#F1F4F2]'
    }`}>
      {icon}
      <h2 className={`text-[15px] font-extrabold tracking-tight ${accent ? 'text-brand-800' : 'text-[#14221A]'}`}>{children}</h2>
    </div>
  )
}

function InfoRow({ label, children }) {
  return (
    <div className="border-b border-dashed border-[#E6EBE8] py-3.5 last:border-0 last:pb-0">
      <dt className="text-[11px] font-bold uppercase tracking-wider text-brand-700/70">{label}</dt>
      <dd className="mt-1.5">{children}</dd>
    </div>
  )
}

function CheckItem({ children }) {
  return (
    <li className="flex items-start gap-2.5 text-[13px] leading-relaxed text-[#14221A]">
      <svg className="mt-0.5 h-[18px] w-[18px] shrink-0 text-brand-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fillRule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
      </svg>
      <span>{children}</span>
    </li>
  )
}

function CarPlaceholder() {
  return (
    <svg className="h-14 w-14 text-[#14221A]/15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z" />
    </svg>
  )
}

export const ConfirmRequestStep = ({
  vehicleName,
  vehicleImage,
  plateNumber,
  vehicleYear,
  pickupAddress,
  pickup,
  driverNote,
  passengerCount,
  planTitle,
  durationLabel,
  formattedStart,
  projectedEnd,
  priceNote,
  depositNote,
  totalAmount,
  depositAmount,
  submitting = false,
  onBack,
  onSubmit,
}) => {
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#14221A]">3. Xác nhận yêu cầu</h1>
        <p className="mt-1.5 text-sm leading-relaxed text-[#14221A]/65">
          Kiểm tra xe, lịch trình, điểm đón và cọc trước khi gửi.
        </p>
      </div>

      {/* Vehicle card */}
      <section className="overflow-hidden rounded-2xl border-2 border-[#D9DFD9] shadow-sm">
        <SectionHeader icon={
          <svg className="h-[18px] w-[18px] text-brand-700" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M.99 5.24A2.25 2.25 0 013.25 3h13.5A2.25 2.25 0 0119 5.25l.01 9.5A2.25 2.25 0 0116.76 17H3.26A2.25 2.25 0 011 14.76l-.01-9.52zm8.26 9.52v-.625a.75.75 0 00-.75-.75H3.25a.75.75 0 00-.75.75v.615c0 .414.336.75.75.75h5.373a.75.75 0 00.627-.74zm1.5 0a.75.75 0 00.627.74h5.373a.75.75 0 00.75-.75v-.615a.75.75 0 00-.75-.75H11.5a.75.75 0 00-.75.75v.625zM3.25 4.5a.75.75 0 00-.75.75v8.5c0 .023.001.046.003.068H9V4.5H3.25zm7.25 0v9.318c.002-.022.003-.045.003-.068v-8.5a.75.75 0 00-.003-.068V4.5h.75z" clipRule="evenodd" />
          </svg>
        }>
          Xe thuê
        </SectionHeader>
        <div className="flex items-center gap-5 bg-white p-5 sm:p-6">
          <div className="h-24 w-32 shrink-0 overflow-hidden rounded-xl border border-[#E6EBE8] bg-[#F7F8F6]">
            {vehicleImage ? (
              <img src={vehicleImage} alt={vehicleName} className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full w-full items-center justify-center">
                <CarPlaceholder />
              </div>
            )}
          </div>
          <div className="min-w-0">
            <p className="text-lg font-extrabold text-[#14221A]">{vehicleName}</p>
            {vehicleYear ? (
              <p className="mt-0.5 text-sm text-[#14221A]/60">Năm {vehicleYear}</p>
            ) : null}
            {plateNumber ? (
              <span className="mt-2 inline-flex rounded-lg border-2 border-brand-200 bg-brand-50/50 px-3 py-1 text-sm font-bold tabular-nums text-brand-800">
                {plateNumber}
              </span>
            ) : null}
          </div>
        </div>
      </section>

      {/* Trip details — each field on its own row */}
      <section className="overflow-hidden rounded-2xl border-2 border-[#D9DFD9] shadow-sm">
        <SectionHeader icon={
          <svg className="h-[18px] w-[18px] text-brand-700" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M5.75 2a.75.75 0 01.75.75V4h7V2.75a.75.75 0 011.5 0V4h.25A2.75 2.75 0 0118 6.75v8.5A2.75 2.75 0 0115.25 18H4.75A2.75 2.75 0 012 15.25v-8.5A2.75 2.75 0 014.75 4H5V2.75A.75.75 0 015.75 2zm-1 5.5c-.69 0-1.25.56-1.25 1.25v6.5c0 .69.56 1.25 1.25 1.25h10.5c.69 0 1.25-.56 1.25-1.25v-6.5c0-.69-.56-1.25-1.25-1.25H4.75z" clipRule="evenodd" />
          </svg>
        }>
          Lịch trình
        </SectionHeader>
        <dl className="bg-white px-5 py-3 sm:px-6">
          <InfoRow label="Gói thuê">
            <p className="text-[15px] font-extrabold text-[#14221A]">{planTitle}</p>
            <p className="mt-0.5 text-xs font-medium text-[#14221A]/55">{durationLabel}</p>
          </InfoRow>
          <InfoRow label="Nhận xe">
            <p className="text-[15px] font-bold text-[#14221A]">{formattedStart}</p>
          </InfoRow>
          <InfoRow label="Trả xe">
            <p className="text-[15px] font-bold text-[#14221A]">{projectedEnd}</p>
          </InfoRow>
          <InfoRow label="Hành khách">
            <p className="text-[15px] font-bold text-[#14221A]">{passengerCount} người</p>
          </InfoRow>
        </dl>
      </section>

      {/* Pickup location — its own section */}
      <section className="overflow-hidden rounded-2xl border-2 border-[#D9DFD9] shadow-sm">
        <SectionHeader icon={
          <svg className="h-[18px] w-[18px] text-brand-700" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M9.69 18.933l.003.001C9.89 19.02 10 19 10 19s.11.02.308-.066l.002-.001.006-.003.018-.008a5.741 5.741 0 00.281-.14c.186-.096.446-.24.757-.433a19.695 19.695 0 002.683-2.006c1.9-1.696 3.945-4.243 3.945-7.343a8 8 0 10-16 0c0 3.1 2.045 5.647 3.945 7.343a19.695 19.695 0 002.683 2.006 12.336 12.336 0 00.757.433l.14.071.07.036.017.008.006.003zM10 11.25a2.25 2.25 0 100-4.5 2.25 2.25 0 000 4.5z" clipRule="evenodd" />
          </svg>
        }>
          Điểm đón
        </SectionHeader>
        <div className="bg-white p-5 sm:p-6">
          <p className="text-[15px] font-bold text-[#14221A]">{pickupAddress}</p>
          {pickup?.latitude != null ? (
            <p className="mt-1.5 text-xs tabular-nums text-[#14221A]/50">
              {Number(pickup.latitude).toFixed(6)}, {Number(pickup.longitude).toFixed(6)}
            </p>
          ) : null}
          {driverNote ? (
            <div className="mt-3 rounded-xl border border-brand-200 bg-brand-50/40 px-4 py-3">
              <p className="text-[11px] font-bold uppercase tracking-wider text-brand-700/70">Ghi chú cho tài xế</p>
              <p className="mt-1 text-sm leading-relaxed text-[#14221A]/80">{driverNote}</p>
            </div>
          ) : null}
        </div>
      </section>

      {/* Cost breakdown */}
      <section className="overflow-hidden rounded-2xl border-2 border-[#D9DFD9] shadow-sm">
        <SectionHeader icon={
          <svg className="h-[18px] w-[18px] text-brand-700" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path d="M10.75 10.818a2.4 2.4 0 01-.874.135 2.266 2.266 0 01-2.126-1.476.75.75 0 00-1.41.504A3.766 3.766 0 009.25 12.38v.87a.75.75 0 001.5 0v-.874a3.766 3.766 0 002.19-1.32.75.75 0 00-1.19-.914 2.266 2.266 0 01-1 .676z" />
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm7-8a7 7 0 11-14 0 7 7 0 0114 0z" clipRule="evenodd" />
          </svg>
        }>
          Chi phí
        </SectionHeader>
        <div className="bg-white p-5 sm:p-6">
          <div className="space-y-3">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-[15px] font-bold text-[#14221A]">Tổng tiền thuê</p>
                <p className="mt-0.5 text-xs font-medium text-[#14221A]/55">{priceNote}</p>
              </div>
              <p className="text-xl font-extrabold tabular-nums text-[#14221A]">{formatCurrency(totalAmount)}</p>
            </div>
            <div className="border-t-2 border-dashed border-[#E6EBE8]" />
            <div className="flex items-start justify-between gap-4 rounded-xl bg-brand-50/50 p-3 -mx-1">
              <div>
                <p className="text-[15px] font-bold text-brand-800">Cọc giữ chỗ 15 phút</p>
                <p className="mt-0.5 text-xs font-medium text-brand-700/60">{depositNote}</p>
              </div>
              <p className="text-xl font-extrabold tabular-nums text-brand-700">{formatCurrency(depositAmount)}</p>
            </div>
          </div>
          <p className="mt-4 text-[13px] leading-relaxed text-[#14221A]/70">{DEPOSIT_HOLD_COPY.submitHint}</p>
          <p className="mt-1 text-[13px] text-[#14221A]/55">Kênh thanh toán cọc: VNPAY hoặc VietQR.</p>
        </div>
      </section>

      {/* Pre-submit checklist */}
      <section className="overflow-hidden rounded-2xl border-2 border-brand-300 shadow-sm">
        <SectionHeader icon={
          <svg className="h-[18px] w-[18px] text-brand-700" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
        } accent>
          Trước khi gửi
        </SectionHeader>
        <div className="bg-brand-50/40 p-5 sm:p-6">
          <ul className="space-y-3">
            <CheckItem>{DEPOSIT_HOLD_COPY.submitHint}</CheckItem>
            <CheckItem>{DEPOSIT_HOLD_COPY.confirmedWhen}</CheckItem>
          </ul>
        </div>
      </section>

      {/* Navigation */}
      <div className="flex flex-col-reverse items-center justify-between gap-3 pt-2 sm:flex-row">
        <button
          onClick={onBack}
          disabled={submitting}
          className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-[#E6EBE8] bg-white px-5 py-3 text-sm font-semibold text-[#14221A] transition-colors hover:bg-[#F7F8F6] focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50 sm:w-auto"
          type="button"
        >
          <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M17 10a.75.75 0 01-.75.75H5.612l4.158 3.96a.75.75 0 11-1.04 1.08l-5.5-5.25a.75.75 0 010-1.08l5.5-5.25a.75.75 0 111.04 1.08L5.612 9.25H16.25A.75.75 0 0117 10z" clipRule="evenodd" />
          </svg>
          Quay lại chỉnh sửa
        </button>

        <button
          onClick={onSubmit}
          disabled={submitting}
          className="flex w-full min-w-[220px] items-center justify-center gap-2 rounded-xl bg-brand-700 px-7 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-800 focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-60 sm:w-auto"
          type="button"
        >
          {submitting ? (
            <>
              <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
              Đang gửi...
            </>
          ) : (
            <>
              Gửi yêu cầu đặt xe
              <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path d="M3.105 2.289a.75.75 0 00-.826.95l1.414 4.925A1.5 1.5 0 005.135 9.25h6.115a.75.75 0 010 1.5H5.135a1.5 1.5 0 00-1.442 1.086l-1.414 4.926a.75.75 0 00.826.95 28.896 28.896 0 0015.293-7.154.75.75 0 000-1.115A28.897 28.897 0 003.105 2.289z" />
              </svg>
            </>
          )}
        </button>
      </div>
    </div>
  )
}
