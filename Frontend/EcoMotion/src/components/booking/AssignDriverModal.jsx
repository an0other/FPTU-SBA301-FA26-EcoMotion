import { useEffect, useState } from 'react'
import { bookingService } from '../../services/bookingService'
import { IconUser, IconPhone, IconStar, IconIdCard, IconSearch, IconAlertTriangle } from '../icons/Icons'

export default function AssignDriverModal({
  isOpen,
  onClose,
  onConfirm,
  bookingId,
  bookingCode,
  currentDriverId,
  submitting,
}) {
  const [drivers, setDrivers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedDriverId, setSelectedDriverId] = useState(currentDriverId || null)
  const [reloadTrigger, setReloadTrigger] = useState(0)

  useEffect(() => {
    let active = true
    if (!isOpen || !bookingId) return

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const data = await bookingService.getAvailableDrivers(bookingId)
        if (!active) return
        setDrivers(data || [])
        if (currentDriverId) {
          setSelectedDriverId(currentDriverId)
        }
      } catch (err) {
        if (!active) return
        setError(err.message || 'Không thể tải danh sách tài xế khả dụng.')
      } finally {
        if (active) setLoading(false)
      }
    }

    load()

    return () => {
      active = false
    }
  }, [isOpen, bookingId, currentDriverId, reloadTrigger])

  function handleRetry() {
    setReloadTrigger((v) => v + 1)
  }

  if (!isOpen) return null

  const filteredDrivers = drivers.filter((d) => {
    const q = searchQuery.toLowerCase().trim()
    if (!q) return true
    return (
      d.fullName?.toLowerCase().includes(q) ||
      d.phone?.includes(q) ||
      d.licenseNo?.toLowerCase().includes(q)
    )
  })

  function handleSubmit(e) {
    e.preventDefault()
    if (!selectedDriverId) return
    onConfirm(selectedDriverId)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs">
      <div className="relative w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl border border-slate-100 max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="mb-4 flex items-center gap-3 border-b border-slate-100 pb-3 shrink-0">
          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
            <IconUser className="h-6 w-6" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-900">Chỉ định tài xế phục vụ</h3>
            <p className="text-xs text-slate-500">Đơn đặt xe #{bookingCode}</p>
          </div>
        </div>

        {/* Search Input */}
        <div className="relative mb-3 shrink-0">
          <IconSearch className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo tên tài xế, SĐT, số GPLX..."
            className="w-full rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-3 py-2 text-xs text-slate-800 placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:outline-hidden"
          />
        </div>

        {/* Driver list */}
        <div className="flex-1 overflow-y-auto space-y-2.5 pr-1 py-1">
          {loading ? (
            <div className="space-y-3 py-4">
              {[1, 2, 3].map((n) => (
                <div key={n} className="animate-pulse rounded-xl border border-slate-200 p-3.5 bg-slate-50">
                  <div className="h-4 w-1/3 bg-slate-200 rounded mb-2" />
                  <div className="h-3 w-1/2 bg-slate-200 rounded" />
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-700">
              <p className="font-semibold">{error}</p>
              <button
                type="button"
                onClick={handleRetry}
                className="mt-2 rounded bg-red-100 px-3 py-1 font-bold text-red-800 hover:bg-red-200"
              >
                Thử lại
              </button>
            </div>
          ) : filteredDrivers.length === 0 ? (
            <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-xs text-slate-500">
              <IconUser className="mx-auto h-8 w-8 text-slate-300 mb-2" />
              <p className="font-semibold text-slate-700">Không tìm thấy tài xế phù hợp</p>
              <p className="text-slate-400 mt-0.5">
                {searchQuery
                  ? 'Không có kết quả khớp với từ khóa tìm kiếm.'
                  : 'Chưa có tài xế nào được đăng ký hoặc tất cả đều bận lịch.'}
              </p>
            </div>
          ) : (
            filteredDrivers.map((driver) => {
              const isSelected = selectedDriverId === driver.driverId
              const isAvailable = driver.available

              return (
                <label
                  key={driver.driverId}
                  className={`flex items-start gap-3 rounded-xl border p-3.5 transition cursor-pointer ${
                    isSelected
                      ? 'border-indigo-600 bg-indigo-50/50 shadow-xs'
                      : isAvailable
                      ? 'border-slate-200 bg-white hover:border-indigo-200 hover:bg-slate-50/50'
                      : 'border-slate-200 bg-slate-50/60 opacity-60 cursor-not-allowed'
                  }`}
                >
                  <input
                    type="radio"
                    name="selectedDriver"
                    disabled={!isAvailable}
                    checked={isSelected}
                    onChange={() => isAvailable && setSelectedDriverId(driver.driverId)}
                    className="mt-1 h-4 w-4 text-indigo-600 border-slate-300 focus:ring-indigo-500"
                  />

                  <div className="flex-1 space-y-1">
                    <div className="flex items-center justify-between">
                      <h4 className="text-sm font-bold text-slate-900">{driver.fullName}</h4>
                      {isAvailable ? (
                        <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-800">
                          Khả dụng
                        </span>
                      ) : (
                        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">
                          Bận lịch
                        </span>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                      <span className="flex items-center gap-1">
                        <IconPhone className="h-3 w-3" />
                        {driver.phone || 'Chưa có SĐT'}
                      </span>
                      <span className="flex items-center gap-1">
                        <IconIdCard className="h-3 w-3" />
                        {driver.licenseClass} - {driver.licenseNo}
                      </span>
                      <span className="flex items-center gap-1 font-semibold text-amber-600">
                        <IconStar className="h-3 w-3 fill-amber-400 text-amber-400" />
                        {driver.ratingAvg ? Number(driver.ratingAvg).toFixed(1) : '5.0'}
                      </span>
                    </div>

                    {!isAvailable && driver.unavailabilityReason && (
                      <p className="text-[11px] font-medium text-amber-700 flex items-center gap-1 pt-0.5">
                        <IconAlertTriangle className="h-3 w-3 shrink-0" />
                        {driver.unavailabilityReason}
                      </p>
                    )}
                  </div>
                </label>
              )
            })
          )}
        </div>

        {/* Footer */}
        <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-end gap-3 shrink-0">
          <button
            type="button"
            disabled={submitting}
            onClick={onClose}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100 disabled:opacity-50"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            disabled={submitting || !selectedDriverId}
            onClick={handleSubmit}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-indigo-700 disabled:opacity-50"
          >
            {submitting ? (
              <>
                <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                Đang gán...
              </>
            ) : (
              'Xác nhận chỉ định'
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
