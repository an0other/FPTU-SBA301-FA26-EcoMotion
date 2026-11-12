import { licenseFields } from './licenseReviewPresentation'
import { useEffect, useState } from 'react'
import { drivingLicenseReviewService as api } from '../../services/drivingLicenseReviewService'
import VerificationEvidenceSummary from './VerificationEvidenceSummary'
import { secondaryClass, humanDate } from './identityUi'


export function PrivateImage({ row, side, admin, identity = false }) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => () => { if (url) URL.revokeObjectURL(url) }, [url])
  return <div className="space-y-2"><button type="button" className={secondaryClass} disabled={busy} aria-expanded={!!url} onClick={async () => { if (url) { setUrl(''); return } setBusy(true); setError(''); try { const blob = identity ? await api.identityImage(row.id,side) : await api.image(row.id, side, admin); setUrl(URL.createObjectURL(blob)) } catch (err) { setError(err.message || 'Chưa tải được ảnh.') } finally { setBusy(false) } }}>{busy ? 'Đang tải…' : `${url ? 'Ẩn' : 'Xem'} mặt ${side === 'front' ? 'trước' : 'sau'}`}</button>{url && <img src={url} className="max-h-96 w-full rounded-lg object-contain" alt={`Ảnh ${identity ? 'CCCD' : 'GPLX'} mặt ${side === 'front' ? 'trước' : 'sau'}`} />}{error && <p role="alert" className="text-sm text-red-700">{error}</p>}</div>
}

export default function DrivingLicenseReviewDetails({ row, admin = false }) {
  if (!row) return null
  const approvedData = [...(row.history || [])].reverse().find(event => event.action === 'APPROVE')?.approvedData
  const shownDocument = row.status === 'APPROVED' ? approvedData || row.document : row.document
  return <div className="space-y-4"><p className="text-xs text-slate-500">Hồ sơ tạo lúc {humanDate(row.createdAt)}</p>
    <p className="text-sm text-slate-600">Khai báo của người nộp: {row.noQrDeclared ? 'Giấy tờ không có QR (Admin cần đối chiếu mẫu)' : 'Không khai báo thiếu QR'}. Khai báo này không tắt bộ đọc QR.</p>
    <VerificationEvidenceSummary document={{ evidence: row.evidence }} admin={admin} status={row.status} canSubmitForReview={row.status === 'READY_TO_SUBMIT'} />
    <details open={admin || row.status === 'APPROVED' || undefined} className="rounded-xl border border-slate-200 p-3"><summary className="cursor-pointer text-sm font-semibold">Xem thông tin đã đọc</summary><dl className="grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">{Object.entries(licenseFields).map(([key, label]) => <div key={key}><dt className="text-slate-500">{label}{row.status === 'APPROVED' ? ' — đã được Admin xác nhận' : ' — dữ liệu AI đọc'}</dt><dd>{shownDocument?.[key] || (key === 'expiryDate' && shownDocument?.expiryUnlimited ? 'Không thời hạn' : 'Chưa đọc được')}</dd></div>)}</dl></details>
    <div className="grid gap-3 sm:grid-cols-2">{['front', 'back'].map(side => <PrivateImage key={`${row.id}-${side}`} row={row} side={side} admin={admin} />)}</div>
    <ol className="space-y-2 text-sm">{(row.history || []).map((entry, index) => <li key={index} role={entry.action === 'REJECT' || entry.action === 'NEEDS_CHANGES' ? 'alert' : undefined} className={`rounded-lg border p-3 ${entry.action === 'REJECT' ? 'border-red-300 bg-red-50 text-red-900' : entry.action === 'NEEDS_CHANGES' ? 'border-amber-300 bg-amber-50 text-amber-950' : 'border-slate-200'}`}><strong>{({ SUBMIT: 'Đã gửi', APPROVE: 'Admin chấp nhận', NEEDS_CHANGES: 'Admin yêu cầu bổ sung', REJECT: 'Admin từ chối', CANCEL: 'Đã hủy' })[entry.action] || 'Cập nhật'} · {humanDate(entry.at)}</strong>{entry.note && <p className="mt-1 whitespace-pre-wrap break-words">{entry.note}</p>}{entry.approvedData && <details className="mt-2"><summary className="cursor-pointer">Thông tin được Admin xác nhận</summary><dl className="mt-2 grid gap-2 sm:grid-cols-2">{Object.entries(licenseFields).map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{entry.approvedData[key] || '—'}</dd></div>)}</dl></details>}</li>)}</ol>
  </div>
}
