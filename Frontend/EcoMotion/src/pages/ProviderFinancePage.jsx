import { canReviewRefund, refundReviewPayload } from '../utils/tv5Contracts'
import { RefundProofModal } from '../components/payments/RefundProofModal'
import { useState, useEffect, useCallback, useRef } from 'react'
import ProviderLayout from '../components/provider/ProviderLayout'
import Toast from '../components/Toast'
import { useRealtimeSync, TV5_REALTIME_EVENTS } from '../hooks/useRealtimeSync'
import { providerFinanceService, SUPPORTED_BANKS } from '../services/providerFinanceService'
import { PaymentEvidenceImage } from '../components/payments/PaymentEvidenceImage'
import { HoldCountdown } from '../components/payments/HoldCountdown'

function formatVnd(amount) {
  if (amount == null || Number.isNaN(Number(amount))) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function formatDateTime(isoString) {
  if (!isoString) return '—'
  const d = new Date(isoString)
  if (Number.isNaN(d.getTime())) return isoString
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(d)
}

function Icon({ name, className = '' }) {
  return (
    <span className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}>
      {name}
    </span>
  )
}

const TX_STATUS_CONFIG = {
  PAID: { label: 'Đã ghi nhận tiền', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  RECONCILIATION_REQUIRED: { label: 'Đã nhận tiền, cần đối soát', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  SUCCESS: { label: 'Thành công', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  CONFIRMED: { label: 'Đã thanh toán', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  PENDING: { label: 'Đang xử lý', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  PROCESSING: { label: 'Đang xử lý', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  FAILED: { label: 'Thất bại', bg: 'bg-red-50 text-red-800 border-red-200' },
  REJECTED: { label: 'Thất bại', bg: 'bg-red-50 text-red-800 border-red-200' },
  REFUNDED: { label: 'Đã hoàn tiền', bg: 'bg-purple-50 text-purple-800 border-purple-200' },
}

const REQUEST_STATUS_CONFIG = {
  PENDING: { label: 'Chờ thanh toán', bg: 'bg-amber-50 text-amber-800 border-amber-200', dot: 'bg-amber-500' },
  PROOF_SUBMITTED: { label: 'Chờ đối soát biên lai', bg: 'bg-blue-50 text-blue-800 border-blue-200', dot: 'bg-blue-500' },
  PARTIALLY_PAID: { label: 'Thanh toán một phần', bg: 'bg-orange-50 text-orange-800 border-orange-200', dot: 'bg-orange-500' },
  CONFIRMED: { label: 'Đã xác nhận đủ', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200', dot: 'bg-emerald-500' },
  REJECTED: { label: 'Đã từ chối', bg: 'bg-red-50 text-red-800 border-red-200', dot: 'bg-red-500' },
  EXPIRED: { label: 'Hết hạn', bg: 'bg-slate-100 text-slate-700 border-slate-300', dot: 'bg-slate-400' },
  REVIEW_REQUIRED: { label: 'Cần đối soát', bg: 'bg-purple-50 text-purple-800 border-purple-200', dot: 'bg-purple-500' },
}

const REFUND_STATUS_CONFIG = {
  REQUESTED: { label: 'Chờ xem xét', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  UNDER_REVIEW: { label: 'Đang xét duyệt', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  APPROVED: { label: 'Đã chấp thuận, chưa hoàn tiền', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
  PROCESSING: { label: 'Chờ chuyển khoản và biên lai', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
  REFUNDED: { label: 'Đã hoàn tiền', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  REJECTED: { label: 'Đã từ chối', bg: 'bg-red-50 text-red-800 border-red-200' },
  FAILED: { label: 'Hoàn tiền thất bại', bg: 'bg-red-50 text-red-800 border-red-200' },
  PENDING_RECONCILIATION: { label: 'Chờ đối soát', bg: 'bg-purple-50 text-purple-800 border-purple-200' },
}

export default function ProviderFinancePage() {
  const [activeTab, setActiveTab] = useState('transactions') // transactions | incoming | refunds | bankAccounts | payout

  // Summary & Core Metrics
  const [summary, setSummary] = useState(null)
  const [bankAccounts, setBankAccounts] = useState([])

  // Transactions State
  const [transactions, setTransactions] = useState([])
  const [totalTransactions, setTotalTransactions] = useState(0)
  const [txTypeFilter, setTxTypeFilter] = useState('ALL')
  const [txPage, setTxPage] = useState(1)
  const txPageSize = 10
  const [selectedTxDetail, setSelectedTxDetail] = useState(null)

  // Incoming Payment Requests State
  const [paymentRequests, setPaymentRequests] = useState([])
  const [totalPaymentRequests, setTotalPaymentRequests] = useState(0)
  const [requestStatusFilter, setRequestStatusFilter] = useState('ALL')
  const [requestPage, setRequestPage] = useState(1)
  const requestPageSize = 10

  // Refunds State
  const [refunds, setRefunds] = useState([])
  const [proofRefund, setProofRefund] = useState(null)
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

  const [payoutList, setPayoutList] = useState([])
  const [payoutLoading, setPayoutLoading] = useState(false)
  const [payoutError, setPayoutError] = useState(null)

  // Global State
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Modals
  const [confirmModalData, setConfirmModalData] = useState(null)
  const [rejectModalData, setRejectModalData] = useState(null)
  const [reportIssueModalData, setReportIssueModalData] = useState(null)
  const [viewProofModalData, setViewProofModalData] = useState(null)
  const [breakdownData, setBreakdownData] = useState(null)

  // Form States for Confirmation
  const [receivedAmountInput, setReceivedAmountInput] = useState('')
  const [bankRefInput, setBankRefInput] = useState('')
  const [confirmNoteInput, setConfirmNoteInput] = useState('')
  const [isConfirming, setIsConfirming] = useState(false)

  // Form States for Reject & Issue
  const [rejectReasonInput, setRejectReasonInput] = useState('')
  const [isRejecting, setIsRejecting] = useState(false)
  const [issueNoteInput, setIssueNoteInput] = useState('')
  const [isReportingIssue, setIsReportingIssue] = useState(false)


  // Fetch summary and bank accounts
  const financeSequence = useRef(0)
  const txSequence = useRef(0)
  const paymentSequence = useRef(0)
  const fetchFinanceData = useCallback(async () => {
    const id = ++financeSequence.current
    try {
      setLoading(true)
      setError(null)
      const [sumRes, banksRes, refundsRes] = await Promise.allSettled([
        providerFinanceService.getFinanceSummary(),
        providerFinanceService.listBankAccounts(),
        providerFinanceService.listRefunds(),
      ])

      if (id !== financeSequence.current) return
      if (sumRes.status === 'fulfilled') {
        setSummary(sumRes.value)
      } else {
        console.error('Failed to load finance summary:', sumRes.reason)
        const errMsg =
          sumRes.reason?.response?.data?.message ||
          sumRes.reason?.message ||
          'Không thể tải dữ liệu tổng quan tài chính.'
        setError(errMsg)
      }

      if (banksRes.status === 'fulfilled') {
        const list = banksRes.value || []
        setBankAccounts(list)
      } else {
        console.error('Failed to load bank accounts:', banksRes.reason)
      }

      if (refundsRes.status === 'fulfilled') {
        setRefunds(refundsRes.value || [])
      } else {
        console.error('Failed to load refunds:', refundsRes.reason)
      }
    } catch (err) {
      console.error('Failed to load finance data:', err)
      setError(err?.response?.data?.message || err?.message || 'Không thể tải thông tin tài chính.')
      setToast({ message: err?.message || 'Không thể tải dữ liệu tài chính.', type: 'error' })
    } finally {
      setLoading(false)
    }
  }, [])

  // Fetch transactions with pagination
  const fetchTransactions = useCallback(async () => {
    const id = ++txSequence.current
    try {
      const res = await providerFinanceService.listTransactions({
        type: txTypeFilter !== 'ALL' ? txTypeFilter : undefined,
        offset: (txPage - 1) * txPageSize,
        limit: txPageSize,
      })
      if (id !== txSequence.current) return
      setTransactions(res?.items || [])
      setTotalTransactions(res?.total || 0)
    } catch {
      // Quiet fail
    }
  }, [txTypeFilter, txPage, txPageSize])

  // Fetch incoming payment requests with pagination
  const fetchPaymentRequests = useCallback(async () => {
    const id = ++paymentSequence.current
    try {
      const res = await providerFinanceService.listProviderPaymentRequests({
        status: requestStatusFilter !== 'ALL' ? requestStatusFilter : undefined,
        offset: (requestPage - 1) * requestPageSize,
        limit: requestPageSize,
      })
      if (id !== paymentSequence.current) return
      setPaymentRequests(res?.items || [])
      setTotalPaymentRequests(res?.total || 0)
    } catch {
      // Quiet fail
    }
  }, [requestStatusFilter, requestPage, requestPageSize])

  // Realtime Sync Hook
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.PAYMENT_VERIFIED]: () => {
        setToast({ message: 'Thông tin thanh toán đã được máy chủ cập nhật.', type: 'success' })
      },
      [TV5_REALTIME_EVENTS.REFUND_UPDATED]: () => {
        setToast({ message: 'Thông báo: Yêu cầu hoàn tiền đã có cập nhật mới.', type: 'info' })
      },
    },
    onResync: () => Promise.all([fetchFinanceData(), fetchTransactions(), fetchPaymentRequests()]),
  })

  useEffect(() => {
    const timer = setTimeout(fetchFinanceData, 0)
    return () => { clearTimeout(timer); ++financeSequence.current }
  }, [fetchFinanceData])
  useEffect(() => {
    const timer = setTimeout(fetchTransactions, 0)
    return () => { clearTimeout(timer); ++txSequence.current }
  }, [fetchTransactions])
  useEffect(() => {
    const timer = setTimeout(fetchPaymentRequests, 0)
    return () => { clearTimeout(timer); ++paymentSequence.current }
  }, [fetchPaymentRequests])

  // Handle Review Refund
  const handleReviewRefundSubmit = async (e) => {
    e.preventDefault()
    if (!reviewingRefund || submittingReview) return
    if (reviewDecision === 'REJECT' && (!reviewNote.trim() || reviewNote.trim().length > 500)) {
      setToast({ message: 'Vui lòng nhập lý do từ chối từ 1 đến 500 ký tự.', type: 'error' })
      return
    }
    setSubmittingReview(true)
    try {
      await providerFinanceService.reviewRefund(reviewingRefund.id, refundReviewPayload(reviewDecision, reviewNote))
      setToast({
        message: reviewDecision === 'APPROVE' ? 'Đã duyệt yêu cầu. Cần chuyển khoản và tải biên lai để xác nhận hoàn tiền.' : 'Đã từ chối yêu cầu hoàn tiền.',
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
      await providerFinanceService.createBankAccount({
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

  useEffect(() => {
    if (activeTab !== 'payout') return
    let cancelled = false
    const timer = setTimeout(async () => {
      setPayoutLoading(true)
      setPayoutError(null)
      try {
        const result = await providerFinanceService.listPayouts({ offset: 0, limit: 50 })
        if (!cancelled) setPayoutList(result?.items ?? [])
      } catch (err) {
        if (!cancelled) setPayoutError(err?.message || 'Không thể tải lịch sử chi trả.')
      } finally {
        if (!cancelled) setPayoutLoading(false)
      }
    }, 0)
    return () => { cancelled = true; clearTimeout(timer) }
  }, [activeTab])

  // Handle Confirm Payment
  const handleOpenConfirmModal = (pr) => {
    setConfirmModalData(pr)
    setReceivedAmountInput(String(pr.amount || pr.remainingAmount || ''))
    setBankRefInput('')
    setConfirmNoteInput('')
  }

  const handleConfirmPayment = async (e) => {
    e.preventDefault()
    if (!confirmModalData || isConfirming) return
    if (!Number.isFinite(Number(receivedAmountInput)) || Number(receivedAmountInput) <= 0) {
      setToast({ message: 'Số tiền nhận phải lớn hơn 0.', type: 'error' }); return
    }

    try {
      setIsConfirming(true)
      const payload = {
        receivedAmount: Number(receivedAmountInput),
        bankTransactionRef: bankRefInput.trim() || undefined,
        note: confirmNoteInput.trim() || undefined,
      }
      await providerFinanceService.confirmPayment(confirmModalData.id, payload)
      setToast({ message: 'Xác nhận đã nhận tiền thành công! Giao dịch tài chính đã được cập nhật.', type: 'success' })
      setConfirmModalData(null)
      fetchFinanceData()
      fetchPaymentRequests()
    } catch (err) {
      console.error('Failed to confirm payment:', err)
      setToast({ message: err.message || 'Xác nhận nhận tiền thất bại.', type: 'error' })
    } finally {
      setIsConfirming(false)
    }
  }

  // Handle Reject Payment Proof
  const handleOpenRejectModal = (pr) => {
    setRejectModalData(pr)
    setRejectReasonInput('')
  }

  const handleRejectPayment = async (e) => {
    e.preventDefault()
    if (!rejectModalData || isRejecting) return
    if (!rejectReasonInput.trim()) {
      setToast({ message: 'Vui lòng nhập lý do từ chối biên lai', type: 'error' })
      return
    }

    try {
      setIsRejecting(true)
      await providerFinanceService.rejectPayment(rejectModalData.id, { reason: rejectReasonInput.trim() })
      setToast({ message: 'Đã từ chối biên lai thanh toán.', type: 'success' })
      setRejectModalData(null)
      fetchFinanceData()
      fetchPaymentRequests()
    } catch (err) {
      console.error('Failed to reject proof:', err)
      setToast({ message: err.message || 'Từ chối thất bại.', type: 'error' })
    } finally {
      setIsRejecting(false)
    }
  }

  // Handle Report Issue
  const handleOpenReportIssueModal = (pr) => {
    setReportIssueModalData(pr)
    setIssueNoteInput('')
  }

  const handleReportIssue = async (e) => {
    e.preventDefault()
    if (!reportIssueModalData || isReportingIssue) return
    if (!issueNoteInput.trim()) {
      setToast({ message: 'Vui lòng mô tả sai lệch hoặc vấn đề cần đối soát', type: 'error' })
      return
    }

    try {
      setIsReportingIssue(true)
      await providerFinanceService.reportIssue(reportIssueModalData.id, { issueNote: issueNoteInput.trim() })
      setToast({ message: 'Đã báo cáo sai lệch giao dịch thành công. Trạng thái đã chuyển sang Cần đối soát.', type: 'success' })
      setReportIssueModalData(null)
      fetchFinanceData()
      fetchPaymentRequests()
    } catch (err) {
      console.error('Failed to report issue:', err)
      setToast({ message: err.message || 'Báo cáo sai lệch thất bại.', type: 'error' })
    } finally {
      setIsReportingIssue(false)
    }
  }

  // Handle View Financial Breakdown
  const handleViewBreakdown = async (bookingIdOrCode) => {
    try {
      const data = await providerFinanceService.getBookingBreakdown(bookingIdOrCode)
      setBreakdownData(data)
    } catch (err) {
      console.error('Failed to load breakdown:', err)
      setToast({ message: err.message || 'Không thể tải đối soát tài chính của đơn thuê.', type: 'error' })
    }
  }

  const handleCopy = (text, label) => {
    if (!text) return
    navigator.clipboard.writeText(text)
    setToast({ message: `Đã sao chép ${label}`, type: 'success' })
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

          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex h-10 items-center gap-1.5 rounded-lg bg-amber-50 px-3 text-xs font-bold text-amber-800 border border-amber-200">
              <span className="h-2 w-2 rounded-full bg-amber-500" />
              <span>Cổng thanh toán: VNPAY Sandbox</span>
            </span>
            <button
              type="button"
              onClick={() => {
                fetchFinanceData()
                fetchTransactions()
                fetchPaymentRequests()
              }}
              disabled={loading}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 disabled:opacity-50"
            >
              <Icon name="refresh" className={`text-[18px] ${loading ? 'animate-spin' : ''}`} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800 flex flex-wrap items-center justify-between gap-3 shadow-2xs">
            <div className="flex items-start gap-2">
              <Icon name="error" className="text-red-600 text-[18px] shrink-0 mt-0.5" />
              <div>
                <p className="font-bold text-red-900">Không thể tải dữ liệu tài chính</p>
                <p className="text-red-700 mt-0.5">{error}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                fetchFinanceData()
                fetchTransactions()
                fetchPaymentRequests()
              }}
              disabled={loading}
              className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-red-700 transition cursor-pointer disabled:opacity-50"
            >
              <Icon name="refresh" className={`text-[15px] ${loading ? 'animate-spin' : ''}`} />
              <span>Thử lại</span>
            </button>
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
            {loading && !summary ? (
              <div className="h-8 w-32 animate-pulse rounded bg-slate-200 my-0.5" />
            ) : (
              <div className="text-2xl font-bold font-mono text-slate-900">
                {formatVnd(summary?.totalConfirmedRevenue ?? 0)}
              </div>
            )}
            <div className="text-[11px] text-slate-500">
              Thực thu hợp lệ trừ tiền đã hoàn, chưa trừ hoa hồng
            </div>
          </div>

          {/* Card 2: Tiền đặt cọc đã thu */}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-1">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-xs font-semibold">Nhà xe nhận trực tiếp</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                <Icon name="lock" className="text-lg" />
              </span>
            </div>
            {loading && !summary ? (
              <div className="h-8 w-28 animate-pulse rounded bg-blue-100 my-0.5" />
            ) : (
              <div className="text-2xl font-bold font-mono text-blue-900">
                {formatVnd(summary?.directReceived ?? 0)}
              </div>
            )}
            <div className="text-[11px] text-slate-500">
              Tổng chuyển khoản đã xác nhận, trước hoàn tiền
            </div>
          </div>

          {/* Card 3: Số tiền chưa đối soát / Chờ quyết toán */}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-1">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-xs font-semibold">Nền tảng đã nhận</span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-50 text-amber-700">
                <Icon name="hourglass_top" className="text-lg" />
              </span>
            </div>
            {loading && !summary ? (
              <div className="h-8 w-28 animate-pulse rounded bg-amber-100 my-0.5" />
            ) : (
              <div className="text-2xl font-bold font-mono text-amber-900">
                {formatVnd(summary?.platformReceived ?? 0)}
              </div>
            )}
            <div className="text-[11px] text-slate-500">
              Tổng VNPay đã xác minh, trước hoàn tiền và chi trả
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
            {loading && !summary ? (
              <div className="h-8 w-28 animate-pulse rounded bg-purple-100 my-0.5" />
            ) : (
              <div className="text-2xl font-bold font-mono text-purple-900">
                {formatVnd(summary?.refundedAmount ?? 0)}
              </div>
            )}
            <div className="text-[11px] text-slate-500">
              {refunds.length} yêu cầu hoàn tiền ghi nhận
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-200 bg-white px-2 rounded-t-lg shadow-2xs gap-1 overflow-x-auto scrollbar-none">
          <button
            type="button"
            onClick={() => setActiveTab('transactions')}
            className={`py-3 px-4 text-xs font-bold whitespace-nowrap shrink-0 transition-all border-b-2 flex items-center gap-1.5 ${
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
            onClick={() => setActiveTab('incoming')}
            className={`py-3 px-4 text-xs font-bold whitespace-nowrap shrink-0 transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'incoming'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="payments" className="text-[16px]" />
            <span>Yêu cầu thanh toán ({totalPaymentRequests})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('refunds')}
            className={`py-3 px-4 text-xs font-bold whitespace-nowrap shrink-0 transition-all border-b-2 flex items-center gap-1.5 ${
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
            className={`py-3 px-4 text-xs font-bold whitespace-nowrap shrink-0 transition-all border-b-2 flex items-center gap-1.5 ${
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
            className={`py-3 px-4 text-xs font-bold whitespace-nowrap shrink-0 transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'payout'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="payments" className="text-[16px]" />
            <span>Lịch sử chi trả</span>
          </button>
        </div>

        {/* TAB 1: TRANSACTIONS TABLE */}
        {activeTab === 'transactions' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs overflow-hidden">
            {/* Toolbar */}
            <div className="p-3.5 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3 bg-slate-50">
              <div className="flex items-center gap-2">
                <label htmlFor="finance-tx-type-filter" className="text-xs font-semibold text-slate-600">
                  Loại giao dịch:
                </label>
                <div className="relative h-10 w-52">
                  <select
                    id="finance-tx-type-filter"
                    value={txTypeFilter}
                    onChange={(e) => {
                      setTxTypeFilter(e.target.value)
                      setTxPage(1)
                    }}
                    className="h-10 w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3.5 pr-9 text-xs font-medium text-slate-800 hover:border-slate-300 focus:border-brand-600 focus:ring-1 focus:ring-brand-600 focus:outline-none transition"
                  >
                    <option value="ALL">Tất cả giao dịch</option>
                    <option value="DEPOSIT">Đặt cọc (DEPOSIT)</option>
                    <option value="SETTLEMENT">Quyết toán (SETTLEMENT)</option>
                    <option value="EXTRA_CHARGE">Phụ phí phát sinh</option>
                    <option value="REFUND">Hoàn tiền (REFUND)</option>
                  </select>
                  <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                    <Icon name="expand_more" className="text-[18px]" />
                  </div>
                </div>
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
                  {loading && transactions.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="py-12 text-center text-slate-500">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <Icon name="progress_activity" className="animate-spin text-2xl text-brand-700" />
                          <span className="text-xs font-medium">Đang tải lịch sử giao dịch...</span>
                        </div>
                      </td>
                    </tr>
                  ) : transactions.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="py-12 text-center text-slate-400">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <Icon name="receipt_long" className="text-3xl text-slate-300" />
                          <span>Chưa có giao dịch tài chính nào trong bộ lọc này</span>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    transactions.map((tx) => {
                      const statusBadge = TX_STATUS_CONFIG[tx.processingNote?.startsWith('RECONCILIATION_REQUIRED') ? 'RECONCILIATION_REQUIRED' : tx.status] || {
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
                              {tx.method || '—'}{tx.verificationMethod === 'MOCK_SIMULATED' ? ' · MOCK/SANDBOX' : ''}
                            </span>
                          </td>
                          <td className="px-4 py-3 font-mono text-slate-500 text-[11px]">
                            {tx.gatewayRef || '—'}
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
                            {tx.type === 'REFUND' && tx.status === 'PENDING' && (
                              <button type="button" aria-label={`Xác nhận hoàn tiền cho ${tx.bookingCode}`} onClick={() => setProofRefund({ bookingCode: tx.bookingCode, paymentTransactionId: tx.id })} className="ml-2 rounded border border-slate-300 px-2 py-1 text-xs font-semibold text-brand-800">Biên lai hoàn tiền</button>
                            )}
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

        {/* TAB 2: INCOMING PAYMENT REQUESTS */}
        {activeTab === 'incoming' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs overflow-hidden">
            {/* Toolbar */}
            <div className="p-3.5 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3 bg-slate-50">
              <div className="flex items-center gap-2">
                <label htmlFor="finance-req-status-filter" className="text-xs font-semibold text-slate-600">
                  Trạng thái:
                </label>
                <div className="relative h-10 w-56">
                  <select
                    id="finance-req-status-filter"
                    value={requestStatusFilter}
                    onChange={(e) => {
                      setRequestStatusFilter(e.target.value)
                      setRequestPage(1)
                    }}
                    className="h-10 w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3.5 pr-9 text-xs font-medium text-slate-800 hover:border-slate-300 focus:border-brand-600 focus:ring-1 focus:ring-brand-600 focus:outline-none transition"
                  >
                    <option value="ALL">Tất cả trạng thái</option>
                    <option value="PENDING">Chờ thanh toán</option>
                    <option value="PROOF_SUBMITTED">Chờ đối soát biên lai</option>
                    <option value="PARTIALLY_PAID">Thanh toán một phần</option>
                    <option value="CONFIRMED">Đã xác nhận đủ</option>
                    <option value="REVIEW_REQUIRED">Cần đối soát</option>
                    <option value="REJECTED">Đã từ chối</option>
                  </select>
                  <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                    <Icon name="expand_more" className="text-[18px]" />
                  </div>
                </div>
              </div>

              <span className="text-xs text-slate-500">
                Hiển thị trang {requestPage} • Tổng số {totalPaymentRequests} yêu cầu
              </span>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1100px] border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold text-slate-500 uppercase">
                    <th className="px-4 py-3">Mã đơn</th>
                    <th className="px-4 py-3">Loại</th>
                    <th className="px-4 py-3 text-right">Số trên QR</th>
                    <th className="px-4 py-3 text-right">Đã ghi sổ</th>
                    <th className="px-4 py-3 text-right">Còn thiếu</th>
                    <th className="px-4 py-3">Nội dung CK</th>
                    <th className="px-4 py-3">Hold còn</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3 text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {loading && paymentRequests.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="py-12 text-center text-slate-500">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <Icon name="progress_activity" className="animate-spin text-2xl text-brand-700" />
                          <span className="text-xs font-medium">Đang tải danh sách yêu cầu thanh toán...</span>
                        </div>
                      </td>
                    </tr>
                  ) : paymentRequests.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="py-12 text-center text-slate-400">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <Icon name="payments" className="text-3xl text-slate-300" />
                          <span>Chưa có yêu cầu thanh toán nào trong bộ lọc này</span>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    paymentRequests.map((pr) => {
                      const statusMeta = REQUEST_STATUS_CONFIG[pr.status] || {
                        label: pr.status,
                        bg: 'bg-slate-100 text-slate-700 border-slate-200',
                        dot: 'bg-slate-400',
                      }
                      const hasProof = pr.evidences && pr.evidences.length > 0

                      return (
                        <tr key={pr.id} className="hover:bg-slate-50/80">
                          <td className="px-4 py-3 font-mono font-bold text-brand-800">
                            <button
                              type="button"
                              onClick={() => handleViewBreakdown(pr.bookingCode)}
                              className="hover:underline font-bold"
                              title="Xem đối soát chi tiết đơn này"
                            >
                              {pr.bookingCode}
                            </button>
                          </td>
                          <td className="px-4 py-3 font-medium text-slate-700">
                            {pr.paymentType === 'DEPOSIT' ? 'Tiền cọc' : 'Quyết toán'}
                          </td>
                          <td className="px-4 py-3 text-right font-mono font-bold text-slate-900">
                            {formatVnd(pr.amount)}
                          </td>
                          <td className="px-4 py-3 text-right font-mono font-bold text-emerald-700">
                            {formatVnd(pr.paidAmount || 0)}
                          </td>
                          <td className="px-4 py-3 text-right font-mono font-semibold text-slate-700">
                            {formatVnd(pr.remainingAmount ?? Math.max(0, Number(pr.amount || 0) - Number(pr.paidAmount || 0)))}
                          </td>
                          <td className="px-4 py-3 font-mono text-[11px] font-semibold text-slate-600">
                            {pr.transferReference || '—'}
                          </td>
                          <td className="px-4 py-3">
                            {['PENDING', 'PROOF_SUBMITTED', 'PARTIALLY_PAID', 'REJECTED'].includes(pr.status) ? (
                              <HoldCountdown expiresAt={pr.expiresAt} compact />
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${statusMeta.bg}`}>
                              <span className={`h-1.5 w-1.5 rounded-full ${statusMeta.dot}`} />
                              {statusMeta.label}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-center">
                            <div className="flex items-center justify-center gap-1.5 flex-wrap">
                              {hasProof && (
                                <button
                                  type="button"
                                  onClick={() => setViewProofModalData(pr)}
                                  className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold text-slate-700 hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-brand-500/40"
                                  title="Xem biên lai khách tải lên"
                                >
                                  Biên lai
                                </button>
                              )}
                              {pr.status !== 'CONFIRMED' && (
                                <>
                                  <button
                                    type="button"
                                    onClick={() => handleOpenConfirmModal(pr)}
                                    className="rounded-lg bg-brand-700 px-3 py-1.5 text-[11px] font-bold text-white hover:bg-brand-800 focus-visible:ring-2 focus-visible:ring-brand-500/40"
                                    title="Xác nhận đã nhận tiền"
                                  >
                                    Xác nhận
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => handleOpenRejectModal(pr)}
                                    className="rounded border border-red-200 bg-white px-2 py-1 text-[11px] font-semibold text-red-600 hover:bg-red-50"
                                    title="Từ chối biên lai"
                                  >
                                    Từ chối
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => handleOpenReportIssueModal(pr)}
                                    className="rounded border border-purple-200 bg-white px-2 py-1 text-[11px] font-semibold text-purple-700 hover:bg-purple-50"
                                    title="Báo cáo sai lệch"
                                  >
                                    Báo lệch
                                  </button>
                                </>
                              )}
                            </div>
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
              <span>Trang {requestPage}</span>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => setRequestPage((p) => Math.max(1, p - 1))}
                  disabled={requestPage <= 1}
                  className="rounded border border-slate-200 bg-white px-3 py-1 font-semibold hover:bg-slate-100 disabled:opacity-40"
                >
                  Trước
                </button>
                <button
                  type="button"
                  onClick={() => setRequestPage((p) => p + 1)}
                  disabled={paymentRequests.length < requestPageSize}
                  className="rounded border border-slate-200 bg-white px-3 py-1 font-semibold hover:bg-slate-100 disabled:opacity-40"
                >
                  Sau
                </button>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: REFUNDS TABLE */}
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
                  {loading && refunds.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-500">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <Icon name="progress_activity" className="animate-spin text-2xl text-brand-700" />
                          <span className="text-xs font-medium">Đang tải danh sách hoàn tiền...</span>
                        </div>
                      </td>
                    </tr>
                  ) : refunds.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-400">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <Icon name="assignment_return" className="text-3xl text-slate-300" />
                          <span>Chưa có yêu cầu hoàn tiền nào được ghi nhận</span>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    refunds.map((ref) => {
                      const badge = REFUND_STATUS_CONFIG[ref.status] || { label: ref.status, bg: 'bg-slate-100 text-slate-700' }
                      const canReview = canReviewRefund(ref.status)

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
                                  setReviewNote('')
                                  setReviewDecision('APPROVE')
                                }}
                                className="inline-flex h-8 items-center justify-center rounded-lg bg-brand-50 px-3 text-xs font-bold text-brand-800 hover:bg-brand-100 border border-brand-200"
                              >
                                Xem xét duyệt
                              </button>
                            ) : (
                              ref.status === 'PROCESSING' && ref.paymentTransactionId ? (
                                <button
                                  type="button"
                                  onClick={() => setProofRefund(ref)}
                                  className="inline-flex h-8 items-center justify-center rounded-lg border border-brand-200 bg-brand-50 px-3 text-xs font-bold text-brand-800 hover:bg-brand-100"
                                >
                                  Tải biên lai hoàn tiền
                                </button>
                              ) : (
                                <span className="text-slate-400 text-[11px]">
                                  {ref.status === 'REFUNDED' ? 'Đã hoàn tiền' : 'Không có thao tác'}
                                </span>
                              )
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
              {loading && bankAccounts.length === 0 ? (
                <div className="col-span-full py-12 text-center text-slate-500">
                  <div className="flex flex-col items-center justify-center gap-2">
                    <Icon name="progress_activity" className="animate-spin text-2xl text-brand-700" />
                    <span className="text-xs font-medium">Đang tải tài khoản ngân hàng...</span>
                  </div>
                </div>
              ) : bankAccounts.length === 0 ? (
                <div className="col-span-full py-12 text-center text-slate-400">
                  <div className="flex flex-col items-center justify-center gap-3">
                    <Icon name="account_balance" className="text-3xl text-slate-300" />
                    <span className="text-xs">Chưa có tài khoản ngân hàng nào được thiết lập</span>
                    <button
                      type="button"
                      onClick={() => setShowAddBankModal(true)}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-brand-50 border border-brand-200 px-3 py-1.5 text-xs font-bold text-brand-800 hover:bg-brand-100"
                    >
                      <Icon name="add" className="text-[14px]" />
                      <span>Thêm tài khoản ngay</span>
                    </button>
                  </div>
                </div>
              ) : (
                bankAccounts.map((b) => (
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
                ))
              )}
            </div>
          </div>
        )}

        {activeTab === 'payout' && (
          <div className="rounded-lg border border-slate-200 bg-white shadow-xs p-5 space-y-4">
            <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-xs text-amber-900">
              <h3 className="font-bold text-sm mb-1">Lịch sử chi trả từ máy chủ</h3>
              <p>Chỉ tiền nền tảng thực thu mới được xét chi trả. Tiền khách chuyển trực tiếp nhà xe được theo dõi riêng ở Giao dịch. Admin quản lý lệnh chi trả; giao dịch MOCK/SANDBOX chưa chuyển tiền ngân hàng.</p>
            </div>
            {payoutLoading ? <p role="status" className="text-sm text-slate-500">Đang tải lịch sử chi trả...</p>
              : payoutError ? <p role="alert" className="text-sm text-red-700">{payoutError}</p>
              : payoutList.length === 0 ? <p className="py-6 text-center text-xs text-slate-500">Chưa có lệnh chi trả được ghi nhận.</p>
              : payoutList.map(po => (
                <div key={po.id} className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs flex flex-wrap justify-between gap-3">
                  <div><p className="font-mono font-bold">{po.bookingCode}</p><p>{formatDateTime(po.createdAt)}</p></div>
                  <div className="text-right"><p className="font-mono font-bold">{formatVnd(po.netAmount)}</p><p>{po.status} · {po.verificationMethod === 'MOCK_SIMULATED' ? 'MOCK/SANDBOX' : po.verificationMethod || 'Chưa xác minh'}</p></div>
                </div>
              ))}
          </div>
        )}
      </div>

      {/* Transaction Details Modal */}
      {selectedTxDetail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-xl bg-white p-5 shadow-lg border border-slate-200">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-3">
              <h3 className="text-base font-bold text-slate-900">Chi tiết giao dịch</h3>
              <button
                type="button"
                onClick={() => setSelectedTxDetail(null)}
                className="rounded p-1 text-slate-400 hover:text-slate-700"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <div className="space-y-2.5 text-xs">
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Mã giao dịch:</span>
                <span className="font-mono font-bold text-slate-900 inline-flex items-center gap-1.5">
                  {selectedTxDetail.transactionCode || selectedTxDetail.id}
                  <button
                    type="button"
                    onClick={() => handleCopy(selectedTxDetail.transactionCode || selectedTxDetail.id, 'mã giao dịch')}
                    className="text-slate-400 hover:text-slate-600"
                    title="Sao chép mã giao dịch"
                  >
                    <Icon name="content_copy" className="text-[14px]" />
                  </button>
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Đơn thuê:</span>
                <span className="font-mono font-bold text-brand-800">#{selectedTxDetail.bookingCode || '—'}</span>
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
                <span className="font-semibold text-emerald-800">{selectedTxDetail.method || '—'}{selectedTxDetail.verificationMethod === 'MOCK_SIMULATED' ? ' · MOCK/SANDBOX' : ''}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500">Mã tham chiếu ngân hàng:</span>
                <span className="font-mono text-slate-700 inline-flex items-center gap-1.5">
                  {selectedTxDetail.gatewayRef || '—'}
                  {selectedTxDetail.gatewayRef && (
                    <button
                      type="button"
                      onClick={() => handleCopy(selectedTxDetail.gatewayRef, 'mã tham chiếu')}
                      className="text-slate-400 hover:text-slate-600"
                      title="Sao chép mã tham chiếu"
                    >
                      <Icon name="content_copy" className="text-[14px]" />
                    </button>
                  )}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Thời gian ghi nhận:</span>
                <span className="font-mono text-slate-700">{formatDateTime(selectedTxDetail.paidAt || selectedTxDetail.createdAt)}</span>
              </div>
            </div>

            <div className="mt-5 flex items-center justify-between gap-2">
              {selectedTxDetail.bookingCode && (
                <button
                  type="button"
                  onClick={() => handleViewBreakdown(selectedTxDetail.bookingCode)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-brand-200 bg-brand-50 px-3 py-1.5 text-xs font-bold text-brand-800 hover:bg-brand-100"
                >
                  <Icon name="receipt_long" className="text-[15px]" />
                  <span>Đối soát đơn #{selectedTxDetail.bookingCode}</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => setSelectedTxDetail(null)}
                className="ml-auto rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Review Refund Modal */}
      {proofRefund && <RefundProofModal refund={proofRefund} onClose={() => setProofRefund(null)} onSuccess={async () => { await Promise.all([fetchFinanceData(), fetchTransactions()]); setToast({ message: 'Backend đã xác nhận biên lai hoàn tiền.', type: 'success' }) }} />}
      {reviewingRefund && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-xl bg-white p-5 shadow-lg border border-slate-200">
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
                    disabled={submittingReview}
                    onClick={() => setReviewDecision('APPROVE')}
                    className={`h-10 px-3 rounded-xl border text-xs font-bold transition ${
                      reviewDecision === 'APPROVE'
                        ? 'border-brand-600 bg-brand-50 text-brand-900 ring-1 ring-brand-500'
                        : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    Chấp thuận hoàn tiền
                  </button>
                  <button
                    type="button"
                    disabled={submittingReview}
                    onClick={() => setReviewDecision('REJECT')}
                    className={`h-10 px-3 rounded-xl border text-xs font-bold transition ${
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
                <label className="block text-xs font-bold text-slate-700 mb-1">Lý do từ chối (bắt buộc khi từ chối)</label>
                <textarea
                  rows="2"
                  required={reviewDecision === 'REJECT'}
                  disabled={submittingReview || reviewDecision !== 'REJECT'}
                  maxLength={500}
                  value={reviewNote}
                  onChange={(e) => setReviewNote(e.target.value)}
                  placeholder="Ghi rõ lý do hoặc thỏa thuận đã đạt được..."
                  className="w-full rounded-xl border border-slate-200 p-2.5 text-xs text-slate-800 placeholder-slate-400 focus:border-brand-600 focus:outline-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setReviewingRefund(null)}
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingReview}
                  className="inline-flex h-10 items-center justify-center rounded-lg bg-brand-700 px-4 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50"
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
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-xl bg-white p-5 shadow-lg border border-slate-200">
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
                <div className="relative h-10 w-full">
                  <select
                    value={formBankCode}
                    onChange={(e) => setFormBankCode(e.target.value)}
                    className="h-10 w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3.5 pr-9 text-xs font-semibold text-slate-800 hover:border-slate-300 focus:border-brand-600 focus:ring-1 focus:ring-brand-600 focus:outline-none transition"
                  >
                    {SUPPORTED_BANKS.map((b) => (
                      <option key={b.code} value={b.code}>
                        {b.shortName} — {b.fullName}
                      </option>
                    ))}
                  </select>
                  <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                    <Icon name="expand_more" className="text-[18px]" />
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Số tài khoản</label>
                <input
                  type="text"
                  value={formAccountNumber}
                  onChange={(e) => setFormAccountNumber(e.target.value.replace(/\s+/g, ''))}
                  placeholder="Nhập số tài khoản chính xác..."
                  className="h-10 w-full rounded-lg border border-slate-200 px-3 text-xs font-mono font-bold text-slate-800 focus:border-brand-600 focus:outline-none"
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
                  className="h-10 w-full rounded-lg border border-slate-200 px-3 text-xs uppercase font-bold text-slate-800 focus:border-brand-600 focus:outline-none"
                  required
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddBankModal(false)}
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 px-3.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingBank}
                  className="inline-flex h-10 items-center justify-center rounded-lg bg-brand-700 px-4 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50"
                >
                  {submittingBank ? 'Đang lưu...' : 'Lưu tài khoản'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* ======================================================== */}
      {/* MODAL 2: CONFIRM PAYMENT (SCREEN C)                      */}
      {/* ======================================================== */}
      {confirmModalData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-base font-bold text-slate-900">Xác nhận đã nhận tiền chuyển khoản</h3>
              <button
                type="button"
                onClick={() => setConfirmModalData(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <form onSubmit={handleConfirmPayment} className="mt-4 space-y-4">
              <div className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800 space-y-1">
                <div className="font-semibold">Đơn {confirmModalData.bookingCode}</div>
                <div>Nội dung CK: <span className="font-mono font-bold">{confirmModalData.transferReference}</span></div>
                <div>Số trên QR: <span className="font-bold">{formatVnd(confirmModalData.amount)}</span></div>
                <div>Đã ghi sổ: <span className="font-bold">{formatVnd(confirmModalData.paidAmount || 0)}</span></div>
              </div>
              {confirmModalData.evidences?.[0] ? (
                <PaymentEvidenceImage
                  evidence={confirmModalData.evidences[0]}
                  alt="Biên lai khách gửi"
                  className="h-36 w-full rounded-xl border border-slate-200 object-contain bg-slate-50"
                />
              ) : null}

              <div>
                <label className="block text-xs font-semibold text-slate-700">Số tiền trên sao kê bạn đã nhận (VND) *</label>
                <input
                  type="number"
                  required
                  min="1000"
                  value={receivedAmountInput}
                  onChange={(e) => setReceivedAmountInput(e.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-xs focus:border-brand-600 focus:outline-none"
                />
                <span className="text-[11px] text-slate-500">Gõ số trên sao kê. Để nguyên nếu đúng số QR. Thiếu sẽ ghi nhận một phần; thừa sẽ tạo lệnh hoàn.</span>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700">Mã giao dịch ngân hàng (Tùy chọn)</label>
                <input
                  type="text"
                  value={bankRefInput}
                  onChange={(e) => setBankRefInput(e.target.value)}
                  placeholder="FT24..."
                  className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-xs focus:border-[#059669] focus:outline-hidden"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700">Ghi chú đối soát (Tùy chọn)</label>
                <textarea
                  rows={2}
                  value={confirmNoteInput}
                  onChange={(e) => setConfirmNoteInput(e.target.value)}
                  placeholder="Ghi chú nội bộ..."
                  className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden"
                />
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setConfirmModalData(null)}
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-300 px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isConfirming}
                  className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-[#059669] px-4 text-xs font-semibold text-white shadow-xs hover:bg-[#047857]"
                >
                  {isConfirming && <Icon name="sync" className="animate-spin text-sm" />}
                  Xác nhận đã nhận tiền
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODAL 3: REJECT PAYMENT PROOF (SCREEN C)                 */}
      {/* ======================================================== */}
      {rejectModalData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-base font-bold text-red-600">Từ chối biên lai thanh toán</h3>
              <button
                type="button"
                onClick={() => setRejectModalData(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <form onSubmit={handleRejectPayment} className="mt-4 space-y-4">
              <p className="text-xs text-slate-600">
                Bạn đang từ chối biên lai thanh toán của đơn thuê <span className="font-bold">#{rejectModalData.bookingCode}</span>. Khách hàng sẽ nhận được thông báo để gửi lại biên lai chính xác.
              </p>

              <div>
                <label className="block text-xs font-semibold text-slate-700">Lý do từ chối *</label>
                <textarea
                  rows={3}
                  required
                  value={rejectReasonInput}
                  onChange={(e) => setRejectReasonInput(e.target.value)}
                  placeholder="Ví dụ: Chưa nhận được biến động số dư trong tài khoản, ảnh biên lai mờ..."
                  className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-red-500 focus:outline-hidden"
                />
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setRejectModalData(null)}
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-300 px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isRejecting}
                  className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-red-600 px-4 text-xs font-semibold text-white shadow-xs hover:bg-red-700"
                >
                  {isRejecting && <Icon name="sync" className="animate-spin text-sm" />}
                  Xác nhận từ chối
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODAL 4: REPORT DISCREPANCY / ISSUE (SCREEN C)           */}
      {/* ======================================================== */}
      {reportIssueModalData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-base font-bold text-purple-700">Báo cáo sai lệch / Cần đối soát</h3>
              <button
                type="button"
                onClick={() => setReportIssueModalData(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <form onSubmit={handleReportIssue} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700">Mô tả sai lệch hoặc tranh chấp *</label>
                <textarea
                  rows={3}
                  required
                  value={issueNoteInput}
                  onChange={(e) => setIssueNoteInput(e.target.value)}
                  placeholder="Ví dụ: Khách hàng chuyển sai mã tham chiếu, số tiền chuyển không khớp..."
                  className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-purple-500 focus:outline-hidden"
                />
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setReportIssueModalData(null)}
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-300 px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isReportingIssue}
                  className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-purple-700 px-4 text-xs font-semibold text-white shadow-xs hover:bg-purple-800"
                >
                  {isReportingIssue && <Icon name="sync" className="animate-spin text-sm" />}
                  Gửi báo cáo
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODAL 5: VIEW PROOF IMAGE (SCREEN C)                     */}
      {/* ======================================================== */}
      {viewProofModalData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-base font-bold text-slate-900">Biên lai thanh toán của khách hàng</h3>
              <button
                type="button"
                onClick={() => setViewProofModalData(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              {viewProofModalData.evidences && viewProofModalData.evidences.length > 0 ? (
                viewProofModalData.evidences.map((ev) => (
                  <div key={ev.id} className="rounded-xl border border-slate-200 p-4 bg-slate-50">
                    <div className="flex justify-between items-center text-xs mb-2">
                      <span className="font-semibold text-slate-800">Người gửi: {ev.submittedByName || 'Khách hàng'}</span>
                      <span className="text-slate-500">{formatDateTime(ev.createdAt)}</span>
                    </div>
                    <div className="text-xs mb-3 text-slate-600">
                      Số trên VietQR: <span className="font-bold text-emerald-700">{formatVnd(viewProofModalData.amount)}</span>
                      {ev.transactionReference && (
                        <span className="ml-3 font-mono text-[11px]">Mã GD: {ev.transactionReference}</span>
                      )}
                    </div>
                    {ev.note && <div className="text-xs italic text-slate-600 mb-3 bg-white p-2 rounded">"{ev.note}"</div>}
                    <PaymentEvidenceImage
                      evidence={ev}
                      className="w-full max-h-80 object-contain rounded-lg border border-slate-200 bg-white"
                    />
                  </div>
                ))
              ) : (
                <div className="p-4 text-center text-xs text-slate-500">Chưa có ảnh bằng chứng nào được tải lên.</div>
              )}
            </div>

            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={() => setViewProofModalData(null)}
                className="rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODAL 6: FINANCIAL BREAKDOWN (SCREEN C)                  */}
      {/* ======================================================== */}
      {breakdownData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-2xl rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  Đối soát tài chính đơn #{breakdownData.bookingCode}
                </h3>
                <span className="text-xs text-slate-500">Trạng thái chuyến: <span className="font-bold">{breakdownData.bookingStatus}</span></span>
              </div>
              <button
                type="button"
                onClick={() => setBreakdownData(null)}
                className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <div className="mt-5 space-y-6">
              {/* Financial Summary Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-50 p-4 rounded-xl border border-slate-200 text-xs">
                <div>
                  <span className="text-slate-500">Giá thuê gốc:</span>
                  <div className="font-bold text-slate-900">{formatVnd(breakdownData.baseRental)}</div>
                </div>
                <div>
                  <span className="text-slate-500">Phụ phí chốt:</span>
                  <div className="font-bold text-blue-700">+{formatVnd(breakdownData.approvedCharges)}</div>
                </div>
                <div>
                  <span className="text-slate-500">Đã thanh toán:</span>
                  <div className="font-bold text-emerald-700">{formatVnd(breakdownData.netConfirmedPayments)}</div>
                </div>
                <div>
                  <span className="text-slate-500">Còn thiếu:</span>
                  <div className="font-bold text-red-600">{formatVnd(breakdownData.remainingAmount)}</div>
                </div>
              </div>

              {/* Surcharges list */}
              {breakdownData.charges && breakdownData.charges.length > 0 && (
                <div>
                  <h4 className="text-xs font-bold text-slate-900 mb-2 uppercase tracking-wider">Khoản mục phụ phí</h4>
                  <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg text-xs overflow-hidden">
                    {breakdownData.charges.map((c) => (
                      <div key={c.id} className="p-3 flex justify-between items-center bg-white">
                        <div>
                          <span className="font-semibold text-slate-800">{c.description}</span>
                          <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600 font-mono">{c.chargeType}</span>
                        </div>
                        <span className="font-bold text-slate-900">{formatVnd(c.amount)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Payment Timeline */}
              <div>
                <h4 className="text-xs font-bold text-slate-900 mb-2 uppercase tracking-wider">Lịch sử giao dịch tiền</h4>
                {breakdownData.transactions && breakdownData.transactions.length > 0 ? (
                  <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg text-xs overflow-hidden">
                    {breakdownData.transactions.map((tx) => (
                      <div key={tx.id} className="p-3 flex justify-between items-center bg-white">
                        <div>
                          <div className="font-semibold text-slate-800">{tx.type} • {tx.method}</div>
                          <div className="text-[11px] text-slate-400 font-mono">{tx.gatewayRef || '—'} • {formatDateTime(tx.paidAt)}</div>
                        </div>
                        <span className="font-bold text-emerald-700">+{formatVnd(tx.amount)}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="p-4 text-center text-xs text-slate-400 border border-dashed rounded-lg">Chưa có giao dịch tiền nào được ghi nhận.</div>
                )}
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                onClick={() => setBreakdownData(null)}
                className="rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </ProviderLayout>
  )
}
