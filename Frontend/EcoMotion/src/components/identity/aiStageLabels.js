const labels = {
  QUEUED: 'AI đã tiếp nhận tác vụ', PROCESSING: 'AI đang xử lý',
  VALIDATING_FILES: 'Kiểm tra định dạng ảnh', CHECKING_SOURCE_QUALITY: 'Kiểm tra chất lượng ảnh',
  ANALYZING_IMAGE_SIGNALS: 'Phân tích đặc điểm ảnh', APPLYING_SCREENING_POLICY: 'Tổng hợp kiểm tra ảnh',
  LOCALIZING_AND_CHECKING_QUALITY: 'Xác định vùng giấy tờ', QUICK_QR_PAIR_CHECK: 'Đọc và đối chiếu QR',
  CHECKING_ORIENTATION_AND_TYPE: 'Xác định chiều và loại giấy tờ', READING_FIELDS: 'Đọc chữ trên giấy tờ',
  COMPARING_OCR_AND_QR: 'Đối chiếu chữ trên ảnh với QR', EXTRACTING_AND_VALIDATING_FIELDS: 'Kiểm tra thông tin đọc được',
  LOADING_MODELS: 'Chuẩn bị bộ xử lý khuôn mặt', REFERENCE_FACE: 'Xác định khuôn mặt trên CCCD',
  READING_VIDEO: 'Đọc video', ANALYZING_VIDEO: 'Kiểm tra động tác và so sánh khuôn mặt',
  ASSESSING_FACE_SIMILARITY: 'Tổng hợp điểm đối chiếu khuôn mặt với CCCD',
  CHECKING_CHALLENGE: 'Tổng hợp chuỗi động tác', FINALIZING: 'Tổng hợp kết quả', COMPLETED: 'AI đã xử lý xong',
}
export function aiStageLabel(progress) {
  if (!progress || !labels[progress.stage]) return null
  const side = progress.side === 'FRONT' ? ' — mặt trước' : progress.side === 'BACK' ? ' — mặt sau' : ''
  return labels[progress.stage] + side
}

export function aiReportedSteps(progress) {
  if (!Array.isArray(progress?.steps)) return []
  return progress.steps.slice(-80).filter(step => step && typeof step.stage === 'string').map((step, index) => ({
    key: `${index}-${step.stage}-${step.side || 'PAIR'}`,
    label: aiStageLabel(step) || 'Bước xử lý khác do AI báo cáo',
    stage: step.stage,
    side: step.side,
  }))
}
