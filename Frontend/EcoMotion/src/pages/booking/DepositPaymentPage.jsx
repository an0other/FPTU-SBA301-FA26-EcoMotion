import { Link } from 'react-router-dom'

export function DepositPaymentPage() {
  return (
    <div className="max-w-xl mx-auto px-6 py-16 text-center bg-white border border-[#E6EBE8] rounded-[16px] my-10">
      <h1 className="text-xl font-bold text-[#14221A]">Thanh toán giữ chỗ</h1>
      <p className="text-sm text-[#14221A]/70 mt-2">Chỉ mở sau khi chủ xe chấp nhận. API checkout thuộc R08.</p>
      <Link to="/renter/bookings" className="inline-block mt-6 text-[#15803D] font-semibold">Về đơn của tôi</Link>
    </div>
  )
}
