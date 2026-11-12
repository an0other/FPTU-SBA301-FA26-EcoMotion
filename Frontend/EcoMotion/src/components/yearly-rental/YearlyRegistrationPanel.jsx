import { useState } from 'react'
import { yearlyWorkflowService } from '../../services/yearlyWorkflowService'
import ActionForm from './YearlyActionForm'
import YearlyContractPanel from './YearlyContractPanel'
import YearlyPaymentsPanel from './YearlyPaymentsPanel'
import EvidenceImage from '../booking/EvidenceImage'
import { cancelMyBooking } from '../../services/bookingService'
import { yearlyDate, yearlyMoney } from '../../utils/yearlyRental'

const steps = ['Chờ duyệt', 'Điều khoản', 'Hợp đồng đã ký', 'Cọc và tháng đầu', 'Giao nhận xe']
const phases = { REQUESTED: 0, TERMS_DRAFT: 1, TERMS_REVIEW: 1, CONTRACT: 2, PAYMENT: 3, NOT_DISPATCHED: 4, DISPATCHED: 4, ARRIVED: 4 }
export default function YearlyRegistrationPanel({ role, code, data, busy, run, act, form, fields, upload }) {
  const [editingHandover, setEditingHandover] = useState(false)
  const r = data.registration, v = data.workflow, h = data.handover
  const provider = role === 'provider'
  const current = phases[r.phase]
  const closed = current == null
  const payments = data.payments || []
  const initial = payments.filter(p => p.purpose === 'SECURITY_DEPOSIT' || p.installmentNumber === 1)
  const remaining = initial.reduce((sum, p) => sum + Number(p.remainingAmount || 0), 0)
  const terms = <dl className="grid gap-3 text-sm sm:grid-cols-2">
    <div><dt>Km cho phép cả năm</dt><dd>{v.annualKm ?? 'Chưa đề xuất'}</dd></div>
    <div><dt>Phí vượt mỗi km</dt><dd>{v.annualKm == null ? 'Chưa đề xuất' : yearlyMoney(v.excessKmRate)}</dd></div>
    <div><dt>Số ngày ân hạn trả xe</dt><dd>{v.annualKm == null ? 'Chưa đề xuất' : v.graceDays}</dd></div>
    <div><dt>Phí trả trễ mỗi ngày</dt><dd>{v.annualKm == null ? 'Chưa đề xuất' : yearlyMoney(v.lateDailyRate)}</dd></div>
  </dl>
  return <div className="space-y-5">
    <header className="rounded-xl border bg-white p-5">
      <h2 className="text-xl font-bold">Đăng ký thuê năm · {code}</h2>
      <p className="mt-2 text-sm">Dự kiến nhận {yearlyDate(v.contract.plannedStart)} · trả {yearlyDate(v.contract.dueOn)}</p>
      <ol className="mt-4 grid gap-2 sm:grid-cols-5">{steps.map((label, i) => <li key={label} aria-current={current === i ? 'step' : undefined} className={'rounded-lg p-3 text-sm ' + (current === i ? 'bg-emerald-100 font-bold text-emerald-950' : 'bg-slate-50 text-slate-600')}>{i + 1}. {label}{current > i ? ' ✓' : ''}</li>)}</ol>
    </header>
    <section role="status" className="rounded-xl border border-blue-200 bg-blue-50 p-5">
      <h3 className="font-bold">{closed ? 'Đăng ký đã dừng' : 'Việc cần làm tiếp theo'}</h3>
      <p className="mt-2">{r.message}</p>
      {r.holdExpiresAt && current < 4 && <p className="mt-2 text-sm">Hạn giữ xe: {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(r.holdExpiresAt))}. Hoàn thành bước hiện tại trước hạn này.</p>}
      {!closed && <p className="mt-2 text-sm">Bên cần thực hiện: {({ PROVIDER: 'Nhà cung cấp', RENTER: 'Người thuê', BOTH: 'Người thuê chuyển tiền, nhà cung cấp đối soát' })[r.responsibleRole]}</p>}
      {r.phase === 'REQUESTED' && provider && <p className="mt-2 text-sm">Dùng nút Chấp nhận hoặc Từ chối yêu cầu ở đầu trang này.</p>}
    </section>
    <section className="rounded-xl border bg-white p-5">
      <h3 className="font-bold">Giá và tiền cần chuẩn bị</h3>
      <dl className="mt-3 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div><dt>Tổng thuê 12 tháng</dt><dd className="font-bold">{yearlyMoney(v.contract.totalRent)}</dd></div>
        <div><dt>Cọc riêng, không phải tiền thuê</dt><dd className="font-bold">{yearlyMoney(r.securityDeposit)}</dd></div>
        <div><dt>Tiền thuê tháng đầu</dt><dd className="font-bold">{yearlyMoney(r.firstMonthRent)}</dd></div>
        <div><dt>Phí giao xe thu cùng tháng đầu</dt><dd className="font-bold">{yearlyMoney(r.pickupFee)}</dd></div>
      </dl>
      <p className="mt-3 text-sm text-slate-600">{current < 3 ? 'Chưa cần chuyển tiền. Chỉ sử dụng thông tin ngân hàng trên yêu cầu thanh toán sau khi nộp hợp đồng.' : current === 3 ? 'Còn cần đối soát trước nhận xe: ' + yearlyMoney(remaining) : 'Đã hoàn tất cọc và tháng đầu. Chưa bắt đầu thuê cho đến khi người thuê xác nhận nhận xe.'}</p>
    </section>
    {!closed && current === 1 && <section className="space-y-4 rounded-xl border bg-white p-5">
      <h3 className="font-bold">Điều khoản hai bên cần thống nhất</h3>
      {terms}
      <p className="text-sm">Thanh toán trước từng tháng. Trả sớm giữ tiền tháng đã bắt đầu và tính phí tối đa một tháng trung bình làm tròn, không vượt tiền thuê còn lại. Cọc quyết toán riêng.</p>
      {provider && r.canSetTerms && form('Đề xuất điều khoản', 'terms', d => Object.fromEntries(fields.terms.map(f => [f.name, Number(d.get(f.name))])), { fields: fields.terms.map(f => ({ ...f, value: v[f.name] ?? f.value })) })}
      {!provider && r.canAcceptTerms && form('Tôi đồng ý các điều khoản trên', 'terms/accept', () => ({}))}
    </section>}
    {!closed && current === 2 && (!provider && data.contract ? <YearlyContractPanel contract={data.contract} onUploaded={() => run(async () => {}, 'Đã nộp hợp đồng đã ký. Tiếp theo thanh toán cọc và tháng đầu.')} /> : <p className="rounded-xl border bg-white p-5">Chờ người thuê nộp hợp đồng PDF đã ký. Chưa nhận tiền ở bước này.</p>)}
    {!closed && current === 3 && <>
      <p className="text-sm">Chuyển hai giao dịch riêng: cọc và tháng đầu kèm phí giao xe. Mỗi giao dịch có mã ngân hàng riêng. Không chuyển toàn bộ giá năm.</p>
      <YearlyPaymentsPanel role={role} rows={payments} busy={busy} run={run} initialOnly canCollect={r.canPay} canReportMismatch={!!data.booking?.disputeCategories?.includes('PAYMENT_MISMATCH')} />
    </>}
    {!closed && current === 4 && <section className="space-y-4 rounded-xl border bg-white p-5">
      <h3 className="font-bold">Bàn giao xe tự lái</h3>
      {h.initialKm != null && <p>Công-tơ-mét khi giao: {h.initialKm} km</p>}
      {h.evidenceUrl && <EvidenceImage src={h.evidenceUrl} alt="Ảnh bàn giao và công-tơ-mét" openable className="max-h-64 max-w-full rounded-lg object-contain" />}
      {provider && r.canDispatch && form('Bắt đầu giao xe', 'handover/dispatch', () => ({}))}
      {provider && h.stage === 'ARRIVED' && <div role="status" className="space-y-2 rounded-lg bg-emerald-50 p-4 text-emerald-950">
        <p className="font-semibold">Đã gửi biên bản giao xe — chờ người thuê xác nhận nhận xe.</p>
        <p className="text-sm">Ảnh và số km phía trên là dữ liệu đã lưu. Không cần gửi lại nếu thông tin đúng.</p>
        {r.canArrive && !editingHandover && <button type="button" disabled={busy} className="text-sm font-semibold underline" onClick={() => setEditingHandover(true)}>Chỉnh sửa ảnh / số km đã gửi</button>}
      </div>}
      {provider && r.canArrive && (h.stage === 'DISPATCHED' || editingHandover) && <>
        <ActionForm title={h.stage === 'ARRIVED' ? 'Chỉnh sửa biên bản giao xe' : 'Ghi nhận xe đã đến điểm giao'} submitLabel={h.stage === 'ARRIVED' ? 'Lưu thay đổi và gửi lại' : 'Gửi biên bản cho người thuê'} busy={busy} confirm
          fields={[{ name: 'initialKm', label: 'Công-tơ-mét khi giao (km)', type: 'number', value: h.initialKm }, { name: 'file', label: 'Ảnh bàn giao và công-tơ-mét', type: 'file', required: h.stage !== 'ARRIVED' }]}
          onSubmit={async d => {
            const success = await run(async () => {
              const file = d.get('file')
              const evidenceId = file?.size ? (await upload(file)).id : data.evidence.find(e => e.imageUrl === h.evidenceUrl)?.id
              if (!evidenceId) throw new Error('Chọn ảnh bàn giao để gửi biên bản.')
              await act('handover/arrive', { initialKm: Number(d.get('initialKm')), evidenceId })
            }, 'Đã gửi biên bản giao xe. Đang chờ người thuê kiểm tra và xác nhận.')
            if (success) setEditingHandover(false)
            return success
          }} />
        {editingHandover && <button type="button" disabled={busy} onClick={() => setEditingHandover(false)} className="text-sm underline">Hủy chỉnh sửa — giữ biên bản đã gửi</button>}
      </>}
      {r.receiptIssue && <div role="alert" className="space-y-2 rounded-lg border border-amber-300 bg-amber-50 p-4 text-amber-950">
        <p className="font-semibold">Chưa thể nhận xe vào ngày {yearlyDate(r.receiptStart)}</p>
        <p className="text-sm">{r.receiptIssue}</p>
        <p className="text-sm">Ngày nhận đã đặt là {yearlyDate(v.contract.plannedStart)}. Biên bản đã được lưu; không cần upload hoặc thanh toán lại. Hãy nhận theo lịch đã giữ hoặc thống nhất lịch mới còn trống với nhà cung cấp.</p>
      </div>}
      {!provider && r.canReceive && <>
        <p className="rounded-lg bg-amber-50 p-4 text-sm">Nếu xác nhận nhận xe hôm nay, hợp đồng bắt đầu {yearlyDate(r.receiptStart)} và kết thúc {yearlyDate(r.receiptDue)}. Lịch 12 kỳ chuyển theo ngày nhận thực tế; giá và cọc giữ nguyên. BE kiểm tra lại lịch xe và hạn GPLX.</p>
        {form('Tôi đã kiểm tra xe, số km và đồng ý nhận xe', 'handover/receive', () => ({ version: h.version }))}
      </>}
    </section>}
    {provider && current >= 3 && <button disabled={busy} className="text-sm font-semibold text-emerald-800 underline" onClick={() => run(async () => {
      const blob = await yearlyWorkflowService.downloadProviderContract(code)
      const url = URL.createObjectURL(blob), link = document.createElement('a')
      link.href = url; link.download = 'hop-dong-' + code + '.pdf'; link.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    }, 'Đã tải hợp đồng đã ký.', false)}>Tải hợp đồng đã ký</button>}
    <details className="rounded-xl border bg-white p-5"><summary className="cursor-pointer font-semibold">Xem lại điều khoản và các khoản thanh toán</summary><div className="mt-4 space-y-4">{terms}{!provider && data.contract?.contractUploaded && <YearlyContractPanel contract={{ ...data.contract, canUpload: false }} onUploaded={() => {}} />}<YearlyPaymentsPanel role={role} rows={payments} busy={busy} run={run} initialOnly canCollect={false} canReportMismatch={!!data.booking?.disputeCategories?.includes('PAYMENT_MISMATCH')} /><p className="text-sm">Các tháng sau thanh toán sau khi nhận xe. Giá thuê 12 tháng không bao gồm phí giao xe.</p></div></details>
    {!provider && !closed && <details className="rounded-xl border border-red-100 p-4"><summary className="cursor-pointer text-sm text-red-800">Hủy yêu cầu trước khi nhận xe</summary><ActionForm title="Xác nhận hủy trước nhận xe" description="Nếu đã chuyển tiền, khoản đó cần được đối soát; gửi hủy không có nghĩa đã được hoàn tiền." busy={busy} confirm onSubmit={() => run(() => cancelMyBooking(code), 'Đã ghi nhận hủy yêu cầu. Kiểm tra trạng thái đối soát khoản đã chuyển nếu có.')} /></details>}
  </div>
}
