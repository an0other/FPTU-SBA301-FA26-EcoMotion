import { useEffect, useState } from 'react'
import { paymentService } from '../../services/paymentService'
import { formatPaymentDate } from '../../utils/paymentDisplay'

export function VietQrCallbackHistory() {
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({})
  const key = `${page}:${attempt}`
  useEffect(() => {
    const controller = new AbortController()
    paymentService.getVietQrCallbacks(page, controller.signal).then(result => {
      if (!controller.signal.aborted) setState({ key, result })
    }).catch(() => {
      if (!controller.signal.aborted) setState({ key, error: 'Không thể tải lịch sử callback.' })
    })
    return () => controller.abort()
  }, [page, key])
  return (
    <details className="border-b border-slate-200 p-4 text-sm">
      <summary className="cursor-pointer font-semibold">Lịch sử xác minh VietQR</summary>
      <p className="my-2 text-xs text-slate-600">Giao dịch không khớp được lưu để đối soát. Báo lệch hoặc yêu cầu hoàn tiền khi cần.</p>
      {state.key !== key ? <p>Đang tải...</p> : state.error ? <p role="alert">{state.error}</p> : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead><tr><th className="p-2">Giao dịch</th><th className="p-2">Mã thanh toán</th><th className="p-2">Kết quả</th><th className="p-2">Tiếp nhận</th></tr></thead>
            <tbody>{(state.result?.content || []).map(item => (
              <tr key={item.transactionId} className="border-t border-slate-100">
                <td className="p-2 font-mono">{item.transactionId}</td><td className="p-2">{item.orderId}</td>
                <td className="p-2">{item.outcome === 'CONFIRMED' ? 'Đã thanh toán' : 'Cần đối soát'}</td>
                <td className="p-2">{formatPaymentDate(item.receivedAt)}</td>
              </tr>
            ))}</tbody>
          </table>
          {!state.result?.content?.length && <p>Chưa có callback.</p>}
        </div>
      )}
      <div className="mt-3 flex gap-2">
        <button type="button" className="rounded border px-3 py-1 disabled:opacity-50" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Trang trước</button>
        <button type="button" className="rounded border px-3 py-1 disabled:opacity-50" disabled={state.key !== key || !state.result || state.result.last !== false} onClick={() => setPage(value => value + 1)}>Trang sau</button>
        <button type="button" className="rounded border px-3 py-1" onClick={() => setAttempt(value => value + 1)}>Tải lại callback</button>
      </div>
    </details>
  )
}
