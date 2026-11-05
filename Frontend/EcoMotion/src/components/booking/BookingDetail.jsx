import { useState } from 'react';
import { readErrorMessage } from '../../services/apiClient';
import { CancelBookingModal } from './CancelBookingModal';
import { bookingStatusMeta, renterCanSeeDriver } from '../../utils/bookingStatus';
import { PickupLocationCard } from './PickupLocationCard';
import { EditBookingRequestModal } from './EditBookingRequestModal';
import LiveMapViewer from '../map/LiveMapViewer';
import { HoldCountdown } from '../payments/HoldCountdown';
import YearlyInstallmentOverview from './YearlyInstallmentOverview';
import { formatCurrency } from '../../utils/formatCurrency';

const DISPUTE_LABELS = {
  CHARGE: 'Phụ phí / khoản phí',
  SERVICE_QUALITY: 'Chất lượng dịch vụ / tài xế',
  DEPOSIT_REFUND: 'Hoàn cọc',
  TRIP_NOT_CONFIRMED: 'Chuyến chưa được xác nhận kết thúc',
};

function getRemainingTimeToPickup(pickupTime, actualStartTime) {
  if (actualStartTime) return 'Chuyến đã bắt đầu';
  if (!pickupTime) return 'Chưa xác định';
  const diffMs = new Date(pickupTime).getTime() - Date.now();
  if (diffMs <= 0) return 'Đã đến giờ đón';
  const hours = Math.floor(diffMs / 3600000);
  const minutes = Math.floor((diffMs % 3600000) / 60000);
  if (hours > 24) {
    const days = Math.floor(hours / 24);
    return `Còn khoảng ${days} ngày nữa đến giờ đón`;
  }
  if (hours > 0) {
    return `Còn khoảng ${hours} giờ ${minutes} phút nữa đến giờ đón`;
  }
  return `Còn khoảng ${minutes} phút nữa đến giờ đón`;
}

function getDriverStatusLabel(booking) {
  if (!booking.driver && !renterCanSeeDriver(booking.status)) {
    return 'Chưa phân công tài xế';
  }
  if (booking.status === 'DRIVER_ASSIGNED') {
    return 'Đã phân công (Chờ tài xế xác nhận)';
  }
  if (booking.status === 'DRIVER_CONFIRMED') {
    return 'Tài xế đã nhận chuyến & sẵn sàng';
  }
  if (booking.status === 'DRIVER_ARRIVED' || booking.status === 'AWAITING_START_CONFIRMATION') {
    return 'Tài xế đã đến điểm đón';
  }
  if (booking.status === 'IN_PROGRESS' || booking.status === 'AWAITING_END_CONFIRMATION') {
    return 'Tài xế đang chở khách';
  }
  if (['COMPLETED', 'AWAITING_FINAL_PAYMENT'].includes(booking.status)) {
    return 'Đã hoàn thành chuyến';
  }
  if (['CANCELLED', 'CANCELLATION_REVIEW'].includes(booking.status)) {
    return 'Chuyến đã hủy';
  }
  return booking.driver ? 'Đã gán tài xế' : 'Chưa phân công';
}

export const BookingDetail = ({
  booking,
  operationalRisk,
  onGoBack,
  onGoToCarPage,
  onPayDeposit,
  onCancelBooking,
  onCompleteBooking,
  onConfirmStart,
  onOpenDispute,
  onAcceptCharge,
  onUploadDisputeEvidence,
  onEditRequest,
  trackingData,
  trackingLoading,
  trackingError,
  onRefreshTracking,
}) => {
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [complaintCategory, setComplaintCategory] = useState('');
  const [complaintCharge, setComplaintCharge] = useState('');
  const [complaintDescription, setComplaintDescription] = useState('');
  const [evidenceList, setEvidenceList] = useState([]);
  const [submittingDispute, setSubmittingDispute] = useState(false);
  const [actionError, setActionError] = useState('');
  const [disputeNote, setDisputeNote] = useState('');
  const [editing, setEditing] = useState(false);

  const statusMeta = bookingStatusMeta(booking.status);

  // Financial metrics
  const totalRentalPrice = Number(booking.totalAmount || 0);
  const requiredDeposit = Number(booking.depositAmount || 0);
  const isDepositSettled = [
    'CONFIRMED',
    'DRIVER_ASSIGNED',
    'DRIVER_CONFIRMED',
    'DRIVER_ARRIVED',
    'IN_PROGRESS',
    'AWAITING_START_CONFIRMATION',
    'AWAITING_END_CONFIRMATION',
    'COMPLETED',
    'AWAITING_FINAL_PAYMENT',
  ].includes(booking.status) || Boolean(booking.depositCollected);

  const recordedDeposit = isDepositSettled ? requiredDeposit : 0;
  const missingDeposit = Math.max(0, requiredDeposit - recordedDeposit);

  const surcharges = (booking.charges || []).filter(
    (c) => !['BASE_RENTAL', 'DEPOSIT'].includes(c.chargeType) && !c.isWaived
  );
  const totalSurcharges = surcharges.reduce((sum, c) => sum + Number(c.amount || 0), 0);
  const paidSurcharges = surcharges
    .filter((c) => c.confirmationStatus === 'CONFIRMED' || c.confirmationStatus === 'PAID')
    .reduce((sum, c) => sum + Number(c.amount || 0), 0);
  const unpaidSurcharges = Math.max(0, totalSurcharges - paidSurcharges);

  const remainingObligation = booking.status === 'AWAITING_DEPOSIT'
    ? requiredDeposit
    : (totalRentalPrice - recordedDeposit) + unpaidSurcharges;

  const handleFileSelect = async (e) => {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    for (const file of files) {
      if (!file.type.startsWith('image/')) {
        setActionError('Chỉ hỗ trợ tệp định dạng hình ảnh (JPG, PNG, WebP).');
        continue;
      }
      if (file.size > 10 * 1024 * 1024) {
        setActionError('Dung lượng ảnh không được vượt quá 10MB.');
        continue;
      }

      const tempId = String(Date.now() + Math.random());
      const previewUrl = URL.createObjectURL(file);

      setEvidenceList((prev) => [
        ...prev,
        { id: tempId, file, previewUrl, url: null, uploading: true, error: null },
      ]);

      if (onUploadDisputeEvidence) {
        try {
          const uploadedUrl = await onUploadDisputeEvidence(file);
          setEvidenceList((prev) =>
            prev.map((item) =>
              item.id === tempId ? { ...item, url: uploadedUrl, uploading: false } : item
            )
          );
        } catch {
          setEvidenceList((prev) =>
            prev.map((item) =>
              item.id === tempId
                ? { ...item, uploading: false, error: 'Tải ảnh thất bại' }
                : item
            )
          );
        }
      }
    }
    e.target.value = '';
  };

  const handleRemoveEvidence = (idToRemove) => {
    setEvidenceList((prev) => {
      const target = prev.find((item) => item.id === idToRemove);
      if (target?.previewUrl) {
        URL.revokeObjectURL(target.previewUrl);
      }
      return prev.filter((item) => item.id !== idToRemove);
    });
  };

  const handleComplaintSubmit = async (e) => {
    e.preventDefault();
    setActionError('');

    if (!complaintDescription.trim()) {
      setActionError('Vui lòng nhập mô tả chi tiết lý do khiếu nại.');
      return;
    }

    const isStillUploading = evidenceList.some((item) => item.uploading);
    if (isStillUploading) {
      setActionError('Hình ảnh bằng chứng đang được tải lên, vui lòng đợi một chút.');
      return;
    }

    setSubmittingDispute(true);
    try {
      const evidenceUrls = evidenceList.map((item) => item.url).filter(Boolean);
      await onOpenDispute({
        category: complaintCategory,
        chargeId: complaintCategory === 'CHARGE' ? Number(complaintCharge) : undefined,
        chargeType: complaintCategory === 'CHARGE' ? complaintCharge : undefined,
        description: complaintDescription.trim(),
        evidenceUrls,
      });
      setDisputeNote('Đã gửi khiếu nại thành công kèm hình ảnh minh chứng. Ban quản trị sẽ thụ lý và phản hồi.');
      setComplaintCategory('');
      setComplaintCharge('');
      setComplaintDescription('');
      setEvidenceList([]);
    } catch (err) {
      setActionError(readErrorMessage(err));
    } finally {
      setSubmittingDispute(false);
    }
  };

  return (
    <div className="w-full max-w-[1120px] mx-auto px-4 py-8 space-y-6">
      {/* Breadcrumb & Navigation */}
      <div className="flex flex-col gap-2">
        <div className="text-sm font-medium text-[#6D7A72] flex items-center gap-1.5">
          <button onClick={onGoBack} className="hover:text-[#14221A] transition-colors cursor-pointer">
            Đơn của tôi
          </button>
          <span>/</span>
          <span className="text-[#14221A] font-medium">Chi tiết đơn</span>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <h1 className="font-mono font-bold text-2xl sm:text-[28px] text-[#14221A] tracking-tight">
              #{booking.id}
            </h1>
            <span className={`px-3.5 py-1 rounded-full border text-xs font-bold ${statusMeta.badgeClass}`}>
              {statusMeta.label}
            </span>
          </div>

          <div className="text-xs sm:text-sm text-slate-500">
            Thời gian tạo đơn: <span className="font-medium text-[#14221A]">{booking.createdAt}</span>
          </div>
        </div>

        {actionError && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
            {actionError}
          </div>
        )}

        {operationalRisk?.atRisk && !['CANCELLED', 'CANCELLATION_REVIEW', 'REJECTED', 'COMPLETED'].includes(booking.status) && (
          <div role="alert" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950">
            <p className="font-bold">Nguy cơ xe về trễ</p>
            <p className="mt-1">{operationalRisk.userMessage}</p>
            <p className="mt-1 text-xs">
              Nhà xe đang xử lý điều phối. Khoản tiền cọc đã nhận được bảo lưu và giải quyết theo chính sách.
            </p>
          </div>
        )}
      </div>

      {booking.planType === 'YEARLY' && (
        <YearlyInstallmentOverview
          bookingCode={booking.id}
          onPay={() => onPayDeposit(booking)}
        />
      )}

      {/* =========================================================================
          KHU VỰC 4: TRẠNG THÁI & HÀNH ĐỘNG THEO VAI TRÒ (Hero Call-To-Action Box)
          ========================================================================= */}
      <section aria-label="Trạng thái và hành động thao tác" className="space-y-4">
        {/* AWAITING_DEPOSIT / PENDING PAYMENT */}
        {booking.status === 'AWAITING_DEPOSIT' && (
          <div className="rounded-2xl border border-emerald-300 bg-emerald-50/80 p-5 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-base font-bold text-emerald-950">Lịch xe đang được giữ chỗ</h2>
              <p className="text-xs text-emerald-800 mt-1">
                Vui lòng chuyển khoản cọc VietQR cho nhà cung cấp xe trước khi hết thời hạn giữ chỗ.
              </p>
            </div>
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              <HoldCountdown expiresAt={booking.holdExpiresAt} />
              {booking.canPay ? (
                <button
                  onClick={() => onPayDeposit(booking)}
                  type="button"
                  className="px-6 py-2.5 bg-brand-700 hover:bg-brand-800 text-white font-bold text-sm rounded-xl shadow-xs transition-colors cursor-pointer whitespace-nowrap"
                >
                  Thanh toán cọc VietQR
                </button>
              ) : null}
            </div>
          </div>
        )}

        {/* PENDING APPROVAL */}
        {booking.status === 'PENDING' && (
          <div className="p-5 bg-white border border-[#E6EBE8] rounded-2xl shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-base font-bold text-[#14221A]">Đơn đang chờ nhà cung cấp phê duyệt</h2>
              <p className="text-xs text-slate-500 mt-1">Chưa thu cọc. Bạn có thể chỉnh sửa yêu cầu hoặc hủy đơn nếu đổi kế hoạch.</p>
            </div>
            <div className="flex items-center gap-2">
              {booking.canEditRequest && (
                <button
                  onClick={() => setEditing(true)}
                  type="button"
                  className="px-4 py-2 text-brand-700 border border-brand-600 hover:bg-brand-50 text-xs font-semibold rounded-xl transition"
                >
                  Chỉnh sửa yêu cầu
                </button>
              )}
              {booking.canCancel && (
                <button
                  onClick={() => setIsCancelModalOpen(true)}
                  type="button"
                  className="px-4 py-2 bg-white text-red-600 border border-red-200 hover:bg-red-50 text-xs font-semibold rounded-xl transition cursor-pointer"
                >
                  Huỷ đơn
                </button>
              )}
            </div>
          </div>
        )}

        {/* REJECTED */}
        {booking.status === 'REJECTED' && (
          <div className="rounded-2xl bg-red-50 border border-red-200 p-5 shadow-xs flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center shrink-0 text-red-600">
                <span className="material-symbols-outlined text-[24px]">cancel</span>
              </div>
              <div>
                <h2 className="text-base font-bold text-red-950">Chủ xe đã từ chối đơn đặt</h2>
                <p className="text-xs text-red-800 mt-0.5">
                  Lý do: {booking.rejectionReason || 'Chủ xe không thể sắp xếp xe vào khung giờ này.'}
                </p>
              </div>
            </div>
            <button
              onClick={onGoToCarPage}
              className="inline-flex items-center justify-center px-5 py-2.5 rounded-xl bg-brand-700 hover:bg-brand-800 text-white text-xs font-bold transition-colors shrink-0 cursor-pointer"
              type="button"
            >
              Chọn xe khác
            </button>
          </div>
        )}

        {/* DRIVER_ARRIVED / AWAITING_START_CONFIRMATION */}
        {['DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION'].includes(booking.status) && (
          <div className="p-5 text-white bg-cyan-700 rounded-2xl shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-base font-bold">Tài xế đã đến điểm hẹn đón</h2>
              <p className="text-xs sm:text-sm text-cyan-100 mt-1">
                Chuyến đi sẽ chính thức bắt đầu sau khi cả bạn và tài xế xác nhận nhận xe.
              </p>
            </div>
            {booking.canConfirmStart ? (
              <button
                onClick={async () => {
                  setActionError('');
                  try {
                    await onConfirmStart(booking.id);
                  } catch (err) {
                    setActionError(readErrorMessage(err));
                  }
                }}
                type="button"
                className="px-5 py-2.5 bg-white text-cyan-800 font-bold text-xs sm:text-sm rounded-xl shadow-xs hover:bg-cyan-50 transition cursor-pointer"
              >
                Xác nhận bắt đầu chuyến
              </button>
            ) : (
              <span className="rounded-lg bg-cyan-800/60 px-3 py-1.5 text-xs font-semibold text-white">
                Đã xác nhận · Đang chờ tài xế
              </span>
            )}
          </div>
        )}

        {/* IN_PROGRESS / AWAITING_END_CONFIRMATION */}
        {['IN_PROGRESS', 'AWAITING_END_CONFIRMATION', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(booking.status) && (
          <div className="p-5 text-white bg-emerald-700 rounded-2xl shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <h2 className="text-base font-bold">
                {booking.status === 'IN_PROGRESS' ? 'Chuyến đi đang thực hiện' : 'Đang xác nhận kết thúc chuyến đi'}
              </h2>
              <p className="text-xs sm:text-sm text-emerald-100">
                {booking.actualStartTime
                  ? `Khởi hành thực tế: ${booking.actualStartTime}`
                  : 'Chuyến xe đang di chuyển cùng bạn.'}
              </p>
            </div>
            {booking.canConfirmEnd ? (
              <button
                onClick={async () => {
                  setActionError('');
                  try {
                    await onCompleteBooking(booking.id);
                  } catch (err) {
                    setActionError(readErrorMessage(err));
                  }
                }}
                type="button"
                className="px-5 py-2.5 bg-white text-emerald-800 font-bold text-xs sm:text-sm rounded-xl shadow-xs hover:bg-emerald-50 transition cursor-pointer"
              >
                Xác nhận kết thúc chuyến
              </button>
            ) : (
              <span className="rounded-lg bg-emerald-800/60 px-3 py-1.5 text-xs font-semibold text-white">
                Đang chờ phía nhà cung cấp xác nhận hoàn tất
              </span>
            )}
          </div>
        )}

        {/* AWAITING_FINAL_PAYMENT (Settlement) */}
        {booking.status === 'AWAITING_FINAL_PAYMENT' && (
          <div className="p-5 bg-purple-50 border border-purple-200 rounded-2xl shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-base font-bold text-purple-950">Chuyến đi đã xong — Chờ quyết toán</h2>
              <p className="text-xs text-purple-800 mt-1">
                Số tiền quyết toán còn lại: <strong>{formatCurrency(remainingObligation)}</strong>. Vui lòng thanh toán để đóng đơn thuê hoàn tất.
              </p>
            </div>
            {booking.canPay && (
              <button
                onClick={() => onPayDeposit(booking)}
                type="button"
                className="px-6 py-2.5 bg-purple-700 hover:bg-purple-800 text-white font-bold text-xs sm:text-sm rounded-xl transition cursor-pointer whitespace-nowrap"
              >
                Thanh toán quyết toán
              </button>
            )}
          </div>
        )}

        {/* CANCELLATION_REVIEW */}
        {booking.status === 'CANCELLATION_REVIEW' && (
          <div className="p-5 bg-amber-50 border border-amber-200 rounded-2xl shadow-xs">
            <h2 className="text-base font-bold text-amber-950">
              {booking.depositCollected ? 'Đơn đã huỷ · Nhà xe đang chuyển hoàn cọc' : 'Đang đối soát yêu cầu huỷ đơn'}
            </h2>
            <p className="text-xs text-amber-800 mt-1 leading-relaxed">
              {booking.depositCollected
                ? 'Tiền cọc của bạn đã được ghi nhận an toàn. Nhà cung cấp đang xử lý chuyển khoản hoàn cọc theo chính sách.'
                : 'Hệ thống đang đối soát trạng thái giao dịch trước khi hoàn tất thủ tục huỷ.'}
            </p>
          </div>
        )}

        {/* COMPLETED */}
        {booking.status === 'COMPLETED' && (
          <div className="p-5 bg-slate-50 border border-slate-200 rounded-2xl flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-emerald-100 flex items-center justify-center text-emerald-700">
                <span className="material-symbols-outlined text-[22px]">check_circle</span>
              </div>
              <div>
                <h2 className="text-sm font-bold text-slate-900">Đơn thuê đã hoàn tất trọn vẹn</h2>
                <p className="text-xs text-slate-500">Cảm ơn bạn đã lựa chọn phương tiện di chuyển xanh EcoMotion.</p>
              </div>
            </div>
            <button
              onClick={onGoToCarPage}
              className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-white"
            >
              Đặt chuyến mới
            </button>
          </div>
        )}
      </section>

      {/* =========================================================================
          GPS LIVE TRACKING (WHEN DRIVER EN ROUTE OR TRIP IN PROGRESS)
          ========================================================================= */}
      {['DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION'].includes(booking.status) && (
        <section className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-bold text-[#14221A]">Vị trí xe theo thời gian thực</h3>
              <p className="mt-1 text-xs text-slate-500">
                {trackingData?.latestLocation?.receivedAt
                  ? `Cập nhật lúc ${new Date(trackingData.latestLocation.receivedAt).toLocaleTimeString('vi-VN')}${trackingData.latestLocation.accuracyMeters ? ` • sai số khoảng ${Math.round(trackingData.latestLocation.accuracyMeters)} m` : ''}`
                  : 'Đang chờ tín hiệu GPS từ tài xế.'}
              </p>
            </div>
            <button
              type="button"
              onClick={onRefreshTracking}
              disabled={trackingLoading}
              className="rounded-lg border border-brand-600 px-3 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-50"
            >
              {trackingLoading ? 'Đang cập nhật...' : 'Làm mới vị trí'}
            </button>
          </div>
          {trackingError && <p className="mb-3 text-xs text-red-600">{trackingError}</p>}
          <LiveMapViewer
            vehicleLocation={trackingData?.latestLocation}
            pickupLocation={{ latitude: booking.pickupLatitude, longitude: booking.pickupLongitude, address: booking.pickupAddress }}
            vehicleInfo={{ licensePlate: booking.vehicle.plateNumber, name: booking.vehicle.name }}
            height="320px"
          />
        </section>
      )}

      {/* =========================================================================
          KHU VỰC 1 & 2: THÔNG TIN ĐƠN XE VÀ THỜI GIAN THUÊ
          ========================================================================= */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {/* KHU VỰC 1: THÔNG TIN ĐƠN XE */}
        <section aria-label="Thông tin đơn xe" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400">
                1. Thông tin đơn xe
              </h2>
              <span className={`px-2.5 py-0.5 rounded-full border text-[11px] font-bold ${statusMeta.badgeClass}`}>
                {statusMeta.label}
              </span>
            </div>

            <div className="mt-4 flex gap-4 items-start">
              <img
                src={booking.vehicle.image}
                alt={booking.vehicle.name}
                className="h-20 w-24 rounded-xl object-cover border border-slate-200 bg-slate-100 shrink-0"
              />
              <div className="min-w-0 flex-1">
                <h3 className="text-base font-bold text-slate-900 truncate">{booking.vehicle.name}</h3>
                <p className="mt-0.5 font-mono text-xs font-bold text-brand-700 bg-brand-50 inline-block px-2 py-0.5 rounded border border-brand-200">
                  {booking.vehicle.plateNumber}
                </p>
                <div className="mt-2 text-xs text-slate-500">
                  Gói thuê: <strong className="text-slate-800">{booking.planTitle}</strong>
                </div>
              </div>
            </div>

            <dl className="mt-4 space-y-2 border-t border-slate-100 pt-3 text-xs">
              <div className="flex justify-between items-center">
                <dt className="text-slate-500">Mã đơn đặt xe:</dt>
                <dd className="font-mono font-bold text-slate-900">{booking.id}</dd>
              </div>
              <div className="flex justify-between items-center">
                <dt className="text-slate-500">Nhà cung cấp xe (Provider):</dt>
                <dd className="font-semibold text-slate-900">{booking.providerName || 'EcoMotion Partner'}</dd>
              </div>
              <div className="flex justify-between items-center">
                <dt className="text-slate-500">Người thuê xe (Renter):</dt>
                <dd className="font-semibold text-slate-900">{booking.renterName || 'Khách thuê'}</dd>
              </div>
              <div className="flex justify-between items-center">
                <dt className="text-slate-500">Trạng thái tài xế:</dt>
                <dd className="font-semibold text-slate-900">{getDriverStatusLabel(booking)}</dd>
              </div>
            </dl>
          </div>

          {booking.driver && renterCanSeeDriver(booking.status) && (
            <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <img
                  src={booking.driver.avatar}
                  alt={booking.driver.name}
                  className="h-9 w-9 rounded-full object-cover ring-1 ring-slate-200"
                />
                <div className="text-xs">
                  <p className="font-bold text-slate-900">{booking.driver.name}</p>
                  <p className="font-mono text-slate-500">{booking.driver.phone}</p>
                </div>
              </div>
              <a
                href={`tel:${booking.driver.phone}`}
                className="rounded-lg bg-brand-700 px-3 py-1.5 text-[11px] font-bold text-white hover:bg-brand-800 transition"
              >
                Gọi tài xế
              </a>
            </div>
          )}
        </section>

        {/* KHU VỰC 2: THỜI GIAN THUÊ */}
        <section aria-label="Thời gian thuê xe" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400">
                2. Thời gian thuê
              </h2>
              <span className="text-xs font-semibold text-brand-700 bg-brand-50 px-2.5 py-0.5 rounded-full border border-brand-200">
                {getRemainingTimeToPickup(booking.pickupTime, booking.actualStartAt)}
              </span>
            </div>

            <dl className="mt-4 space-y-2.5 text-xs">
              <div className="flex justify-between items-start">
                <dt className="text-slate-500">Bắt đầu dự kiến:</dt>
                <dd className="font-semibold text-slate-900 text-right">{booking.formattedStart || '—'}</dd>
              </div>
              <div className="flex justify-between items-start">
                <dt className="text-slate-500">Kết thúc dự kiến:</dt>
                <dd className="font-semibold text-slate-900 text-right">{booking.formattedEnd || '—'}</dd>
              </div>
              <div className="flex justify-between items-start border-t border-slate-100 pt-2">
                <dt className="text-slate-500">Bắt đầu thực tế:</dt>
                <dd className="font-semibold text-slate-900 text-right">
                  {booking.formattedActualStart || 'Chưa khởi hành'}
                </dd>
              </div>
              <div className="flex justify-between items-start">
                <dt className="text-slate-500">Kết thúc thực tế:</dt>
                <dd className="font-semibold text-slate-900 text-right">
                  {booking.formattedActualEnd || 'Chưa kết thúc'}
                </dd>
              </div>
              <div className="flex justify-between items-start border-t border-slate-100 pt-2">
                <dt className="text-slate-500">Thời lượng dự kiến:</dt>
                <dd className="font-semibold text-slate-900 text-right">
                  {booking.durationLabel || `${booking.slots} slot`}
                </dd>
              </div>
            </dl>
          </div>

          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Điểm đón khách:</span>
            <p className="mt-1 font-semibold text-slate-800 leading-snug">{booking.pickupAddress}</p>
            {booking.driverNote && (
              <p className="mt-1 text-[11px] text-slate-500 italic">
                Ghi chú cho tài xế: &ldquo;{booking.driverNote}&rdquo;
              </p>
            )}
          </div>
        </section>
      </div>

      {/* Pickup distance proof card if applicable */}
      <PickupLocationCard
        pickupAddress={booking.pickupAddress}
        pickupLatitude={booking.pickupLatitude}
        pickupLongitude={booking.pickupLongitude}
        driverNote={booking.driverNote}
        originLatitude={booking.dispatchOriginLatitude}
        originLongitude={booking.dispatchOriginLongitude}
        distanceKm={booking.distanceKm}
        freeRadiusKm={booking.freeRadiusKm}
        chargeableKm={booking.chargeableKm}
        outOfRadiusRate={booking.outOfRadiusRate}
        pickupFee={booking.pickupFee}
      />

      {/* =========================================================================
          KHU VỰC 3: THÔNG TIN TÀI CHÍNH
          ========================================================================= */}
      <section aria-label="Thông tin tài chính" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-5">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400">
            3. Thông tin tài chính
          </h2>
          <span className="text-xs text-slate-500">
            Nghĩa vụ còn lại: <strong className="text-brand-700 text-sm">{formatCurrency(remainingObligation)}</strong>
          </span>
        </div>

        {/* 4 Stat Metric Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
            <span className="text-[11px] font-semibold uppercase text-slate-500">Tổng giá thuê</span>
            <p className="mt-1 text-base font-bold text-slate-900">{formatCurrency(totalRentalPrice)}</p>
          </div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
            <span className="text-[11px] font-semibold uppercase text-slate-500">Cọc phải trả</span>
            <p className="mt-1 text-base font-bold text-slate-900">{formatCurrency(requiredDeposit)}</p>
          </div>
          <div className="rounded-xl border border-emerald-200 bg-emerald-50/60 p-3.5">
            <span className="text-[11px] font-semibold uppercase text-emerald-800">Cọc đã ghi nhận</span>
            <p className="mt-1 text-base font-bold text-emerald-700">{formatCurrency(recordedDeposit)}</p>
          </div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
            <span className="text-[11px] font-semibold uppercase text-slate-500">Cọc còn thiếu</span>
            <p className={`mt-1 text-base font-bold ${missingDeposit > 0 ? 'text-amber-700' : 'text-slate-700'}`}>
              {formatCurrency(missingDeposit)}
            </p>
          </div>
        </div>

        {/* Financial Breakdown Table */}
        <div className="rounded-xl border border-slate-200 overflow-hidden text-xs">
          <table className="w-full text-left">
            <thead className="bg-slate-50 border-b border-slate-200 text-slate-600">
              <tr>
                <th className="py-2.5 px-3 font-semibold">Hạng mục tài chính</th>
                <th className="py-2.5 px-3 font-semibold text-right">Phát sinh</th>
                <th className="py-2.5 px-3 font-semibold text-right">Đã thanh toán</th>
                <th className="py-2.5 px-3 font-semibold text-right">Còn lại</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr>
                <td className="py-2.5 px-3 font-medium text-slate-900">Tiền cọc giữ chỗ</td>
                <td className="py-2.5 px-3 text-right font-mono">{formatCurrency(requiredDeposit)}</td>
                <td className="py-2.5 px-3 text-right font-mono text-emerald-700">{formatCurrency(recordedDeposit)}</td>
                <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">{formatCurrency(missingDeposit)}</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-medium text-slate-900">
                  Phụ phí phát sinh ({surcharges.length} khoản)
                </td>
                <td className="py-2.5 px-3 text-right font-mono">{formatCurrency(totalSurcharges)}</td>
                <td className="py-2.5 px-3 text-right font-mono text-emerald-700">{formatCurrency(paidSurcharges)}</td>
                <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">{formatCurrency(unpaidSurcharges)}</td>
              </tr>
              <tr className="bg-slate-50 font-bold">
                <td className="py-2.5 px-3 text-slate-900">Tổng nghĩa vụ tài chính còn lại</td>
                <td className="py-2.5 px-3 text-right font-mono">{formatCurrency(totalRentalPrice + totalSurcharges)}</td>
                <td className="py-2.5 px-3 text-right font-mono text-emerald-700">{formatCurrency(recordedDeposit + paidSurcharges)}</td>
                <td className="py-2.5 px-3 text-right font-mono text-brand-700 text-sm">
                  {formatCurrency(remainingObligation)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* Refund information if applicable */}
        {booking.refundAmount > 0 && (
          <div className="rounded-xl border border-blue-200 bg-blue-50/70 p-4 text-xs text-blue-900 flex items-start gap-3">
            <span className="material-symbols-outlined text-[20px] text-blue-700 shrink-0 mt-0.5">currency_exchange</span>
            <div className="space-y-1">
              <strong className="block font-bold">Thông tin hoàn cọc: {formatCurrency(booking.refundAmount)}</strong>
              <p className="text-blue-800">
                {booking.refundExplanation || 'Khoản tiền hoàn cọc đang được nhà cung cấp và hệ thống xử lý chuyển khoản hoàn trả.'}
              </p>
            </div>
          </div>
        )}

        {/* Surcharges list if any */}
        {surcharges.length > 0 && (
          <div className="space-y-2">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">Chi tiết phụ phí</h3>
            <div className="overflow-x-auto rounded-xl border border-slate-200">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-slate-600">
                    <th className="py-2 px-3 font-semibold">Khoản</th>
                    <th className="py-2 px-3 font-semibold">Mô tả</th>
                    <th className="py-2 px-3 font-semibold text-right">Cách tính</th>
                    <th className="py-2 px-3 font-semibold text-right">Số tiền</th>
                    <th className="py-2 px-3 font-semibold text-center">Trạng thái</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {surcharges.map((charge) => (
                    <tr key={charge.chargeId ?? `${charge.chargeType}-${charge.description}`}>
                      <td className="py-2.5 px-3 font-medium text-slate-900">{charge.label}</td>
                      <td className="py-2.5 px-3 text-slate-600">
                        {charge.description}
                        {/^https?:\/\//i.test(charge.evidenceUrl || '') && (
                          <a
                            href={charge.evidenceUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="mt-0.5 block text-brand-700 underline"
                          >
                            Xem minh chứng
                          </a>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono text-slate-500">{charge.formula}</td>
                      <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">
                        {formatCurrency(charge.amount)}
                      </td>
                      <td className="py-2.5 px-3 text-center">
                        {charge.confirmationStatus === 'PENDING' ? (
                          <button
                            type="button"
                            className="rounded-lg border border-brand-600 px-2 py-1 text-[11px] font-semibold text-brand-700 hover:bg-brand-50"
                            onClick={() => onAcceptCharge?.(charge.chargeId)}
                          >
                            Chấp nhận
                          </button>
                        ) : charge.confirmationStatus === 'DISPUTED' ? (
                          <span className="rounded bg-rose-50 px-2 py-0.5 text-[11px] font-semibold text-rose-700">
                            Đang khiếu nại
                          </span>
                        ) : (
                          <span className="rounded bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
                            Đã xác nhận
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>

      {/* =========================================================================
          TIẾN TRÌNH ĐƠN (TIMELINE)
          ========================================================================= */}
      <section aria-label="Tiến trình đơn" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
        <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400 mb-4">
          Tiến trình đơn xe
        </h3>
        <div className="flex flex-col gap-4 relative pl-6 border-l-2 border-slate-200 ml-2">
          {booking.timeline.map((item, idx) => (
            <div key={idx} className="relative flex items-start gap-3">
              <div
                className={`absolute -left-[31px] top-1.5 w-3 h-3 rounded-full shrink-0 ring-2 ${
                  item.isFailed
                    ? 'bg-red-600 ring-red-100'
                    : item.completed
                    ? 'bg-brand-700 ring-brand-100'
                    : 'bg-slate-300 ring-slate-100'
                }`}
              />
              <div className="flex flex-col">
                <span className={`text-xs sm:text-sm font-bold ${item.isFailed ? 'text-red-700' : 'text-slate-900'}`}>
                  {item.title}
                </span>
                <span className="font-mono text-[11px] text-slate-400 mt-0.5">
                  {item.timestamp}
                </span>
                {item.description && (
                  <p className="text-xs text-slate-600 mt-0.5">{item.description}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* =========================================================================
          KHIẾU NẠI (DISPUTES)
          ========================================================================= */}
      {booking.disputeCategories.length > 0 && (
        <section aria-label="Khiếu nại" className="bg-white p-5 flex flex-col gap-4 rounded-2xl border border-slate-200 shadow-xs">
          <h3 className="text-sm font-bold text-[#14221A]">Khiếu nại dịch vụ / Phụ phí</h3>
          <form onSubmit={handleComplaintSubmit} className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="complaint-category-select" className="text-xs font-semibold text-[#14221A]">Loại khiếu nại</label>
              <select
                id="complaint-category-select"
                value={complaintCategory}
                onChange={(e) => setComplaintCategory(e.target.value)}
                required
                className="w-full h-10 px-3 rounded-xl text-xs sm:text-sm bg-slate-50 border border-slate-200 outline-none cursor-pointer"
              >
                <option value="" disabled>-- Chọn loại khiếu nại --</option>
                {booking.disputeCategories.map((category) => (
                  <option key={category} value={category}>{DISPUTE_LABELS[category] || category}</option>
                ))}
              </select>
            </div>

            {complaintCategory === 'CHARGE' && (
              <div className="flex flex-col gap-1.5">
                <label htmlFor="complaint-charge-select" className="text-xs font-semibold text-[#14221A]">Khoản phí</label>
                <select
                  id="complaint-charge-select"
                  value={complaintCharge}
                  onChange={(e) => setComplaintCharge(e.target.value)}
                  required
                  className="w-full h-10 px-3 rounded-xl text-xs sm:text-sm bg-slate-50 border border-slate-200 outline-none cursor-pointer"
                >
                  <option value="" disabled>-- Chọn khoản phí --</option>
                  {booking.charges.filter((charge) => !charge.isWaived && !['BASE_RENTAL', 'DEPOSIT'].includes(charge.chargeType)).map((charge) => (
                    <option key={charge.chargeId} value={charge.chargeId}>{charge.label}</option>
                  ))}
                </select>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label htmlFor="complaint-description-textarea" className="text-xs font-semibold text-[#14221A]">
                Mô tả chi tiết lý do khiếu nại <span className="text-red-500">*</span>
              </label>
              <textarea
                id="complaint-description-textarea"
                rows={3}
                required
                value={complaintDescription}
                onChange={(e) => setComplaintDescription(e.target.value)}
                placeholder="Nhập lý do khiếu nại cụ thể (ví dụ: Xe không đúng như mô tả, tài xế tính phụ phí không hợp lý...)"
                className="w-full p-3 rounded-xl text-xs sm:text-sm bg-slate-50 border border-slate-200 outline-none focus:border-brand-600 focus:bg-white transition resize-none"
              />
            </div>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-[#14221A]">
                  Ảnh chụp minh chứng đính kèm
                </label>
                <span className="text-[11px] text-slate-500">
                  {evidenceList.length} ảnh đã chọn
                </span>
              </div>

              <label className="flex items-center justify-center gap-2 px-4 py-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 hover:bg-slate-100 cursor-pointer transition text-xs font-medium text-slate-600">
                <span className="material-symbols-outlined text-[18px] text-slate-400">add_photo_alternate</span>
                <span>Chọn ảnh minh chứng (JPG, PNG, WebP)</span>
                <input
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp"
                  onChange={handleFileSelect}
                  className="hidden"
                />
              </label>

              {evidenceList.length > 0 && (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 pt-1">
                  {evidenceList.map((item) => (
                    <div key={item.id} className="relative group rounded-xl overflow-hidden border border-slate-200 aspect-square bg-slate-100">
                      <img
                        src={item.previewUrl}
                        alt="Bằng chứng"
                        className="w-full h-full object-cover"
                      />
                      {item.uploading && (
                        <div className="absolute inset-0 bg-black/40 flex items-center justify-center text-white text-[10px] font-bold">
                          Đang tải...
                        </div>
                      )}
                      {item.error && (
                        <div className="absolute inset-0 bg-red-600/70 flex items-center justify-center text-white text-[10px] p-1 text-center font-bold">
                          Lỗi tải ảnh
                        </div>
                      )}
                      <button
                        type="button"
                        onClick={() => handleRemoveEvidence(item.id)}
                        className="absolute top-1 right-1 w-5 h-5 bg-black/60 hover:bg-black/80 rounded-full text-white text-xs flex items-center justify-center opacity-80 group-hover:opacity-100 transition cursor-pointer"
                        title="Xóa ảnh"
                      >
                        ×
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {disputeNote && <p className="text-xs text-brand-700 font-semibold">{disputeNote}</p>}

            <div className="flex justify-end pt-1">
              <button
                type="submit"
                disabled={submittingDispute}
                className="px-5 py-2 font-bold text-xs border border-red-500 text-red-600 hover:bg-red-50 rounded-xl transition cursor-pointer disabled:opacity-50"
              >
                {submittingDispute ? 'Đang gửi...' : 'Gửi khiếu nại'}
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Cancel Confirmation Dialog Modal */}
      <CancelBookingModal
        bookingCode={booking.id}
        isOpen={isCancelModalOpen}
        onClose={() => setIsCancelModalOpen(false)}
        refundExplanation={booking.refundExplanation}
        refundAmount={booking.refundAmount}
        requiresBankAccount={booking.refundRequiresBankAccount}
        onConfirmCancel={async (account) => {
          setActionError('');
          try {
            await onCancelBooking(booking.id, account);
            setIsCancelModalOpen(false);
          } catch (err) {
            setActionError(readErrorMessage(err));
            throw err;
          }
        }}
      />

      {editing && (
        <EditBookingRequestModal
          booking={booking}
          onClose={() => setEditing(false)}
          onSave={onEditRequest}
        />
      )}
    </div>
  );
};
