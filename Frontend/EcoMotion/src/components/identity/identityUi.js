export const buttonClass = 'rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50'
export const secondaryClass = 'rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50'
export const inputClass = 'w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 disabled:bg-slate-100'
export const cardClass = 'rounded-2xl border border-slate-200 bg-white p-5 shadow-sm'

export const statusLabels = {
  DRAFT: 'Chưa gửi hồ sơ', PROCESSING: 'Đang xử lý', DOCUMENT_READY: 'Đã đọc CCCD',
  READY_TO_SUBMIT: 'Sẵn sàng xác nhận gửi', SUBMITTED: 'Đang chờ Admin duyệt', APPROVED: 'Đã được duyệt',
  NEEDS_CHANGES: 'Cần bổ sung', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy', EXPIRED: 'Đã hết hạn', ERROR: 'Cần thử lại',
}

const messages = {
  IDENTITY_THRESHOLD_NOT_CONFIGURED: 'Hệ thống chưa cấu hình ngưỡng xác minh. Vui lòng liên hệ quản trị viên.',
  IDENTITY_AI_UNAVAILABLE: 'Dịch vụ phân tích chưa sẵn sàng. Vui lòng thử lại sau.',
  IDENTITY_AI_AUTH_FAILED: 'Hệ thống chưa xác thực được với dịch vụ phân tích. Vui lòng liên hệ quản trị viên.',
  IDENTITY_AI_ENDPOINT_MISSING: 'Dịch vụ phân tích cần được cập nhật. Vui lòng liên hệ quản trị viên.',
  IDENTITY_AI_INVALID_RESPONSE: 'Hệ thống chưa đọc được kết quả từ dịch vụ phân tích. Vui lòng liên hệ quản trị viên.',
  IDENTITY_AI_INPUT_REJECTED: 'Ảnh hoặc video chưa hợp lệ. Hãy kiểm tra khuôn mặt rõ, chỉ một người và video đúng giới hạn.',
  IDENTITY_AI_TIMEOUT: 'Phân tích quá thời gian. Hãy cập nhật trạng thái trước khi thử gửi lại.',
  IDENTITY_AI_BUSY: 'Hệ thống đang xử lý yêu cầu khác. Vui lòng thử lại sau ít phút.',
  IDENTITY_ATTEMPT_STALE: 'Hồ sơ đã thay đổi. Hãy cập nhật trạng thái rồi thực hiện lại.',
  PROVIDER_APPLICATION_STALE: 'Hồ sơ đã thay đổi. Hãy cập nhật trước khi gửi lại.',
  IDENTITY_FACE_RETRY_REQUIRED: 'Chưa đủ điều kiện đối chiếu khuôn mặt. Hãy quay lại ở nơi đủ sáng và giữ mặt rõ trong khung.',
  VIDEO_RETAKE_REQUIRED: 'Video chưa hoàn thành hướng dẫn hoặc chưa đủ hình ảnh rõ. Vui lòng quay lại.',
  DOCUMENT_RETAKE_REQUIRED: 'Chưa đọc đủ thông tin CCCD. Hãy chụp rõ cả hai mặt, tránh lóa sáng.',
  IDENTITY_PROCESSING_TIMEOUT: 'Lượt xử lý đã quá thời gian. Bạn có thể gửi lại.',
  IDENTITY_LINK_REQUIRED: 'CCCD đã có trên một tài khoản. Hãy dùng tính năng liên kết hai tài khoản.',
  IDENTITY_CANCEL_ACTIVE_ATTEMPT_FIRST: 'Hãy hủy phiên xác minh hiện tại trước khi xác nhận liên kết.',
  IDENTITY_LINK_STALE_OR_EXPIRED: 'Yêu cầu liên kết đã thay đổi hoặc hết hạn. Vui lòng tạo yêu cầu mới.',
  IDENTITY_LINK_NOT_ALLOWED: 'Hai tài khoản chưa đủ điều kiện liên kết. Cần khác vai trò và một tài khoản đã được xác minh.',
  IDENTITY_LINK_TARGET_INVALID: 'Tài khoản đích chưa hợp lệ để liên kết.',
  IDENTITY_PHONE_VERIFICATION_REQUIRED: 'Bạn cần xác nhận số điện thoại trước khi gửi.',
  PROVIDER_IDENTITY_APPROVAL_REQUIRED: 'Cần hoàn tất duyệt danh tính trước khi gửi hồ sơ Provider.',
  PROVIDER_ACCOUNT_VERIFICATION_REQUIRED: 'Bạn cần hoàn tất xác nhận tài khoản và số điện thoại.',
  DOCUMENT_UNREADABLE: 'Giấy tờ chưa rõ. Hãy gửi lại ảnh rõ nét, không lóa.',
  FACE_UNCLEAR: 'Khuôn mặt hoặc video chưa rõ. Vui lòng quay lại ở nơi đủ sáng.',
  DETAILS_INCONSISTENT: 'Thông tin chưa nhất quán. Vui lòng kiểm tra và bổ sung hồ sơ.',
  ADDRESS_REQUIRED: 'Vui lòng kiểm tra, bổ sung địa chỉ và vị trí giao nhận.',
  BANK_FIELDS_REQUIRED: 'Vui lòng kiểm tra thông tin ngân hàng, số tài khoản và chủ tài khoản.',
  OTHER_REVIEW_REQUIRED: 'Hồ sơ cần được bổ sung hoặc kiểm tra thêm. Vui lòng liên hệ hỗ trợ nếu chưa rõ.',
  APPEAL_ACCEPTED: 'Admin đã mở lại hồ sơ để bạn bổ sung và gửi lại.',
}

export function identityMessage(error) {
  const code = typeof error === 'string' ? error : error?.errorCode || error?.original?.response?.data?.errorCode
  return messages[code] || (typeof error === 'string' ? error.replaceAll('_', ' ') : error?.message) || 'Không thể thực hiện. Vui lòng thử lại.'
}

export function humanDate(value) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—'
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 10000)
}
