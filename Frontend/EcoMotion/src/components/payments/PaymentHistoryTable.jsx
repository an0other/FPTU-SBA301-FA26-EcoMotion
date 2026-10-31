import PaymentStatusBadge from "./PaymentStatusBadge";
import {
  PAYMENT_METHOD_LABELS,
  PAYMENT_TYPE_LABELS,
  formatPaymentDate,
  formatPaymentMoney,
} from "../../utils/paymentDisplay";

export default function PaymentHistoryTable({
  items,
  selectedBookingCode,
  onSelectBooking,
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-212.5 text-left text-sm">
        <caption className="sr-only">
          Lịch sử giao dịch thanh toán của bạn
        </caption>

        <thead className="bg-slate-50 text-xs text-slate-500">
          <tr>
            <th scope="col" className="px-5 py-4 font-semibold">
              Đơn thuê
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Giao dịch
            </th>
            <th scope="col" className="px-5 py-4 text-right font-semibold">
              Số tiền
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Phương thức
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Trạng thái
            </th>
            <th scope="col" className="px-5 py-4 font-semibold">
              Thời gian
            </th>
            {onSelectBooking && (
              <th scope="col" className="px-5 py-4 font-semibold">
                Chi tiết
              </th>
            )}
          </tr>
        </thead>

        <tbody className="divide-y divide-slate-100">
          {items.map((payment, index) => {
            const selected = selectedBookingCode === payment.bookingCode;
            const refunded = payment.type === "REFUND";
            const succeeded = payment.status === "PAID";

            return (
              <tr
                key={`${payment.bookingCode}-${payment.type}-${payment.createdAt}-${index}`}
                className={selected ? "bg-brand-50/60" : "hover:bg-slate-50/70"}
              >
                <td className="px-5 py-5">
                  <div className="font-semibold text-slate-900">{payment.bookingCode}</div>
                  {payment.transactionCode && (
                    <span className="mt-1 inline-block font-mono text-[11px] font-bold text-brand-700 bg-brand-50 px-1.5 py-0.5 rounded border border-brand-200">
                      {payment.transactionCode}
                    </span>
                  )}
                </td>

                <td className="px-5 py-5">
                  <p className="font-medium text-slate-800">
                    {PAYMENT_TYPE_LABELS[payment.type] ?? payment.type}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {refunded ? "Hoàn về khách thuê" : "Khách thuê thanh toán"}
                  </p>
                </td>

                <td
                  className={`whitespace-nowrap px-5 py-5 text-right font-bold tabular-nums ${
                    refunded && succeeded ? "text-brand-700" : "text-slate-900"
                  }`}
                >
                  {formatPaymentMoney(payment.amount)}
                </td>

                <td className="px-5 py-5 text-slate-600">
                  {PAYMENT_METHOD_LABELS[payment.method] ?? payment.method}
                </td>

                <td className="px-5 py-5">
                  <PaymentStatusBadge status={payment.status} />
                </td>

                <td className="whitespace-nowrap px-5 py-5 text-xs text-slate-600">
                  {formatPaymentDate(payment.paidAt || payment.createdAt)}
                  <span className="mt-1 block text-slate-400">
                    {payment.paidAt ? "Thanh toán" : "Tạo giao dịch"}
                  </span>
                </td>

                {onSelectBooking && (
                  <td className="px-5 py-5">
                    <button
                      type="button"
                      aria-pressed={selected}
                      onClick={() => onSelectBooking(payment.bookingCode)}
                      className="whitespace-nowrap rounded-lg border border-brand-200 px-3 py-2 text-xs font-semibold text-brand-700 transition hover:bg-brand-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
                    >
                      Xem khoản phí
                    </button>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
