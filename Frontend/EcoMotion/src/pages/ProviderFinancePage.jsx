import { useState, useEffect, useMemo, useCallback } from 'react'
import ProviderLayout from '../components/provider/ProviderLayout'
import Toast from '../components/Toast'
import { useRealtimeSync, TV5_REALTIME_EVENTS } from '../hooks/useRealtimeSync'
import { providerFinanceService, SUPPORTED_BANKS } from '../services/providerFinanceService'

function formatVnd(amount) {
  if (amount == null || Number.isNaN(Number(amount))) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function formatDateTime(isoString) {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    if (Number.isNaN(d.getTime())) return isoString
    const hours = String(d.getHours()).padStart(2, '0')
    const mins = String(d.getMinutes()).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    return `${hours}:${mins} ${day}/${month}/${year}`
  } catch {
    return isoString
  }
}

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

const TX_STATUS_CONFIG = {
  SUCCESS: { label: 'Thành công', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  CONFIRMED: { label: 'Đã thanh toán', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  PENDING: { label: 'Đang xử lý', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  PROCESSING: { label: 'Đang xử lý', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  FAILED: { label: 'Thất bại', bg: 'bg-red-50 text-red-800 border-red-200' },
  REJECTED: { label: 'Thất bại', bg: 'bg-red-50 text-red-800 border-red-200' },
  REFUNDED: { label: 'Đã hoàn tiền', bg: 'bg-purple-50 text-purple-800 border-purple-200' },
}

const REFUND_STATUS_CONFIG = {
  PENDING: { label: 'Chờ duyệt', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  APPROVED: { label: 'Đã chấp thuận', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
  COMPLETED: { label: 'Đã hoàn tất', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  REJECTED: { label: 'Đã từ chối', bg: 'bg-red-50 text-red-800 border-red-200' },
}

export default function ProviderFinancePage() {
  const [activeTab, setActiveTab] = useState('transactions') // transactions | refunds | bankAccounts | payout

  // Summary & Core Metrics
  const [summary, setSummary] = useState(null)
  const [bankAccounts, setBankAccounts] = useState([])
  const [activeBank, setActiveBank] = useState(null)

  // Transactions State
  const [transactions, setTransactions] = useState([])
  const [totalTransactions, setTotalTransactions] = useState(0)
  const [txTypeFilter, setTxTypeFilter] = useState('ALL')
  const [txStatusFilter, setTxStatusFilter] = useState('ALL')
  const [txPage, setTxPage] = useState(1)
  const [txPageSize, setTxPageSize] = useState(10)
  const [selectedTxDetail, setSelectedTxDetail] = useState(null)

  // Refunds State
  const [refunds, setRefunds] = useState([])
  const [reviewingRefund, setReviewingRefund] = useState(null)
  const [reviewDecision, setReviewDecision] = useState('APPROVE')
  const [reviewNote, setReviewNote] = useState('')
  const [submittingReview, setSubmittingReview] = useState(false)

  // Bank Form State
  const [showAddBankModal, setShowAddBankModal] = useState(false)
  const [formBankCode, setFormBankCode] = useState('VCB')
  const [formAccountNumber, setFormAccountNumber] = useState('')
  const [formAccountHolder, setFormAccountHolder] = useState('')
  const [submittingBank, setSubmittingBank] = useState(false)
  const [bankFormError, setBankFormError] = useState(null)

  // Payout Mock State
  const [payoutList, setPayoutList] = useState([])
  const [requestingPayout, setRequestingPayout] = useState(false)

  // Global State
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Realtime Sync Hook
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.PAYMENT_VERIFIED]: () => {
        fetchFinanceData()
        setToast({ message: 'Thông báo tài chính: Giao dịch thanh toán VNPAY vừa được xác minh!', type: 'success' })
      },
      [TV5_REALTIME_EVENTS.REFUND_UPDATED]: () => {
        fetchFinanceData()
        setToast({ message: 'Thông báo: Yêu cầu hoàn tiền đã có cập nhật mới.', type: 'info' })
      },
    },
    onResync: () => fetchFinanceData(),
  })

  // Fetch summary and bank accounts
  const fetchFinanceData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const [sumRes, banksRes, refundsRes] = await Promise.allSettled([
        providerFinanceService.getFinanceSummary(),
        providerFinanceService.listBankAccounts(),
        providerFinanceService.listRefunds(),
      ])

      if (sumRes.status === 'fulfilled') setSummary(sumRes.value)
      if (banksRes.status === 'fulfilled') {
        const list = banksRes.value || []
        setBankAccounts(list)
        setActiveBank(list.find((b) => b.isActive) || list[0] || null)
      }
      if (refundsRes.status === 'fulfilled') setRefunds(refundsRes.value || [])
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Không thể tải thông tin tài chính.')
    } finally {
      setLoading(false)
    }
  }, [])

  // Fetch transactions with pagination
  const fetchTransactions = useCallback(async () => {
    try {
      const res = await providerFinanceService.listTransactions({
        type: txTypeFilter !== 'ALL' ? txTypeFilter : undefined,
        offset: (txPage - 1) * txPageSize,
        limit: txPageSize,
      })
      setTransactions(res.items || [])
      setTotalTransactions(res.total || 0)
    } catch {
      // Quiet fail
    }
  }, [txTypeFilter, txPage, txPageSize])

  useEffect(() => {
    fetchFinanceData()
  }, [fetchFinanceData])

  useEffect(() => {
    if (activeTab === 'transactions') {
      fetchTransactions()
    }
  }, [activeTab, fetchTransactions])

  // Handle Review Refund
  const handleReviewRefundSubmit = async (e) => {
    e.preventDefault()
    if (!reviewingRefund) return
    setSubmittingReview(true)
    try {
      await providerFinanceService.reviewRefund(reviewingRefund.id, {
        approved: reviewDecision === 'APPROVE',
        note: reviewNote.trim() || undefined,
      })
      setToast({
        message: reviewDecision === 'APPROVE' ? 'Đã duyệt yêu cầu hoàn tiền thành công!' : 'Đã từ chối yêu cầu hoàn tiền.',
        type: 'success',
      })
      setReviewingRefund(null)
      setReviewNote('')
      fetchFinanceData()
    } catch (err) {
      setToast({ message: err?.response?.data?.message || err?.message || 'Không thể cập nhật hoàn tiền', type: 'error' })
    } finally {
      setSubmittingReview(false)
    }
  }

  // Handle Add Bank Account
  const handleAddBankSubmit = async (e) => {
    e.preventDefault()
    if (!formAccountNumber.trim() || !formAccountHolder.trim()) {
      setBankFormError('Vui lòng điền đầy đủ số tài khoản và tên chủ tài khoản.')
      return
    }

    setSubmittingBank(true)
    setBankFormError(null)
    try {
      const created = await providerFinanceService.createBankAccount({
        bankCode: formBankCode,
        accountNumber: formAccountNumber.trim(),
        accountHolderName: formAccountHolder.trim().toUpperCase(),
        isDefault: true,
      })
      setToast({ message: 'Đã thêm tài khoản ngân hàng thụ hưởng thành công!', type: 'success' })
      setShowAddBankModal(false)
      setFormAccountNumber('')
      setFormAccountHolder('')
      fetchFinanceData()
    } catch (err) {
      setBankFormError(err?.response?.data?.message || err?.message || 'Không thể liên kết tài khoản ngân hàng.')
    } finally {
      setSubmittingBank(false)
    }
  }

  // Handle Mock Payout Request
  const handleMockPayout = () => {
    setRequestingPayout(true)
    setTimeout(() => {
      setRequestingPayout(false)
      const mockItem = {
        id: Date.now(),
        payoutCode: `PO-${Date.now().toString().slice(-6)}`,
        amount: summary?.pendingBalance || 1500000,
        status: 'PROCESSING',
        bankSnapshot: activeBank ? `${activeBank.bankCode} • ${activeBank.accountNumber}` : 'VCB • 9988776655',
        requestedAt: new Date().toISOString(),
      }
      setPayoutList((prev) => [mockItem, ...prev])
      setToast({ message: '[SANDBOX MOCK] Đã tạo lệnh yêu cầu đối soát rút tiền thử nghiệm!', type: 'info' })
    }, 600)
  }

  return (
    <ProviderLayout active="Tài chính">
      <div className="mx-auto max-w-7xl space-y-5">
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: '', type: 'success' })}
        />

        {/* Header Strip */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
              Quản trị tài chính nhà xe
            </h1>
            <p className="mt-0.5 text-xs text-slate-500">
              Đối soát doanh thu, tiền đặt cọc VNPAY, phụ phí và quản lý hoàn tiền
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-800 border border-amber-200 flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-amber-500" />
              <span>Cổng thanh toán: VNPAY Sandbox</span>
            </span>
            <button
              type="button"
              onClick={fetchFinanceData}
              disabled={loading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50"
            >
              <Icon name="refresh" className={`text-[16px] ${loading ? 'animate-spin' : ''}`} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800 flex items-start gap-2">
            <Icon name="error" className="text-red-600 text-[18px] shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {/* 4 Master Financial Metric KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Card 1: Tổng doanh thu / Tổng giao dịch */}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-1">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-xs font-semibold">Tổng giao dịch</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
                <Icon name="account_balance_wallet" className="text-lg" />
              </span>
            </div>
            <div className="text-2xl font-bold font-mono text-slate-900">
              {formatVnd(summary?.totalRevenue || summary?.totalVolume || 0)}
            </div>
            <div className="text-[11px] text-slate-500">
              Tích lũy từ tất cả các đơn đặt xe hoàn tất
            </div>
          </div>

          {/* Card 2: Tiền đặt cọc đã thu */}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-1">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-xs font-semibold">Tiền đặt cọc</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                <Icon name="lock" className="text-lg" />
              </span>
            </div>
            <div className="text-2xl font-bold font-mono text-blue-900">
              {formatVnd(summary?.depositCollected || summary?.totalDeposit || 0)}
            </div>
            <div className="text-[11px] text-slate-500">
              Đang giữ đảm bảo thực hiện hợp đồng
            </div>
          </div>

          {/* Card 3: Số tiền chưa đối soát / Chờ quyết toán */}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-1">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-xs font-semibold">Chưa đối soát</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-50 text-amber-700">
                <Icon name="hourglass_top" className="text-lg" />
              </span>
            </div>
            <div className="text-2xl font-bold font-mono text-amber-900">
              {formatVnd(summary?.pendingSettlement || summary?.pendingBalance || 0)}
            </div>
            <div className="text-[11px] text-slate-500">
              Chờ quyết toán cuối chuyến &amp; phụ phí
            </div>
          </div>

          {/* Card 4: Tổng tiền đã hoàn trả */}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-1">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-xs font-semibold">Tổng tiền hoàn</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-50 text-purple-700">
                <Icon name="replay" className="text-lg" />
              </span>
            </div>
            <div className="text-2xl font-bold font-mono text-purple-900">
              {formatVnd(summary?.totalRefunded || 0)}
            </div>
            <div className="text-[11px] text-slate-500">
              {refunds.length} yêu cầu hoàn tiền ghi nhận
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-200 bg-white px-2 rounded-t-lg shadow-2xs gap-1">
          <button
            type="button"
            onClick={() => setActiveTab('transactions')}
            className={`py-3 px-4 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'transactions'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="receipt_long" className="text-[16px]" />
            <span>Lịch sử giao dịch ({totalTransactions})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('refunds')}
            className={`py-3 px-4 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'refunds'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="assignment_return" className="text-[16px]" />
            <span>Yêu cầu hoàn tiền ({refunds.length})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('bankAccounts')}
            className={`py-3 px-4 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'bankAccounts'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="account_balance" className="text-[16px]" />
            <span>Tài khoản thụ hưởng ({bankAccounts.length})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('payout')}
            className={`py-3 px-4 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'payout'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="payments" className="text-[16px]" />
            <span>Đối soát &amp; Rút tiền (Sandbox)</span>
          </button>
        </div>

        {/* TAB 1: TRANSACTIONS TABLE */}
        {activeTab === 'transactions' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs overflow-hidden">
            {/* Toolbar */}
            <div className="p-3.5 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3 bg-slate-50">
              <div className="flex items-center gap-2">
                <label className="text-xs font-semibold text-slate-500">Loại giao dịch:</label>
                <select
                  value={txTypeFilter}
                  onChange={(e) => {
                    setTxTypeFilter(e.target.value)
                    setTxPage(1)
                  }}
                  className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-800 focus:border-brand-600 focus:outline-none"
                >
                  <option value="ALL">Tất cả giao dịch</option>
                  <option value="DEPOSIT">Đặt cọc (DEPOSIT)</option>
                  <option value="SETTLEMENT">Quyết toán (SETTLEMENT)</option>
                  <option value="EXTRA_CHARGE">Phụ phí phát sinh</option>
                  <option value="REFUND">Hoàn tiền (REFUND)</option>
                </select>
              </div>

              <span className="text-xs text-slate-500">
                Hiển thị trang {txPage} • Tổng số {totalTransactions} giao dịch
              </span>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full min-w-[950px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold text-slate-500 uppercase">
                    <th className="px-4 py-3">Mã GD</th>
                    <th className="px-4 py-3">Mã đơn thuê</th>
                    <th className="px-4 py-3">Phân loại</th>
                    <th className="px-4 py-3">Phương thức</th>
                    <th className="px-4 py-3">Cổng / Ref VNPAY</th>
                    <th className="px-4 py-3 text-right">Số tiền</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3">Thời gian</th>
                    <th className="px-4 py-3 text-center">Chi tiết</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {transactions.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="py-12 text-center text-slate-400">
                        Chưa có giao dịch tài chính nào trong bộ lọc này
                      </td>
                    </tr>
                  ) : (
                    transactions.map((tx) => {
                      const statusBadge = TX_STATUS_CONFIG[tx.status] || {
                        label: tx.status,
                        bg: 'bg-slate-100 text-slate-700',
                      }

                      return (
                        <tr key={tx.id || tx.transactionCode} className="hover:bg-slate-50/80">
                          <td className="px-4 py-3 font-mono font-bold text-slate-800">
                            {tx.transactionCode || `#TX-${tx.id}`}
                          </td>
                          <td className="px-4 py-3 font-mono font-semibold text-brand-800">
                            {tx.bookingCode || '—'}
                          </td>
                          <td className="px-4 py-3 text-slate-700 font-medium">
                            {tx.type}
                          </td>
                          <td className="px-4 py-3">
                            <span className="rounded bg-slate-100 px-2 py-0.5 font-semibold text-slate-700 text-[10px]">
                              {tx.paymentMethod || 'VNPAY'}
                            </span>
                          </td>
                          <td className="px-4 py-3 font-mono text-slate-500 text-[11px]">
                            {tx.gatewayTxnRef || tx.vnpTxnRef || 'VNPAY-SANDBOX'}
                          </td>
                          <td className="px-4 py-3 text-right font-mono font-bold text-slate-900">
                            {formatVnd(tx.amount)}
                          </td>
                          <td className="px-4 py-3">
                            <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${statusBadge.bg}`}>
                              {statusBadge.label}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-slate-500 font-mono text-[11px]">
                            {formatDateTime(tx.paidAt || tx.createdAt)}
                          </td>
                          <td className="px-4 py-3 text-center">
                            <button
                              type="button"
                              onClick={() => setSelectedTxDetail(tx)}
                              className="rounded p-1 text-slate-400 hover:text-brand-800 hover:bg-slate-100"
                              title="Xem chi tiết giao dịch"
                            >
                              <Icon name="visibility" className="text-[18px]" />
                            </button>
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600">
              <span>Trang {txPage}</span>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => setTxPage((p) => Math.max(1, p - 1))}
                  disabled={txPage <= 1}
                  className="rounded border border-slate-200 bg-white px-3 py-1 font-semibold hover:bg-slate-100 disabled:opacity-40"
                >
                  Trước
                </button>
                <button
                  type="button"
                  onClick={() => setTxPage((p) => p + 1)}
                  disabled={transactions.length < txPageSize}
                  className="rounded border border-slate-200 bg-white px-3 py-1 font-semibold hover:bg-slate-100 disabled:opacity-40"
                >
                  Sau
                </button>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: REFUNDS TABLE */}
        {activeTab === 'refunds' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs overflow-hidden space-y-4 p-5">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h2 className="text-base font-bold text-slate-900">Danh sách yêu cầu hoàn tiền</h2>
                <p className="text-xs text-slate-500">Các yêu cầu hoàn cọc hoặc hoàn tiền do hủy chuyến, sự cố phát sinh</p>
              </div>
            </div>

            <div className="overflow-x-auto rounded-xl border border-slate-200">
              <table className="w-full min-w-[850px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold text-slate-500 uppercase">
                    <th className="px-4 py-3">Mã hoàn tiền</th>
                    <th className="px-4 py-3">Mã đơn thuê</th>
                    <th className="px-4 py-3 text-right">Số tiền hoàn</th>
                    <th className="px-4 py-3">Lý do hoàn tiền</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3">Thời gian tạo</th>
                    <th className="px-4 py-3 text-center">Thao tác duyệt</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {refunds.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-10 text-center text-slate-400">
                        Chưa có yêu cầu hoàn tiền nào được ghi nhận
                      </td>
                    </tr>
                  ) : (
                    refunds.map((ref) => {
                      const badge = REFUND_STATUS_CONFIG[ref.status] || { label: ref.status, bg: 'bg-slate-100 text-slate-700' }
                      const canReview = ref.status === 'PENDING'

                      return (
                        <tr key={ref.id} className="hover:bg-slate-50/80">
                          <td className="px-4 py-3 font-mono font-bold text-slate-800">
                            #{ref.id}
                          </td>
                          <td className="px-4 py-3 font-mono font-semibold text-brand-800">
                            {ref.bookingCode || '—'}
                          </td>
                          <td className="px-4 py-3 text-right font-mono font-bold text-purple-900">
                            {formatVnd(ref.amount)}
                          </td>
                          <td className="px-4 py-3 text-slate-700 max-w-[280px] truncate" title={ref.reason}>
                            {ref.reason}
                          </td>
                          <td className="px-4 py-3">
                            <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${badge.bg}`}>
                              {badge.label}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-slate-500 font-mono text-[11px]">
                            {formatDateTime(ref.createdAt)}
                          </td>
                          <td className="px-4 py-3 text-center">
                            {canReview ? (
                              <button
                                type="button"
                                onClick={() => {
                                  setReviewingRefund(ref)
                                  setReviewDecision('APPROVE')
                                }}
                                className="rounded bg-brand-50 px-3 py-1 text-xs font-bold text-brand-800 hover:bg-brand-100 border border-brand-200"
                              >
                                Xem xét duyệt
                              </button>
                            ) : (
                              <span className="text-slate-400 text-[11px]">Đã xử lý</span>
                            )}
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 3: BANK ACCOUNTS */}
        {activeTab === 'bankAccounts' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs p-5 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h2 className="text-base font-bold text-slate-900">Tài khoản ngân hàng nhận thanh toán</h2>
                <p className="text-xs text-slate-500">Tài khoản dùng để nhận đối soát doanh thu từ hệ thống</p>
              </div>
              <button
                type="button"
                onClick={() => setShowAddBankModal(true)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-brand-700 px-3.5 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800"
              >
                <Icon name="add" className="text-[16px]" />
                <span>Thêm tài khoản ngân hàng</span>
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {bankAccounts.map((b) => (
                <div
                  key={b.id}
                  className={`rounded-lg border p-4 space-y-2 transition ${
                    b.isActive
                      ? 'border-brand-500 bg-brand-50/40 ring-2 ring-brand-500/20'
                      : 'border-slate-200 bg-white'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="rounded bg-slate-900 px-2 py-0.5 text-xs font-bold text-white font-mono">
                      {b.bankCode}
                    </span>
                    {b.isActive && (
                      <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-[10px] font-bold text-emerald-800">
                        Đang kích hoạt nhận tiền
                      </span>
                    )}
                  </div>
                  <div>
                    <span className="text-[11px] text-slate-500 block uppercase">Số tài khoản</span>
                    <span className="font-mono text-lg font-bold text-slate-900 tracking-wider">
                      {b.accountNumber}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-slate-500 block uppercase">Chủ tài khoản</span>
                    <span className="font-bold text-slate-800 text-sm">{b.accountHolderName}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 4: MOCK PAYOUT (SANDBOX) */}
        {activeTab === 'payout' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs p-5 space-y-4">
            <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-xs text-amber-900">
              <div className="font-bold flex items-center gap-2 text-sm mb-1">
                <Icon name="science" className="text-amber-700" />
                Môi trường thử nghiệm đối soát (Sandbox Mock Payout)
              </div>
              <p className="leading-relaxed">
                Chức năng này mô phỏng chu trình đối soát tiền thật giữa EcoMotion Platform và Nhà xe. Các giao dịch ở đây không chuyển tiền thật ngoài ngân hàng thực tế.
              </p>
            </div>

            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-sm font-bold text-slate-900">Yêu cầu đối soát rút tiền</h3>
                <p className="text-xs text-slate-500">Số dư chờ đối soát khả dụng: <strong className="text-brand-900 font-mono">{formatVnd(summary?.pendingSettlement || summary?.pendingBalance || 1500000)}</strong></p>
              </div>
              <button
                type="button"
                onClick={handleMockPayout}
                disabled={requestingPayout}
                className="inline-flex items-center gap-1.5 rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800 disabled:opacity-50"
              >
                {requestingPayout && <Icon name="sync" className="animate-spin text-[16px]" />}
                <span>{requestingPayout ? 'Đang tạo lệnh...' : 'Tạo lệnh rút tiền thử nghiệm'}</span>
              </button>
            </div>

            {/* Payout History List */}
            <div className="space-y-2">
              <h4 className="text-xs font-bold text-slate-700 uppercase">Lịch sử lệnh rút đối soát:</h4>
              {payoutList.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">
                  Chưa có lệnh rút đối soát nào trong phiên làm việc hiện tại
                </div>
              ) : (
                <div className="space-y-2">
                  {payoutList.map((po) => (
                    <div key={po.id} className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs flex justify-between items-center">
                      <div>
                        <div className="font-mono font-bold text-slate-900">{po.payoutCode}</div>
                        <div className="text-[11px] text-slate-500">{po.bankSnapshot} • {formatDateTime(po.requestedAt)}</div>
                      </div>
                      <div className="text-right">
                        <div className="font-mono font-bold text-brand-900 text-sm">{formatVnd(po.amount)}</div>
                        <span className="rounded bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">
                          Đang đối soát ngân hàng
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Transaction Details Modal */}
      {selectedTxDetail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
          <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-3">
              <h3 className="text-base font-bold text-slate-900">Chi tiết giao dịch VNPAY</h3>
              <button
                type="button"
                onClick={() => setSelectedTxDetail(null)}
                className="rounded p-1 text-slate-400 hover:text-slate-700"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <div className="space-y-2.5 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-500">Mã giao dịch:</span>
                <span className="font-mono font-bold text-slate-900">{selectedTxDetail.transactionCode || selectedTxDetail.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Đơn thuê:</span>
                <span className="font-mono font-bold text-brand-800">#{selectedTxDetail.bookingCode}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Số tiền:</span>
                <span className="font-mono font-bold text-slate-900 text-sm">{formatVnd(selectedTxDetail.amount)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Loại giao dịch:</span>
                <span className="font-semibold text-slate-800">{selectedTxDetail.type}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Cổng thanh toán:</span>
                <span className="font-semibold text-emerald-800">VNPAY Sandbox</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Mã tham chiếu ngân hàng:</span>
                <span className="font-mono text-slate-700">{selectedTxDetail.gatewayTxnRef || 'VNPAY-REF'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Thời gian ghi nhận:</span>
                <span className="font-mono text-slate-700">{formatDateTime(selectedTxDetail.paidAt || selectedTxDetail.createdAt)}</span>
              </div>
            </div>

            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedTxDetail(null)}
                className="rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Review Refund Modal */}
      {reviewingRefund && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
          <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
            <h3 className="text-base font-bold text-slate-900 mb-1">Xét duyệt yêu cầu hoàn tiền</h3>
            <p className="text-xs text-slate-500 mb-3">
              Đơn thuê: <span className="font-mono font-bold text-slate-800">#{reviewingRefund.bookingCode}</span> • Số tiền: <strong className="font-mono text-purple-900">{formatVnd(reviewingRefund.amount)}</strong>
            </p>

            <form onSubmit={handleReviewRefundSubmit} className="space-y-3.5">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Quyết định của chủ xe</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setReviewDecision('APPROVE')}
                    className={`p-2.5 rounded-xl border text-xs font-bold transition ${
                      reviewDecision === 'APPROVE'
                        ? 'border-brand-600 bg-brand-50 text-brand-900 ring-1 ring-brand-500'
                        : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Chấp thuận hoàn tiền
                  </button>
                  <button
                    type="button"
                    onClick={() => setReviewDecision('REJECT')}
                    className={`p-2.5 rounded-xl border text-xs font-bold transition ${
                      reviewDecision === 'REJECT'
                        ? 'border-rose-500 bg-rose-50 text-rose-950 ring-1 ring-rose-400'
                        : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Từ chối hoàn tiền
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Ghi chú đối soát</label>
                <textarea
                  rows="2"
                  value={reviewNote}
                  onChange={(e) => setReviewNote(e.target.value)}
                  placeholder="Ghi rõ lý do hoặc thỏa thuận đã đạt được..."
                  className="w-full rounded-xl border border-slate-200 p-2 text-xs text-slate-800 placeholder-slate-400 focus:border-brand-600 focus:outline-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setReviewingRefund(null)}
                  className="rounded-lg border border-slate-200 px-3.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingReview}
                  className="rounded-lg bg-brand-700 px-4 py-1.5 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50"
                >
                  {submittingReview ? 'Đang lưu...' : 'Xác nhận kết quả'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Bank Account Modal */}
      {showAddBankModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
          <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
            <h3 className="text-base font-bold text-slate-900 mb-1">Liên kết tài khoản ngân hàng</h3>
            <p className="text-xs text-slate-500 mb-3">Tài khoản chính thức nhận thanh toán doanh thu</p>

            {bankFormError && (
              <div className="mb-3 rounded-lg bg-red-50 p-2 text-xs text-red-800 border border-red-200">
                {bankFormError}
              </div>
            )}

            <form onSubmit={handleAddBankSubmit} className="space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Ngân hàng thụ hưởng</label>
                <select
                  value={formBankCode}
                  onChange={(e) => setFormBankCode(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white p-2 text-xs font-semibold text-slate-800 focus:border-brand-600 focus:outline-none"
                >
                  {SUPPORTED_BANKS.map((b) => (
                    <option key={b.code} value={b.code}>
                      {b.shortName} — {b.fullName}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Số tài khoản</label>
                <input
                  type="text"
                  value={formAccountNumber}
                  onChange={(e) => setFormAccountNumber(e.target.value.replace(/\s+/g, ''))}
                  placeholder="Nhập số tài khoản chính xác..."
                  className="w-full rounded-xl border border-slate-200 p-2 text-xs font-mono font-bold text-slate-800 focus:border-brand-600 focus:outline-none"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Tên chủ tài khoản (Viết hoa không dấu)</label>
                <input
                  type="text"
                  value={formAccountHolder}
                  onChange={(e) => setFormAccountHolder(e.target.value.toUpperCase())}
                  placeholder="NGUYEN VAN A"
                  className="w-full rounded-xl border border-slate-200 p-2 text-xs uppercase font-bold text-slate-800 focus:border-brand-600 focus:outline-none"
                  required
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddBankModal(false)}
                  className="rounded-lg border border-slate-200 px-3.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingBank}
                  className="rounded-lg bg-brand-700 px-4 py-1.5 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50"
                >
                  {submittingBank ? 'Đang lưu...' : 'Lưu tài khoản'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </ProviderLayout>
  )
}
