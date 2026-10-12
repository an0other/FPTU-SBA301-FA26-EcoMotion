import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BookingStatusBadge from '../components/provider/BookingStatusBadge'
import ProviderLayout from '../components/provider/ProviderLayout'
import { useProviderBookings } from '../hooks/useProviderBookings'

const FILTERS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'ACCEPTED', label: 'Đã chấp nhận' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'IN_PROGRESS', label: 'Đang chạy' },
  { value: 'COMPLETED', label: 'Hoàn tất' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'CANCELLED', label: 'Đã huỷ' },
]

// Row accent per status: left bar, row tint, dimming, strike-through of time/amount.
const ROW_STYLE = {
  PENDING: { bar: 'bg-amber-500', row: 'bg-amber-500/[0.03]' },
  IN_PROGRESS: { bar: 'bg-primary', row: 'bg-primary-fixed/10' },
  COMPLETED: { row: 'opacity-85' },
  REJECTED: { row: 'opacity-75', strike: true },
  CANCELLED: { row: 'opacity-75', strike: true },
}

const ACTION_LABEL = { PENDING: 'Xem & duyệt', IN_PROGRESS: 'Giám sát' }

const AVATAR_COLORS = [
  'bg-surface-container-high text-primary',
  'bg-secondary-fixed text-secondary',
  'bg-tertiary-fixed text-tertiary',
  'bg-primary-fixed text-primary',
  'bg-surface-container-highest text-on-surface-variant',
]

const pad = (n) => String(n).padStart(2, '0')

// HH:mm dd/MM/yyyy (24h)
function formatDateTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return `${pad(d.getHours())}:${pad(d.getMinutes())} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`
}

// 1.250.000 ₫
function formatVnd(amount) {
  const value = Number(amount)
  if (amount === null || amount === undefined || Number.isNaN(value)) return '—'
  return `${new Intl.NumberFormat('vi-VN').format(value)} ₫`
}

function initialsOf(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

function avatarColor(name) {
  let hash = 0
  for (const ch of name || '') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

const TH = 'px-4 py-3.5 font-semibold'
const TD = 'px-4 py-4 align-middle'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

function BookingRow({ booking, onOpen }) {
  const [copied, setCopied] = useState(false)
  const style = ROW_STYLE[booking.status] ?? {}

  async function copyCode(event) {
    event.stopPropagation()
    try {
      await navigator.clipboard.writeText(booking.bookingCode)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // clipboard unavailable (e.g. insecure context): ignore silently
    }
  }

  const live = booking.status === 'IN_PROGRESS'

  return (
    <tr
      tabIndex={0}
      onClick={() => onOpen(booking.bookingCode)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') onOpen(booking.bookingCode)
      }}
      className={`cursor-pointer transition hover:bg-surface-container-low/50 focus:bg-surface-container-low/50 focus:outline-none ${style.row ?? ''}`}
    >
      <td className={TD}>
        <div className="flex items-center gap-1.5">
          {style.bar && <span className={`mr-1 h-8 w-1.5 shrink-0 rounded-full ${style.bar}`} />}
          <span className="font-mono-badge rounded bg-surface-container px-2 py-0.5 text-xs font-semibold uppercase">
            {booking.bookingCode}
          </span>
          <button
            type="button"
            onClick={copyCode}
            title="Sao chép mã"
            className="p-0.5 text-outline transition hover:text-on-surface"
          >
            <Icon name={copied ? 'check' : 'content_copy'} className="text-[16px]" />
          </button>
        </div>
      </td>
      <td className={TD}>
        <div className="flex items-center gap-3">
          <span
            className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[13px] font-semibold ${avatarColor(booking.renterName)}`}
          >
            {initialsOf(booking.renterName)}
          </span>
          <span className="min-w-0 truncate text-[15px] font-semibold">{booking.renterName}</span>
        </div>
      </td>
      <td className={TD}>
        <div className="flex items-center gap-2.5">
          {booking.vehicleImageUrl ? (
            <img
              src={booking.vehicleImageUrl}
              alt={booking.vehicleName}
              className="h-7 w-10 shrink-0 rounded object-cover shadow-xs"
            />
          ) : (
            <div className="flex h-7 w-10 shrink-0 items-center justify-center rounded bg-surface-container text-on-surface-variant">
              <Icon name="directions_car" className="text-[16px]" />
            </div>
          )}
          <div>
            <div className="text-[13px] font-semibold">{booking.vehicleName}</div>
            <span className="font-mono-badge mt-0.5 inline-block rounded bg-surface-container px-1.5 py-0.5 text-[11px] font-semibold text-on-surface-variant">
              {booking.licensePlate}
            </span>
          </div>
        </div>
      </td>
      <td className={`${TD} max-w-xs`}>
        <div className="flex items-start gap-1.5">
          <Icon
            name={live ? 'near_me' : style.strike ? 'block' : 'schedule'}
            className={`mt-0.5 shrink-0 text-[16px] ${live ? 'text-primary' : 'text-outline'}`}
          />
          <span
            className={`text-[13px] font-semibold tabular-nums ${
              live ? 'text-primary' : style.strike ? 'font-medium text-outline line-through' : ''
            }`}
          >
            {formatDateTime(booking.pickupTime)}
          </span>
        </div>
        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-on-surface-variant">
          <Icon name="location_on" className="shrink-0 text-[15px] text-outline" />
          <span className="truncate" title={booking.pickupAddress}>
            {booking.pickupAddress}
          </span>
        </div>
      </td>
      <td
        className={`${TD} text-right text-base font-semibold tabular-nums ${
          style.strike ? 'text-outline line-through' : ''
        }`}
      >
        {formatVnd(booking.totalAmount)}
      </td>
      <td className={TD}>
        <BookingStatusBadge status={booking.status} />
      </td>
      <td className={`${TD} text-center`}>
        <Link
          to={`/provider/bookings/${booking.bookingCode}`}
          onClick={(event) => event.stopPropagation()}
          className={`inline-flex h-8 items-center gap-1 rounded-lg px-3 text-[13px] font-semibold transition hover:bg-surface-container ${
            booking.status === 'PENDING' ? 'bg-primary text-on-primary hover:!bg-primary-container' : 'text-on-surface-variant hover:text-on-surface'
          } ${live ? '!text-primary' : ''}`}
        >
          {ACTION_LABEL[booking.status] ?? 'Chi tiết'}
          <Icon name="arrow_forward" className="text-[16px]" />
        </Link>
      </td>
    </tr>
  )
}

function SkeletonRows() {
  return Array.from({ length: 5 }).map((_, index) => (
    <tr key={index} className="animate-pulse">
      {Array.from({ length: 6 }).map((__, cell) => (
        <td key={cell} className={TD}>
          <div className="h-4 rounded bg-surface-container-high" />
        </td>
      ))}
      <td className={TD}>
        <div className="mx-auto h-4 w-16 rounded bg-surface-container-high" />
      </td>
    </tr>
  ))
}

export default function ProviderBookingQueuePage() {
  const [status, setStatus] = useState('ALL')
  const { bookings, loading, error } = useProviderBookings(status)
  const navigate = useNavigate()

  const openBooking = (code) => navigate(`/provider/bookings/${code}`)

  return (
    <ProviderLayout active="Đơn thuê">
      <div className="mb-7 flex flex-col">
        <div className="mb-1.5 flex items-center gap-2 text-[13px] font-medium text-on-surface-variant">
          <span>Quản lý kinh doanh</span>
          <Icon name="chevron_right" className="text-[16px] text-outline" />
          <span className="font-semibold text-primary">Đơn thuê</span>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-[28px] font-semibold leading-9 tracking-tight">Đơn thuê</h1>
          {!loading && !error && (
            <span className="flex items-center gap-1.5 rounded-full bg-surface-container-high px-2.5 py-0.5 text-[11px] font-semibold text-primary">
              <span className="h-2 w-2 animate-pulse rounded-full bg-primary" />
              {bookings.length} đơn
            </span>
          )}
        </div>
        <p className="mt-1 text-sm font-medium text-on-surface-variant">
          Duyệt và quản lý các yêu cầu thuê xe theo thời gian thực
        </p>
      </div>

      <div className="mb-5 rounded-xl bg-surface-container-lowest p-3.5 shadow-sm">
        <div
          className="flex items-center gap-1.5 overflow-x-auto text-on-surface-variant"
          role="tablist"
          aria-label="Lọc theo trạng thái"
        >
          {FILTERS.map((filter) => {
            const active = filter.value === status
            return (
              <button
                key={filter.value}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setStatus(filter.value)}
                className={`shrink-0 rounded-lg px-3.5 py-1.5 text-sm font-medium transition ${
                  active
                    ? 'bg-primary font-semibold text-on-primary shadow-sm'
                    : 'hover:bg-surface-container hover:text-on-surface'
                }`}
              >
                {filter.label}
              </button>
            )
          })}
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-error-container bg-error-container/40 p-6 text-center">
          <p className="text-[14px] font-semibold text-on-error-container">{error}</p>
        </div>
      )}

      {!error && !loading && bookings.length === 0 && (
        <div className="rounded-xl bg-surface-container-lowest p-12 text-center shadow-sm">
          <Icon name="local_taxi" className="mb-2 text-[40px] text-outline" />
          <p className="text-[15px] font-semibold">Chưa có đơn thuê nào</p>
          <p className="mt-1 text-[13px] text-on-surface-variant">
            {status === 'ALL'
              ? 'Khi khách đặt xe của bạn, đơn thuê sẽ xuất hiện ở đây.'
              : 'Không có đơn thuê nào ở trạng thái này.'}
          </p>
        </div>
      )}

      {!error && (loading || bookings.length > 0) && (
        <div className="mb-5 overflow-hidden rounded-xl bg-surface-container-lowest shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] border-collapse text-left text-sm font-medium">
              <thead>
                <tr className="select-none bg-surface-container-low/70 text-[11px] uppercase tracking-wider text-on-surface-variant">
                  <th className={TH}>Mã đơn</th>
                  <th className={TH}>Khách thuê</th>
                  <th className={TH}>Xe &amp; Biển số</th>
                  <th className={TH}>Giờ &amp; Điểm đón</th>
                  <th className={`${TH} text-right`}>Tổng tiền</th>
                  <th className={TH}>Trạng thái</th>
                  <th className={`${TH} text-center`}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {loading && <SkeletonRows />}
                {!loading &&
                  bookings.map((booking) => (
                    <BookingRow key={booking.bookingCode} booking={booking} onOpen={openBooking} />
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </ProviderLayout>
  )
}
