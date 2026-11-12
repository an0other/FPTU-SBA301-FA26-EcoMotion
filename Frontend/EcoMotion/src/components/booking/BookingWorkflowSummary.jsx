import { useState } from 'react'
import { Link } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { REFUND_WORKFLOW_LABELS, shouldShowRefundDetailsList, visibleProposal } from '../../utils/bookingWorkflow'
import { bookingWorkflowService } from '../../services/bookingWorkflowService'
import BookingProgressStepper from './BookingProgressStepper'
import DisputeHistory from './DisputeHistory'
import RefundReceiptTimeline from './RefundReceiptTimeline'

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

function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return 'Chưa xác định'
  const totalHours = Math.round(ms / 3600000)
  const days = Math.floor(totalHours / 24)
  const hours = totalHours % 24
  return [days ? `${days} ngày` : '', hours ? `${hours} giờ` : ''].filter(Boolean).join(' ') || '0 giờ'
}

// Lịch hiện tại vs lịch đề xuất. Giờ trả mới luôn = giờ nhận mới + đúng thời lượng thuê hiện tại.
function buildComparison(proposal, booking) {
  const start = booking?.pickupTime ? new Date(booking.pickupTime) : null
  const endRaw = booking?.plannedEndAt || booking?.endTime
  const end = endRaw ? new Date(endRaw) : null
  const duration = start && end ? end.getTime() - start.getTime() : NaN
  const rescheduled = proposal.resolution === 'RESCHEDULE' && proposal.pickupTime
  const newStart = rescheduled ? new Date(proposal.pickupTime) : start
  const newEnd = newStart && Number.isFinite(duration) ? new Date(newStart.getTime() + duration) : null
  const vehicleNow = booking?.vehicleName
    ? `${booking.vehicleName}${booking.licensePlate ? ` · ${booking.licensePlate}` : ''}`
    : 'Xe hiện tại'
  return {
    current: { vehicle: vehicleNow, start, end, duration: formatDuration(duration) },
    proposed: {
      vehicle: proposal.resolution === 'CHANGE_VEHICLE' ? proposal.vehicleName : vehicleNow,
      start: newStart, end: newEnd, duration: formatDuration(duration),
    },
  }
}

export default function BookingWorkflowSummary({
  workflow,
  booking = {},
  error,
  onRefresh,
  provider = false,
  onAction,
}) {
  const [decision, setDecision] = useState(null)
  const [responseReason, setResponseReason] = useState('')
  // B1: ackLower - khách phải tích xác nhận khi xe thay thế có chất lượng thấp hơn.
  const [ackLower, setAckLower] = useState(false)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [message, setMessage] = useState('')

  if (!workflow?.bookingCode && !booking?.bookingCode) {
    return error ? (
      <div role="alert" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-xs text-amber-900 shadow-xs flex items-center justify-between">
        <span>Không thể tải tiến trình: {error}.</span>
        <button type="button" onClick={onRefresh} className="font-bold underline text-brand-900 ml-2">
          Thử lại
        </button>
      </div>
    ) : null
  }

  // B4: đề xuất đã rút/hết hiệu lực không hiển thị cho khách (dữ liệu vẫn được lưu và nhà xe vẫn xem được).
  const proposal = visibleProposal(workflow?.proposal, { provider })
  const pendingProposal = proposal?.status === 'PENDING'
  const when = (value) => formatBookingWhen(value) || 'Chưa xác định'

  async function respond() {
    if (busy) return
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      await bookingWorkflowService.respond(workflow.bookingCode, proposal.id, decision, responseReason.trim(), ackLower)
      setDecision(null)
      setAckLower(false)
      await onRefresh()
      setMessage(
        decision === 'ACCEPT'
          ? 'Đã ghi nhận đồng ý. Thay đổi chỉ được áp dụng sau khi hệ thống kiểm tra lại lịch và xe.'
          : 'Đã ghi nhận từ chối. Xe và giờ đón ban đầu được giữ nguyên.'
      )
    } catch (err) {
      setActionError(err.message || 'Không thể gửi phản hồi. Hãy tải lại trang trước khi thử tiếp.')
      await onRefresh()
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="Tiến trình đơn thuê" className="space-y-3">
      {/* Visual Stepper & Phase Card */}
      <BookingProgressStepper
        workflow={workflow}
        booking={booking}
        provider={provider}
        onAction={onAction}
      />

      <DisputeHistory disputes={workflow?.disputes} />

      {/* Operational Proposal Card if pending / accepted / rejected */}
      {proposal && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 space-y-2 text-xs text-amber-950 shadow-2xs">
          <div className="flex items-center gap-2">
            <Icon name="swap_horiz" className="text-lg text-amber-700" />
            <h3 className="font-bold text-sm">
              {pendingProposal
                ? provider
                  ? 'Phương án đang chờ khách đồng ý'
                  : proposal.resolution === 'CHANGE_VEHICLE' ? 'Nhà xe đề xuất đổi xe' : 'Nhà xe đề xuất dời lịch thuê'
                : proposal.status === 'REJECTED'
                  ? 'Khách đã từ chối phương án'
                  : proposal.status === 'ACCEPTED'
                    ? 'Khách đã đồng ý phương án'
                    : 'Phương án không còn chờ phản hồi'}
            </h3>
          </div>

          {pendingProposal && (
            <p className="rounded-lg bg-amber-100 px-2.5 py-1.5 font-semibold text-amber-900">
              {provider
                ? 'Đang chờ khách phản hồi. Xe và giờ đón hiện tại được giữ nguyên cho đến khi khách đồng ý.'
                : 'Đang chờ bạn phản hồi. Chưa có thay đổi nào được áp dụng cho đến khi bạn đồng ý.'}
            </p>
          )}

          {pendingProposal && (() => {
            const cmp = buildComparison(proposal, booking)
            const rows = [
              ...(proposal.resolution === 'CHANGE_VEHICLE' ? [['Xe', cmp.current.vehicle, cmp.proposed.vehicle]] : []),
              ['Giờ nhận xe', cmp.current.start && when(cmp.current.start), cmp.proposed.start && when(cmp.proposed.start)],
              ['Giờ trả xe', cmp.current.end && when(cmp.current.end), cmp.proposed.end && when(cmp.proposed.end)],
              ['Thời lượng thuê', cmp.current.duration, cmp.proposed.duration],
              ['Giá thuê', booking?.totalAmount != null ? formatCurrency(booking.totalAmount) : 'Chưa xác định',
                booking?.totalAmount != null
                  ? `${formatCurrency(booking.totalAmount)} (${provider ? 'giữ nguyên hợp đồng' : 'giữ nguyên, bạn không phải trả thêm'})`
                  : (provider ? 'Giữ nguyên hợp đồng' : 'Giữ nguyên, bạn không phải trả thêm')],
            ]
            return (
              <div className="overflow-hidden rounded-lg border border-amber-200/70 bg-white/80">
                <div className="grid grid-cols-[7.5rem_1fr_1fr] gap-x-2 border-b border-amber-200/70 bg-amber-100/60 px-2.5 py-1.5 text-[10px] font-bold uppercase text-slate-600">
                  <span /> <span>Hiện tại</span> <span>Đề xuất</span>
                </div>
                {rows.map(([label, now, next]) => (
                  <div key={label} className="grid grid-cols-[7.5rem_1fr_1fr] gap-x-2 border-b border-amber-100 px-2.5 py-1.5 last:border-b-0">
                    <span className="text-[11px] font-semibold text-slate-500">{label}</span>
                    <span className="text-slate-700">{now || 'Chưa xác định'}</span>
                    <strong className={next && next !== now ? 'text-amber-800' : 'text-slate-900'}>{next || 'Chưa xác định'}</strong>
                  </div>
                ))}
              </div>
            )
          })()}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
            <div className="rounded-lg bg-white/80 p-2.5 border border-amber-200/70">
              <span className="text-[10px] text-slate-500 uppercase font-bold block">Nội dung thay đổi</span>
              <strong className="text-slate-900 block mt-0.5">
                {proposal.resolution === 'CHANGE_VEHICLE'
                  ? `Xe đề xuất: ${proposal.vehicleName}`
                  : proposal.resolution === 'OTHER'
                    ? 'Phương án khác (xem mô tả)'
                    : `Giờ đón đề xuất: ${when(proposal.pickupTime)}`}
              </strong>
            </div>
            <div className="rounded-lg bg-white/80 p-2.5 border border-amber-200/70">
              <span className="text-[10px] text-slate-500 uppercase font-bold block">{proposal.resolution === 'OTHER' ? 'Mô tả phương án' : 'Lý do điều chỉnh'}</span>
              <span className="text-slate-800 block mt-0.5">{proposal.reason}</span>
            </div>
          </div>

          {pendingProposal && proposal.resolution === 'CHANGE_VEHICLE' && proposal.lowerQuality && (
            <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-2.5 text-rose-900">
              <strong className="block">
                {provider ? 'Xe đề xuất có chất lượng thấp hơn xe hiện tại' : 'Xe thay thế có chất lượng thấp hơn xe hiện tại của bạn'}
              </strong>
              <ul className="mt-1 list-disc pl-4">
                {(proposal.qualityNotes || []).map((note) => <li key={note}>{note}</li>)}
              </ul>
              <span className="mt-1 block">
                {provider
                  ? 'Hệ thống chỉ đổi xe sau khi khách xác nhận rõ là đồng ý nhận xe này; chưa có xác nhận thì xe hiện tại được giữ nguyên.'
                  : 'Xe sẽ không được đổi nếu bạn chưa xác nhận đồng ý.'}
              </span>
            </div>
          )}

          {proposal.resolution === 'CHANGE_VEHICLE' && (proposal.status === 'ACCEPTED' || pendingProposal) && Number(proposal.priceReduction) > 0 && (
            <p className="rounded-lg bg-emerald-50 px-2.5 py-1.5 font-semibold text-emerald-900">
              {pendingProposal
                ? `Xe mới có giá thuê thấp hơn (${formatCurrency(proposal.proposedAmount)} so với ${formatCurrency(proposal.contractAmount)}): nếu ${provider ? 'khách đồng ý, khách sẽ' : 'bạn đồng ý, bạn sẽ'} được giảm trừ ${formatCurrency(proposal.priceReduction)} vào hóa đơn cuối cùng.`
                : `Xe mới có giá thuê thấp hơn: ${provider ? 'khách được giảm trừ' : 'bạn được giảm trừ'} ${formatCurrency(proposal.priceReduction)} vào hóa đơn cuối cùng.`}
              {' '}Giá trị hợp đồng và tiền cọc không thay đổi.
            </p>
          )}

          <p className="text-[11px] text-amber-800 leading-normal">
            {provider
              ? 'Giá trị hợp đồng và tiền cọc được giữ nguyên. Nếu xe mới đắt hơn, nhà xe chịu phần chênh lệch; nếu rẻ hơn, phần chênh lệch được trừ vào hóa đơn cuối cùng của khách. Lịch và tài xế chỉ được áp dụng khi khách thuê đồng ý.'
              : 'Giá trị hợp đồng và tiền cọc của bạn không thay đổi. Nếu xe mới đắt hơn, bạn không phải trả thêm; nếu rẻ hơn, phần chênh lệch được trừ vào hóa đơn cuối cùng của bạn. Lịch và tài xế chỉ được áp dụng khi bạn đồng ý.'}
          </p>

          {proposal.responseReason && (
            <p className="text-[11px] text-slate-700">
              Phản hồi từ khách: <strong>{proposal.responseReason}</strong>
            </p>
          )}

          {/* Renter Decision Buttons */}
          {!provider && pendingProposal && (
            <div className="flex flex-wrap gap-2 pt-1">
              <button
                type="button"
                disabled={busy}
                onClick={() => setDecision('ACCEPT')}
                className="rounded-lg bg-brand-700 px-3.5 py-1.5 text-xs font-bold text-white shadow-2xs hover:bg-brand-800 disabled:opacity-50"
              >
                Đồng ý phương án
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => setDecision('REJECT')}
                className="rounded-lg border border-slate-300 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
              >
                Từ chối phương án
              </button>
            </div>
          )}

          {/* Provider Withdraw Button */}
          {provider && pendingProposal && (
            <div className="pt-1">
              <button
                type="button"
                disabled={busy}
                onClick={async () => {
                  setBusy(true)
                  setActionError('')
                  try {
                    await bookingWorkflowService.withdraw(workflow.bookingCode, proposal.id)
                    await onRefresh()
                  } catch (err) {
                    setActionError(err.message || 'Không thể rút đề xuất.')
                    await onRefresh()
                  } finally {
                    setBusy(false)
                  }
                }}
                className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 shadow-2xs"
              >
                Rút đề xuất để điều phối phương án khác
              </button>
            </div>
          )}
        </div>
      )}

      {/* Driver Feedback History (Provider Only) */}
      {provider && workflow?.driverFeedback?.length > 0 && (
        <details className="rounded-xl border border-slate-200 bg-white p-3.5 text-xs text-slate-600 shadow-2xs">
          <summary className="cursor-pointer font-bold text-slate-800 flex items-center gap-1.5">
            <Icon name="history" className="text-base text-slate-400" />
            <span>Lịch sử từ chối / phản hồi của tài xế ({workflow.driverFeedback.length})</span>
          </summary>
          <ul className="mt-2.5 space-y-1.5 border-t border-slate-100 pt-2">
            {workflow.driverFeedback.map((item, index) => (
              <li key={`${item.driverName}-${index}`} className="flex items-center justify-between text-[11px] bg-slate-50 p-2 rounded-lg">
                <span className="font-semibold text-slate-800">{item.driverName}</span>
                <span className="text-slate-500">
                  {item.status === 'TIMEOUT' ? 'Quá hạn phản hồi (15 phút)' : 'Đã từ chối'}
                  {item.reason ? ` · Lý do: ${item.reason}` : ''}
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}

      {shouldShowRefundDetailsList(workflow?.refunds, { provider }) && (
        <div className="rounded-xl border border-slate-200 bg-white p-4 text-xs space-y-2 shadow-2xs">
          <h3 className="font-bold text-slate-900 flex items-center gap-1.5">
            <Icon name="receipt" className="text-base text-brand-700" />
            <span>
              {workflow.refunds.length > 1
                ? `Chi tiết ${workflow.refunds.length} yêu cầu hoàn`
                : 'Chi tiết hoàn tiền'}
            </span>
          </h3>
          <div className="space-y-1.5">
            {workflow.refunds.map((refund) => (
              <div
                key={refund.id}
                className="rounded-lg bg-slate-50 p-2.5 border border-slate-100"
              >
                <div>
                  <strong className="font-mono text-slate-900">{formatCurrency(refund.amount)}</strong>
                  <span className="text-slate-500 ml-2">
                    {REFUND_WORKFLOW_LABELS[refund.status] || refund.status}
                  </span>
                  {refund.rejectionReason && (
                    <span className="text-rose-700 block text-[10px] mt-0.5">
                      Lý do từ chối: {refund.rejectionReason}
                    </span>
                  )}
                </div>
                <RefundReceiptTimeline refund={refund} provider={provider} onRefresh={onRefresh} />
                {provider && refund.status === 'PROCESSING' && (
                  <Link
                    to="/provider/finance"
                    className="text-[11px] font-bold text-brand-700 underline"
                  >
                    Xác nhận đã hoàn tiền
                  </Link>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Status Messages */}
      {actionError && (
        <div role="alert" className="rounded-lg bg-rose-50 border border-rose-200 p-3 text-xs text-rose-800 flex items-center gap-2">
          <Icon name="error" className="text-base text-rose-600" />
          <span>{actionError}</span>
        </div>
      )}
      {message && (
        <div role="status" className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800 flex items-center gap-2">
          <Icon name="check_circle" className="text-base text-emerald-600" />
          <span>{message}</span>
        </div>
      )}

      {/* Renter Decision Confirmation Dialog */}
      {decision && pendingProposal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Xác nhận phản hồi phương án"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
        >
          <div className="w-full max-w-md rounded-xl bg-white p-5 space-y-4 shadow-xl border border-slate-200">
            <h3 className="font-bold text-base text-slate-900">
              {decision === 'ACCEPT' ? 'Xác nhận đồng ý phương án thay thế?' : 'Xác nhận từ chối phương án thay thế?'}
            </h3>
            <p className="text-xs text-slate-600">
              {proposal.resolution === 'CHANGE_VEHICLE' ? proposal.vehicleName : proposal.resolution === 'OTHER' ? proposal.reason : when(proposal.pickupTime)}
            </p>
            {decision === 'ACCEPT' && proposal.resolution === 'CHANGE_VEHICLE' && Number(proposal.priceReduction) > 0 && (
              <p className="rounded-lg bg-emerald-50 px-2.5 py-1.5 text-xs font-semibold text-emerald-900">
                Bạn sẽ được giảm trừ {formatCurrency(proposal.priceReduction)} vào hóa đơn cuối cùng. Giá trị hợp đồng và tiền cọc không thay đổi.
              </p>
            )}
            {decision === 'ACCEPT' && proposal.resolution === 'CHANGE_VEHICLE' && proposal.lowerQuality && (
              <label className="flex gap-2 rounded-lg border border-rose-200 bg-rose-50 p-2.5 text-xs text-rose-900">
                <input type="checkbox" checked={ackLower} disabled={busy} onChange={(e) => setAckLower(e.target.checked)} />
                <span>Tôi đã đọc thông tin và đồng ý đổi sang xe có chất lượng thấp hơn: {(proposal.qualityNotes || []).join('; ')}.</span>
              </label>
            )}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Ghi chú phản hồi (tùy chọn)
              </label>
              <textarea
                aria-label="Ghi chú phản hồi"
                maxLength={500}
                rows={3}
                value={responseReason}
                onChange={(e) => setResponseReason(e.target.value)}
                disabled={busy}
                placeholder="Nhập ghi chú phản hồi..."
                className="w-full rounded-lg border border-slate-300 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-brand-600"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setDecision(null)}
                disabled={busy}
                className="rounded-lg border border-slate-300 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                Quay lại
              </button>
              <button
                type="button"
                onClick={respond}
                disabled={busy || (decision === 'ACCEPT' && proposal.lowerQuality && proposal.resolution === 'CHANGE_VEHICLE' && !ackLower)}
                className="rounded-lg bg-brand-700 px-4 py-1.5 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50 shadow-2xs"
              >
                {busy ? 'Đang gửi...' : 'Xác nhận phản hồi'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
