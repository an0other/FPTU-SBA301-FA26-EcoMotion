import { useEffect, useRef, useState } from 'react'
import { buttonClass, secondaryClass } from './identityUi'

const prompts = ['Nhìn thẳng vào camera', 'Từ từ quay đầu sang trái của bạn', 'Từ từ quay đầu sang phải của bạn', 'Trở về nhìn thẳng và giữ nguyên']

export default function VideoCapture({ value, onChange, constraints, disabled, onCaptureChange }) {
  const videoRef = useRef(null)
  const previewRef = useRef(null)
  const streamRef = useRef(null)
  const recorderRef = useRef(null)
  const timerRef = useRef(null)
  const cancelledRef = useRef(false)
  const mountedRef = useRef(true)
  const completedRef = useRef(false)
  const [camera, setCamera] = useState(false)
  const [recording, setRecording] = useState(false)
  const [countdown, setCountdown] = useState(null)
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState('')
  const [opening, setOpening] = useState(false)
  const duration = Math.min(10, Math.max(0, (constraints?.maxVideoSeconds ?? 15) - 0.4))

  function stopTracks() {
    streamRef.current?.getTracks().forEach(track => track.stop())
    streamRef.current = null
  }

  function cancel() {
    cancelledRef.current = true
    clearInterval(timerRef.current)
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
    stopTracks()
    setRecording(false)
    setCamera(false)
    setCountdown(null)
    setOpening(false)
    onCaptureChange?.(false)
  }

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      cancelledRef.current = true
      clearInterval(timerRef.current)
      if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
      streamRef.current?.getTracks().forEach(track => track.stop())
      onCaptureChange?.(false)
    }
  }, [onCaptureChange])

  useEffect(() => {
    if (!value) return
    const url = URL.createObjectURL(value)
    if (previewRef.current) previewRef.current.src = url
    return () => URL.revokeObjectURL(url)
  }, [value])

  async function openCamera() {
    setError('')
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
      setError('Trình duyệt chưa hỗ trợ quay camera. Bạn có thể chọn video đã quay bên dưới. Camera cần HTTPS hoặc localhost.')
      return
    }
    if (duration < 8) {
      setError('Giới hạn video trên hệ thống quá ngắn cho hướng dẫn này. Vui lòng liên hệ quản trị viên.')
      return
    }
    try {
      cancelledRef.current = false
      setOpening(true)
      onCaptureChange?.(true)
      onChange(null)
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280, max: 1280 }, height: { ideal: 720, max: 720 }, frameRate: { ideal: 30, max: 30 } }, audio: false })
      if (!mountedRef.current || cancelledRef.current) { stream.getTracks().forEach(track => track.stop()); return }
      streamRef.current = stream
      videoRef.current.srcObject = stream
      await videoRef.current.play()
      if (!mountedRef.current || cancelledRef.current) { stream.getTracks().forEach(track => track.stop()); return }
      stream.getVideoTracks()[0]?.addEventListener('ended', () => {
        if (!mountedRef.current || cancelledRef.current) return
        cancel()
        setError('Camera đã bị ngắt. Vui lòng cấp quyền hoặc mở lại camera để quay đủ video.')
      }, { once: true })
      setCamera(true)
      setOpening(false)
    } catch (err) {
      stopTracks()
      setOpening(false)
      onCaptureChange?.(false)
      setError(err.name === 'NotAllowedError' ? 'Bạn chưa cho phép truy cập camera. Hãy cấp quyền trong trình duyệt hoặc chọn video có sẵn.' : 'Không mở được camera. Hãy đóng ứng dụng đang dùng camera và thử lại.')
    }
  }

  function beginRecording() {
    if (!streamRef.current) return
    try {
      const mimeType = ['video/webm;codecs=vp8', 'video/webm', 'video/mp4'].find(type => MediaRecorder.isTypeSupported(type))
      const recorder = new MediaRecorder(streamRef.current, { ...(mimeType ? { mimeType } : {}), videoBitsPerSecond: 1800000 })
      recorderRef.current = recorder
      const chunks = []
      recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
      recorder.onerror = () => { cancelledRef.current = true; cancel(); setError('Quay video gặp lỗi. Vui lòng mở lại camera.') }
      recorder.onstop = () => {
        clearInterval(timerRef.current)
        stopTracks()
        if (!mountedRef.current) return
        setRecording(false)
        setCamera(false)
        onCaptureChange?.(false)
        if (cancelledRef.current) return
        if (!completedRef.current) { setError('Video bị gián đoạn. Vui lòng quay lại đủ hướng dẫn.'); return }
        const type = recorder.mimeType || chunks[0]?.type || 'video/webm'
        const file = new File(chunks, `xac-minh.${type.includes('mp4') ? 'mp4' : 'webm'}`, { type })
        if (!file.size || file.size > constraints.maxVideoBytes) { setError('Video rỗng hoặc vượt dung lượng. Vui lòng quay lại.'); return }
        onChange(file)
      }
      onChange(null)
      completedRef.current = false
      setElapsed(0)
      setRecording(true)
      recorder.start(250)
      const start = performance.now()
      timerRef.current = setInterval(() => {
        const seconds = (performance.now() - start) / 1000
        setElapsed(seconds)
        if (seconds >= duration && recorder.state === 'recording') { completedRef.current = true; recorder.stop() }
      }, 100)
    } catch {
      cancel()
      setError('Trình duyệt không quay được định dạng này. Hãy thử trình duyệt khác hoặc chọn file video.')
    }
  }

  function start() {
    cancelledRef.current = false
    let left = 3
    setCountdown(left)
    timerRef.current = setInterval(() => {
      left -= 1
      if (left === 0) { clearInterval(timerRef.current); setCountdown(null); beginRecording() }
      else setCountdown(left)
    }, 1000)
  }

  const step = Math.min(3, Math.floor(elapsed / (duration / 4)))
  return <div className="space-y-3">
    <div className="rounded-2xl bg-slate-50 p-4 text-sm leading-6 text-slate-600"><p className="font-semibold text-slate-800">Quay khoảng {duration.toFixed(0)} giây theo hướng dẫn</p><p>Nhìn thẳng → quay trái → quay phải → nhìn thẳng. Mỗi động tác giữ khoảng 2 giây, không quay đầu quá nhanh.</p><p className="mt-2 text-xs">Chọn nơi đủ sáng, bỏ khẩu trang và giữ mặt trong khung. Video không thu âm; kết quả được kiểm tra sau khi gửi.</p></div>
    <div className={`relative overflow-hidden rounded-2xl bg-slate-950 ${camera ? '' : 'hidden'}`}>
      <video ref={videoRef} muted playsInline className="aspect-video w-full -scale-x-100 object-contain" />
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center"><div className="h-4/5 w-1/3 rounded-[50%] border-2 border-white/80" /></div>
      <div className="absolute inset-x-0 bottom-0 bg-black/70 p-3 text-center text-sm font-semibold text-white" aria-live="polite">
        {countdown !== null ? `Chuẩn bị: ${countdown}` : recording ? prompts[step] : 'Giữ khuôn mặt ở giữa khung'}
      </div>
    </div>
    {recording && <><progress className="h-2 w-full accent-brand-600" max={duration} value={elapsed} aria-label="Tiến độ quay video" /><p className="text-xs text-slate-500">Đang quay {Math.min(duration, elapsed).toFixed(1)} / {duration.toFixed(0)} giây. Kết quả động tác sẽ được kiểm tra sau khi gửi.</p></>}
    <div className="flex flex-wrap gap-2">
      {!camera && <button type="button" className={buttonClass} onClick={openCamera} disabled={disabled || opening}>{opening ? 'Đang mở camera…' : value ? 'Quay lại video' : 'Mở camera để quay'}</button>}
      {camera && !recording && countdown === null && <button type="button" className={buttonClass} onClick={start} disabled={disabled}>Bắt đầu quay</button>}
      {(camera || opening) && <button type="button" className={secondaryClass} onClick={cancel}>Hủy quay / tắt camera</button>}
    </div>
    {!camera && !opening && <details className="rounded-xl border border-slate-200 p-3"><summary className="cursor-pointer text-sm text-slate-600">Dùng video đã quay sẵn</summary><label className="mt-3 block text-sm font-medium text-slate-700">Chọn video có đủ các động tác trên
      <input className="mt-2 block w-full text-sm" type="file" accept="video/mp4,video/webm,video/quicktime,video/x-matroska,.mkv" disabled={disabled} onChange={event => { const file = event.target.files?.[0]; if (!file) return; if (file.size > constraints.maxVideoBytes) { setError('Video vượt dung lượng cho phép.'); return } setError(''); onChange(file) }} />
    </label></details>}
    {value && !camera && <div className="space-y-2"><video ref={previewRef} controls playsInline className="max-h-72 w-full rounded-xl bg-slate-950" /><p className="text-xs text-slate-500">{value.name} · {(value.size / 1048576).toFixed(1)} MB</p></div>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
  </div>
}
