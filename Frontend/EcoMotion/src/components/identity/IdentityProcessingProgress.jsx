import { useEffect, useRef, useState } from 'react'
import { aiReportedSteps, aiStageLabel } from './aiStageLabels'
import { identityMessage } from './identityUi'
import { processingPresentation } from './identityProcessing'

export default function IdentityProcessingProgress({ task, remote, pollError = false, completed = false }) {
  const [now, setNow] = useState(Date.now)
  const timeline = useRef(null)
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer) }, [])
  const state = processingPresentation(task, remote, completed ? task?.finishedAt || now : now)
  const stage = remote?.aiProgress?.stage
  const aiStage = aiStageLabel(remote?.aiProgress)
  const steps = aiReportedSteps(remote?.aiProgress)
  useEffect(() => { if (timeline.current) timeline.current.scrollTop = timeline.current.scrollHeight }, [steps.length, stage])
  const aiFinished = stage === 'COMPLETED'
  const title = state.failed && aiFinished ? 'AI đã hoàn tất · lỗi ở bước xử lý kết quả' : completed && remote?.status === 'CANCELLED' ? 'Lượt phân tích đã hủy' : completed && !state.failed ? 'Đã nhận kết quả phân tích' : state.failed ? 'Không thể hoàn tất xử lý'
    : pollError ? 'Đang kết nối lại để cập nhật tiến trình'
      : state.returning || (state.processing && aiFinished) ? 'AI đã xử lý xong, đang cập nhật hồ sơ'
        : state.processing && aiStage ? aiStage : state.title
  const uploadPercent = state.uploading ? state.percent : null
  return <section className="space-y-3 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-blue-950" aria-label="Tiến trình xử lý hồ sơ" aria-busy={!state.failed && !completed}>
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-semibold" role="status" aria-live="polite">{title}</h3>
      <span className="text-xs tabular-nums">{state.timingKnown ? `Đã chờ ${state.seconds} giây` : 'Đang lấy thời gian xử lý'}</span>
    </div>
    {!state.failed && !completed && <div role="progressbar" aria-label={state.uploading ? 'Gửi file' : 'Xử lý hồ sơ theo bước AI'} aria-valuetext={title}
      aria-valuemin={uploadPercent !== null ? 0 : undefined} aria-valuemax={uploadPercent !== null ? 100 : undefined} aria-valuenow={uploadPercent ?? undefined}
      className="h-2 overflow-hidden rounded-full bg-blue-100">
      <div className={`h-full rounded-full bg-blue-600 ${uploadPercent !== null ? '' : `w-full opacity-40 ${pollError ? '' : 'motion-safe:animate-pulse'}`}`}
        style={uploadPercent !== null ? { width: `${uploadPercent}%` } : undefined} />
    </div>}
    {uploadPercent !== null && <p className="text-xs">Đã gửi {uploadPercent}% dữ liệu. Sau khi gửi xong, hệ thống tiếp tục phân tích.</p>}
    <ol className="grid gap-2 text-xs sm:grid-cols-3">
      {['Gửi ảnh/video', 'AI phân tích', 'Nhận kết quả'].map((label, index) => <li key={label} aria-current={state.step === index ? 'step' : undefined}
        className={`rounded-lg border px-3 py-2 ${index === state.step ? 'border-blue-400 bg-white font-semibold' : 'border-blue-100'}`}>
        {index < state.step ? '✓' : index + 1}. {label}
      </li>)}
    </ol>
    {steps.length > 0 && <div className="rounded-xl border border-blue-100 bg-white p-3">
      <p className="mb-2 text-xs font-semibold">Các bước AI đã báo cáo</p>
      <ol ref={timeline} className="max-h-52 space-y-2 overflow-y-auto text-sm" aria-label="Lịch sử bước xử lý AI">
        {steps.map((item, index) => {
          const current = index === steps.length - 1
          return <li key={item.key} aria-current={current && state.processing && !aiFinished ? 'step' : undefined} className={`flex gap-2 ${current ? 'font-semibold' : 'text-slate-600'}`}>
            <span aria-hidden="true">{current ? state.failed ? '!' : aiFinished ? '✓' : '●' : '·'}</span>
            <span>{item.label}{current && pollError ? ' — cập nhật gần nhất' : current && state.failed ? ' — dừng tại đây' : current && state.processing && !aiFinished ? ' — đang thực hiện' : ''}</span>
          </li>
        })}
      </ol>
    </div>}
    <p className="text-xs leading-5">{state.failed ? 'Lượt xử lý gặp lỗi. Xem thông báo bên dưới.'
      : pollError ? 'Chưa lấy được trạng thái mới. Các bước bên trên là thông tin gần nhất đã nhận.'
        : completed ? 'Kết quả phân tích đã được lưu. Các bước bên trên là dấu vết đã nhận từ AI, không phải quyết định duyệt hồ sơ.' : state.returning || aiFinished ? 'Đang lưu và hiển thị kết quả; đây chưa phải quyết định duyệt hồ sơ.'
          : state.processing ? aiStage ? 'Bước hiện tại được lấy từ dịch vụ AI. Vui lòng chưa gửi lại file.' : 'Đã tiếp nhận hồ sơ, đang chuẩn bị phân tích.'
            : state.uploading ? 'Vui lòng giữ trang này mở trong khi gửi file.' : 'Đã gửi dữ liệu, đang chờ xác nhận tiếp nhận.'}</p>
    {state.processing && Number.isInteger(remote?.aiProgress?.sampledFrames) && remote.aiProgress.sampledFrames > 0 && <p className="text-sm tabular-nums">
      Đã phân tích {remote.aiProgress.sampledFrames} khung hình mẫu{Number.isFinite(remote.aiProgress.videoSeconds) ? ` — đến khoảng giây ${remote.aiProgress.videoSeconds.toFixed(1)} của video` : ''}.
    </p>}
    {state.failed && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-900">{identityMessage(remote.errorCode || 'IDENTITY_PROCESSING_TIMEOUT')} Kiểm tra trạng thái hồ sơ trước khi gửi lại.</p>}
    {pollError && <p role="alert" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-950">Đang thử kết nối lại. Mất kết nối không đồng nghĩa AI đã dừng hoặc hồ sơ bị từ chối.</p>}
    {state.slow && !pollError && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-950">Lượt xử lý đang mất thời gian. Trang vẫn kiểm tra trạng thái; chưa đủ thông tin để kết luận bị treo.</p>}
    {state.processing && remote?.aiProgress?.updatedAt && <p className="text-xs">Bước hiện tại được ghi nhận từ {new Date(remote.aiProgress.updatedAt).toLocaleTimeString('vi-VN')}.</p>}
    {remote?.observedAt && <p className="text-xs">Trạng thái cập nhật lúc {new Date(remote.observedAt).toLocaleTimeString('vi-VN')}. Tiến trình AI được hiển thị theo bước, không tự ước lượng % hoàn tất.</p>}
  </section>
}
