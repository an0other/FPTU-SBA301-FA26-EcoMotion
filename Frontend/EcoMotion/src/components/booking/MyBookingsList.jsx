import { useState } from 'react';
import { BOOKING_FILTERS, bookingMatchesFilter, bookingStatusMeta } from '../../utils/bookingStatus';


export const MyBookingsList = ({
  bookings,
  onSelectBooking,
  onPayDeposit,
  onBookCar,
}) => {
  const [activeFilter, setActiveFilter] = useState('ALL');

  const filteredBookings = bookings.filter((b) => {
    return bookingMatchesFilter(b.status, activeFilter);
  });

  const getStatusBadge = (status) => {
    const meta = bookingStatusMeta(status);
    return <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border whitespace-nowrap ${meta.badgeClass}`}>{meta.label}</span>;
  };

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Page Title */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#14221A]">Đơn của tôi</h1>
        <p className="text-sm sm:text-base text-[#14221A]/70 mt-1">
          Quản lý và theo dõi trạng thái các đơn thuê xe của bạn
        </p>
      </div>

      {/* Filter Chips Bar */}
      <div className="w-full overflow-x-auto pb-2 scrollbar-none">
        <div className="inline-flex items-center gap-2 shrink-0">
          {BOOKING_FILTERS.map((tab) => {
            const isActive = activeFilter === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveFilter(tab.id)}
                type="button"
                className={`px-4 py-2 rounded-full text-sm font-medium transition-all duration-180 cursor-pointer ${
                  isActive
                    ? 'bg-[#15803D] text-white border border-transparent shadow-xs'
                    : 'bg-white text-[#14221A] border border-[#E6EBE8] hover:bg-[#F7F8F6]'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Booking List */}
      {filteredBookings.length > 0 ? (
        <div className="flex flex-col gap-3.5 transition-opacity duration-180">
          {filteredBookings.map((booking) => (
            <article
              key={booking.id}
              onClick={() => onSelectBooking(booking)}
              className="bg-white rounded-[16px] border border-[#E6EBE8] p-4 shadow-[0_8px_24px_rgba(20,34,26,0.06)] hover:-translate-y-0.5 transition-all duration-180 cursor-pointer"
            >
              <div className="flex flex-col md:flex-row md:items-center gap-4 lg:gap-6 min-h-[76px]">
                {/* Vehicle Thumbnail */}
                <img
                  alt={booking.vehicle.name}
                  className="w-28 h-20 rounded-[12px] object-cover shrink-0"
                  src={booking.vehicle.image}
                />

                {/* Col 1 ID & Vehicle Name */}
                <div className="w-full md:w-52 shrink-0">
                  <span className="font-mono font-bold text-xs text-[#14221A]/70 block leading-tight">
                    {booking.id}
                  </span>
                  <h2 className="font-bold text-base text-[#14221A] mt-0.5 truncate">
                    {booking.vehicle.name} ({booking.vehicle.year})
                  </h2>
                </div>

                {/* Col 2: Pickup Time */}
                <div className="w-full md:w-56 shrink-0 flex items-center gap-2 text-sm text-[#14221A]">
                  <span className="material-symbols-outlined text-[18px] text-[#14221A]/60 shrink-0">
                    calendar_today
                  </span>
                  <span>{booking.formattedStart}</span>
                </div>

                {/* Col 3: Address */}
                <div className="flex-1 min-w-0 text-sm text-[#14221A]/80 truncate">
                  {booking.pickupAddress}
                </div>

                {/* Col 4: Price */}
                <div className="w-full md:w-32 shrink-0 md:text-right">
                  <span className="text-base font-bold text-[#14221A] whitespace-nowrap">
                    {booking.totalAmount.toLocaleString('vi-VN')} đ
                  </span>
                </div>

                {/* Col 5: Badge */}
                <div className="shrink-0">{getStatusBadge(booking.status)}</div>

                {/* Col 6: Contextual Action (if ACCEPTED) */}
                {booking.canPay && (
                  <div className="shrink-0">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onPayDeposit(booking);
                      }}
                      className="px-4 py-2.5 rounded-[12px] bg-[#15803D] hover:bg-[#166534] text-white text-sm font-medium shadow-xs whitespace-nowrap cursor-pointer active:scale-98 transition-all duration-180"
                      type="button"
                    >
                      {bookingStatusMeta(booking.status).action}
                    </button>
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        /* Empty State */
        <div className="flex flex-col items-center justify-center text-center py-16 px-4 bg-white border border-[#E6EBE8] rounded-[16px] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <div className="w-16 h-16 rounded-full bg-[#E8F6EE] flex items-center justify-center mb-4 text-[#15803D]">
            <span className="material-symbols-outlined text-[32px]">directions_car</span>
          </div>
          <h3 className="text-lg font-bold text-[#14221A] mb-1">Chưa có đơn ở trạng thái này</h3>
          <p className="text-sm text-[#14221A]/70 max-w-sm mb-6">
            Bạn chưa có đơn thuê xe nào trong danh mục này.
          </p>
          <button
            onClick={onBookCar}
            className="px-6 py-2.5 rounded-[12px] bg-[#15803D] hover:bg-[#166534] text-white text-sm font-semibold shadow-xs cursor-pointer active:scale-98 transition-all duration-180"
            type="button"
          >
            Khám phá & Đặt xe
          </button>
        </div>
      )}
    </div>
  );
};
