import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { driverStatusService } from '../../services/driverStatusService'
import { useRealtimeSync } from '../../hooks/useRealtimeSync'
import DriverStatusBadge from './DriverStatusBadge'
import { formatTripDateTime } from '../../utils/driverTripUtils'
import {
  DASHBOARD_MODE,
  buildDriverDashboardView,
  canConfirmAssignment,
  countdownSeconds,
  formatCountdown,
  inTripStatusLabel,
} from '../../utils/driverDashboardState'

function Icon({ name, className = '' }) {
  return (
    <span
      className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}
      aria-hidden="true"
    >
      {name}
    </span>
  )
}

const ACTIVITY_MODES = [
  {
    value: 'AVAILABLE',
    label: 'Sẵn sàng',
    description: 'Có thể nhận yêu cầu mới',
    icon: 'check_circle',
    activeContainer: 'border-emerald-600 bg-emerald-50/70 text-emerald-950 ring-2 ring-emerald-500/20 shadow-xs',
    activeIcon: 'text-emerald-600',
    activeDot: 'bg-emerald-500 ring-2 ring-emerald-200',
    inactiveContainer: 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50/70',
    inactiveIcon: 'text-slate-400',
  },
  {
    value: 'UNAVAILABLE',
    label: 'Tạm nghỉ',
    description: 'Không nhận yêu cầu mới',
    icon: 'pause_circle',
    activeContainer: 'border-amber-500 bg-amber-50/70 text-amber-950 ring-2 ring-amber-500/20 shadow-xs',
    activeIcon: 'text-amber-600',
    activeDot: 'bg-amber-500 ring-2 ring-amber-200',
    inactiveContainer: 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50/70',
    inactiveIcon: 'text-slate-400',
  },
  {
    value: 'OFFLINE',
    label: 'Ngoại tuyến',
    description: 'Không sẵn sàng làm việc',
    icon: 'power_settings_new',
    activeContainer: 'border-slate-600 bg-slate-100 text-slate-900 ring-2 ring-slate-400/20 shadow-xs',
    activeIcon: 'text-slate-700',
    activeDot: 'bg-slate-500 ring-2 ring-slate-200',
    inactiveContainer: 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50/70',
    inactiveIcon: 'text-slate-400',
  },
]

const MODE_LABEL = { AVAILABLE: 'Sẵn sàng', UNAVAILABLE: 'Tạm nghỉ', OFFLINE: 'Ngoại tuyến' }

function getOperationalDescription(operationalStatus) {
  switch (operationalStatus) {
    case 'ON_TRIP':
      return 'Bạn đang trong chuyến đi. Hệ thống tự động khóa chế độ nhận lịch cho tới khi kết thúc chuyến.'
    case 'SCHEDULED':
      return 'Bạn đã có lịch trình sắp tới. Vui lòng theo dõi giờ đón khách và chuẩn bị phương tiện.'
    case 'AVAILABLE':
      return 'Bạn đang sẵn sàng tiếp nhận các yêu cầu điều phối chuyến đi mới.'
    case 'UNAVAILABLE':
      return 'Đang tạm ngưng nhận cuốc mới. Các chuyến đã nhận trước đó vẫn được bảo toàn nguyên vẹn.'
    case 'OFFLINE':
      return 'Tài khoản đang ngoại tuyến. Hãy chuyển sang "Sẵn sàng" khi bạn bắt đầu ca làm việc.'
    default:
      return 'Đang đồng bộ trạng thái vận hành...'
  }
}

export default function DriverAvailability() {
  const [status, setStatus] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [successToast, setSuccessToast] = useState('')
  const [now, setNow] = useState(() => Date.now())
  const request = useRef(0)

  const refresh = useCallback(async () => {
    const id = ++request.current
    try {
      const data = await driverStatusService.getStatus()
      if (id === request.current) {
        setStatus(data)
        setError('')
      }
    } catch (err) {
      if (id === request.current) {
        setError(err?.response?.data?.message || err?.message || 'Không thể tải trạng thái làm việc')
      }
    }
  }, [])

  useEffect(() => {
    const timer = setTimeout(refresh, 0)
    const req = request
    return () => {
      clearTimeout(timer)
      req.current++
    }
  }, [refresh])

  useRealtimeSync({ onResync: refresh })

  const view = buildDriverDashboardView(status, now)
  const isInTrip = view.mode === DASHBOARD_MODE.IN_TRIP
  const expiredRequestRef = useRef(null)

  // Đồng hồ chỉ chạy khi có yêu cầu đang chờ. Mỗi giây chỉ đổi nhãn; khi tới hạn thì tải lại đúng một lần
  // cho mỗi yêu cầu (khóa theo assignmentExpiresAt), không lặp gọi API.
  useEffect(() => {
    const next = status?.nextTrip
    if (next?.status !== 'DRIVER_ASSIGNED') return undefined
    const expiresAt = next.assignmentExpiresAt
    const timer = setInterval(() => {
      const tick = Date.now()
      setNow(tick)
      if (countdownSeconds(expiresAt, tick) === 0 && expiredRequestRef.current !== expiresAt) {
        expiredRequestRef.current = expiresAt
        refresh()
      }
    }, 1000)
    return () => clearInterval(timer)
  }, [status, refresh])

  async function handleSelectMode(newMode) {
    if (saving || !status) return
    if (isInTrip || status.operationalStatus === 'ON_TRIP') return
    if (status.activityStatus === newMode) return

    setSaving(true)
    setError('')
    setSuccessToast('')
    ++request.current
    try {
      await driverStatusService.updateStatus(newMode)
      await refresh()
      setSuccessToast(`Đã chuyển sang chế độ "${MODE_LABEL[newMode] || newMode}"`)
      setTimeout(() => setSuccessToast(''), 3000)
    } catch (err) {
      await refresh()
      setError(err?.response?.data?.message || err?.message || 'Không thể cập nhật trạng thái làm việc. Vui lòng thử lại.')
    } finally {
      setSaving(false)
    }
  }

  const activeMode = status?.activityStatus || 'AVAILABLE'
  const operationalDesc = isInTrip
    ? 'Bạn đang thực hiện chuyến. Chế độ nhận lịch được ẩn cho tới khi kết thúc chuyến.'
    : getOperationalDescription(status?.operationalStatus)
  const badgeStatus = isInTrip ? 'ON_TRIP' : status?.operationalStatus

  return (
    <section
      className="mb-5 rounded-2xl border border-slate-200/90 bg-white p-4.5 sm:p-5 shadow-xs transition"
      aria-label="Trạng thái làm việc"
    >
      {/* VÙNG A: TRẠNG THÁI TỔNG QUAN */}
      <div className="flex flex-wrap items-start justify-between gap-3 pb-4 border-b border-slate-100">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700 shadow-2xs">
            <Icon name="work_history" className="text-[22px]" />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-sm font-bold tracking-tight text-slate-900">Trạng thái làm việc</h2>
              <DriverStatusBadge status={badgeStatus} />
            </div>
            <p className="mt-1 text-xs text-slate-600 leading-normal">{operationalDesc}</p>
          </div>
        </div>

        {saving ? (
          <div className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
            <Icon name="progress_activity" className="text-[14px] animate-spin text-brand-600" />
            <span>Đang lưu...</span>
          </div>
        ) : successToast ? (
          <div className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800 border border-emerald-200 animate-in fade-in duration-200">
            <Icon name="check" className="text-[14px] text-emerald-600" />
            <span>{successToast}</span>
          </div>
        ) : null}
      </div>

      {/* VÙNG B: CHẾ ĐỘ NHẬN LỊCH MỚI — ẩn hoàn toàn khi đang thực hiện chuyến */}
      {view.showAvailability && (
        <div className="pt-4 pb-4">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-2.5">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Chế độ nhận lịch mới</span>
            <span className="text-[11px] text-slate-400 hidden sm:inline">Chọn chế độ tiếp nhận yêu cầu phân công</span>
          </div>

          <div role="radiogroup" aria-label="Chế độ nhận lịch mới" className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
            {ACTIVITY_MODES.map((mode) => {
              const isSelected = activeMode === mode.value
              const isDisabled = !status || saving

              return (
                <button
                  key={mode.value}
                  type="button"
                  role="radio"
                  aria-checked={isSelected}
                  aria-label={`${mode.label}: ${mode.description}`}
                  disabled={isDisabled}
                  onClick={() => handleSelectMode(mode.value)}
                  className={`relative flex items-center sm:items-start gap-3 rounded-xl border p-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                    isSelected ? mode.activeContainer : mode.inactiveContainer
                  } ${isDisabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
                >
                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${isSelected ? 'bg-white shadow-2xs' : 'bg-slate-100'}`}>
                    <Icon name={mode.icon} className={`text-[20px] ${isSelected ? mode.activeIcon : mode.inactiveIcon}`} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-1">
                      <span className="text-xs font-bold leading-tight">{mode.label}</span>
                      {isSelected && <span className={`h-2 w-2 rounded-full ${mode.activeDot}`} aria-hidden="true" />}
                    </div>
                    <p className="mt-0.5 text-[11px] text-slate-500 leading-snug">{mode.description}</p>
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      )}

      {/* VÙNG C: CHUYẾN (theo ưu tiên: đang thực hiện > đã xác nhận > chờ xác nhận) */}
      <div className={`${view.showAvailability ? 'pt-3 border-t border-slate-100' : 'pt-1'}`}>
        {view.mode === DASHBOARD_MODE.IN_TRIP && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-3.5 space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                  <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
                </span>
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-900">Chuyến đang thực hiện</span>
                <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-emerald-800 border border-emerald-200">
                  {inTripStatusLabel(view.trip.status)}
                </span>
                <span className="font-mono text-xs font-bold text-slate-800 bg-white px-2 py-0.5 rounded border border-emerald-200">
                  #{view.trip.bookingCode}
                </span>
              </div>
              <Link
                to={`/driver/trips/${encodeURIComponent(view.trip.bookingCode)}`}
                className="text-xs font-bold text-emerald-700 hover:text-emerald-900 hover:underline flex items-center gap-1"
              >
                <span>Xem chi tiết chuyến</span>
                <Icon name="arrow_forward" className="text-[14px]" />
              </Link>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-1">
              <div className="flex items-start gap-1.5 text-slate-700">
                <Icon name="schedule" className="text-[16px] text-emerald-600 shrink-0 mt-0.5" />
                <div>
                  <span className="text-[10px] text-slate-400 uppercase font-bold block">Thời gian đón</span>
                  <span>{formatTripDateTime(view.trip.pickupTime)}</span>
                </div>
              </div>
              <div className="flex items-start gap-1.5 text-slate-700">
                <Icon name="navigation" className="text-[16px] text-emerald-600 shrink-0 mt-0.5" />
                <div className="min-w-0">
                  <span className="text-[10px] text-slate-400 uppercase font-bold block">Điểm đón khách</span>
                  <p className="truncate" title={view.trip.pickupAddress}>{view.trip.pickupAddress || 'Đang cập nhật'}</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {view.mode === DASHBOARD_MODE.PENDING_CONFIRMATION && (
          <div className="rounded-xl border border-amber-200 bg-amber-50/50 p-3.5 space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-bold text-amber-950">Yêu cầu chuyến đi</span>
                <span className="font-mono text-xs font-bold text-slate-800 bg-white px-2 py-0.5 rounded border border-amber-200">
                  #{view.trip.bookingCode}
                </span>
                <span className="inline-flex items-center gap-1 rounded-full bg-white px-2.5 py-0.5 text-[11px] font-bold text-amber-800 border border-amber-200">
                  Chờ xác nhận · còn {formatCountdown(view.countdown)}
                </span>
              </div>
              {canConfirmAssignment(view) && (
                <Link
                  to={`/driver/trips/${encodeURIComponent(view.trip.bookingCode)}`}
                  className="rounded-lg bg-brand-700 px-2.5 py-1 text-[11px] font-bold text-white hover:bg-brand-800 transition shadow-2xs"
                >
                  Mở để xác nhận
                </Link>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-1">
              <div className="flex items-start gap-1.5 text-slate-700">
                <Icon name="schedule" className="text-[16px] text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <span className="text-[10px] text-slate-400 uppercase font-bold block">Thời gian đón</span>
                  <span>{formatTripDateTime(view.trip.pickupTime)}</span>
                </div>
              </div>
              <div className="flex items-start gap-1.5 text-slate-700">
                <Icon name="pin_drop" className="text-[16px] text-amber-600 shrink-0 mt-0.5" />
                <div className="min-w-0">
                  <span className="text-[10px] text-slate-400 uppercase font-bold block">Địa điểm đón</span>
                  <p className="truncate" title={view.trip.pickupAddress}>{view.trip.pickupAddress || 'Đang cập nhật'}</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {view.mode === DASHBOARD_MODE.UPCOMING_CONFIRMED && (
          <div className="rounded-xl border border-blue-100 bg-blue-50/40 p-3.5 space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-bold text-blue-950">Chuyến sắp tới</span>
                <span className="font-mono text-xs font-bold text-slate-800 bg-white px-2 py-0.5 rounded border border-blue-200">
                  #{view.trip.bookingCode}
                </span>
                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-800 border border-emerald-200">
                  <Icon name="check" className="text-[13px]" />
                  <span>{view.trip.status === 'DRIVER_ARRIVED' ? 'Đã đến điểm đón' : 'Đã xác nhận'}</span>
                </span>
              </div>
              <Link
                to={`/driver/trips/${encodeURIComponent(view.trip.bookingCode)}`}
                className="text-xs font-bold text-brand-800 hover:underline flex items-center gap-1"
              >
                <span>Xem chi tiết chuyến</span>
                <Icon name="arrow_forward" className="text-[14px]" />
              </Link>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-1">
              <div className="flex items-start gap-1.5 text-slate-700">
                <Icon name="schedule" className="text-[16px] text-blue-600 shrink-0 mt-0.5" />
                <div>
                  <span className="text-[10px] text-slate-400 uppercase font-bold block">Thời gian đón</span>
                  <span>{formatTripDateTime(view.trip.pickupTime)}</span>
                </div>
              </div>
              <div className="flex items-start gap-1.5 text-slate-700">
                <Icon name="pin_drop" className="text-[16px] text-blue-600 shrink-0 mt-0.5" />
                <div className="min-w-0">
                  <span className="text-[10px] text-slate-400 uppercase font-bold block">Địa điểm đón</span>
                  <p className="truncate" title={view.trip.pickupAddress}>{view.trip.pickupAddress || 'Đang cập nhật'}</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {view.mode === DASHBOARD_MODE.EMPTY && (
          <div className="flex items-center gap-2.5 text-xs text-slate-500 py-1">
            <Icon name="event_available" className="text-[18px] text-slate-400 shrink-0" />
            <span>Hiện tại bạn chưa có chuyến đi nào được lên lịch. Khi có phân công mới, thông tin cuốc xe sẽ hiển thị tại đây.</span>
          </div>
        )}
      </div>

      {error && (
        <div role="alert" className="mt-3.5 text-xs text-rose-800 bg-rose-50 p-3 rounded-xl border border-rose-200 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Icon name="error" className="text-[18px] text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button type="button" onClick={refresh} className="text-xs font-bold text-rose-700 hover:text-rose-900 underline shrink-0">
            Thử lại
          </button>
        </div>
      )}
    </section>
  )
}
