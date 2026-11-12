import IdentityProcessingProgress from '../identity/IdentityProcessingProgress'
import useLicenseProcessing from '../identity/useLicenseProcessing'
import { useEffect, useState } from 'react'
import { drivingLicenseReviewService as api } from '../../services/drivingLicenseReviewService'
import DrivingLicenseReviewDetails from '../identity/DrivingLicenseReviewDetails'
import { buttonClass, secondaryClass } from '../identity/identityUi'

export default function DrivingLicenseForm({ license, onSaved }) {
  const [row, setRow] = useState(null)
  const [history, setHistory] = useState([])
  const [historyPage, setHistoryPage] = useState(0)
  const [historyBusy, setHistoryBusy] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [front, setFront] = useState(null)
  const [back, setBack] = useState(null)
  const [noQr, setNoQr] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const processing = useLicenseProcessing(async result => {
    const current = await api.current()
    setRow(current)
    if (result.status === 'COMPLETED') { setError(''); await onSaved?.() }
    if (result.status === 'ERROR' && !(current && result.startedAt && Date.parse(current.createdAt) >= Date.parse(result.startedAt))) setError(result.errorCode === 'LICENSE_PROGRESS_UNAVAILABLE'
      ? 'Không còn thông tin tiến trình. Đã tải lại hồ sơ; hãy kiểm tra trước khi gửi lại.'
      : 'Lượt đọc GPLX chưa hoàn tất. Hãy kiểm tra thông báo và hồ sơ trước khi thử lại.')
  })
  const locked = busy || processing.running
  useEffect(() => { let alive = true; api.current().then(value => { if (alive) setRow(value) }).catch(err => { if (alive) setError(err.message || 'Chưa tải được hồ sơ.') }).finally(() => { if (alive) setLoading(false) }); return () => { alive = false } }, [])
  async function loadHistory(page = 0) {
    setHistoryBusy(true); setHistoryError('')
    try { const result = await api.history(page); setHistory(result); setHistoryPage(page); setHistoryLoaded(true) }
    catch (err) { setHistoryError(err.message || 'Chưa tải được lịch sử.') }
    finally { setHistoryBusy(false) }
  }
  async function perform(action) {
    setBusy(true); setError('')
    try { const result = await action(); setRow(result); setConfirmed(false); setHistoryLoaded(false); setHistory([]); await onSaved?.() }
    catch (err) { setError(err.message || 'Chưa thực hiện được. Vui lòng cập nhật trạng thái.'); try { setRow(await api.current()) } catch { /* Keep the last visible snapshot when refresh fails. */ } }
    finally { processing.finish(); setBusy(false) }
  }
  const active = ['READY_TO_SUBMIT', 'SUBMITTED'].includes(row?.status)
  return <section id="driving-license" className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-card">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-bold">GPLX cho thuê tự lái</h2><button type="button" className={secondaryClass} disabled={locked} onClick={() => perform(() => api.current())}>Cập nhật trạng thái</button></div>
    <p className="text-sm text-slate-600">Tải hai mặt → xem kết quả đọc → xác nhận gửi → Admin xét duyệt. Giấy tờ cũ không có QR vẫn được tiếp nhận; điểm AI không tự xác minh giấy tờ.</p>
    {license?.licenseNo && <div className="rounded-xl bg-slate-50 p-4 text-sm"><strong>GPLX đang được ghi nhận: {license.licenseNo} · {license.licenseClass}</strong><p>Hạn: {license.licenseExpiryUnlimited ? 'Không thời hạn' : license.licenseExpiry || 'Chưa có'}. {license.yearlyBookingEligible ? 'Đạt điều kiện sơ bộ; còn kiểm tra theo kỳ thuê.' : 'Chưa đủ điều kiện sơ bộ cho gói năm.'}</p><p>Bản nộp mới không thay thế GPLX này cho đến khi được chấp nhận. Dữ liệu cũ/seed không mặc định đã đi qua quy trình Admin mới.</p></div>}
    {processing.running && <IdentityProcessingProgress task={processing.task || { kind: 'DOCUMENT' }} remote={processing.remote} pollError={processing.pollError} />}
    <details className="rounded-xl border border-blue-200 p-3"><summary className="cursor-pointer font-semibold">Pipeline các lần đọc GPLX đã lưu</summary><div className="mt-3 space-y-3"><button type="button" className={secondaryClass} disabled={processing.historyBusy} onClick={()=>processing.loadHistory(0)}>Tải lại lịch sử pipeline</button>{processing.historyError && <p role="alert" className="text-sm text-red-700">{processing.historyError}</p>}{processing.history.filter(run=>run.status !== 'PROCESSING').map(run=><details key={run.id} className="rounded-lg border p-3"><summary className="cursor-pointer text-sm">{new Date(run.startedAt).toLocaleString('vi-VN')} · {run.status}</summary><IdentityProcessingProgress task={{kind:'DOCUMENT',startedAt:Date.parse(run.startedAt),finishedAt:Date.parse(run.finishedAt)}} remote={{status:run.status,aiProgress:run.progress,errorCode:run.errorCode,seenProcessing:true}} completed /></details>)}<div className="flex gap-3"><button type="button" className={secondaryClass} disabled={processing.historyBusy || processing.historyPage === 0} onClick={()=>processing.loadHistory(processing.historyPage-1)}>Trang trước</button><span>Trang {processing.historyPage+1}</span><button type="button" className={secondaryClass} disabled={processing.historyBusy || processing.history.length<10} onClick={()=>processing.loadHistory(processing.historyPage+1)}>Trang sau</button></div></div></details>
    {loading && <p role="status">Đang tải hồ sơ GPLX…</p>}{error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {row && <DrivingLicenseReviewDetails row={row} />}
    {row?.status === 'APPROVED' && <p className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900">GPLX đã được Admin chấp nhận. Xem thông tin đã xác nhận và hai mặt giấy tờ ở trên. Chỉ nộp thay thế khi giấy tờ thay đổi hoặc cần sửa thông tin.</p>}
    {!active && !loading && <details open={row?.status !== 'APPROVED' || undefined}><summary className="cursor-pointer font-semibold">{row?.status === 'APPROVED' ? 'Cập nhật / thay thế GPLX' : 'Nộp giấy phép lái xe'}</summary><form className="space-y-3 border-t border-slate-200 pt-4" onSubmit={event => { event.preventDefault(); if (!front || !back || locked) return; perform(async () => { const upload = processing.start(); const result = await api.prepare(front, back, noQr, upload.onUploadProgress, upload.id); setFront(null); setBack(null); return result }) }}>
      <p className="text-sm">Nộp bản mới để bổ sung/thay thế. JPEG/PNG, tối đa 8 MB mỗi ảnh.</p>
      <div className="grid gap-3 sm:grid-cols-2">{[['Mặt trước', setFront], ['Mặt sau', setBack]].map(([label, setter]) => <label key={label} className="block rounded-xl border border-slate-200 p-3 text-sm">{label}<input className="mt-2 block w-full" type="file" accept="image/jpeg,image/png" required disabled={locked} onChange={event => setter(event.target.files?.[0] || null)} /></label>)}</div>
      <label className="flex gap-2 text-sm"><input type="checkbox" checked={noQr} disabled={locked} onChange={event => setNoQr(event.target.checked)} />Giấy tờ của tôi không có QR — đây là khai báo để Admin đối chiếu.</label>
      <button className={buttonClass} disabled={locked || !front || !back}>{busy ? 'Đang xử lý…' : 'Đọc ảnh và chuẩn bị hồ sơ'}</button>
    </form></details>}
    {row?.status === 'READY_TO_SUBMIT' && <div className="space-y-3"><label className="flex gap-2 text-sm"><input type="checkbox" checked={confirmed} disabled={locked} onChange={event => setConfirmed(event.target.checked)} />Tôi xác nhận giấy tờ này là của mình và gửi Admin kiểm tra các thông tin chưa rõ.</label><div className="flex flex-wrap gap-3"><button className={buttonClass} disabled={locked || !confirmed} onClick={() => perform(() => api.submit(row))}>Gửi Admin xét duyệt</button><button className={secondaryClass} disabled={locked} onClick={() => perform(() => api.cancel(row))}>Hủy bản chuẩn bị để thay ảnh</button></div></div>}
    {row?.status === 'SUBMITTED' && <p className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900">Hồ sơ đang chờ Admin. Chưa được dùng bản GPLX này để mở quyền tự lái.</p>}
    <details className="rounded-xl border border-slate-200 p-4"><summary className="cursor-pointer font-semibold">Các lần nộp GPLX trước</summary><div className="mt-3 space-y-3"><button type="button" className={secondaryClass} disabled={historyBusy} onClick={() => loadHistory(0)}>Tải/cập nhật lịch sử</button>{historyError && <p role="alert" className="text-sm text-red-700">{historyError}</p>}{historyLoaded && history.length === 0 && <p>Chưa có hồ sơ.</p>}{history.map(item => <details key={item.id} className="rounded-lg border border-slate-200 p-3"><summary className="cursor-pointer">{item.document?.documentNumber || 'Chưa đọc số GPLX'} · {item.status}</summary><div className="mt-3"><DrivingLicenseReviewDetails row={item} /></div></details>)}{historyLoaded && <div className="flex flex-wrap gap-3"><button type="button" className={secondaryClass} disabled={historyBusy || historyPage === 0} onClick={() => loadHistory(historyPage - 1)}>Trang trước</button><span>Trang {historyPage + 1}</span><button type="button" className={secondaryClass} disabled={historyBusy || history.length < 10} onClick={() => loadHistory(historyPage + 1)}>Trang sau</button></div>}</div></details>
    <p className="text-xs text-slate-500">Admin xét duyệt theo ảnh và thông tin hồ sơ; hệ thống chưa tra cứu cơ quan cấp hoặc xác nhận đầy đủ tính thật của giấy tờ.</p>
  </section>
}
