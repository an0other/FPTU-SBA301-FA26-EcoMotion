import { yearlyDate, yearlyMoney } from '../../utils/yearlyRental'

const stages = ['ACTIVE', 'RETURN_REQUESTED', 'RETURN_INSPECTED', 'RETURNED', 'SETTLEMENT_PROPOSED', 'SETTLEMENT_ACCEPTED', 'REFUND_TRANSFER_RECORDED', 'REFUND_RECEIVED', 'COMPLETED']
export default function YearlyRentalStatus({ view, provider, reviewOpen, payments, operations }) {
  const stage = view.stage
  const returned = stages.indexOf(stage) >= stages.indexOf('RETURNED')
  const completed = stage === 'COMPLETED'
  const due = Number(view.money?.balanceDue || 0)
  const balancePaid = due === 0 || payments.some(p => p.purpose === 'ADJUSTMENT' && p.status === 'CONFIRMED')
  const next = {
    ACTIVE: 'Đang sử dụng xe. Thanh toán trước kỳ tiếp theo; chỉ gửi yêu cầu trả khi muốn bắt đầu thủ tục kết thúc.',
    RETURN_REQUESTED: provider ? 'Người thuê đã gửi yêu cầu trả xe. Nhận lại xe, kiểm tra và gửi biên bản ảnh/km bên dưới.' : 'Đã gửi yêu cầu trả xe. Chờ nhà cung cấp nhận lại xe và gửi biên bản kiểm định; hợp đồng chưa hoàn tất.',
    RETURN_INSPECTED: provider ? 'Đã gửi biên bản kiểm định. Chờ người thuê xác nhận ngày trả, tình trạng xe và số km.' : 'Nhà cung cấp đã gửi kiểm định. Xem ngày trả, ảnh và số km, rồi xác nhận hoặc yêu cầu xem xét.',
    RETURNED: provider ? 'Hai bên đã xác nhận trả xe. Lập bảng quyết toán để người thuê kiểm tra.' : 'Đã trả xe, kết thúc sử dụng. Chờ nhà cung cấp gửi quyết toán; cọc chưa mặc định được hoàn.',
    SETTLEMENT_PROPOSED: provider ? 'Đã gửi quyết toán. Chờ người thuê đồng ý hoặc phản hồi.' : 'Kiểm tra từng khoản phí, tiền đã trả và cọc; đồng ý quyết toán hoặc nêu nội dung chưa đồng ý.',
    SETTLEMENT_ACCEPTED: !balancePaid ? 'Quyết toán đã chốt. Người thuê cần thanh toán bổ sung ' + yearlyMoney(due) + '; nhà cung cấp đối soát khoản này trước khi hoàn tiền.' : 'Quyết toán đã chốt và không còn khoản bổ sung chưa trả. Nhà cung cấp chuyển tiền hoàn nếu có, hoặc hoàn tất nếu hết nghĩa vụ.',
    REFUND_TRANSFER_RECORDED: provider ? 'Đã ghi nhận chuyển tiền hoàn. Chờ người thuê xác nhận thực nhận.' : 'Nhà cung cấp đã gửi bằng chứng chuyển hoàn. Xác nhận đã nhận đủ hoặc báo chưa nhận/nhận thiếu.',
    REFUND_RECEIVED: provider ? 'Người thuê đã xác nhận nhận đủ tiền hoàn. Bấm Hoàn tất hợp đồng khi các report và nghĩa vụ còn lại đã xử lý.' : 'Đã xác nhận tiền hoàn. Chờ nhà cung cấp hoàn tất hồ sơ.',
    COMPLETED: 'Hợp đồng đã hoàn tất. Thông tin và chứng từ được giữ để tra cứu.',
  }
  const mode = { EARLY: 'Trả sớm theo chính sách', STANDARD: 'Trả theo hạn hợp đồng', FULL_CONTRACT: 'Thanh toán đủ hợp đồng', PROVIDER_FAILURE: 'Kết thúc do phía nhà cung cấp' }[view.returnMode]
  return <section aria-label="Trạng thái hợp đồng thuê năm" className="space-y-3 rounded-xl border border-emerald-200 bg-emerald-50 p-5">
    <h2 className="text-xl font-bold">{completed ? 'Hợp đồng đã hoàn tất' : returned ? 'Đã kết thúc sử dụng xe — đang quyết toán' : stage === 'ACTIVE' ? 'Đang thuê xe' : 'Đang làm thủ tục trả xe — chưa hoàn tất hợp đồng'}</h2>
    <p className="text-sm">Nhận xe: {yearlyDate(view.contract.actualStart)} · Hạn trả: {yearlyDate(view.contract.dueOn)}{view.returnedOn ? ' · Ngày trả ghi nhận: ' + yearlyDate(view.returnedOn) : ''}</p>
    {mode && <p className="font-semibold">{mode}</p>}
    <p role="status">{operations?.nextMessage || (reviewOpen ? 'Đang có nội dung cần xem xét. Theo dõi hồ sơ và chờ kết luận trước khi chốt các bước liên quan.' : next[stage] || 'Xem trạng thái xử lý bên dưới.')}</p>
    <ol className="flex flex-wrap gap-2 text-sm">{[
      ['Đang thuê', stage === 'ACTIVE'],
      ['Trả xe / kiểm định', ['RETURN_REQUESTED', 'RETURN_INSPECTED'].includes(stage)],
      ['Quyết toán / hoàn tiền', returned && !completed],
      ['Hoàn tất', completed],
    ].map(([label, active]) => <li key={label} aria-current={active ? 'step' : undefined} className={'rounded-lg px-3 py-2 ' + (active ? 'bg-emerald-800 font-bold text-white' : 'bg-white text-slate-600')}>{label}</li>)}</ol>
  </section>
}
