import { yearlyDate, yearlyMoney } from '../../utils/yearlyRental'

export default function YearlyContractTerms({ view: v }) {
  const annual = v.annualKm
  return <section className="space-y-4 rounded-xl border bg-white p-5">
    <h3 className="text-lg font-bold">Điều khoản km và trả trễ đã chốt</h3>
    <p className="text-sm text-slate-600">Các mức dưới đây lấy từ điều khoản provider đã lưu cho hợp đồng này, không lấy giá hiện tại của xe.</p>
    {annual == null ? <p className="text-amber-800">Provider chưa đề xuất điều khoản km và phí. Chưa thể coi mức phí là 0 đồng.</p> : <>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div><dt className="text-slate-500">Hạn mức cả năm</dt><dd className="font-semibold">{annual.toLocaleString('vi-VN')} km</dd></div>
        <div><dt className="text-slate-500">Phí mỗi km vượt</dt><dd className="font-semibold">{yearlyMoney(v.excessKmRate)} / km</dd></div>
        <div><dt className="text-slate-500">Ân hạn trả xe</dt><dd className="font-semibold">{v.graceDays} ngày</dd></div>
        <div><dt className="text-slate-500">Phí trả trễ</dt><dd className="font-semibold">{yearlyMoney(v.lateDailyRate)} / ngày tính phí</dd></div>
        <div><dt className="text-slate-500">Hạn trả theo hợp đồng</dt><dd className="font-semibold">{yearlyDate(v.contract.dueOn)}</dd></div>
        <div><dt className="text-slate-500">Trạng thái điều khoản</dt><dd>{v.termsAccepted ? 'Người thuê đã đồng ý' : 'Đề xuất, chờ người thuê đồng ý'}</dd></div>
      </dl>
      <div className="space-y-2 rounded-lg bg-slate-50 p-4 text-sm">
        <h4 className="font-semibold">Cách tính phí vượt km</h4>
        <p>Tổng km đã đi = tổng (km trả − km nhận) của từng xe. Khi đổi xe, cộng quãng đường từng chặng; không trừ đồng hồ xe mới cho đồng hồ xe cũ.</p>
        <p>Hạn mức áp dụng = làm tròn xuống ({annual.toLocaleString('vi-VN')} × số tháng áp dụng / 12). Trả sớm tính theo số tháng thuê đã bắt đầu, tối đa 12; chọn thanh toán đủ hợp đồng thì áp dụng đủ hạn mức năm.</p>
        <p>Km vượt = max(0, tổng km đã đi − hạn mức áp dụng). Phí vượt = km vượt × {yearlyMoney(v.excessKmRate)}.</p>
        <p className="text-slate-500">Phí kết thúc sớm không mua thêm hạn mức km. Ví dụ vượt 100 km thì phí km là {yearlyMoney(100 * Number(v.excessKmRate))}.</p>
      </div>
      <div className="space-y-2 rounded-lg bg-slate-50 p-4 text-sm">
        <h4 className="font-semibold">Cách tính số ngày và phí trả trễ</h4>
        <p>Ngày trễ = max(0, ngày trả thực tế − ngày đến hạn), tính theo ngày Việt Nam, không tính theo giờ.</p>
        <p>Ngày tính phí = max(0, ngày trễ − {v.graceDays} ngày ân hạn). Phí trễ = ngày tính phí × {yearlyMoney(v.lateDailyRate)}.</p>
        <p>Trả đúng ngày đến hạn không bị tính trễ. Bắt đầu tính phí từ ngày trễ thứ {v.graceDays + 1}; chỉ tính phần vượt ân hạn.</p>
        <p className="text-slate-500">Ví dụ trả trễ {v.graceDays + 2} ngày: 2 ngày tính phí, tương đương {yearlyMoney(2 * Number(v.lateDailyRate))}.</p>
      </div>
      <p className="text-sm text-emerald-800">Số km, ngày trả thực tế và bảng tính của booking nằm ở mục Trả xe & quyết toán sau khi provider gửi biên bản kiểm định. Ví dụ ở đây chỉ minh họa công thức.</p>
    </>}
  </section>
}
