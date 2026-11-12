import { useEffect, useRef, useState } from 'react'
import { providerFinanceService } from '../../services/providerFinanceService'
import { PaymentEvidenceImage } from '../payments/PaymentEvidenceImage'
import ActionForm from './YearlyActionForm'
import YearlyPaymentFeedback from './YearlyPaymentFeedback'
import { BankReceiptReconciliationModal } from '../payments/BankReceiptReconciliationModal'
import { yearlyButtonClass, yearlyDate, yearlyMoney, yearlyUuid } from '../../utils/yearlyRental'

const labels = { PENDING: 'Chưa thanh toán', PROOF_SUBMITTED: 'Chờ nhà cung cấp đối soát', PARTIALLY_PAID: 'Đã trả một phần', CONFIRMED: 'Đã nhận tiền', REJECTED: 'Cần gửi lại bằng chứng', EXPIRED: 'Đã hết hạn', REVIEW_REQUIRED: 'Cần đối soát', VOIDED: 'Đã dừng thu' }
const name = (row) => row.purpose === 'SECURITY_DEPOSIT' ? 'Tiền bảo đảm' : row.installmentNumber > 0 ? 'Tiền thuê tháng ' + row.installmentNumber : 'Thanh toán bổ sung quyết toán'

export default function YearlyPaymentsPanel({ role, rows, busy, run, initialOnly = false, canCollect = true, canPayRent = canCollect, canPayAdjustment = canCollect, allowExceptions = false, canReportMismatch = false }) {
  const visibleRows = initialOnly ? rows.filter(r => r.purpose === 'SECURITY_DEPOSIT' || r.installmentNumber === 1) : rows
  const [selected, setSelected] = useState(null)
  const [paymentError, setPaymentError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [detailRetry, setDetailRetry] = useState(0)
  const [mode, setMode] = useState('receive')
  const [bankReceiptOpen, setBankReceiptOpen] = useState(false)
  const confirmationId = useRef('')
  const detailRef = useRef(null)
  const selectedId = selected?.row.id
  useEffect(() => { if (selectedId) detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }, [selectedId])
  const initialComplete = rows.some((r) => r.purpose === 'SECURITY_DEPOSIT' && r.status === 'CONFIRMED')
    && rows.some((r) => r.installmentNumber === 1 && r.status === 'CONFIRMED')

  const collectible = row => canCollect && (row.purpose === 'ADJUSTMENT' ? canPayAdjustment : canPayRent)
  const pendingEvidence = selected?.detail.evidences?.filter(item => item.status === 'PENDING').sort((a,b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')) || b.id - a.id)[0]
  const selectedRow = selected && rows.find(row => row.id === selected.row.id)
  const detailFresh = selected?.rows === rows && selected?.detailRetry === detailRetry && !detailError
  useEffect(() => {
    if (!selected?.row.id) return
    let live = true
    const id = selected.row.id
    providerFinanceService.getPaymentRequestDetail(id).then(detail => {
      if (live) { setSelected(old => old?.row.id === id ? { ...old, detail, rows, detailRetry } : old); setDetailError('') }
    }).catch(() => { if (live) setDetailError('Chưa đồng bộ được chi tiết. Nội dung đã nhập vẫn được giữ; hãy thử đồng bộ lại trước khi gửi.') })
    return () => { live = false }
  }, [rows, selected?.row.id, detailRetry])
  const selectedAction = selectedRow && collectible(selectedRow) && (role === 'renter' ? ['PENDING', 'REJECTED', 'PARTIALLY_PAID'] : ['PENDING', 'PROOF_SUBMITTED', 'PARTIALLY_PAID']).includes(selectedRow.status) && (selectedRow.installmentNumber <= 1 || initialComplete)
  const confirmationKey = JSON.stringify([selectedRow?.id, selectedRow?.status, selectedRow?.amount, selectedRow?.paidAmount, selected?.detail.bankNameSnapshot, selected?.detail.accountNumberSnapshot, selected?.detail.accountHolderSnapshot, selected?.detail.transferReference])

  async function open(row) {
    setPaymentError(''); setDetailError(''); setMode('receive')
    await run(async () => {
      const detail = await providerFinanceService.getPaymentRequestDetail(row.id)
      confirmationId.current = yearlyUuid()
      setSelected({ row, detail, rows, detailRetry, actionOpened: collectible(row) && (role === 'renter' ? ['PENDING', 'REJECTED', 'PARTIALLY_PAID'] : ['PENDING', 'PROOF_SUBMITTED', 'PARTIALLY_PAID']).includes(row.status) && (row.installmentNumber <= 1 || initialComplete) })
    }, '', false)
  }

  async function submit(data) {
    if (busy || !detailFresh || !selectedAction) { setPaymentError('Khoản thanh toán đang cập nhật hoặc không còn cho phép thao tác. Kiểm tra trạng thái mới trước khi gửi.'); return false }
    setPaymentError('')
    const success = await run(async () => {
      try {
        if (role === 'renter') {
          await providerFinanceService.submitPaymentProof(selected.row.id, {
            file: data.get('file'), transferredAmount: Number(data.get('amount')),
            transactionReference: data.get('reference'), note: 'Thanh toán ' + name(selected.row),
          })
        } else {
          const result = await providerFinanceService.confirmPayment(selected.row.id, {
            receivedAmount: Number(data.get('amount')), bankTransactionRef: data.get('reference'),
            confirmationId: confirmationId.current, note: 'Nhà cung cấp xác nhận ' + name(selected.row),
          })
          if (!['CONFIRMED', 'PARTIALLY_PAID'].includes(result?.status)) {
            throw new Error(result?.issueNote || 'Khoản tiền cần được đối soát thêm. Chưa xác nhận thanh toán thành công; tải lại trạng thái trước khi thao tác tiếp.')
          }
        }
      } catch (error) {
        const message = error.message || 'Chưa thể xử lý khoản thanh toán.'
        setPaymentError(message.includes('đã được đối soát trước đó')
          ? 'Mã giao dịch ngân hàng này đã được dùng để ghi nhận khoản khác. Cọc và tiền thuê tháng là các lần chuyển riêng, cần mã giao dịch riêng. Nếu đang demo, dùng mã mới cho đúng lần chuyển hiện tại; không xác nhận lại cùng một lần chuyển tiền.'
          : message)
        throw error
      }
    }, role === 'renter' ? 'Đã gửi bằng chứng, chờ nhà cung cấp xác nhận.' : 'Đã ghi nhận số tiền thực nhận. Kiểm tra số đã nhận và số còn phải trả trong bảng.')
    if (success) setSelected(null)
    return success
  }

  return (
    <section id="yearly-payments" className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
      <h3 className="text-lg font-bold text-slate-900">Tiền bảo đảm và lịch thanh toán</h3>
      <p className="text-sm text-slate-600">Chuyển tiền trực tiếp cho nhà cung cấp theo từng nội dung thanh toán. Tiền bảo đảm được giữ riêng, không trừ vào tiền thuê. Ảnh biên lai cần được nhà cung cấp xác nhận.</p>
      {!rows.length ? <p className="text-sm text-amber-800">Các khoản thanh toán sẽ xuất hiện sau khi hợp đồng được lưu.</p> : <>
        {!initialComplete && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Hoàn tất tiền bảo đảm và tháng đầu trước khi nhận xe hoặc thanh toán trước các tháng sau.</p>}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead><tr className="border-b text-slate-500"><th className="p-2">Khoản thanh toán</th><th className="p-2">Hạn trả</th><th className="p-2">Phải trả / Đã trả</th><th className="p-2">Trạng thái</th><th className="p-2">Thao tác</th></tr></thead>
            <tbody>{visibleRows.map((row) => {
              const payable = collectible(row) && ['PENDING', 'REJECTED', 'PARTIALLY_PAID'].includes(row.status)
                && (row.installmentNumber <= 1 || initialComplete)
              const confirmable = collectible(row) && ['PENDING', 'PROOF_SUBMITTED', 'PARTIALLY_PAID'].includes(row.status)
                && (row.installmentNumber <= 1 || initialComplete)
              return <tr key={row.id} className="border-b border-slate-100 align-top">
                <td className="p-2 font-medium">{name(row)}{row.periodStart && <span className="mt-1 block text-xs font-normal text-slate-500">{yearlyDate(row.periodStart)} – {yearlyDate(row.periodEnd)}</span>}</td>
                <td className="p-2">{row.dueOn ? yearlyDate(row.dueOn) : 'Trước bàn giao'}</td>
                <td className="p-2 whitespace-nowrap">{yearlyMoney(row.amount)}<span className="block text-xs text-emerald-800">Đã đối soát: {yearlyMoney(row.paidAmount)}</span></td>
                <td className="p-2">{labels[row.status] || row.status}{row.overdue && <span className="block text-xs text-red-700">Đã quá hạn</span>}</td>
                <td className="p-2">{(role === 'renter' ? payable : confirmable) && <button disabled={busy} className={yearlyButtonClass} onClick={() => open(row)}>{role === 'renter' ? 'Thanh toán' : 'Đối soát'}</button>}{!(role === 'renter' ? payable : confirmable) && <button disabled={busy} className="text-emerald-800 underline" onClick={() => open(row)}>Xem chi tiết / biên lai</button>}</td>
              </tr>
            })}</tbody>
          </table>
        </div>
      </>}
      {selected && <div ref={detailRef} role="region" aria-label="Chi tiết khoản thanh toán" className="scroll-mt-24 space-y-3 rounded-xl border border-emerald-200 p-4">
        <div className="flex items-center justify-between gap-3"><h4 className="font-bold">{name(selected.row)}</h4><button disabled={busy} onClick={() => setSelected(null)} className="text-sm underline">Đóng</button></div>
        {!detailFresh && <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm">Đang đồng bộ chi tiết khoản thanh toán. Nội dung đang nhập được giữ nguyên; nút gửi mở lại sau khi cập nhật xong.</p>}
        {detailError && <div role="alert" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-950"><p>{detailError}</p><button type="button" disabled={busy} className="mt-2 font-semibold underline" onClick={() => { setDetailError(''); setDetailRetry(value => value + 1) }}>Thử đồng bộ lại</button></div>}
        <dl className="grid gap-2 rounded-lg bg-slate-50 p-3 text-sm sm:grid-cols-3"><div><dt>Nghĩa vụ phải trả</dt><dd className="font-semibold">{yearlyMoney(selectedRow?.amount)}</dd></div><div><dt>Đã đối soát vào khoản này</dt><dd className="font-semibold">{yearlyMoney(selectedRow?.paidAmount)}</dd></div><div><dt>Còn phải trả</dt><dd className="font-semibold">{yearlyMoney(Math.max(0, Number(selectedRow?.amount || 0) - Number(selectedRow?.paidAmount || 0)))}</dd></div></dl>
        <p className="rounded-lg bg-amber-50 p-3 text-sm">Số trên biên lai là renter khai báo, chưa phải tiền đã xác nhận. Không cộng các ảnh biên lai thành tiền thực nhận. Provider kiểm tra sao kê và nhập số của đúng giao dịch; chuyển thiếu được ghi nhận một phần, phần còn lại vẫn phải trả.</p>
        <dl className="grid gap-2 text-sm sm:grid-cols-2">
          <div><dt className="text-slate-500">Ngân hàng</dt><dd>{selected.detail.bankNameSnapshot}</dd></div>
          <div><dt className="text-slate-500">Chủ tài khoản</dt><dd>{selected.detail.accountHolderSnapshot}</dd></div>
          <div><dt className="text-slate-500">Số tài khoản</dt><dd className="font-mono">{selected.detail.accountNumberSnapshot}</dd></div>
          <div><dt className="text-slate-500">Nội dung chuyển khoản</dt><dd className="break-all font-mono">{selected.detail.transferReference}</dd></div>
        </dl>
        {detailFresh && selectedAction && role === 'renter' && <p className="text-sm text-slate-600">QR mặc định là số còn phải trả. Nếu chỉ chuyển một phần, chỉnh số tiền trong ứng dụng ngân hàng trước khi chuyển, rồi khai đúng số thực chuyển ở form dưới. Nhập số tại đây không thay đổi giao dịch ngân hàng.</p>}
        {detailFresh && selectedAction && role === 'renter' && selected.detail.qrCodeUrl && <img src={selected.detail.qrCodeUrl} alt="Mã QR chuyển khoản cho nhà cung cấp" className="mx-auto w-64 max-w-full" />}
        {selected.detail.evidences?.map((item) => <div key={item.id} className="rounded-lg bg-slate-50 p-3"><p className="mb-2 text-sm">Biên lai #{item.id} · Renter khai đã chuyển: <strong>{yearlyMoney(item.transferredAmount)}</strong> · {item.transactionReference || 'Chưa nhập mã giao dịch'} · {({ PENDING: 'Chờ đối soát', ACCEPTED: 'Đã xem xét', REJECTED: 'Đã từ chối' })[item.status] || item.status}</p><PaymentEvidenceImage evidence={item} className="max-h-64 max-w-full rounded-lg object-contain" /></div>)}
        {paymentError && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{paymentError}</p>}
        {!selectedAction && <p className="rounded-lg bg-slate-50 p-3 text-sm">Chỉ xem thông tin và biên lai. Khoản này hiện không mở thu tiền; không chuyển thêm theo thông tin ngân hàng bên trên.</p>}
        {selected.detail.rejectionReason && <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm">{selectedRow?.status === 'PROOF_SUBMITTED' ? 'Phản hồi trước (biên lai mới đang chờ đối soát): ' : 'Provider yêu cầu bổ sung: '}{selected.detail.rejectionReason}</p>}
        {role === 'provider' && <div className="flex flex-wrap gap-2 rounded-lg border p-3">
          {selectedAction && <button disabled={busy || !detailFresh} className={yearlyButtonClass} onClick={() => setMode('receive')}>Ghi nhận tiền thực nhận</button>}
          {detailFresh && canCollect && selectedRow?.status === 'PROOF_SUBMITTED' && pendingEvidence && <button disabled={busy} className="rounded-lg border border-amber-500 px-3 py-2 text-sm font-semibold text-amber-900" onClick={() => setMode('reject')}>Chưa thấy tiền / yêu cầu sửa biên lai</button>}
          {detailFresh && allowExceptions && <button disabled={busy} className="rounded-lg border px-3 py-2 text-sm font-semibold" onClick={() => setBankReceiptOpen(true)}>Tiền đã nhận nhưng cần đối soát</button>}
        </div>}
        {(selected.actionOpened || selectedAction) && (role === 'renter' || mode === 'receive') && <ActionForm key={selected.row.id} title={role === 'renter' ? 'Gửi biên lai chuyển khoản' : 'Xác nhận tiền đã thực nhận'}
          description={role === 'provider' ? 'Kiểm tra tiền thực nhận. Mã giao dịch ngân hàng là mã của lần chuyển khoản này, không phải mã booking hay nội dung chuyển khoản. Không dùng lại mã đã xác nhận cọc hoặc tháng khác.' : 'Chuyển đúng nội dung cho khoản đang chọn; sau đó gửi ảnh biên lai.'}
          fields={[
            { name: 'amount', label: role === 'renter' ? 'Số tiền của lần chuyển này theo ngân hàng (đ)' : 'Số tiền thực nhận của giao dịch trên sao kê (đ)', type: 'money', min: 1000 },
            { name: 'reference', label: 'Mã giao dịch ngân hàng của lần chuyển này', type: 'text' },
            ...(role === 'renter' ? [{ name: 'file', label: 'Ảnh biên lai', type: 'file' }] : []),
          ]} confirm confirmationKey={confirmationKey} disabled={!selectedAction} submissionDisabled={!detailFresh} busy={busy} onSubmit={submit} submitLabel={role === 'renter' ? 'Gửi bằng chứng' : 'Xác nhận đã nhận tiền'} />}
        {role === 'provider' && mode === 'reject' && pendingEvidence && <ActionForm key={pendingEvidence.id} disabled={!detailFresh || !canCollect || selectedRow?.status !== 'PROOF_SUBMITTED'} title={'Yêu cầu bổ sung biên lai #' + pendingEvidence.id} description="Chỉ phản hồi biên lai mới. Tiền đã ghi nhận từ những lần trước được giữ nguyên; không hủy nghĩa vụ còn thiếu." submitLabel="Gửi yêu cầu bổ sung" busy={busy} confirm fields={[{ name: 'reason', label: 'Lý do chưa xác nhận / nội dung cần bổ sung', type: 'textarea' }]} onSubmit={async d => {
          if (!detailFresh || !canCollect || selectedRow?.status !== 'PROOF_SUBMITTED') return false
          const ok = await run(() => providerFinanceService.rejectYearlyEvidence(selected.row.id, pendingEvidence.id, { reason: d.get('reason') }), 'Đã phản hồi biên lai mới. Tiền đã đối soát trước đó được giữ nguyên; renter có thể gửi lại bằng chứng.')
          if (ok) setSelected(null)
          return ok
        }} />}
        {role === 'renter' && <YearlyPaymentFeedback request={selected.detail} canReport={detailFresh && canReportMismatch} busy={busy} run={run} onDone={() => setSelected(null)} />}
        {bankReceiptOpen && <BankReceiptReconciliationModal request={selected.detail} onClose={() => setBankReceiptOpen(false)} onRecorded={async () => { setBankReceiptOpen(false); setSelected(null); await run(async () => {}, 'Đã ghi nhận tiền để đối soát, chưa áp vào nghĩa vụ và chưa tự hoàn tiền.') }} />}
      </div>}
    </section>
  )
}
