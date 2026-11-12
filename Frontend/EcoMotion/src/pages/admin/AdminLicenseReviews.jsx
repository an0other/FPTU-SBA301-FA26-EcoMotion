import { useEffect, useState } from 'react'
import { drivingLicenseReviewService as api } from '../../services/drivingLicenseReviewService'
import DrivingLicenseReviewDetails, { PrivateImage } from '../../components/identity/DrivingLicenseReviewDetails'
import { compareLicenseIdentity, licenseFields, licenseStatusLabels } from '../../components/identity/licenseReviewPresentation'
import { buttonClass, secondaryClass, inputClass } from '../../components/identity/identityUi'

function Review({ id, onClose, onReviewed }) {
  const [row, setRow] = useState(null)
  const [applicant, setApplicant] = useState(null)
  const [document, setDocument] = useState({})
  const [action, setAction] = useState('APPROVE')
  const [reason, setReason] = useState('DOCUMENT_UNREADABLE')
  const [note, setNote] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { let alive = true; Promise.all([api.detail(id), api.applicant(id)]).then(([value, profile]) => { if (alive) { setRow(value); setApplicant(profile); setDocument(value.document || {}) } }).catch(err => { if (alive) setError(err.message) }); return () => { alive = false } }, [id])
  const comparison = compareLicenseIdentity(document, applicant)
  return <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5"><div className="flex justify-between gap-3"><h2 className="font-bold">Xét duyệt GPLX · {row?.email}</h2><button className={secondaryClass} disabled={busy} onClick={onClose}>Đóng</button></div>{error && <p role="alert" className="rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-900">{error}</p>}{!row && <p>Đang tải…</p>}{row && <><section className="space-y-3 rounded-xl border border-blue-200 bg-blue-50 p-4" aria-label="Hồ sơ người nộp và đối chiếu danh tính">
      <h3 className="font-bold">Hồ sơ người nộp đã được xác minh</h3>
      <p className="text-sm">{applicant?.accountName} · {applicant?.email} · Tài khoản {applicant?.accountStatus}</p>
      <p className="text-sm">CCCD: {applicant?.identityNumber || 'Chưa có'} · Trạng thái danh tính: {applicant?.identityStatus || 'Chưa xác minh'}</p>
      <details className="rounded-xl border border-blue-200 bg-white p-3"><summary className="cursor-pointer font-semibold">Xem ảnh CCCD trong hồ sơ danh tính đã duyệt</summary><div className="mt-3 grid gap-3 sm:grid-cols-2">{['front','back'].map(side=><PrivateImage key={`${row.id}-identity-${side}`} row={row} side={side} admin identity />)}</div></details>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Thông tin đối chiếu</th><th className="p-2">CCCD đã duyệt</th><th className="p-2">GPLX đang xét</th></tr></thead><tbody>
        <tr className={`border-t ${comparison.fullName ? '' : 'bg-amber-100 text-amber-950'}`}><th className="p-2">Họ tên</th><td className="p-2">{applicant?.identityName || 'Chưa có'}</td><td className="p-2">{document.fullName || 'Chưa đọc được'}</td></tr>
        <tr className={`border-t ${applicant?.identityBirthDate && document.dateOfBirth && applicant.identityBirthDate !== document.dateOfBirth ? 'bg-amber-100 text-amber-950' : ''}`}><th className="p-2">Ngày sinh</th><td className="p-2">{applicant?.identityBirthDate || 'Chưa có'}</td><td className="p-2">{document.dateOfBirth || 'Chưa đọc được'}</td></tr>
      </tbody></table></div>
      <p className="text-xs">Đối chiếu họ tên và ngày sinh với ảnh GPLX bên dưới. Số CCCD và số GPLX là hai số giấy tờ khác nhau. Nếu dữ liệu không khớp, sửa lỗi OCR hoặc yêu cầu người nộp bổ sung; không sửa hồ sơ CCCD tại đây.</p>
      {action === 'APPROVE' && !comparison.matches && <p role="alert" className="rounded-lg border border-amber-300 bg-amber-100 p-3 text-sm font-semibold">Chưa thể chấp nhận: họ tên/ngày sinh GPLX phải khớp hồ sơ danh tính đã duyệt. Đối chiếu ảnh và sửa lỗi OCR hoặc chọn yêu cầu bổ sung/từ chối.</p>}
    </section><DrivingLicenseReviewDetails row={row} admin />{row.status === 'SUBMITTED' && <form className="space-y-3 border-t pt-4" onSubmit={async event => { event.preventDefault(); if (busy) return; setBusy(true); setError(''); try { await api.review(row, action, action === 'APPROVE' ? null : reason, action === 'APPROVE' ? null : note.trim(), action === 'APPROVE' ? { ...document, expiryDate: document.expiryUnlimited ? null : document.expiryDate } : null); onReviewed() } catch (err) { setError(err.message || 'Chưa lưu được quyết định.'); try { const latest = await api.detail(id); setRow(latest); setConfirmed(false) } catch { /* Retain the visible record. */ } } finally { setBusy(false) } }}>
      <label className="block text-sm">Quyết định<select className={inputClass} value={action} disabled={busy} onChange={event => { setAction(event.target.value); setConfirmed(false) }}><option value="APPROVE">Chấp nhận</option><option value="NEEDS_CHANGES">Yêu cầu bổ sung bản mới</option><option value="REJECT">Từ chối</option></select></label>
      {action === 'APPROVE' ? <><p className="text-sm text-slate-600">Đối chiếu ảnh gốc và sửa lỗi OCR nếu có. Họ tên/ngày sinh phải khớp danh tính đã được chấp nhận. Giá trị OCR gốc vẫn được giữ.</p><div className="grid gap-3 sm:grid-cols-2">{Object.entries(licenseFields).map(([key, label]) => <label key={key} className="text-sm">{label}<input className={inputClass} type={['dateOfBirth','issueDate','expiryDate'].includes(key) ? 'date' : 'text'} required={key !== 'expiryDate' || !document.expiryUnlimited} disabled={busy} maxLength={key === 'fullName' ? 150 : key === 'documentNumber' ? 20 : 10} value={document[key] || ''} onChange={event => { setDocument(value => ({ ...value, [key]: event.target.value, ...(key === 'expiryDate' ? { expiryUnlimited: !event.target.value && !!row.document?.expiryUnlimited } : {}) })); setConfirmed(false) }} /></label>)}</div></> : <><label className="block text-sm">Lý do<select className={inputClass} value={reason} onChange={event => setReason(event.target.value)} disabled={busy}><option value="DOCUMENT_UNREADABLE">Ảnh/thông tin chưa rõ</option><option value="DETAILS_INCONSISTENT">Thông tin chưa nhất quán</option><option value="EXPIRED_DOCUMENT">Giấy tờ đã hết hạn</option><option value="OTHER_REVIEW_REQUIRED">Cần kiểm tra thêm</option></select></label><label className="block text-sm">Hướng dẫn cụ thể<textarea className={inputClass} required minLength={5} maxLength={1000} value={note} onChange={event => setNote(event.target.value)} disabled={busy} /></label></>}
      <label className="flex gap-2 text-sm"><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={busy} />Tôi đã xem hai mặt giấy tờ và đối chiếu dữ liệu trước khi quyết định.</label><button className={buttonClass} disabled={busy || !applicant || action === 'APPROVE' && !comparison.matches || !confirmed || action !== 'APPROVE' && note.trim().length < 5}>{busy ? 'Đang lưu…' : 'Lưu quyết định'}</button>
    </form>}</>}</section>
}

export default function AdminLicenseReviews() {
  const [status, setStatus] = useState('SUBMITTED')
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState([])
  const [selected, setSelected] = useState(null)
  const [revision, setRevision] = useState(0)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useEffect(() => { let alive = true; queueMicrotask(() => { if (alive) { setLoading(true); setError('') } }); api.queue(status, page).then(value => { if (alive) setRows(value) }).catch(err => { if (alive) { setRows([]); setError(err.message) } }).finally(() => { if (alive) setLoading(false) }); return () => { alive = false } }, [status, page, revision])
  return <div className="space-y-4"><h1 className="text-xl font-bold">Hồ sơ GPLX</h1><div className="flex flex-wrap gap-3"><select className={inputClass} aria-label="Trạng thái GPLX" value={status} onChange={event => { setStatus(event.target.value); setPage(0); setSelected(null) }}>{['SUBMITTED','APPROVED','NEEDS_CHANGES','REJECTED'].map(value => <option key={value} value={value}>{licenseStatusLabels[value]}</option>)}</select><button className={secondaryClass} disabled={loading} onClick={() => { setSelected(null); setRevision(value => value + 1) }}>Cập nhật</button></div>{error && <p role="alert" className="text-red-700">{error}</p>}{loading ? <p>Đang tải…</p> : <div className="grid gap-3 sm:grid-cols-2">{rows.map(row => <button key={row.id} className="rounded-xl border border-slate-200 bg-white p-4 text-left" onClick={() => setSelected(row.id)}><strong>{row.email}</strong><p>{licenseStatusLabels[row.status]}</p></button>)}{!rows.length && <p>Không có hồ sơ ở trạng thái này.</p>}</div>}<div className="flex gap-3"><button className={secondaryClass} disabled={loading || page === 0} onClick={() => { setPage(value => value - 1); setSelected(null) }}>Trang trước</button><span>Trang {page + 1}</span><button className={secondaryClass} disabled={loading || rows.length < 20} onClick={() => { setPage(value => value + 1); setSelected(null) }}>Trang sau</button></div>{selected && <Review key={selected} id={selected} onClose={() => setSelected(null)} onReviewed={() => { setSelected(null); setRevision(value => value + 1) }} />}</div>
}
