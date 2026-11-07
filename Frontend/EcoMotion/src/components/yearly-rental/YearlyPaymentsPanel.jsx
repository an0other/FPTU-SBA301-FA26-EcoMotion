import { useRef, useState } from 'react'
import { providerFinanceService } from '../../services/providerFinanceService'
import { PaymentEvidenceImage } from '../payments/PaymentEvidenceImage'
import ActionForm from './YearlyActionForm'
import { yearlyButtonClass, yearlyDate, yearlyMoney, yearlyUuid } from '../../utils/yearlyRental'

const labels = { PENDING: 'Chưa thanh toán', PROOF_SUBMITTED: 'Chờ nhà cung cấp đối soát', PARTIALLY_PAID: 'Đã trả một phần', CONFIRMED: 'Đã nhận tiền', REJECTED: 'Cần gửi lại bằng chứng', EXPIRED: 'Đã hết hạn', REVIEW_REQUIRED: 'Cần đối soát', VOIDED: 'Đã dừng thu' }
const name = (row) => row.purpose === 'SECURITY_DEPOSIT' ? 'Tiền bảo đảm' : row.installmentNumber > 0 ? 'Tiền thuê tháng ' + row.installmentNumber : 'Thanh toán bổ sung quyết toán'

export default function YearlyPaymentsPanel({ role, rows, busy, run }) {
  const [selected, setSelected] = useState(null)
  const [paymentError, setPaymentError] = useState('')
  const confirmationId = useRef('')
  const initialComplete = rows.some((r) => r.purpose === 'SECURITY_DEPOSIT' && r.status === 'CONFIRMED')
    && rows.some((r) => r.installmentNumber === 1 && r.status === 'CONFIRMED')

  async function open(row) {
    setPaymentError('')
    await run(async () => {
      const detail = await providerFinanceService.getPaymentRequestDetail(row.id)
      confirmationId.current = yearlyUuid()
      setSelected({ row, detail })
    }, '', false)
  }

  async function submit(data) {
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
          ? 'Mã giao dịch ngân hàng này đã được dùng để ghi nhận khoản khác. Cọc và tiền thuê tháng là các lần chuyển riêng, cần mã giao dịch riêng. Nếu đang demo, dùng mã mới cho giao dịch tháng 1; không xác nhận lại cùng một lần chuyển tiền.'
          : message)
        throw error
      }
    }, role === 'renter' ? 'Đã gửi bằng chứng, chờ nhà cung cấp xác nhận.' : 'Đã ghi nhận tiền thực nhận.')
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
          <table className="w-full min-w-[650px] text-left text-sm">
            <thead><tr className="border-b text-slate-500"><th className="p-2">Khoản thanh toán</th><th className="p-2">Hạn trả</th><th className="p-2">Phải trả / Đã trả</th><th className="p-2">Trạng thái</th><th className="p-2">Thao tác</th></tr></thead>
            <tbody>{rows.map((row) => {
              const payable = ['PENDING', 'REJECTED', 'PARTIALLY_PAID'].includes(row.status)
                && (row.installmentNumber <= 1 || initialComplete)
              const confirmable = ['PENDING', 'PROOF_SUBMITTED', 'PARTIALLY_PAID'].includes(row.status)
                && (row.installmentNumber <= 1 || initialComplete)
              return <tr key={row.id} className="border-b border-slate-100 align-top">
                <td className="p-2 font-medium">{name(row)}{row.periodStart && <span className="mt-1 block text-xs font-normal text-slate-500">{yearlyDate(row.periodStart)} – {yearlyDate(row.periodEnd)}</span>}</td>
                <td className="p-2">{row.dueOn ? yearlyDate(row.dueOn) : 'Trước bàn giao'}</td>
                <td className="p-2 whitespace-nowrap">{yearlyMoney(row.amount)}<span className="block text-xs text-emerald-800">Đã nhận: {yearlyMoney(row.paidAmount)}</span></td>
                <td className="p-2">{labels[row.status] || row.status}{row.overdue && <span className="block text-xs text-red-700">Đã quá hạn</span>}</td>
                <td className="p-2">{(role === 'renter' ? payable : confirmable) && <button disabled={busy} className={yearlyButtonClass} onClick={() => open(row)}>{role === 'renter' ? 'Thanh toán' : 'Đối soát'}</button>}</td>
              </tr>
            })}</tbody>
          </table>
        </div>
      </>}
      {selected && <div className="space-y-3 rounded-xl border border-emerald-200 p-4">
        <div className="flex items-center justify-between gap-3"><h4 className="font-bold">{name(selected.row)}</h4><button disabled={busy} onClick={() => setSelected(null)} className="text-sm underline">Đóng</button></div>
        <dl className="grid gap-2 text-sm sm:grid-cols-2">
          <div><dt className="text-slate-500">Ngân hàng</dt><dd>{selected.detail.bankNameSnapshot}</dd></div>
          <div><dt className="text-slate-500">Chủ tài khoản</dt><dd>{selected.detail.accountHolderSnapshot}</dd></div>
          <div><dt className="text-slate-500">Số tài khoản</dt><dd className="font-mono">{selected.detail.accountNumberSnapshot}</dd></div>
          <div><dt className="text-slate-500">Nội dung chuyển khoản</dt><dd className="break-all font-mono">{selected.detail.transferReference}</dd></div>
        </dl>
        {role === 'renter' && selected.detail.qrCodeUrl && <img src={selected.detail.qrCodeUrl} alt="Mã QR chuyển khoản cho nhà cung cấp" className="mx-auto w-64 max-w-full" />}
        {selected.detail.evidences?.map((item) => <div key={item.id} className="rounded-lg bg-slate-50 p-3"><p className="mb-2 text-sm">Biên lai: {yearlyMoney(item.transferredAmount)} · {item.transactionReference || 'Chưa nhập mã giao dịch'}</p><PaymentEvidenceImage evidence={item} className="max-h-64 max-w-full rounded-lg object-contain" /></div>)}
        {paymentError && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{paymentError}</p>}
        <ActionForm key={selected.row.id} title={role === 'renter' ? 'Gửi biên lai chuyển khoản' : 'Xác nhận tiền đã thực nhận'}
          description={role === 'provider' ? 'Kiểm tra tiền thực nhận. Mã giao dịch ngân hàng là mã của lần chuyển khoản này, không phải mã booking hay nội dung chuyển khoản. Không dùng lại mã đã xác nhận cọc hoặc tháng khác.' : 'Chuyển đúng nội dung cho khoản đang chọn; sau đó gửi ảnh biên lai.'}
          fields={[
            { name: 'amount', label: 'Số tiền thực chuyển / thực nhận (đ)', type: 'number', min: 1000, value: selected.detail.remainingAmount },
            { name: 'reference', label: 'Mã giao dịch ngân hàng của lần chuyển này', type: 'text' },
            ...(role === 'renter' ? [{ name: 'file', label: 'Ảnh biên lai', type: 'file' }] : []),
          ]} confirm busy={busy} onSubmit={submit} submitLabel={role === 'renter' ? 'Gửi bằng chứng' : 'Xác nhận đã nhận tiền'} />
        {role === 'provider' && selected.row.status === 'PROOF_SUBMITTED' && <ActionForm title="Yêu cầu gửi lại biên lai" busy={busy} confirm fields={[{ name: 'reason', label: 'Lý do từ chối biên lai', type: 'textarea' }]} onSubmit={async d => {
          const ok = await run(() => providerFinanceService.rejectPayment(selected.row.id, { reason: d.get('reason') }), 'Đã yêu cầu gửi lại biên lai.')
          if (ok) setSelected(null)
          return ok
        }} />}
      </div>}
    </section>
  )
}
