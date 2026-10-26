import { useState, useEffect, useCallback } from 'react'
import { adminService } from '../../services/adminService'
import {
  IconCar,
  IconSearch,
  IconX,
  IconEye,
  IconTag,
} from '../../components/icons/Icons'
import VehicleDetailModal from './modals/VehicleDetailModal'

const STATUS_BADGES = {
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
  BLOCKED: 'bg-slate-100 text-slate-700 border-slate-300',
  MAINTENANCE: 'bg-blue-50 text-blue-700 border-blue-200',
}

const DOC_TYPE_LABELS = {
  REGISTRATION: 'Đăng ký xe (Cà-vẹt)',
  INSPECTION: 'Giấy chứng nhận Đăng kiểm',
  INSURANCE: 'Bảo hiểm xe bắt buộc',
  OTHER: 'Giấy tờ pháp lý khác',
}

export default function AdminVehiclesTab({ initialStatus = 'ALL', onStatsUpdate }) {
  const [vehicles, setVehicles] = useState([])
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [message, setMessage] = useState(null)

  const [statusFilter, setStatusFilter] = useState(initialStatus)
  const [keyword, setKeyword] = useState('')

  // Detail Modal
  const [selectedVehicle, setSelectedVehicle] = useState(null)
  const [rejectReason, setRejectReason] = useState('')
  const [showRejectInput, setShowRejectInput] = useState(false)
  const [previewDocUrl, setPreviewDocUrl] = useState(null)

  const loadVehicles = useCallback(async () => {
    setLoading(true)
    setMessage(null)
    try {
      const data = await adminService.getVehicles({ status: statusFilter, keyword })
      setVehicles(data)
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không thể tải danh sách xe' })
    } finally {
      setLoading(false)
    }
  }, [statusFilter, keyword])

  useEffect(() => {
    queueMicrotask(loadVehicles)
  }, [loadVehicles])

  function handleSearchSubmit(e) {
    e.preventDefault()
    loadVehicles()
  }

  async function openDetailModal(vehicleSummary) {
    setSelectedVehicle(null)
    setShowRejectInput(false)
    setRejectReason('')
    try {
      const fullDetail = await adminService.getVehicleDetail(vehicleSummary.id)
      setSelectedVehicle(fullDetail)
    } catch (err) {
      alert('Không thể tải chi tiết xe: ' + err.message)
    }
  }

  async function handleApprove(vehicleId) {
    setActionLoading(true)
    try {
      await adminService.updateVehicleApproval(vehicleId, 'APPROVED')
      setMessage({ type: 'success', text: 'Đã phê duyệt xe mở bán thành công!' })
      setSelectedVehicle(null)
      loadVehicles()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi phê duyệt xe' })
    } finally {
      setActionLoading(false)
    }
  }

  async function handleReject(vehicleId, customReason) {
    const finalReason = customReason || rejectReason
    if (!finalReason || !finalReason.trim()) {
      alert('Vui lòng nhập lý do từ chối duyệt xe.')
      return
    }
    setActionLoading(true)
    try {
      await adminService.updateVehicleApproval(vehicleId, 'REJECTED', finalReason.trim())
      setMessage({ type: 'success', text: 'Đã từ chối xe và gửi thông báo tới chủ xe.' })
      setSelectedVehicle(null)
      loadVehicles()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi từ chối xe' })
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header & Filter Bar */}
      <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-card md:flex-row md:items-center md:justify-between">
        <form onSubmit={handleSearchSubmit} className="relative flex-1 max-w-md">
          <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
            <IconSearch className="h-4 w-4" />
          </span>
          <input
            type="text"
            placeholder="Tìm theo biển số, hiệu xe, model..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-3 text-xs outline-none focus:border-brand-500 focus:bg-white"
          />
        </form>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-slate-500">Trạng thái:</span>
          {['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'BLOCKED'].map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => setStatusFilter(st)}
              className={`rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                statusFilter === st
                  ? 'bg-brand-600 text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {st === 'ALL' ? 'Tất cả' : st}
            </button>
          ))}

          <button
            type="button"
            onClick={loadVehicles}
            className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Làm mới
          </button>
        </div>
      </div>

      {/* Inline Feedback Message */}
      {message && (
        <div
          className={`flex items-center justify-between gap-3 rounded-xl border p-4 text-xs font-medium ${
            message.type === 'error'
              ? 'border-red-200 bg-red-50 text-red-800'
              : 'border-emerald-200 bg-emerald-50 text-emerald-800'
          }`}
        >
          <span>{message.text}</span>
          <button type="button" onClick={() => setMessage(null)} className="p-1 hover:opacity-75">
            <IconX className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Notice Banner */}
      <div className="flex items-center gap-3 rounded-xl border border-brand-200 bg-brand-50/50 px-4 py-3 text-xs text-brand-900">
        <IconTag className="h-4 w-4 shrink-0 text-brand-600" />
        <span>
          <strong>Quy tắc A03:</strong> Xe được phê duyệt mở bán hoàn toàn độc lập với việc gán tài xế. Nhà cung cấp có thể phân công tài xế sau khi xe đã được kiểm định và duyệt.
        </span>
      </div>

      {/* Vehicles Grid */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {vehicles.length === 0 ? (
          <div className="col-span-3 rounded-2xl border border-slate-200 bg-white p-12 text-center text-slate-400">
            {loading ? 'Đang tải danh sách xe...' : 'Không tìm thấy xe nào'}
          </div>
        ) : (
          vehicles.map((v) => (
            <div
              key={v.id}
              className="flex flex-col justify-between overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card transition-all hover:shadow-md"
            >
              <div>
                {/* Photo Header */}
                <div className="relative aspect-16/10 w-full overflow-hidden bg-slate-100">
                  {v.mainImageUrl ? (
                    <img
                      src={v.mainImageUrl}
                      alt={`${v.brand} ${v.model}`}
                      className="h-full w-full object-cover"
                      onError={(e) => { e.target.style.display = 'none' }}
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-slate-400">
                      <IconCar className="h-10 w-10 stroke-1" />
                    </div>
                  )}

                  <span
                    className={`absolute right-3 top-3 rounded-full border px-2.5 py-0.5 text-[10px] font-extrabold tracking-wider shadow-xs ${
                      STATUS_BADGES[v.status] || 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {v.status}
                  </span>

                  <span className="absolute bottom-2 left-2 rounded-md bg-black/75 px-2 py-0.5 text-[11px] font-mono font-bold tracking-wider text-white">
                    {v.licensePlate}
                  </span>
                </div>

                {/* Body Content */}
                <div className="p-5 space-y-2.5">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h4 className="text-base font-bold text-slate-900">
                        {v.brand} {v.model}
                      </h4>
                      <p className="text-xs text-slate-500">
                        Năm SX: {v.manufactureYear || '—'} • {v.seatCount} chỗ • {v.fuelType || 'EV'}
                      </p>
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-1.5 text-[11px] text-slate-600">
                    <span className="rounded-md bg-slate-100 px-2 py-0.5">
                      Chủ xe #{v.providerId}
                    </span>
                    <span className="rounded-md bg-slate-100 px-2 py-0.5">
                      {v.transmission || 'Tự động'}
                    </span>
                    <span className="rounded-md bg-slate-100 px-2 py-0.5">
                      Màu {v.color || '—'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Action Button */}
              <div className="border-t border-slate-100 p-4">
                <button
                  type="button"
                  onClick={() => openDetailModal(v)}
                  className="w-full rounded-xl bg-slate-900 py-2.5 text-xs font-bold text-white shadow-xs transition-all hover:bg-brand-600 active:scale-98"
                >
                  Kiểm định &amp; Duyệt hồ sơ
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Vehicle Detail Inspection Modal */}
      {selectedVehicle && (
        <VehicleDetailModal
          vehicle={selectedVehicle}
          onClose={() => setSelectedVehicle(null)}
          onApprove={(id) => handleApprove(id)}
          onReject={(id, reason) => handleReject(id, reason)}
          actionLoading={actionLoading}
          onPreviewDoc={(url) => setPreviewDocUrl(url)}
        />
      )}

      {/* Document / Photo Preview Modal */}
      {previewDocUrl && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center bg-black/80 p-4"
          onClick={() => setPreviewDocUrl(null)}
        >
          <div className="relative max-h-[90vh] max-w-[90vw] overflow-hidden rounded-2xl bg-white p-2">
            <button
              type="button"
              onClick={() => setPreviewDocUrl(null)}
              className="absolute right-3 top-3 rounded-full bg-black/70 p-2 text-white hover:bg-black"
            >
              <IconX className="h-5 w-5" />
            </button>
            <img src={previewDocUrl} alt="Chi tiết giấy tờ" className="max-h-[85vh] max-w-[85vw] object-contain" />
          </div>
        </div>
      )}
    </div>
  )
}
