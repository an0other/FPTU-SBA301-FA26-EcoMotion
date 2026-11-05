import { Link } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { cancelReasonPresentation, renterCanSeeDriver } from '../../utils/bookingStatus'
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

function TerminalCancellationPanel({
  status,
  booking,
  provider,
  refundData,
  workflow,
  financialMetrics,
}) {
  const isRejected = status === 'REJECTED'
  const title = isRejected ? 'Đơn thuê đã bị từ chối' : 'Đơn thuê đã hủy'
  const headlineIcon = isRejected ? 'block' : 'event_busy'

  const rejectionText = booking.rejectionReason?.trim()
  const cancelInfo = cancelReasonPresentation(booking.cancelReason)
  const reasonTitle = isRejected
    ? (rejectionText ? 'Lý do từ chối' : 'Không tiếp tục thực hiện')
    : (cancelInfo.label ? 'Lý do hủy' : 'Thông tin')
  const reasonBadge = isRejected ? null : cancelInfo.label
  const providerCancelNote = booking.cancelDetailNote?.trim()
  const reasonBody = isRejected
    ? (rejectionText || 'Nhà xe không thể nhận đơn với thông tin và khung giờ hiện tại.')
    : (providerCancelNote || cancelInfo.description || cancelInfo.label || 'Đơn thuê không tiếp tục thực hiện.')

  const vehicleId = booking.vehicleId ?? booking.vehicle?.id
  const rebookPath = vehicleId
    ? `/vehicles/${encodeURIComponent(String(vehicleId))}`
    : null
  const showRebook = !provider && !isRejected && booking.cancelReason === 'DEPOSIT_TIMEOUT' && rebookPath

  return (
    <div className="overflow-hidden rounded-2xl border border-rose-200/90 bg-gradient-to-br from-rose-50/90 via-white to-slate-50 shadow-[0_10px_30px_rgba(190,18,60,0.08)]">
      <div className="h-1 bg-gradient-to-r from-rose-500 via-rose-400 to-amber-300" aria-hidden="true" />
      <div className="p-5 sm:p-6 space-y-5">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex gap-4 min-w-0">
            <div
              className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-white shadow-md ${
                isRejected ? 'bg-slate-700 shadow-slate-200' : 'bg-rose-600 shadow-rose-200/80'
              }`}
            >
              <Icon name={headlineIcon} className="text-[26px]" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-rose-800/70">
                {isRejected ? 'Kết thúc — từ chối' : 'Kết thúc — đã hủy'}
              </p>
              <h3 className="mt-0.5 text-base font-bold text-slate-900 sm:text-lg">{title}</h3>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className="inline-flex items-center gap-1 rounded-full border border-rose-200/80 bg-white/90 px-2.5 py-0.5 text-[11px] font-semibold text-rose-900">
                  <Icon name={isRejected ? 'gavel' : cancelInfo.icon} className="text-[15px]" />
                  {reasonTitle}
                </span>
                {reasonBadge ? (
                  <span className="inline-flex max-w-full truncate rounded-full bg-rose-100 px-2.5 py-0.5 text-[11px] font-bold text-rose-900">
                    {reasonBadge}
                  </span>
                ) : null}
              </div>
              <p className="mt-2.5 text-sm leading-relaxed text-slate-600">{reasonBody}</p>
            </div>
          </div>

          {provider && refundData.hasFinancialObligation ? (
            <Link
              to="/provider/finance"
              className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-brand-700 transition"
            >
              <Icon name="account_balance_wallet" className="text-[18px]" />
              <span>Mở đối soát tài chính</span>
            </Link>
          ) : null}
        </div>

        {!refundData.hasFinancialObligation ? (
          <div className="rounded-xl border border-emerald-200/80 bg-gradient-to-r from-emerald-50/90 to-white p-4 sm:flex sm:items-start sm:gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-emerald-600 text-white">
              <Icon name="verified" className="text-xl" />
            </div>
            <div className="mt-3 sm:mt-0">
              <p className="text-sm font-bold text-emerald-950">Không còn nghĩa vụ tài chính</p>
              <p className="mt-1 text-xs leading-relaxed text-emerald-900/80">
                Đơn đã đóng. Bạn không cần thanh toán thêm và cũng không có khoản hoàn tiền đang chờ xử lý.
              </p>
              {showRebook ? (
                <Link
                  to={rebookPath}
                  className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-brand-200 bg-white px-3 py-1.5 text-xs font-bold text-brand-900 hover:bg-brand-50 transition"
                >
                  <Icon name="directions_car" className="text-base" />
                  Đặt lại xe này
                </Link>
              ) : null}
            </div>
          </div>
        ) : (
          <div className="space-y-4 rounded-xl border border-amber-200/80 bg-amber-50/40 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs font-bold text-slate-800">
              <span className="flex items-center gap-1.5">
                <Icon name="currency_exchange" className="text-base text-amber-700" />
                <span>Tiến trình hoàn trả tiền</span>
              </span>
              <span className="text-[11px] font-normal text-slate-600">
                {refundData.isFullyRefunded
                  ? 'Đã hoàn tất toàn bộ'
                  : refundData.receiptPending ? 'Chờ xác nhận nhận tiền' : 'Còn nghĩa vụ hoàn tiền'}
              </span>
            </div>

            <div className="overflow-x-auto pb-1">
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

            {financialMetrics.length > 0 ? (
              <div className="flex flex-wrap items-center gap-2 border-t border-amber-200/60 pt-3">
                {financialMetrics.length > 1 ? (
                  <span className="text-xs font-medium text-slate-600">Tài chính liên quan:</span>
                ) : null}
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
            ) : null}

            <div className="rounded-lg border border-amber-200 bg-white/80 p-3 text-xs text-amber-950 flex items-start gap-2.5">
              <Icon name="info" className="text-[18px] text-amber-600 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-semibold">{workflow?.nextAction || 'Đang chờ xử lý hoàn tiền theo quy định.'}</p>
                {provider ? (
                  <p className="text-[11px] text-amber-900/90">
                    Nhà xe cần chuyển khoản theo thông tin hoàn tiền và nộp minh chứng tại trang Tài chính.
                  </p>
                ) : null}
              </div>
            </div>
          </div>
        )}
      </div>
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
      <TerminalCancellationPanel
        status={status}
        booking={booking}
        provider={provider}
        refundData={refundData}
        workflow={workflow}
        financialMetrics={financialMetrics}
      />
    )
  }

  // Active Booking Workflow
  const steps = evaluateActiveWorkflowSteps(workflow, booking)
  const currentStep = steps.find((s) => s.state === STEP_STATUS.CURRENT || s.state === STEP_STATUS.WARNING) || steps[0]

  const isDriverPhase = currentStep?.id === 'DISPATCH' ||
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
          {provider && workflow?.assignmentExpiresAt && workflow?.driverStatus === 'PENDING_RESPONSE' && (
            <span className="text-[11px] font-medium text-slate-600 bg-white/80 px-2 py-0.5 rounded border border-slate-200">
              Tài xế cần phản hồi trước: <strong>{formatBookingWhen(workflow.assignmentExpiresAt)}</strong>
            </span>
          )}
        </div>

        {/* Next action description */}
        <p className="text-xs text-slate-800 leading-relaxed font-medium">
          {workflow?.nextAction || 'Theo dõi tiến độ từ nhà xe và tài xế.'}
        </p>

        {!provider && renterCanSeeDriver(status) && (booking.driverName || booking.driver?.fullName) && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-emerald-200 bg-white px-3 py-2.5">
            <div className="min-w-0">
              <span className="block text-[10px] font-bold uppercase tracking-wider text-emerald-800">Tài xế đã nhận chuyến</span>
              <strong className="block text-sm text-slate-900">{booking.driverName || booking.driver?.fullName}</strong>
              {(booking.driverPhone || booking.driver?.phone) && (
                <span className="block font-mono text-xs text-slate-600">{booking.driverPhone || booking.driver?.phone}</span>
              )}
            </div>
            {(booking.driverPhone || booking.driver?.phone) && (
              <a
                href={`tel:${booking.driverPhone || booking.driver?.phone}`}
                className="inline-flex items-center gap-1 rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-bold text-white"
              >
                <Icon name="call" className="text-[14px]" />
                Gọi tài xế
              </a>
            )}
          </div>
        )}

        {/* Anomaly / Overdue Warnings */}
        {provider && pickupAnomaly && (
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

        {provider && isOverdue && !pickupAnomaly && (
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
        {provider && isDriverPhase && (
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

            {!provider && !booking.driverName && <p className="text-sm text-slate-700">Nhà xe đang bố trí tài xế cho chuyến của bạn. Thông tin tài xế sẽ được cập nhật tại đây khi có phân công.</p>}
            {/* Cảnh báo nếu chưa có tài xế */}
            {provider && !booking.driverName && (
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
            {provider && !booking.driverName && (
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
            {provider && booking.driverName && !isDriverConfirmed && !isDriverRejected && (
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
                {booking.providerConfirmedAt && (booking.renterConfirmedAt || workflow?.endConfirmations?.[0]?.confirmationMethod === 'AUTO') && (
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
