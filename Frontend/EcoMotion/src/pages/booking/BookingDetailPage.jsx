import { Link } from 'react-router-dom'

export function BookingDetailPage() {
  return (
    <div className="max-w-xl mx-auto px-6 py-16 text-center bg-white border border-[#E6EBE8] rounded-[16px] my-10">
      <h1 className="text-xl font-bold text-[#14221A]">Chi tiết đơn</h1>
      <p className="text-sm text-[#14221A]/70 mt-2">Màn này sẽ nối API khi làm R10.</p>
      <Link to="/renter/bookings" className="inline-block mt-6 text-[#15803D] font-semibold">Về đơn của tôi</Link>
    </div>
  )
}
