import { useCallback, useEffect, useState } from 'react'
import apiClient from '../../services/apiClient'
import { providerFinanceService } from '../../services/providerFinanceService'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { buildPaymentReceipts, excessRefundViews } from '../../utils/paymentReceipts'

const MISMATCH_TYPES = [
  { value: 'OVERPAID', label: 'Tôi chuyển dư so với số yêu cầu' },
  { value: 'UNDER_RECORDED', label: 'Nhà xe ghi nhận ít hơn số tôi đã chuyển' },
  { value: 'OTHER', label: 'Khác' },
]

/**
 * Biên nhận cho khách: nhà xe xác nhận đã nhận bao nhiêu tiền. Khách chỉ cần đối chiếu với app ngân hàng;
 * nếu sai thì bấm "Số tiền không đúng?" để gửi khiếu nại kèm ảnh chuyển khoản.
 */
export default function PaymentReceiptsCard({ bookingCode, bookingStatus, workflow, disputeCategories = [], onOpenDispute, onUploadEvidence, onChanged }) {
  const [receipts, setReceipts] = useState([])
  const [reportOpen, setReportOpen] = useState(false)
  const [form, setForm] = useState({ paymentRequestId: '', claimedAmount: '', mismatchType: 'OVERPAID', description: '' })
  const [evidence, setEvidence] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    try {
      const requests = await providerFinanceService.getPaymentRequestsByBooking(bookingCode)
      const bookingId = requests?.[0]?.bookingId
      if (!bookingId) { setReceipts([]); return }
      const { data } = await apiClient.get(`/bookings/${bookingId}/payment-history`)
      setReceipts(buildPaymentReceipts(data?.data ?? []))
    } catch {
      setReceipts([])
    }
  }, [bookingCode])

  useEffect(() => {
    const timer = setTimeout(load, 0)
    return () => clearTimeout(timer)
  }, [load, workflow?.updatedAt, workflow?.confirmedPaid])

  const excess = excessRefundViews(workflow?.refunds || [], bookingStatus)
  const activeMismatch = (workflow?.disputes || []).find((d) => d.category === 'PAYMENT_MISMATCH'
    && ['OPEN', 'IN_REVIEW'].includes(d.status))
  const canReport = disputeCategories.includes('PAYMENT_MISMATCH') && !activeMismatch

  if (receipts.length === 0 && excess.length === 0 && !activeMismatch) return null

  const openReport = (receipt) => {
    setForm((current) => ({ ...current, paymentRequestId: String(receipt?.paymentRequestId ?? receipts[0]?.paymentRequestId ?? '') }))
    setReportOpen(true)
    setError('')
  }

  const pickEvidence = async (event) => {
    const files = Array.from(event.target.files || [])
    event.target.value = ''
    for (const file of files.slice(0, 3 - evidence.length)) {
      try {
        const url = await onUploadEvidence(file)
        if (url) setEvidence((list) => [...list, url])
      } catch (err) {
        setError(err.message || 'Không tải được ảnh chuyển khoản')
      }
    }
  }

  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    if (!evidence.length) { setError('Vui lòng đính kèm ảnh chuyển khoản từ app ngân hàng.'); return }
    setBusy(true); setError('')
    try {
      await onOpenDispute({
        category: 'PAYMENT_MISMATCH',
        paymentRequestId: Number(form.paymentRequestId),
        claimedAmount: Number(String(form.claimedAmount).replace(/\D/g, '')),
        mismatchType: form.mismatchType,
        description: form.description.trim() || null,
        evidenceUrls: evidence,
      })
      setReportOpen(false); setEvidence([])
      setNotice('Đã gửi báo cáo. Quản trị viên sẽ đối chiếu sao kê và phản hồi cho bạn.')
    } catch (err) {
      setError(err.message || 'Chưa gửi được báo cáo. Vui lòng thử lại.')
    } finally { setBusy(false) }
  }

  const offset = async (refundId) => {
    if (busy) return
    setBusy(true); setError('')
    try {
      await apiClient.post(`/refunds/${refundId}/offset`)
      setNotice('Đã cấn trừ khoản chuyển dư vào tiền quyết toán.')
      await onChanged?.()
    } catch (err) {
      setError(err.message || 'Chưa cấn trừ được. Vui lòng thử lại.')
    } finally { setBusy(false) }
  }

  return (
    <section className="mx-auto mt-4 max-w-[1120px] px-4" aria-label="Biên nhận thanh toán">
      <div className="rounded-[16px] border border-[#E6EBE8] bg-white p-5 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
        <h3 className="text-sm font-bold text-[#14221A]">Biên nhận thanh toán</h3>

        {receipts.length > 0 && (
          <ul className="mt-3 divide-y divide-[#EEF2EF]">
            {receipts.map((receipt) => (
              <li key={receipt.paymentRequestId} className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
                <span className="text-[#475467]">
                  {receipt.gateway ? 'Đã thanh toán qua VNPAY' : 'Nhà xe xác nhận đã nhận'}
                  {' · '}<strong className="text-[#14221A]">{receipt.label}</strong>
                  {receipt.lastPaidAt ? <span className="text-xs text-[#667085]"> · {formatBookingWhen(receipt.lastPaidAt)}</span> : null}
                  {receipt.adminAdjusted ? <span className="ml-1 text-xs text-[#15803D]">(đã gồm phần admin đối soát)</span> : null}
                </span>
                <span className="flex items-center gap-3">
                  <strong className="font-mono text-[#14221A]">{formatCurrency(receipt.amount)}</strong>
                  {canReport && !receipt.gateway && (
                    <button type="button" onClick={() => openReport(receipt)} className="text-xs font-semibold text-[#B54708] underline-offset-2 hover:underline">
                      Số tiền không đúng?
                    </button>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}

        {activeMismatch && (
          <p className="mt-3 rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-900">
            Bạn đã báo sai lệch số tiền — quản trị viên đang đối chiếu sao kê. Đơn sẽ chưa hoàn tất cho đến khi có kết luận.
          </p>
        )}

        {excess.map((item) => (
          <div key={item.id} className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50/70 px-3 py-2.5 text-xs text-blue-950">
            <span>
              Bạn đã chuyển dư <strong>{formatCurrency(item.amount)}</strong>. {item.statusText}.
              {item.pending && ' Nhà xe bắt buộc hoàn lại; đơn chỉ hoàn tất sau khi hoàn xong.'}
            </span>
            {item.canOffset && (
              <button type="button" disabled={busy} onClick={() => offset(item.id)} className="rounded-lg border border-blue-300 bg-white px-3 py-1.5 font-semibold text-blue-800 hover:bg-blue-100 disabled:opacity-50">
                Cấn trừ vào quyết toán
              </button>
            )}
          </div>
        ))}

        {notice && <p role="status" className="mt-3 text-xs font-semibold text-[#15803D]">{notice}</p>}
        {error && !reportOpen && <p role="alert" className="mt-3 text-xs text-[#B42318]">{error}</p>}

        {reportOpen && (
          <form onSubmit={submit} className="mt-4 space-y-3 rounded-xl border border-[#F2D6B3] bg-[#FFFAF3] p-4">
            <p className="text-sm font-bold text-[#14221A]">Báo số tiền không đúng</p>
            {receipts.length > 1 && (
              <label className="block text-xs font-semibold text-[#14221A]">Khoản thanh toán
                <select value={form.paymentRequestId} onChange={(e) => setForm({ ...form, paymentRequestId: e.target.value })}
                  className="mt-1 h-10 w-full rounded-lg border border-[#E6EBE8] bg-white px-3 text-sm">
                  {receipts.filter((r) => !r.gateway).map((r) => (
                    <option key={r.paymentRequestId} value={r.paymentRequestId}>{r.label} — nhà xe ghi {formatCurrency(r.amount)}</option>
                  ))}
                </select>
              </label>
            )}
            <label className="block text-xs font-semibold text-[#14221A]">Số tiền bạn đã chuyển <span className="text-[#B42318]">*</span>
              <input required inputMode="numeric" value={form.claimedAmount}
                onChange={(e) => setForm({ ...form, claimedAmount: e.target.value.replace(/\D/g, '') })}
                placeholder="Ví dụ: 500000" className="mt-1 h-10 w-full rounded-lg border border-[#E6EBE8] bg-white px-3 text-sm" />
            </label>
            <fieldset className="space-y-1.5">
              <legend className="text-xs font-semibold text-[#14221A]">Sai lệch là gì?</legend>
              {MISMATCH_TYPES.map((type) => (
                <label key={type.value} className="flex items-center gap-2 text-xs text-[#344054]">
                  <input type="radio" name="mismatchType" value={type.value} checked={form.mismatchType === type.value}
                    onChange={() => setForm({ ...form, mismatchType: type.value })} />
                  {type.label}
                </label>
              ))}
            </fieldset>
            <label className="block text-xs font-semibold text-[#14221A]">
              Mô tả {form.mismatchType === 'OTHER' ? <span className="text-[#B42318]">*</span> : '(tuỳ chọn)'}
              <textarea rows={2} required={form.mismatchType === 'OTHER'} value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="mt-1 w-full rounded-lg border border-[#E6EBE8] bg-white p-2 text-sm" />
            </label>
            <div className="text-xs">
              <span className="font-semibold text-[#14221A]">Ảnh chuyển khoản <span className="text-[#B42318]">*</span></span>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                {evidence.map((url) => (
                  <img key={url} src={url} alt="Ảnh chuyển khoản đã tải" className="h-14 w-14 rounded-lg border border-[#E6EBE8] object-cover" />
                ))}
                {evidence.length < 3 && (
                  <label className="cursor-pointer rounded-lg border border-dashed border-[#D0D5DD] px-3 py-2 text-[#475467] hover:bg-white">
                    + Thêm ảnh
                    <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={pickEvidence} />
                  </label>
                )}
              </div>
            </div>
            {error && <p role="alert" className="text-xs text-[#B42318]">{error}</p>}
            <div className="flex flex-wrap gap-2">
              <button type="submit" disabled={busy} className="rounded-lg bg-[#B54708] px-4 py-2 text-xs font-semibold text-white hover:bg-[#93370D] disabled:opacity-50">
                {busy ? 'Đang gửi...' : 'Gửi báo cáo'}
              </button>
              <button type="button" onClick={() => setReportOpen(false)} className="rounded-lg border border-[#E6EBE8] bg-white px-4 py-2 text-xs font-semibold text-[#344054]">
                Huỷ
              </button>
            </div>
          </form>
        )}
      </div>
    </section>
  )
}
