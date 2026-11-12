export function uploadPercentage(event) {
  if (!event || !Number.isFinite(event.loaded) || !Number.isFinite(event.total) || event.total <= 0 || event.loaded < 0) return null
  return Math.max(0, Math.min(100, Math.round(event.loaded * 100 / event.total)))
}

export function processingPresentation(task, remote, now = Date.now()) {
  const processing = remote?.status === 'PROCESSING'
  const stage = processing ? remote.processingStage : task?.kind
  const type = stage === 'VIDEO' ? 'video' : 'ảnh giấy tờ'
  let seconds = Math.max(0, Math.floor((now - (task?.startedAt ?? now)) / 1000))
  const observed = Date.parse(remote?.observedAt)
  const started = Date.parse(remote?.startedAt)
  if (processing && Number.isFinite(observed) && Number.isFinite(started)) seconds = Math.max(0, Math.floor((observed - started + now - (remote.receivedAt ?? now)) / 1000))
  const percent = typeof task?.uploadPercent === 'number' && Number.isFinite(task.uploadPercent) ? Math.max(0, Math.min(100, task.uploadPercent)) : null
  const failed = remote?.status === 'ERROR'
  const returning = !processing && remote?.seenProcessing
  const uploading = !processing && !returning && task?.active && percent !== 100 && task?.phase === 'UPLOAD'
  const timingKnown = Number.isFinite(task?.startedAt) || (processing && Number.isFinite(observed) && Number.isFinite(started))
  return { type, seconds, timingKnown, percent, uploading, processing, failed, returning, slow: !failed && !returning && seconds >= 45,
    title: failed ? 'Không thể hoàn tất xử lý' : uploading ? `Đang gửi ${type}` : processing ? `Hệ thống đang phân tích ${type}` : returning ? 'Đang đồng bộ kết quả xử lý' : 'Đang chờ hệ thống tiếp nhận và trả kết quả',
    step: returning ? 2 : uploading ? 0 : processing ? 1 : 0 }
}
