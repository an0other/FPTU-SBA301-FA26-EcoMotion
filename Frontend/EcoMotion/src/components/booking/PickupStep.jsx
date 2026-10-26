import { useState } from 'react'
import { PickupMap } from './PickupMap'
import { hasValidPickup } from '../../utils/bookingAvailability'

export const PickupStep = ({
  pickup,
  setPickup,
  driverNote,
  setDriverNote,
  passengerCount,
  setPassengerCount,
  seatCount,
  driverIncluded,
  maxPassengers,
  onBack,
  onNext,
}) => {
  const [errorMsg, setErrorMsg] = useState(null)
  const located = hasValidPickup(pickup)

  const handleNextClick = () => {
    if (!hasValidPickup(pickup)) {
      setErrorMsg('Nhập địa chỉ rồi chọn gợi ý, hoặc ghim trên bản đồ để xác định vị trí.')
      return
    }
    setErrorMsg(null)
    onNext()
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#14221A]">2. Điểm đón và số khách</h1>
        <p className="mt-1.5 text-sm leading-relaxed text-[#14221A]/65">
          Cung cấp vị trí đón thuận tiện để tài xế chuyên trách phục vụ chu đáo
        </p>
      </div>

      {/* Pickup location */}
      <section className="space-y-3 rounded-2xl border border-[#E6EBE8] bg-white p-4 shadow-xs sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-[#14221A]">
            Địa điểm đón <span className="text-red-600" aria-hidden="true">*</span>
          </h2>
          {located ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#E8F6EE] px-2.5 py-0.5 text-xs font-semibold text-[#15803D]">
              <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
              Đã xác định vị trí trên bản đồ
            </span>
          ) : (
            <span className="rounded-full bg-[#F7F8F6] px-2.5 py-0.5 text-xs font-medium text-[#14221A]/55">
              Chưa định vị — chọn gợi ý hoặc ghim bản đồ
            </span>
          )}
        </div>
        <p className="text-[11px] tabular-nums text-[#14221A]/45">{(pickup.address ?? '').length}/500 ký tự</p>
        <PickupMap value={pickup} onChange={(next) => { setPickup(next); setErrorMsg(null) }} onError={setErrorMsg} />
        {errorMsg ? (
          <p role="alert" className="flex items-center gap-1.5 rounded-lg bg-red-50 px-3 py-2 text-xs font-medium text-red-700">
            <svg className="h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-5a.75.75 0 01.75.75v4.5a.75.75 0 01-1.5 0v-4.5A.75.75 0 0110 5zm0 10a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
            </svg>
            {errorMsg}
          </p>
        ) : (
          <p className="text-xs leading-relaxed text-[#14221A]/55">
            Gõ địa chỉ rồi chọn gợi ý, hoặc kéo ghim trên bản đồ. Sửa chữ sau khi đã ghim sẽ mất trạng thái đã định vị.
          </p>
        )}
      </section>

      {/* Trip info */}
      <section className="space-y-5 rounded-2xl border border-[#E6EBE8] bg-white p-4 shadow-xs sm:p-6">
        <h2 className="text-sm font-semibold text-[#14221A]">Thông tin chuyến đi</h2>

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-semibold text-[#14221A]" htmlFor="driver-note">Ghi chú cho tài xế</label>
            <span className="text-[11px] tabular-nums text-[#14221A]/45">{driverNote.length}/160</span>
          </div>
          <textarea
            id="driver-note"
            rows={2}
            maxLength={160}
            value={driverNote}
            onChange={(event) => setDriverNote(event.target.value.replace(/[\r\n]+/g, ' '))}
            placeholder="Ví dụ: Có người lớn tuổi, cần hỗ trợ hành lý"
            className="w-full resize-none rounded-xl border border-[#BEC9C1] bg-white px-3.5 py-2.5 text-sm text-[#14221A] placeholder:text-[#14221A]/35 focus:border-brand-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
          />
        </div>

        <div className="space-y-2.5">
          <label className="text-sm font-semibold text-[#14221A]">
            Số hành khách (không tính tài xế) <span className="text-red-600" aria-hidden="true">*</span>
          </label>
          <div className="flex flex-wrap items-center gap-4">
            <div className="inline-flex items-center rounded-xl border border-[#E6EBE8] bg-[#F7F8F6] p-1">
              <button
                type="button"
                aria-label="Giảm số hành khách"
                onClick={() => setPassengerCount(Math.max(1, passengerCount - 1))}
                disabled={passengerCount <= 1}
                className="flex h-10 w-10 items-center justify-center rounded-lg bg-white text-[#14221A] shadow-xs transition-colors hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500 disabled:pointer-events-none disabled:opacity-35"
              >
                <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M4 10a.75.75 0 01.75-.75h10.5a.75.75 0 010 1.5H4.75A.75.75 0 014 10z" clipRule="evenodd" />
                </svg>
              </button>
              <span className="w-12 select-none text-center text-lg font-bold tabular-nums text-[#14221A]">
                {passengerCount}
              </span>
              <button
                type="button"
                aria-label="Tăng số hành khách"
                onClick={() => setPassengerCount(Math.min(maxPassengers, passengerCount + 1))}
                disabled={passengerCount >= maxPassengers}
                className="flex h-10 w-10 items-center justify-center rounded-lg bg-white text-[#14221A] shadow-xs transition-colors hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500 disabled:pointer-events-none disabled:opacity-35"
              >
                <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path d="M10.75 4.75a.75.75 0 00-1.5 0v4.5h-4.5a.75.75 0 000 1.5h4.5v4.5a.75.75 0 001.5 0v-4.5h4.5a.75.75 0 000-1.5h-4.5v-4.5z" />
                </svg>
              </button>
            </div>
            <p className="text-xs font-medium text-[#14221A]/60">
              Xe {seatCount} chỗ
              {driverIncluded ? ` · tối đa ${maxPassengers} hành khách` : ` · tối đa ${maxPassengers} người`}
            </p>
          </div>
        </div>
      </section>

      {/* Navigation */}
      <div className="flex items-center justify-between gap-4 pt-1">
        <button
          onClick={onBack}
          className="inline-flex items-center gap-1.5 rounded-xl border border-[#E6EBE8] bg-white px-5 py-3 text-sm font-semibold text-[#14221A] transition-colors hover:bg-[#F7F8F6] focus-visible:ring-2 focus-visible:ring-brand-500"
          type="button"
        >
          <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M17 10a.75.75 0 01-.75.75H5.612l4.158 3.96a.75.75 0 11-1.04 1.08l-5.5-5.25a.75.75 0 010-1.08l5.5-5.25a.75.75 0 111.04 1.08L5.612 9.25H16.25A.75.75 0 0117 10z" clipRule="evenodd" />
          </svg>
          Quay lại
        </button>
        <button
          onClick={handleNextClick}
          className="inline-flex items-center gap-1.5 rounded-xl bg-brand-700 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-800 focus-visible:ring-2 focus-visible:ring-brand-500"
          type="button"
        >
          Tiếp tục: Xác nhận
          <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z" clipRule="evenodd" />
          </svg>
        </button>
      </div>
    </div>
  )
}
