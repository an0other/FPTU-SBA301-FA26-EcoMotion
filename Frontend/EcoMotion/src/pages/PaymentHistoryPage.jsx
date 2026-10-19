import { useState } from 'react'
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import BookingChargeBreakdown from '../components/payments/BookingChargeBreakdown'
import PaymentHistoryTable from '../components/payments/PaymentHistoryTable'
import PaymentPagination from '../components/payments/PaymentPagination'
import { usePaymentHistory } from '../hooks/usePaymentHistory'

const PAGE_SIZE = 10

export default function PaymentHistoryPage() {
  const [page, setPage] = useState(0)
  const [selectedBookingCode, setSelectedBookingCode] = useState(null)

  const { items, total, hasMore, loading, error, retry } =
    usePaymentHistory(page, PAGE_SIZE)

  function handlePageChange(nextPage) {
    setSelectedBookingCode(null)
    setPage(nextPage)
  }

  return (
    <div className="min-h-screen bg-surface">
      <AppHeader />

      <nav
        aria-label="Đường dẫn trang"
        className="border-b border-slate-200 bg-white"
      >
        <div className="mx-auto flex max-w-350 items-center gap-2 px-4 py-3 text-xs sm:px-6">
          <Link to="/" className="text-slate-500 hover:text-brand-700">
            Trang chủ
          </Link>
          <span aria-hidden="true" className="text-slate-300">/</span>
          <span aria-current="page" className="font-semibold text-slate-800">
            Lịch sử thanh toán
          </span>
        </div>
      </nav>

      <main className="mx-auto max-w-350 space-y-6 px-4 py-8 sm:px-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-brand-700">
              Tài khoản khách thuê
            </p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              Lịch sử thanh toán
            </h1>
            <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-500">
              Theo dõi giao dịch và xem các khoản phí của từng đơn thuê.
            </p>
          </div>

          <Link
            to="/me"
            className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            Tài khoản của tôi
          </Link>
        </div>

        <section
          aria-labelledby="payment-history-heading"
          aria-busy={loading}
          className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card"
        >
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-5 sm:px-6">
            <h2
              id="payment-history-heading"
              className="text-base font-bold text-slate-900"
            >
              Giao dịch của bạn
            </h2>
            <p className="text-xs text-slate-500">
              Đơn vị: VND · Giờ Việt Nam
            </p>
          </div>

          {loading && (
            <div role="status" className="space-y-4 p-6">
              <span className="sr-only">Đang tải lịch sử thanh toán…</span>
              {Array.from({ length: 4 }, (_, index) => (
                <div
                  key={index}
                  className="h-14 animate-pulse rounded-lg bg-slate-100"
                />
              ))}
            </div>
          )}

          {!loading && error && (
            <div role="alert" className="p-10 text-center">
              <p className="font-semibold text-on-error-container">{error}</p>
              <button
                type="button"
                onClick={retry}
                className="mt-4 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
              >
                Thử lại
              </button>
            </div>
          )}

          {!loading && !error && items.length === 0 && (
            <div className="px-6 py-14 text-center">
              <h3 className="text-base font-semibold text-slate-900">
                {page === 0 ? 'Chưa có giao dịch' : 'Trang này không có giao dịch'}
              </h3>
              <p className="mt-2 text-sm text-slate-500">
                {page === 0
                  ? 'Các giao dịch thanh toán của bạn sẽ xuất hiện tại đây.'
                  : 'Bạn có thể quay lại trang trước để xem lịch sử.'}
              </p>

              {page > 0 && (
                <button
                  type="button"
                  onClick={() => handlePageChange(page - 1)}
                  className="mt-4 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-brand-700"
                >
                  Về trang trước
                </button>
              )}
            </div>
          )}

          {!loading && !error && items.length > 0 && (
            <PaymentHistoryTable
              items={items}
              selectedBookingCode={selectedBookingCode}
              onSelectBooking={setSelectedBookingCode}
            />
          )}

          {!loading && !error && items.length > 0 && (
            <PaymentPagination
              page={page}
              size={PAGE_SIZE}
              total={total}
              hasMore={hasMore}
              loading={loading}
              onPageChange={handlePageChange}
            />
          )}
        </section>

        {selectedBookingCode && (
          <BookingChargeBreakdown
            key={selectedBookingCode}
            bookingCode={selectedBookingCode}
            onClose={() => setSelectedBookingCode(null)}
          />
        )}
      </main>
    </div>
  )
}