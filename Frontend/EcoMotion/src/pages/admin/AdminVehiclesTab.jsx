import { useState, useEffect } from 'react'
import { adminService } from '../../services/adminService'
import {
  IconCar,
  IconSearch,
  IconCheckCircle,
  IconAlertTriangle,
  IconX,
  IconEye,
  IconTag,
  IconImage,
} from '../../components/icons/Icons'

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

  useEffect(() => {
    loadVehicles()
  }, [statusFilter])

  async function loadVehicles() {
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
  }

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

  async function handleReject(vehicleId) {
    if (!rejectReason.trim()) {
      alert('Vui lòng nhập lý do từ chối duyệt xe.')
      return
    }
    setActionLoading(true)
    try {
      await adminService.updateVehicleApproval(vehicleId, 'REJECTED', rejectReason.trim())
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

      {/* Vehicle Inspection & Approval Modal */}
      {selectedVehicle && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="relative max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
            {/* Modal Header */}
            <div className="flex items-start justify-between border-b border-slate-100 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-xl font-bold text-slate-900">
                    {selectedVehicle.brand} {selectedVehicle.model} ({selectedVehicle.manufactureYear})
                  </h3>
                  <span
                    className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                      STATUS_BADGES[selectedVehicle.status] || 'bg-slate-100'
                    }`}
                  >
                    {selectedVehicle.status}
                  </span>
                </div>
                <p className="mt-1 font-mono text-xs font-bold text-brand-700">
                  Biển kiểm soát: {selectedVehicle.licensePlate}
                </p>
              </div>

              <button
                type="button"
                onClick={() => setSelectedVehicle(null)}
                className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <IconX className="h-5 w-5" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="mt-6 space-y-6">
              {/* Specs Grid */}
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                  Thông số kỹ thuật &amp; Pháp lý xe
                </h4>
                <div className="grid grid-cols-2 gap-3 rounded-2xl border border-slate-100 bg-slate-50/70 p-4 text-xs sm:grid-cols-4">
                  <div>
                    <span className="text-slate-500 block">Số chỗ:</span>
                    <strong className="text-slate-900">{selectedVehicle.seatCount} chỗ</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Nhiên liệu:</span>
                    <strong className="text-slate-900">{selectedVehicle.fuelType || 'Điện (EV)'}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Hộp số:</span>
                    <strong className="text-slate-900">{selectedVehicle.transmission || 'Tự động'}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Màu sắc:</span>
                    <strong className="text-slate-900">{selectedVehicle.color || '—'}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Số khung:</span>
                    <strong className="text-slate-900 font-mono text-[11px]">{selectedVehicle.chassisNo || '—'}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Số máy:</span>
                    <strong className="text-slate-900 font-mono text-[11px]">{selectedVehicle.engineNo || '—'}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Chủ xe ID:</span>
                    <strong className="text-slate-900">NCC #{selectedVehicle.providerId}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Điểm đánh giá:</span>
                    <strong className="text-slate-900">
                      ★ {selectedVehicle.ratingAvg || '5.0'} ({selectedVehicle.ratingCount || 0} đánh giá)
                    </strong>
                  </div>
                </div>
              </div>

              {/* Legal Documents Review */}
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                  Giấy tờ pháp lý bắt buộc ({selectedVehicle.documents?.length || 0} tài liệu)
                </h4>

                {(!selectedVehicle.documents || selectedVehicle.documents.length === 0) ? (
                  <div className="rounded-xl border border-dashed border-slate-200 p-4 text-center text-xs text-slate-400">
                    Chủ xe chưa tải lên giấy tờ xe (Đăng ký, Đăng kiểm, Bảo hiểm).
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    {selectedVehicle.documents.map((doc) => (
                      <div
                        key={doc.id}
                        className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-xs"
                      >
                        <div className="space-y-1">
                          <span className="block text-[11px] font-bold text-brand-700">
                            {DOC_TYPE_LABELS[doc.docType] || doc.docType}
                          </span>
                          <div className="text-xs text-slate-800">
                            <strong>Số hiệu:</strong> {doc.docNumber || '—'}
                          </div>
                          <div className="text-[11px] text-slate-500">
                            Hạn: {doc.expiryDate || 'Vô thời hạn'}
                          </div>
                          <span
                            className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-bold ${
                              STATUS_BADGES[doc.status] || 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {doc.status}
                          </span>
                        </div>

                        {doc.fileUrl && (
                          <button
                            type="button"
                            onClick={() => setPreviewDocUrl(doc.fileUrl)}
                            className="mt-3 flex items-center justify-center gap-1 rounded-lg border border-slate-200 py-1.5 text-[11px] font-semibold text-slate-700 hover:bg-slate-50"
                          >
                            <IconEye className="h-3.5 w-3.5" />
                            <span>Xem tài liệu</span>
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Reject Reason Form (if toggled) */}
              {showRejectInput && (
                <div className="rounded-2xl border border-rose-200 bg-rose-50/50 p-4 space-y-3">
                  <label className="block text-xs font-bold text-rose-900">
                    Nhập lý do từ chối duyệt xe <span className="text-red-500">*</span>
                  </label>
                  <textarea
                    rows="3"
                    placeholder="Ví dụ: Giấy đăng kiểm đã hết hạn, ảnh xe mờ không rõ biển số..."
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    className="w-full rounded-xl border border-rose-300 bg-white p-3 text-xs outline-none focus:ring-2 focus:ring-rose-500"
                  />
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => setShowRejectInput(false)}
                      className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      Hủy bỏ
                    </button>
                    <button
                      type="button"
                      disabled={actionLoading}
                      onClick={() => handleReject(selectedVehicle.id)}
                      className="rounded-xl bg-rose-600 px-4 py-1.5 text-xs font-bold text-white hover:bg-rose-700"
                    >
                      {actionLoading ? 'Đang gửi...' : 'Xác nhận từ chối xe'}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Modal Actions */}
            <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
              <span className="text-[11px] text-slate-400">
                Lưu ý: Phê duyệt xe không đòi hỏi phải gán tài xế ngay lập tức.
              </span>

              <div className="flex items-center gap-2">
                {!showRejectInput && (
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={() => setShowRejectInput(true)}
                    className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-xs font-bold text-rose-700 hover:bg-rose-100"
                  >
                    Từ chối xe
                  </button>
                )}

                <button
                  type="button"
                  disabled={actionLoading}
                  onClick={() => handleApprove(selectedVehicle.id)}
                  className="rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 active:scale-98"
                >
                  {actionLoading ? 'Đang xử lý...' : 'Phê duyệt xe mở bán'}
                </button>
              </div>
            </div>
          </div>
        </div>
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
