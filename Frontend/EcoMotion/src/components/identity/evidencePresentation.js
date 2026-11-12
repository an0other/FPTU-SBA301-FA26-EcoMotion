export const fieldLabels = {
  documentNumber: 'Số giấy tờ', fullName: 'Họ tên', dateOfBirth: 'Ngày sinh', expiryDate: 'Ngày hết hạn',
  sex: 'Giới tính', address: 'Địa chỉ', licenseClass: 'Hạng GPLX', issueDate: 'Ngày cấp',
}

const reasons = {
  REQUIRED_FIELDS_NEED_REVIEW: 'Một số thông tin cần được đối chiếu lại với ảnh giấy tờ.',
  REQUIRED_FIELDS_MISSING: 'Chưa đọc đủ thông tin bắt buộc; hãy kiểm tra ảnh và chụp lại nếu cần.',
  OCR_QR_MISMATCH: 'Thông tin đọc từ ảnh và QR chưa nhất quán, cần người xét duyệt đối chiếu.',
  QR_PAYLOAD_CONFLICT: 'Thông tin từ các mã QR chưa nhất quán.',
  DOCUMENT_TYPE_CONFLICT: 'Loại giấy tờ nhận được chưa nhất quán với yêu cầu.',
  TYPE_FROM_QR_ONLY: 'Loại giấy tờ mới được nhận biết qua QR, cần đối chiếu ảnh.',
  DUPLICATE_INPUT_IMAGES: 'Hai ảnh có dấu hiệu trùng nhau; hãy kiểm tra đúng mặt trước và mặt sau.',
  INPUT_OR_CARD_QUALITY_UNSUITABLE: 'Ảnh giấy tờ chưa đủ rõ để xử lý. Hãy chụp lại, đủ sáng và không lóa.',
  ORIENTATION_NOT_RESOLVED: 'Chưa xác định rõ hướng giấy tờ; hãy đặt thẻ ngay ngắn và chụp lại.',
  POSSIBLE_SIDE_ORDER_ERROR: 'Hãy kiểm tra lại thứ tự ảnh mặt trước và mặt sau.',
  OCR_REGIONS_INCOMPLETE: 'Một số vùng chữ chưa được đọc đầy đủ.',
  UNCROPPED_IMAGE_FALLBACK: 'Chưa xác định rõ vùng giấy tờ; hãy chụp thẻ nằm trọn trong khung.',
  ADDRESS_OCR_QR_NOT_CONFIRMED: 'Địa chỉ chưa được đối chiếu đầy đủ giữa ảnh và QR.',
  STEP_EXECUTION_ERROR: 'Một bước phân tích gặp lỗi; đây không phải quyết định từ chối hồ sơ.',
  INSUFFICIENT_FACE_SAMPLES: 'Chưa có đủ khung hình mặt rõ để đối chiếu ổn định. Hãy nhìn thẳng và quay lại theo hướng dẫn.',
  LOW_FACE_SIMILARITY: 'Điểm tương đồng với ảnh CCCD thấp; cần Admin đối chiếu trước khi quyết định.',
  MATCH_THRESHOLD_NOT_CONFIGURED: 'Chưa có ngưỡng đã được đánh giá cho model này; kết quả đối chiếu do Admin xem xét.',
  MATCH_SCORE_IN_REVIEW_RANGE: 'Điểm đối chiếu nằm trong vùng chưa đủ cơ sở kết luận; cần Admin xem xét.',
  NO_FACE_DETECTED: 'Chưa phát hiện khuôn mặt trong các khung hình video. Hãy đưa mặt vào khung và tăng ánh sáng.',
  NO_SINGLE_FACE_SEQUENCE: 'Video chưa có chuỗi khung hình chỉ gồm một khuôn mặt. Chỉ để một người trong khung hình.',
  HEAD_POSE_UNAVAILABLE: 'Đã thấy mặt nhưng chưa đọc được góc quay đầu. Giữ mặt rõ và quay theo hướng dẫn.',
  MOTION_CHALLENGE_INCOMPLETE: 'Video chưa ghi nhận đủ động tác theo hướng dẫn. Vui lòng quay lại.',
  NO_USABLE_FRONTAL_FACE: 'Chưa có hình ảnh nhìn thẳng sử dụng được. Hãy quay lại ở nơi đủ sáng.',
}

export function evidenceReason(code) {
  return reasons[code] || 'Có kết quả cần được người xét duyệt kiểm tra thêm.'
}

export function qrLabel(status) {
  return ({ PARSED: 'Đã đọc được QR; cần đối chiếu cùng ảnh', DECODED: 'Đã đọc được QR; cần đối chiếu cùng ảnh',
    NOT_READ: 'Chưa đọc được QR; chưa xác định là không có mã', NOT_DETECTED: 'Chưa phát hiện được QR',
    DISABLED: 'Bộ đọc QR đang tắt; chưa thực hiện kiểm tra', CONFLICT: 'Dữ liệu QR chưa nhất quán',
    UNSUPPORTED_FORMAT: 'Định dạng QR chưa được hỗ trợ', UNREADABLE: 'Chưa giải mã được QR',
  })[status] || 'Chưa có kết quả kiểm tra QR'
}

export function finiteScore(value, min = 0, max = 1) {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max ? value : null
}

export function documentNotices(evidence) {
  if (!evidence) return []
  const messages = (evidence.statusReasons || []).map(evidenceReason)
  const missing = (evidence.missingFields || []).map(name => fieldLabels[name]).filter(Boolean)
  const conflicting = (evidence.inconsistentFields || []).map(name => fieldLabels[name]).filter(Boolean)
  if (missing.length) messages.push(`Chưa đọc được: ${missing.join(', ')}.`)
  if (conflicting.length) messages.push(`Thông tin cần đối chiếu: ${conflicting.join(', ')}.`)
  return [...new Set(messages)]
}

// Rendering follows backend completeness, never a client-side confidence threshold.
export function documentCompletion(evidence) {
  const value = evidence?.completeness
  if (!value || !value.policyVersion || !Number.isInteger(value.totalCount) || value.totalCount <= 0 || value.totalCount > 20
    || !Number.isInteger(value.completedCount) || value.completedCount < 0 || value.completedCount > value.totalCount
    || !Array.isArray(value.fields) || value.fields.length !== value.totalCount
    || new Set(value.fields.map(field => field.name)).size !== value.totalCount
    || value.fields.some(field => !fieldLabels[field.name] || !['READ', 'MISSING', 'INVALID', 'REVIEW'].includes(field.status))
    || value.fields.filter(field => field.status === 'READ').length !== value.completedCount
    || value.percentage !== Math.round(value.completedCount * 100 / value.totalCount)) return null
  return value
}

export function verificationPresentation({ evidence, status, video, canContinue = false, canSubmitForReview = false }) {
  const completion = documentCompletion(evidence)
  const final = {
    SUBMITTED: ['pending', 'Đã nhận hồ sơ — đang chờ xét duyệt', 'Bạn không cần gửi lại trong lúc chờ. Bấm Cập nhật trạng thái để theo dõi kết quả.'],
    APPROVED: ['approved', 'Hồ sơ đã được chấp nhận', 'Kết quả xét duyệt đã được ghi nhận. Điều kiện sử dụng dịch vụ được kiểm tra riêng.'],
    REJECTED: ['rejected', 'Hồ sơ chưa được chấp nhận', 'Xem lý do và phản hồi của người xét duyệt bên dưới.'],
    NEEDS_CHANGES: ['review', 'Hồ sơ cần được bổ sung', 'Làm theo hướng dẫn của người xét duyệt và nộp bằng chứng mới ở bước tương ứng.'],
    PROCESSING: ['pending', 'Đang kiểm tra hồ sơ', 'Vui lòng chờ kết quả. Không cần tạo thêm phiên hoặc gửi lặp lại.'],
    CANCELLED: ['neutral', 'Phiên hồ sơ đã được hủy', 'Bạn có thể bắt đầu phiên mới khi sẵn sàng.'],
    EXPIRED: ['neutral', 'Phiên hồ sơ đã hết hạn', 'Bắt đầu phiên mới để tiếp tục. Đây không phải kết luận giấy tờ đã hết hạn.'],
  }[status]
  if (final) return { tone: final[0], title: final[1], description: final[2], showMeter: false, completion, notices: [] }
  const risk = (evidence?.checks || []).some(check => check.scope === 'IMAGE_SIGNAL_ONLY' && check.status === 'FLAGGED')
  const source = evidence?.readingStatus
  if (status === 'ERROR' || source === 'PROCESSING_ERROR') return { tone: 'error', title: 'Chưa thể hoàn tất kiểm tra', description: 'Một bước xử lý gặp lỗi. Hồ sơ chưa bị từ chối; hãy thử lại hoặc cập nhật trạng thái.', showMeter: false, completion, notices: [] }
  if (['RETAKE_REQUIRED', 'UNSUPPORTED_DOCUMENT'].includes(source)) return { tone: 'retake', title: 'Cần chụp lại giấy tờ', description: 'Ảnh hoặc loại giấy tờ chưa đáp ứng yêu cầu. Chụp rõ hai mặt, đủ góc và tránh phản sáng.', showMeter: false, completion, notices: [] }
  if (video?.workflow?.status === 'RETRY_REQUIRED') return { tone: 'video', title: 'Cần thực hiện lại video', description: 'Xem riêng kết quả động tác và dữ liệu khuôn mặt bên dưới để biết phần cần thực hiện lại.', showMeter: true, completion, notices: [...new Set((video.workflow.reasons || []).map(evidenceReason))] }
  const conflicted = (evidence?.inconsistentFields || []).length > 0 || (evidence?.statusReasons || []).some(code => ['OCR_QR_MISMATCH', 'QR_PAYLOAD_CONFLICT', 'DOCUMENT_TYPE_CONFLICT'].includes(code))
  const complete = completion && completion.completedCount === completion.totalCount
  const notices = []
  if (risk) notices.push('Có tín hiệu hình ảnh cần được người xét duyệt kiểm tra thêm. Đây chưa phải kết luận giấy tờ giả.')
  if (conflicted) notices.push('Một số thông tin chưa nhất quán giữa các nguồn. Người xét duyệt cần đối chiếu ảnh gốc.')
  let description = canSubmitForReview
    ? 'Bạn có thể xác nhận gửi hồ sơ. Những mục chưa rõ sẽ được Admin đối chiếu trước khi quyết định.'
    : canContinue ? 'Bạn có thể tiếp tục bước kế tiếp. Hồ sơ vẫn cần được Admin xét duyệt.'
      : 'Kiểm tra các mục chưa rõ và chụp lại nếu cần. Chưa có quyết định chấp nhận hồ sơ.'
  if (!completion) description = 'Hồ sơ này chưa lưu đủ dữ liệu để hiển thị mức hoàn thiện. Bạn có thể xem thông tin đã đọc và trạng thái hồ sơ.'
  return { tone: risk || conflicted || !complete ? 'review' : 'ready',
    title: risk ? 'Hồ sơ cần được kiểm tra bổ sung' : complete && !conflicted ? 'Thông tin giấy tờ đã được đọc' : 'Thông tin giấy tờ cần kiểm tra thêm',
    description, showMeter: true, completion, notices }
}

export function videoCheckSummary(video) {
  const scored = Number.isInteger(video?.faceMatching?.scoredFrames) ? video.faceMatching.scoredFrames : 0
  return {
    motion: (video?.workflow?.reasons || []).some(reason => ['NO_FACE_DETECTED','NO_SINGLE_FACE_SEQUENCE','HEAD_POSE_UNAVAILABLE'].includes(reason)) ? 'Chưa đánh giá được động tác vì hình ảnh khuôn mặt chưa đủ rõ' : video?.motionChallenge?.status === 'PASS' ? 'Đã ghi nhận đủ chuỗi động tác' : video?.motionChallenge?.status === 'INCOMPLETE' ? 'Chuỗi động tác chưa đầy đủ' : 'Chưa có kết quả động tác',
    face: scored > 0 ? `Đã phân tích ${scored} khung hình khuôn mặt để Admin đối chiếu với CCCD` : 'Chưa có khung hình nhìn thẳng đủ rõ để đối chiếu',
    explanation: 'Nhìn thấy khuôn mặt hoặc làm đủ động tác không đồng nghĩa đúng người trong CCCD. Kết quả đối chiếu cần Admin xem xét; hệ thống chưa dùng ngưỡng đã hiệu chuẩn để kết luận cùng người hay khác người.',
  }
}

export function faceAssessmentPresentation(assessment) {
  if (!assessment) return { tone: 'neutral', title: 'Chưa có đánh giá đối chiếu theo chính sách mới', description: 'Có thể xem bằng chứng khuôn mặt; không suy ra cùng người chỉ từ việc phát hiện được mặt.' }
  const states = {
    NO_USABLE_FACE: ['review', 'Chưa có khuôn mặt rõ để đối chiếu', 'Cần quay lại video ở nơi đủ sáng, nhìn thẳng và giữ mặt trong khung.'],
    INSUFFICIENT_EVIDENCE: ['review', 'Chưa đủ khung hình để đánh giá', 'Hãy quay lại video theo hướng dẫn; không coi điểm từ quá ít khung hình là kết luận đáng tin cậy.'],
    NOT_CALIBRATED: ['review', 'Cần Admin đối chiếu khuôn mặt', 'Đã có điểm tương đồng nhưng chưa có ngưỡng được đánh giá bằng dữ liệu cùng người và khác người. Chưa kết luận khớp hoặc không khớp.'],
    LIKELY_MATCH: ['ready', 'Có dấu hiệu tương đồng với ảnh CCCD', 'Đạt ngưỡng thử nghiệm đang cấu hình; hồ sơ vẫn cần Admin duyệt. Đây không phải xác nhận chắc chắn đúng danh tính.'],
    LOW_SIMILARITY: ['review', 'Độ tương đồng với ảnh CCCD thấp', 'Kiểm tra đúng giấy tờ và video của bạn. Admin cần xem hai bằng chứng trước khi quyết định; đây chưa phải kết luận gian lận.'],
    INCONCLUSIVE: ['review', 'Chưa đủ cơ sở kết luận độ khớp', 'Điểm nằm trong vùng cần kiểm tra thủ công. Có thể bổ sung video rõ hơn hoặc chờ Admin đối chiếu.'],
  }
  const row=states[assessment.status]
  return row ? { tone: row[0], title: row[1], description: row[2] } : { tone: 'review', title: 'Kết quả đối chiếu chưa xác định', description: 'Không dùng trạng thái chưa nhận biết để chấp nhận hồ sơ.' }
}
