import { useState } from 'react'
import { identityVerificationService as api } from '../../services/identityVerificationService'
import IdentityEvidenceGallery from './IdentityEvidenceGallery'
import IdentityReviewHistory from './IdentityReviewHistory'
import { secondaryClass, statusLabels, humanDate } from './identityUi'

export default function IdentityAttemptArchive({ activationToken, currentAttemptId }) {
  const [rows, setRows] = useState([])
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [selected, setSelected] = useState(null)
  async function load(next = 0) {
    setBusy(true); setError('')
    try { setRows(await api.attemptHistory(next, activationToken)); setPage(next); setLoaded(true); setSelected(null) }
    catch (err) { setError(err.message || 'Chưa tải được các phiên hồ sơ.') }
    finally { setBusy(false) }
  }
  return <details className="rounded-xl border border-slate-200 p-4"><summary className="cursor-pointer font-semibold">Xem các phiên hồ sơ trước</summary><div className="mt-3 space-y-3"><p className="text-xs text-slate-500">Mở từng phiên để đối chiếu ảnh/video và lý do đã được phản hồi. Phiên hiện tại cũng có thể nằm trong danh sách.</p><button type="button" className={secondaryClass} disabled={busy} onClick={() => load(0)}>{busy ? 'Đang tải…' : 'Tải/cập nhật danh sách'}</button>{error && <p role="alert" className="text-sm text-red-700">{error}</p>}{loaded && !rows.length && <p className="text-sm">Chưa có phiên hồ sơ.</p>}
      {rows.map(row => <div key={row.attemptId} className="space-y-3 rounded-xl bg-slate-50 p-3"><button type="button" className="text-left text-sm font-semibold text-brand-800" onClick={() => setSelected(selected === row.attemptId ? null : row.attemptId)}>{statusLabels[row.status] || row.status} · Bản giấy tờ {row.documentRevision}{row.submittedAt ? ` · Gửi ${humanDate(row.submittedAt)}` : ''}{row.attemptId === currentAttemptId ? ' · Phiên hiện tại' : ''}</button>{selected === row.attemptId && <><p className="text-xs text-slate-500">Mã hồ sơ: {row.attemptId}</p>{row.status !== 'PROCESSING' && <IdentityEvidenceGallery attemptId={row.attemptId} revision={row.version} activationToken={activationToken} hasFront={row.hasFront} hasBack={row.hasBack} hasVideo={row.hasVideo} title="Bằng chứng đang lưu trong phiên này" />}
        <IdentityReviewHistory entries={row.reviewHistory} renderEvidence={entry => <IdentityEvidenceGallery attemptId={row.attemptId} eventId={entry.id} activationToken={activationToken} hasVideo={!!entry.video} title="Bản bằng chứng trong lịch sử" />} />
      </>}</div>)}
      {loaded && <div className="flex flex-wrap items-center gap-3"><button type="button" className={secondaryClass} disabled={busy || page === 0} onClick={() => load(page - 1)}>Trang trước</button><span className="text-sm">Trang {page + 1}</span><button type="button" className={secondaryClass} disabled={busy || rows.length < 10} onClick={() => load(page + 1)}>Trang sau</button></div>}
    </div></details>
}
