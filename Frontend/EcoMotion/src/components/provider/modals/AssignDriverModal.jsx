import { useState, useMemo } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'
import { useAvailableDrivers } from '../../../hooks/useAvailableDrivers'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

function initialsOf(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

export default function AssignDriverModal({ booking, currentDriverId, isOpen, onClose, onSuccess }) {
  const { drivers, loading: loadingDrivers, error: driversError } = useAvailableDrivers()
  const [selectedDriverId, setSelectedDriverId] = useState(currentDriverId || null)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const filteredDrivers = useMemo(() => {
    if (!drivers) return []
    const q = search.trim().toLowerCase()
    if (!q) return drivers
    return drivers.filter((d) => {
      const nameMatch = (d.fullName || '').toLowerCase().includes(q)
      const phoneMatch = (d.phone || '').includes(q)
      const licenseMatch = (d.licenseNo || '').toLowerCase().includes(q)
      return nameMatch || phoneMatch || licenseMatch
    })
  }, [drivers, search])

  if (!isOpen || !booking) return null

  async function handleConfirm() {
    if (!selectedDriverId) {
      setError('Vui lòng chọn một tài xế để phân công.')
      return
    }

    setLoading(true)
    setError(null)
    try {
      const updated = await providerBookingService.assignDriver(booking.bookingCode, selectedDriverId)
      onSuccess?.(updated, 'Đã phân công tài xế thành công!')
      onClose()
    } catch (err) {
      setError(err?.message || 'Không thể phân công tài xế. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl transition-all flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4 bg-teal-50/50">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-teal-100 text-teal-800">
              <Icon name="person_add" className="text-[22px]" />
            </span>
            <div>
              <h3 className="text-base font-bold text-slate-900">
                {currentDriverId ? 'Đổi tài xế phân công' : 'Phân công tài xế'}
              </h3>
              <p className="text-xs text-slate-500 font-medium">
                Đơn thuê: <span className="font-mono font-bold text-slate-800">{booking.bookingCode}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <Icon name="close" className="text-[20px]" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          {error && (
            <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800 font-medium">
              <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {driversError && (
            <div className="flex items-start gap-2.5 rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-xs text-amber-800 font-medium">
              <Icon name="warning" className="text-[18px] text-amber-600 shrink-0 mt-0.5" />
              <span>{driversError}</span>
            </div>
          )}

          {/* Search box */}
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
              <Icon name="search" className="text-[18px]" />
            </div>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm tài xế theo tên, số điện thoại, số bằng lái..."
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-3 text-xs text-slate-800 placeholder-slate-400 transition focus:border-teal-600 focus:bg-white focus:outline-none"
            />
          </div>

          {/* Driver List */}
          <div className="space-y-2">
            <label className="block text-xs font-bold text-slate-700">
              Chọn tài xế khả dụng ({filteredDrivers.length}):
            </label>

            {loadingDrivers ? (
              <div className="py-8 text-center text-xs text-slate-500">
                <Icon name="progress_activity" className="animate-spin text-2xl text-teal-600 mx-auto mb-2" />
                Đang tải danh sách tài xế...
              </div>
            ) : filteredDrivers.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-200 py-8 text-center text-xs text-slate-400">
                Không tìm thấy tài xế phù hợp
              </div>
            ) : (
              <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                {filteredDrivers.map((driver) => {
                  const isSelected = selectedDriverId === driver.id
                  return (
                    <div
                      key={driver.id}
                      onClick={() => setSelectedDriverId(driver.id)}
                      className={`cursor-pointer flex items-center justify-between p-3.5 rounded-xl border transition-all ${
                        isSelected
                          ? 'border-teal-500 bg-teal-50/60 ring-2 ring-teal-500/30'
                          : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50/70'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        {driver.portraitUrl ? (
                          <img
                            src={driver.portraitUrl}
                            alt={driver.fullName}
                            className="h-10 w-10 rounded-full object-cover shadow-2xs border border-white"
                          />
                        ) : (
                          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-teal-100 font-bold text-xs text-teal-800">
                            {initialsOf(driver.fullName)}
                          </div>
                        )}
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-slate-900 text-xs sm:text-sm">
                              {driver.fullName}
                            </span>
                            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-600">
                              Hạng {driver.licenseClass}
                            </span>
                          </div>
                          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-slate-500 mt-0.5">
                            <span>SĐT: {driver.phone || '—'}</span>
                            <span>GPLX: {driver.licenseNo}</span>
                          </div>
                        </div>
                      </div>

                      <div className="text-right shrink-0">
                        <div className="flex items-center justify-end gap-1 text-xs font-bold text-amber-600">
                          <Icon name="star" className="text-[15px] fill-current" />
                          <span>{Number(driver.ratingAvg || 5).toFixed(1)}</span>
                        </div>
                        <span
                          className={`inline-block mt-1 rounded-full px-2 py-0.5 text-[10px] font-bold ${
                            driver.activeBookingsCount > 0
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-emerald-100 text-emerald-800'
                          }`}
                        >
                          {driver.activeBookingsCount > 0
                            ? `${driver.activeBookingsCount} chuyến đang chạy`
                            : 'Đang rảnh'}
                        </span>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 border-t border-slate-100 bg-slate-50/50 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={loading || !selectedDriverId}
            className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-teal-800 transition disabled:opacity-50"
          >
            {loading && <Icon name="progress_activity" className="animate-spin text-[16px]" />}
            <span>{loading ? 'Đang phân công...' : 'Xác nhận phân công'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
