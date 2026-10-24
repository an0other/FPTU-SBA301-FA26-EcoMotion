import { useState } from 'react'
import { driverTripService } from '../../services/driverTripService'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

const INCIDENT_TYPES = [
  { value: 'TRAFFIC_JAM', label: 'Ùn tắc giao thông nghiêm trọng', icon: 'traffic' },
  { value: 'VEHICLE_BREAKDOWN', label: 'Sự cố xe hỏng hóc / Lốp / Ắc quy', icon: 'car_repair' },
  { value: 'LOST_CONTACT_WITH_CUSTOMER', label: 'Không liên lạc được với khách', icon: 'phone_disabled' },
  { value: 'CANNOT_REACH_PICKUP', label: 'Không thể tiếp cận điểm đón', icon: 'wrong_location' },
  { value: 'ACCIDENT', label: 'Va chạm / Tai nạn giao thông', icon: 'car_crash' },
  { value: 'HEALTH_ISSUE', label: 'Vấn đề sức khỏe đột xuất', icon: 'medical_services' },
  { value: 'OTHER', label: 'Sự cố phát sinh khác', icon: 'warning' },
]

const URGENCY_LEVELS = [
  { value: 'LOW', label: 'Thấp (Chậm nhẹ)', color: 'border-blue-300 text-blue-800' },
  { value: 'MEDIUM', label: 'Trung bình (Cần hỗ trợ)', color: 'border-amber-300 text-amber-800' },
  { value: 'HIGH', label: 'Cao (Chuyến xe gián đoạn)', color: 'border-orange-400 text-orange-900 font-bold' },
  { value: 'CRITICAL', label: 'Khẩn cấp (Cần ứng cứu ngay)', color: 'border-red-500 text-red-900 font-bold bg-red-50' },
]

export default function DriverIncidentReportModal({ trip, currentCoords, isOpen, onClose, onSuccess }) {
  const [incidentType, setIncidentType] = useState('TRAFFIC_JAM')
  const [urgency, setUrgency] = useState('MEDIUM')
  const [description, setDescription] = useState('')
  const [evidenceUrls, setEvidenceUrls] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  if (!isOpen || !trip) return null

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!description.trim()) {
      setError('Vui lòng mô tả chi tiết sự cố.')
      return
    }

    setLoading(true)
    setError(null)
    try {
      const tripId = trip.tripId || trip.bookingCode
      const payload = {
        incidentType,
        urgency,
        description: description.trim(),
        evidenceUrls: evidenceUrls.trim() || undefined,
        latitude: currentCoords?.latitude || undefined,
        longitude: currentCoords?.longitude || undefined,
      }

      await driverTripService.reportIncident(tripId, payload)
      onSuccess?.('Đã gửi báo cáo sự cố thành công! Chủ xe và điều phối đã nhận được cảnh báo.')
      onClose()
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Không thể gửi báo cáo sự cố. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div className="w-full max-w-lg rounded-lg bg-white p-5 shadow-md border border-slate-200 max-h-[92vh] overflow-y-auto">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-3">
          <div className="flex items-center gap-2 text-red-600">
            <Icon name="report_problem" className="text-2xl" />
            <h3 className="text-base font-bold text-slate-900">Báo cáo sự cố khẩn cấp</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <Icon name="close" className="text-lg" />
          </button>
        </div>

        <p className="text-xs text-slate-500 mb-3">
          Đơn #{trip.bookingCode}. Thông tin sự cố sẽ gửi cảnh báo tức thì về màn hình quản trị của Nhà xe.
        </p>

        {error && (
          <div className="mb-3 rounded-lg bg-red-50 p-2.5 text-xs text-red-800 border border-red-200">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3.5">
          {/* Incident Type Grid */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              Phân loại sự cố <span className="text-red-500">*</span>
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
              {INCIDENT_TYPES.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  onClick={() => setIncidentType(t.value)}
                  className={`flex items-center gap-2 p-2 rounded-xl border text-xs text-left transition ${
                    incidentType === t.value
                      ? 'border-red-500 bg-red-50 text-red-950 font-bold ring-1 ring-red-400'
                      : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <Icon name={t.icon} className="text-[16px] text-red-600 shrink-0" />
                  <span className="truncate">{t.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Urgency */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              Mức độ nghiêm trọng <span className="text-red-500">*</span>
            </label>
            <div className="grid grid-cols-2 gap-1.5">
              {URGENCY_LEVELS.map((u) => (
                <button
                  key={u.value}
                  type="button"
                  onClick={() => setUrgency(u.value)}
                  className={`p-2 rounded-xl border text-xs font-semibold text-center transition ${u.color} ${
                    urgency === u.value ? 'ring-2 ring-red-500 shadow-2xs' : 'opacity-70 hover:opacity-100'
                  }`}
                >
                  {u.label}
                </button>
              ))}
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Mô tả chi tiết tình huống <span className="text-red-500">*</span>
            </label>
            <textarea
              rows="3"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Nêu rõ địa điểm hiện tại, tình trạng xe hoặc khách hàng, sự hỗ trợ cần thiết..."
              className="w-full rounded-xl border border-slate-200 p-2.5 text-xs text-slate-800 placeholder-slate-400 focus:border-red-500 focus:outline-none"
              required
            />
          </div>

          {/* Evidence URL */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Đường dẫn ảnh hiện trường / biên bản (tùy chọn)
            </label>
            <input
              type="url"
              value={evidenceUrls}
              onChange={(e) => setEvidenceUrls(e.target.value)}
              placeholder="https://..."
              className="w-full rounded-xl border border-slate-200 p-2 text-xs text-slate-800 placeholder-slate-400 focus:border-red-500 focus:outline-none"
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-xs font-bold text-white hover:bg-red-700 transition disabled:opacity-50"
            >
              {loading && <Icon name="sync" className="animate-spin text-[16px]" />}
              <span>{loading ? 'Đang gửi...' : 'Gửi báo cáo sự cố'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
