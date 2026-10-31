import { Link } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import {
  STEP_STATUS,
  isTerminalBooking,
  checkPickupTimeAnomaly,
  isOverduePickup,
  getActiveFinancialMetrics,
  evaluateActiveWorkflowSteps,
  evaluateRefundWorkflow,
} from '../../utils/bookingWorkflowSteps'

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

function StepIndicator({ step, isLast }) {
  const { state, icon, label, description } = step

  let circleStyle = 'bg-slate-100 text-slate-400 border border-slate-200'
  let labelStyle = 'text-slate-500 font-medium'
  let iconName = icon

  if (state === STEP_STATUS.COMPLETED) {
    circleStyle = 'bg-emerald-600 text-white shadow-xs'
    labelStyle = 'text-emerald-900 font-bold'
    iconName = 'check'
  } else if (state === STEP_STATUS.CURRENT) {
    circleStyle = 'bg-brand-700 text-white ring-4 ring-brand-100 shadow-xs'
    labelStyle = 'text-brand-950 font-bold'
  } else if (state === STEP_STATUS.WARNING) {
    circleStyle = 'bg-amber-600 text-white ring-4 ring-amber-100 shadow-xs'
    labelStyle = 'text-amber-900 font-bold'
    iconName = 'warning'
  } else if (state === STEP_STATUS.FAILED) {
    circleStyle = 'bg-rose-600 text-white ring-4 ring-rose-100 shadow-xs'
    labelStyle = 'text-rose-900 font-bold'
    iconName = 'close'
  }

  return (
    <div className={`flex items-center ${isLast ? 'shrink-0' : 'flex-1 min-w-[130px] sm:min-w-0'}`}>
      <div className="flex flex-col items-center text-center group cursor-default">
        <div className={`flex h-9 w-9 items-center justify-center rounded-full text-base transition-all duration-200 ${circleStyle}`}>
          <Icon name={iconName} className="text-[20px]" />
        </div>
        <div className="mt-1.5 flex flex-col items-center">
          <span className={`text-xs ${labelStyle}`}>{label}</span>
          <span className="text-[11px] text-slate-500 max-w-[120px] truncate leading-tight mt-0.5" title={description}>
            {description}
          </span>
        </div>
      </div>
      {!isLast && (
        <div className="mx-2 mb-6 hidden h-0.5 flex-1 sm:block" aria-hidden="true">
          <div
            className={`h-full rounded-full transition-colors duration-300 ${
              state === STEP_STATUS.COMPLETED ? 'bg-emerald-500' : 'bg-slate-200'
            }`}
          />
        </div>
      )}
    </div>
  )
}

export default function BookingProgressStepper({
  workflow,
  booking = {},
  provider = false,
  onAction,
}) {
  if (!workflow && !booking?.status) return null

  const status = workflow?.bookingStatus || booking?.status
  const isCancelled = isTerminalBooking(status)
  const financialMetrics = getActiveFinancialMetrics(workflow)

  // Pickup time checks
  const pickupAnomaly = checkPickupTimeAnomaly(booking.pickupTime, booking.createdAt)
  const isOverdue = isOverduePickup(booking.pickupTime, status)

  // When booking is terminal (cancelled/rejected/cancellation_review)
  if (isCancelled) {
    const refundData = evaluateRefundWorkflow(workflow, booking)

    return (
      <div className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5 shadow-xs space-y-4">
        {/* Terminal Header */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-rose-100 text-rose-700">
              <Icon name="cancel" className="text-xl" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-900">
                {status === 'REJECTED' ? 'Đơn thuê đã bị từ chối' : 'Đơn thuê đã hủy'}
              </h3>
              <p className="text-xs text-slate-500">
                {booking.rejectionReason
                  ? `Lý do: ${booking.rejectionReason}`
                  : booking.cancelReason
                    ? `Lý do: ${booking.cancelReason}`
                    : 'Đơn thuê không tiếp tục thực hiện.'}
              </p>
            </div>
          </div>

          {provider && refundData.hasFinancialObligation && (
            <Link
              to="/provider/finance"
              className="inline-flex items-center gap-1.5 rounded-lg bg-brand-50 border border-brand-200 px-3 py-1.5 text-xs font-bold text-brand-900 hover:bg-brand-100 transition shadow-2xs"
            >
              <Icon name="account_balance_wallet" className="text-[16px]" />
              <span>Mở đối soát tài chính</span>
            </Link>
          )}
        </div>

        {/* If no financial obligations */}
        {!refundData.hasFinancialObligation ? (
          <div className="rounded-lg bg-slate-50 p-3.5 text-xs text-slate-600 flex items-center gap-2">
            <Icon name="check_circle" className="text-base text-slate-400" />
            <span>Đơn đã kết thúc. Không phát sinh nghĩa vụ tài chính hoặc hoàn tiền.</span>
          </div>
        ) : (
          /* Refund Stepper when financial obligations exist */
          <div className="space-y-4">
            <div className="flex items-center justify-between text-xs font-bold text-slate-700">
              <span className="flex items-center gap-1.5">
                <Icon name="currency_exchange" className="text-base text-amber-600" />
                <span>Tiến trình hoàn trả tiền cọc / thanh toán</span>
              </span>
              <span className="text-[11px] font-normal text-slate-500">
                {refundData.isFullyRefunded
                  ? 'Đã hoàn tất toàn bộ'
                  : 'Nghĩa vụ hoàn tiền còn tồn tại'}
              </span>
            </div>

            {/* Stepper container */}
            <div className="overflow-x-auto pb-2 sm:pb-0">
              <div className="flex min-w-[400px] sm:min-w-0 items-center justify-between">
                {refundData.steps.map((step, idx) => (
                  <StepIndicator
                    key={step.id}
                    step={step}
                    isLast={idx === refundData.steps.length - 1}
                  />
                ))}
              </div>
            </div>

            {/* Financial metrics & Action guidance */}
            {financialMetrics.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-slate-100">
                <span className="text-xs text-slate-500 font-medium mr-1">Tài chính liên quan:</span>
                {financialMetrics.map((m) => (
                  <span
                    key={m.id}
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs border ${m.badgeClass}`}
                  >
                    <span>{m.label}:</span>
                    <strong className="font-mono">{formatCurrency(m.amount)}</strong>
                  </span>
                ))}
              </div>
            )}

            {/* Action suggestion */}
            <div className="rounded-lg bg-amber-50/70 border border-amber-200/80 p-3 text-xs text-amber-900 flex items-start gap-2.5">
              <Icon name="info" className="text-[18px] text-amber-600 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-semibold">{workflow?.nextAction || 'Đang chờ xử lý hoàn tiền theo quy định.'}</p>
                {provider && (
                  <p className="text-[11px] text-amber-800">
                    Nhà xe cần chuyển khoản theo thông tin hoàn tiền và nộp minh chứng chuyển khoản (proof) tại trang Tài chính để hoàn tất nghĩa vụ.
                  </p>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    )
  }

  // Active Booking Workflow
  const steps = evaluateActiveWorkflowSteps(workflow, booking)
  const currentStep = steps.find((s) => s.state === STEP_STATUS.CURRENT || s.state === STEP_STATUS.WARNING) || steps[0]

  const isDriverPhase = currentStep?.id === 'DRIVER' ||
    Boolean(workflow?.needsDriver) ||
    ['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED'].includes(status)

  const isDriverConfirmed = Boolean(
    booking.driverConfirmedAt ||
    status === 'DRIVER_CONFIRMED' ||
    booking.executionStatus === 'CONFIRMED' ||
    workflow?.driverStatus === 'ACCEPTED'
  )

  const isDriverRejected = Boolean(
    workflow?.driverStatus === 'REJECTED' ||
    (booking.driverFeedback && booking.driverFeedback.length > 0 && !booking.driverName)
  )

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5 shadow-xs space-y-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2">
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-50 text-brand-700">
            <Icon name="timeline" className="text-lg" />
          </div>
          <h2 className="text-sm font-bold text-slate-900">Tiến trình vận hành đơn thuê</h2>
        </div>

        {/* Financial Badges (Only non-zero) */}
        {financialMetrics.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            {financialMetrics.map((m) => (
              <span
                key={m.id}
                className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] border ${m.badgeClass}`}
              >
                <span>{m.label}:</span>
                <strong className="font-mono">{formatCurrency(m.amount)}</strong>
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Stepper with icons & connecting lines */}
      <div className="overflow-x-auto pb-2 sm:pb-0">
        <div className="flex min-w-[550px] sm:min-w-0 items-center justify-between">
          {steps.map((step, idx) => (
            <StepIndicator
              key={step.id}
              step={step}
              isLast={idx === steps.length - 1}
            />
          ))}
        </div>
      </div>

      {/* Active Phase & Next Action Guidance Box */}
      <div className="rounded-xl border border-brand-100 bg-brand-50/40 p-4 space-y-2.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="flex h-2 w-2 rounded-full bg-brand-600 animate-pulse" />
            <span className="text-xs font-bold uppercase tracking-wider text-brand-900">
              Giai đoạn hiện tại: {currentStep.label} ({currentStep.description})
            </span>
          </div>

          {/* Timing indicators if applicable */}
          {workflow?.assignmentExpiresAt && workflow?.driverStatus === 'PENDING_RESPONSE' && (
            <span className="text-[11px] font-medium text-slate-600 bg-white/80 px-2 py-0.5 rounded border border-slate-200">
              Tài xế cần phản hồi trước: <strong>{formatBookingWhen(workflow.assignmentExpiresAt)}</strong>
            </span>
          )}
        </div>

        {/* Next action description */}
        <p className="text-xs text-slate-800 leading-relaxed font-medium">
          {workflow?.nextAction || 'Theo dõi tiến độ từ nhà xe và tài xế.'}
        </p>

        {/* Anomaly / Overdue Warnings */}
        {pickupAnomaly && (
          <div className="rounded-lg bg-rose-50 border border-rose-200 p-2.5 text-xs text-rose-800 flex items-start gap-2">
            <Icon name="error_outline" className="text-base shrink-0 mt-0.5 text-rose-600" />
            <div>
              <strong className="block">Cảnh báo thời gian: {pickupAnomaly.message}</strong>
              <span className="text-[11px]">
                Giờ đón khách được đặt là {formatBookingWhen(pickupAnomaly.pickupTime)}, trước thời điểm đơn được khởi tạo ({formatBookingWhen(pickupAnomaly.createdAt)}). Nhà xe cần kiểm tra và gửi đề xuất dời giờ đón.
              </span>
            </div>
          </div>
        )}

        {isOverdue && !pickupAnomaly && (
          <div className="rounded-lg bg-amber-50 border border-amber-200 p-2.5 text-xs text-amber-900 flex items-start gap-2">
            <Icon name="schedule" className="text-base shrink-0 mt-0.5 text-amber-600" />
            <div>
              <strong className="block">Đã quá giờ đón dự kiến ({formatBookingWhen(booking.pickupTime)})</strong>
              <span className="text-[11px]">
                Thời gian đón khách theo lịch đã trôi qua nhưng chuyến đi chưa bắt đầu. Hãy liên hệ với khách thuê hoặc điều phối phương án xử lý khẩn.
              </span>
            </div>
          </div>
        )}

        {/* Driver Phase Operational Details: Trạng thái phân công, Cảnh báo chưa có tài xế, Hướng dẫn xử lý khi hết tài xế */}
        {isDriverPhase && (
          <div className="space-y-2.5 pt-2.5 border-t border-brand-200/60 text-xs">
            {/* Trạng thái phân công */}
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-slate-700">Trạng thái phân công:</span>
              {booking.driverName ? (
                isDriverConfirmed ? (
                  <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 px-2.5 py-0.5 text-xs font-bold text-emerald-800 border border-emerald-200">
                    <Icon name="check_circle" className="text-[14px] text-emerald-600" />
                    Tài xế đã nhận chuyến ({booking.driverName})
                  </span>
                ) : isDriverRejected ? (
                  <span className="inline-flex items-center gap-1 rounded-md bg-rose-50 px-2.5 py-0.5 text-xs font-bold text-rose-800 border border-rose-200">
                    <Icon name="cancel" className="text-[14px] text-rose-600" />
                    Tài xế đã từ chối ({booking.driverName})
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 rounded-md bg-blue-50 px-2.5 py-0.5 text-xs font-bold text-blue-800 border border-blue-200">
                    <Icon name="schedule" className="text-[14px] text-blue-600" />
                    Chờ tài xế xác nhận ({booking.driverName})
                  </span>
                )
              ) : (
                <span className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-2.5 py-0.5 text-xs font-bold text-amber-800 border border-amber-200">
                  <Icon name="warning" className="text-[14px] text-amber-600" />
                  Chưa phân công tài xế
                </span>
              )}
            </div>

            {/* Cảnh báo nếu chưa có tài xế */}
            {!booking.driverName && (
              <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-amber-900 flex items-start gap-2.5">
                <Icon name="warning" className="text-[18px] text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <strong className="block font-semibold">Chưa có tài xế phụ trách đơn thuê</strong>
                  <p className="text-[11px] text-amber-800 mt-0.5 leading-normal">
                    Đơn thuê chưa được chỉ định tài xế. Vui lòng sử dụng nút <strong>&quot;Phân công tài xế&quot;</strong> ở góc trên bên phải màn hình để thực hiện điều phối trước giờ đón khách.
                  </p>
                </div>
              </div>
            )}

            {/* Hướng dẫn xử lý nếu hết tài xế phù hợp */}
            {!booking.driverName && (
              <div className="rounded-lg bg-slate-50 border border-slate-200 p-3 text-slate-700 flex items-start gap-2.5">
                <Icon name="help_outline" className="text-[18px] text-slate-500 shrink-0 mt-0.5" />
                <div>
                  <span className="block font-semibold text-[11px] text-slate-900">Hướng dẫn khi không có tài xế phù hợp:</span>
                  <p className="text-[11px] text-slate-600 mt-1 leading-normal">
                    Nếu tất cả tài xế trong đội xe đều đang bận hoặc vi phạm thời gian nghỉ giữa chuyến (buffer time &ge; 30 phút), nhà xe có thể:
                    <br />• Nhấn nút <strong>&quot;Phân công tài xế&quot;</strong> ở góc trên để dùng tính năng <em>Gợi ý thông minh</em> hoặc <em>Tự động phân công</em>.
                    <br />• Gửi đề xuất <strong>Dời giờ đón</strong> hoặc <strong>Đổi xe</strong> cho khách thuê ở phần Phương án vận hành bên dưới.
                    <br />• Liên hệ thỏa thuận với khách thuê hoặc hỗ trợ hủy đơn theo đúng quy định nếu không thể bố trí phục vụ.
                  </p>
                </div>
              </div>
            )}

            {/* Thông tin thời gian phản hồi khi đang chờ tài xế */}
            {booking.driverName && !isDriverConfirmed && !isDriverRejected && (
              <p className="text-[11px] text-slate-600 pt-0.5">
                Yêu cầu đã được gửi tới tài xế. Tài xế có 15 phút để phản hồi nhận chuyến. Nhà xe có thể sử dụng nút <strong>&quot;Điều phối lại tài xế&quot;</strong> ở góc trên nếu tài xế không phản hồi kịp thời.
              </p>
            )}
          </div>
        )}

        {/* Other Provider Quick Actions (Accept / Deny for PENDING, Add charge for extra charges) */}
        {provider && onAction && (status === 'PENDING' || status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION') && (
          <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-brand-200/60">
            {status === 'PENDING' && (
              <>
                <button
                  type="button"
                  onClick={() => onAction('ACCEPT')}
                  className="inline-flex items-center gap-1 rounded-lg bg-brand-700 px-3 py-1.5 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition"
                >
                  <Icon name="check" className="text-[16px]" />
                  <span>Chấp nhận đơn ngay</span>
                </button>
                <button
                  type="button"
                  onClick={() => onAction('DENY')}
                  className="inline-flex items-center gap-1 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  <span>Từ chối</span>
                </button>
              </>
            )}

            {status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION' && (
              <>
                <button
                  type="button"
                  onClick={() => onAction('ADD_CHARGE')}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-300 bg-indigo-50 px-3 py-1.5 text-xs font-bold text-indigo-900 hover:bg-indigo-100 transition shadow-xs"
                >
                  <Icon name="add_card" className="text-[16px]" />
                  <span>Thêm phụ phí</span>
                </button>
                {booking.providerConfirmedAt && booking.renterConfirmedAt && (
                  <button
                    type="button"
                    onClick={() => onAction('FINALIZE_SETTLEMENT')}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-purple-700 px-3.5 py-1.5 text-xs font-bold text-white shadow-xs hover:bg-purple-800 transition"
                  >
                    <Icon name="price_check" className="text-[16px]" />
                    <span>Chốt quyết toán</span>
                  </button>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
