import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { providerFinanceService } from '../../services/providerFinanceService'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { providerPaymentState, requestLabel } from '../../utils/paymentReceipts'

const TONES = {
  amber: 'border-amber-200 bg-amber-50 text-amber-900',
  emerald: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  rose: 'border-rose-200 bg-rose-50 text-rose-900',
  slate: 'border-slate-200 bg-slate-50 text-slate-700',
}

export default function ProviderPaymentStatusCard({ bookingCode, refreshKey }) {
  const navigate = useNavigate()
  const [requests, setRequests] = useState(null)

  useEffect(() => {
    let cancelled = false
    providerFinanceService.listProviderPaymentRequests({ bookingCode, limit: 20 })
      .then((page) => { if (!cancelled) setRequests(page?.items || []) })
      .catch(() => { if (!cancelled) setRequests([]) })
    return () => { cancelled = true }
  }, [bookingCode, refreshKey])

  if (!requests || requests.length === 0) return null
  const financeLink = (extra = '') => `/provider/finance?tab=incoming&booking=${encodeURIComponent(bookingCode)}${extra}`

  return (
    <section aria-label="Thanh toán của khách" className="rounded-xl border border-slate-200 bg-white p-4 shadow-2xs">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-bold text-slate-900">Thanh toán của khách</h3>
        <button type="button" onClick={() => navigate(financeLink())} className="text-xs font-semibold text-emerald-700 hover:underline">
          Xem chi tiết ở Tài chính
        </button>
      </div>
      <ul className="mt-3 space-y-2">
        {requests.map((request) => {
          const state = providerPaymentState(request)
          return (
            <li key={request.id} className={`flex flex-wrap items-center justify-between gap-3 rounded-lg border px-3 py-2.5 text-xs ${TONES[state.tone]}`}>
              <span className="min-w-0">
                <strong className="block text-[13px]">{requestLabel(request.paymentType)} · {formatCurrency(request.amount)}</strong>
                <span>{state.text}</span>
                {Number(request.paidAmount) > 0 && request.status !== 'CONFIRMED' && (
                  <span className="block">Đã ghi nhận {formatCurrency(request.paidAmount)}</span>
                )}
                {request.confirmedAt && request.status === 'CONFIRMED' && (
                  <span className="block opacity-80">Xác nhận lúc {formatBookingWhen(request.confirmedAt)}</span>
                )}
                {request.issueNote && request.status === 'REVIEW_REQUIRED' && (
                  <span className="block opacity-80">{request.issueNote}</span>
                )}
              </span>
              {state.action && (
                <button
                  type="button"
                  onClick={() => navigate(financeLink(`&confirm=${request.id}`))}
                  className="rounded-lg bg-amber-600 px-3 py-1.5 font-bold text-white hover:bg-amber-700"
                >
                  Xác nhận ngay
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
