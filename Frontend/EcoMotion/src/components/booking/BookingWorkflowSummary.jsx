import { useState } from 'react'
import { Link } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { REFUND_WORKFLOW_LABELS } from '../../utils/bookingWorkflow'
import { bookingWorkflowService } from '../../services/bookingWorkflowService'
import BookingProgressStepper from './BookingProgressStepper'

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
  const [refundAmount, setRefundAmount] = useState('')
  const [refundReason, setRefundReason] = useState('')
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

  const proposal = workflow?.proposal
  const pendingProposal = proposal?.status === 'PENDING'
  const when = (value) => formatBookingWhen(value) || 'Chưa xác định'

  async function respond() {
    if (busy) return
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      await bookingWorkflowService.respond(workflow.bookingCode, proposal.id, decision, responseReason.trim())
      setDecision(null)
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

  async function requestRefund(event) {
    event.preventDefault()
    if (busy) return
    const amount = Number(refundAmount)
    if (
      !Number.isSafeInteger(amount) ||
      amount < 1000 ||
      amount > Number(workflow?.refundableAmount || 0) ||
      !refundReason.trim()
    ) {
      setActionError('Nhập số tiền hợp lệ trong hạn mức và lý do hoàn tiền.')
      return
    }
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      await bookingWorkflowService.requestRefund(workflow.bookingCode, amount, refundReason.trim())
      await onRefresh()
      setRefundAmount('')
      setRefundReason('')
      setMessage('Đã gửi yêu cầu hoàn tiền, đang chờ xem xét. Chưa có giao dịch hoàn tiền thành công ở bước này.')
    } catch (err) {
      setActionError(err.message || 'Không thể gửi yêu cầu hoàn tiền.')
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

      {/* Operational Proposal Card if pending / accepted / rejected */}
      {proposal && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 space-y-2 text-xs text-amber-950 shadow-2xs">
          <div className="flex items-center gap-2">
            <Icon name="swap_horiz" className="text-lg text-amber-700" />
            <h3 className="font-bold text-sm">
              {pendingProposal
                ? 'Phương án đang chờ khách đồng ý'
                : proposal.status === 'REJECTED'
                  ? 'Khách đã từ chối phương án'
                  : proposal.status === 'ACCEPTED'
                    ? 'Khách đã đồng ý phương án'
                    : 'Phương án không còn chờ phản hồi'}
            </h3>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
            <div className="rounded-lg bg-white/80 p-2.5 border border-amber-200/70">
              <span className="text-[10px] text-slate-500 uppercase font-bold block">Nội dung thay đổi</span>
              <strong className="text-slate-900 block mt-0.5">
                {proposal.resolution === 'CHANGE_VEHICLE'
                  ? `Xe đề xuất: ${proposal.vehicleName}`
                  : `Giờ đón đề xuất: ${when(proposal.pickupTime)}`}
              </strong>
            </div>
            <div className="rounded-lg bg-white/80 p-2.5 border border-amber-200/70">
              <span className="text-[10px] text-slate-500 uppercase font-bold block">Lý do điều chỉnh</span>
              <span className="text-slate-800 block mt-0.5">{proposal.reason}</span>
            </div>
          </div>

          <p className="text-[11px] text-amber-800 leading-normal">
            Giá thuê và chính sách đã chốt được giữ nguyên. Lịch và tài xế chỉ được áp dụng khi khách thuê đồng ý.
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

      {/* Refunds History */}
      {workflow?.refunds?.length > 0 && (
        <div className="rounded-xl border border-slate-200 bg-white p-4 text-xs space-y-2 shadow-2xs">
          <h3 className="font-bold text-slate-900 flex items-center gap-1.5">
            <Icon name="receipt" className="text-base text-brand-700" />
            <span>Danh sách yêu cầu hoàn tiền ({workflow.refunds.length})</span>
          </h3>
          <div className="space-y-1.5">
            {workflow.refunds.map((refund) => (
              <div
                key={refund.id}
                className="flex items-center justify-between rounded-lg bg-slate-50 p-2.5 border border-slate-100"
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
                {provider && refund.status === 'PROCESSING' && (
                  <Link
                    to="/provider/finance"
                    className="text-[11px] font-bold text-brand-700 underline"
                  >
                    Nộp biên lai
                  </Link>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Renter Request Refund Section */}
      {!provider && Number(workflow?.refundableAmount || 0) >= 1000 && (
        <details className="rounded-xl border border-slate-200 bg-white p-4 text-xs shadow-2xs">
          <summary className="cursor-pointer font-bold text-slate-800 flex items-center gap-1.5">
            <Icon name="request_quote" className="text-base text-brand-700" />
            <span>Yêu cầu xem xét hoàn tiền</span>
          </summary>
          <div className="mt-3 border-t border-slate-100 pt-3 space-y-3">
            <p className="text-slate-600">
              Hạn mức tiền còn có thể yêu cầu hoàn: <strong>{formatCurrency(workflow.refundableAmount)}</strong>. Việc hoàn tiền thực tế phụ thuộc chính sách và kết quả xem xét.
            </p>
            <form onSubmit={requestRefund} className="flex flex-wrap items-end gap-3">
              <label className="text-xs font-semibold text-slate-700">
                Số tiền yêu cầu
                <input
                  aria-label="Số tiền yêu cầu hoàn"
                  type="number"
                  min="1000"
                  max={workflow.refundableAmount}
                  step="1"
                  required
                  value={refundAmount}
                  onChange={(e) => setRefundAmount(e.target.value)}
                  disabled={busy}
                  className="mt-1 block h-9 w-40 rounded-lg border border-slate-300 px-2.5 text-xs"
                />
              </label>
              <label className="grow text-xs font-semibold text-slate-700">
                Lý do hoàn tiền
                <input
                  aria-label="Lý do yêu cầu hoàn"
                  required
                  maxLength={500}
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  disabled={busy}
                  placeholder="Nhập lý do chi tiết..."
                  className="mt-1 block h-9 w-full rounded-lg border border-slate-300 px-2.5 text-xs"
                />
              </label>
              <button
                type="submit"
                disabled={busy}
                className="h-9 rounded-lg bg-brand-700 px-4 text-xs font-bold text-white shadow-2xs hover:bg-brand-800 disabled:opacity-50"
              >
                {busy ? 'Đang gửi...' : 'Gửi yêu cầu hoàn tiền'}
              </button>
            </form>
          </div>
        </details>
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
              {proposal.resolution === 'CHANGE_VEHICLE' ? proposal.vehicleName : when(proposal.pickupTime)}
            </p>
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
                disabled={busy}
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
