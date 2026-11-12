import { useState } from 'react'
import { humanDate, identityMessage } from './identityUi'
import VerificationEvidenceSummary from './VerificationEvidenceSummary'

const labels = { SUBMIT: 'Đã gửi xét duyệt', APPROVE: 'Đã chấp nhận', NEEDS_CHANGES: 'Yêu cầu bổ sung', REJECT: 'Từ chối', REOPEN: 'Mở lại để bổ sung' }

export default function IdentityReviewHistory({ entries, admin = false, renderEvidence }) {
  const [selected, setSelected] = useState(null)
  const list = entries || []
  const latest = [...list].reverse().find(entry => entry.actorType === 'ADMIN')
  if (!admin && list.length === 0) return null
  return <section className="space-y-3" aria-label="Lịch sử xét duyệt">
    {latest?.note && <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><strong>Phản hồi từ người xét duyệt</strong><p className="mt-1 whitespace-pre-wrap break-words">{latest.note}</p></div>}
    <details className="rounded-xl border border-slate-200 p-4"><summary className="cursor-pointer font-semibold">Lịch sử gửi và xét duyệt ({list.length})</summary>
      {!list.length && <p className="mt-3 text-sm text-slate-500">Chưa có sự kiện được lưu theo cơ chế lịch sử mới. Các quyết định cũ không được tự dựng lại.</p>}
      <ol className="mt-3 space-y-3">{[...list].reverse().map((entry, index) => <li key={entry.id ?? index} className="rounded-lg bg-slate-50 p-3 text-sm">
        <p className="font-semibold">{labels[entry.eventType] || 'Cập nhật hồ sơ'} · {humanDate(entry.occurredAt)}</p>
        <p className="mt-1 text-slate-600">{entry.actorType === 'ADMIN' ? 'Admin' : 'Người nộp'}{admin ? ` ${entry.actorId}` : ''} · Bản giấy tờ {entry.documentRevision}</p>
        {entry.reasonCode && <p className="mt-1">{identityMessage(entry.reasonCode)}</p>}
        {entry.note && <p className="mt-1 whitespace-pre-wrap break-words">{entry.note}</p>}
        {(admin || renderEvidence) && <button type="button" className="mt-2 font-medium text-brand-700 underline" onClick={() => setSelected(selected === entry ? null : entry)}>{selected === entry ? 'Ẩn bằng chứng tại thời điểm này' : 'Xem bằng chứng tại thời điểm này'}</button>}
        {selected === entry && (admin || renderEvidence) && <div className="mt-3 space-y-3"><p className="text-xs text-slate-600">Chính sách: {entry.policyVersion || 'Chưa có'} · Phiên bản trước thao tác: {entry.attemptVersion}</p><VerificationEvidenceSummary document={entry.document} video={entry.video} admin={admin} status={({ SUBMIT: 'SUBMITTED', APPROVE: 'APPROVED', REJECT: 'REJECTED', NEEDS_CHANGES: 'NEEDS_CHANGES' })[entry.eventType]} />{entry.id && renderEvidence?.(entry)}</div>}
      </li>)}</ol>
    </details>
  </section>
}
