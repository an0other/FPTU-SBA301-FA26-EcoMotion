import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import ProviderLayout from '../components/provider/ProviderLayout'
import Toast from '../components/Toast'
import { providerFinanceService, SUPPORTED_BANKS } from '../services/providerFinanceService'
import { PaymentEvidenceImage } from '../components/payments/PaymentEvidenceImage'

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

const REQUEST_STATUS_CONFIG = {
  PENDING: { label: 'Chờ thanh toán', badgeClass: 'bg-amber-50 text-amber-800 border-amber-200', dotClass: 'bg-amber-500' },
  PROOF_SUBMITTED: { label: 'Đã gửi biên lai', badgeClass: 'bg-blue-50 text-blue-800 border-blue-200', dotClass: 'bg-blue-500' },
  PARTIALLY_PAID: { label: 'Thanh toán một phần', badgeClass: 'bg-orange-50 text-orange-800 border-orange-200', dotClass: 'bg-orange-500' },
  CONFIRMED: { label: 'Đã xác nhận đủ', badgeClass: 'bg-emerald-50 text-emerald-800 border-emerald-200', dotClass: 'bg-emerald-500' },
  REJECTED: { label: 'Đã từ chối', badgeClass: 'bg-red-50 text-red-800 border-red-200', dotClass: 'bg-red-500' },
  EXPIRED: { label: 'Hết hạn', badgeClass: 'bg-slate-100 text-slate-700 border-slate-300', dotClass: 'bg-slate-400' },
  REVIEW_REQUIRED: { label: 'Cần đối soát', badgeClass: 'bg-purple-50 text-purple-800 border-purple-200', dotClass: 'bg-purple-500' },
}

const TX_VERIFICATION_LABELS = {
  PROVIDER_CONFIRMED: 'Chủ xe xác nhận',
  ADMIN_RECONCILED: 'Admin đối soát',
  MOCK_SIMULATED: 'Mô phỏng Sandbox',
  MANUAL_UNVERIFIED: 'Chưa xác minh độc lập',
}

const TX_TYPE_LABELS = {
  DEPOSIT_PAY: 'Thanh toán cọc', UPFRONT_PAY: 'Trả trước', SETTLEMENT: 'Quyết toán', REFUND: 'Hoàn tiền', PAYOUT: 'Chi trả nhà cung cấp',
}
const TX_METHOD_LABELS = { BANK_TRANSFER: 'Chuyển khoản', CASH: 'Tiền mặt', CARD: 'Thẻ', E_WALLET: 'Ví điện tử' }
const BOOKING_STATUS_LABELS = {
  AWAITING_DEPOSIT: 'Chờ đặt cọc', CONFIRMED: 'Đã xác nhận', DRIVER_ASSIGNED: 'Đã gán tài xế',
  DRIVER_CONFIRMED: 'Tài xế đã nhận', DRIVER_ARRIVED: 'Tài xế đã đến', AWAITING_START_CONFIRMATION: 'Chờ xác nhận bắt đầu',
  IN_PROGRESS: 'Đang thực hiện', AWAITING_END_CONFIRMATION: 'Chờ xác nhận kết thúc',
  AWAITING_EXTRA_CHARGE_CONFIRMATION: 'Chờ chốt phụ phí', AWAITING_FINAL_PAYMENT: 'Chờ quyết toán',
  CANCELLATION_REVIEW: 'Đang đối soát huỷ', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã huỷ', REJECTED: 'Đã từ chối',
}
const CHARGE_LABELS = {
  BASE_RENTAL: 'Tiền thuê', DEPOSIT: 'Tiền cọc', OVERTIME: 'Phụ phí quá giờ', WAITING: 'Phụ phí chờ',
  WEEKEND: 'Phụ phí cuối tuần', OUT_OF_RADIUS: 'Phụ phí ngoài bán kính',
}

const ACCOUNT_HOLDER_REGEX = /^[a-zA-ZÀ-ỹA-Z0-9\s.'-]+$/

export default function ProviderFinancePage() {
  const [activeTab, setActiveTab] = useState('overview') // overview | incoming | transactions | commissions

  // Main Data States
  const [bankAccount, setBankAccount] = useState(null)
  const [summary, setSummary] = useState(null)
  const [paymentRequests, setPaymentRequests] = useState([])
  const [totalPaymentRequests, setTotalPaymentRequests] = useState(0)
  const [transactions, setTransactions] = useState([])
  const [totalTransactions, setTotalTransactions] = useState(0)
  const [commissions, setCommissions] = useState(null)

  // Loading & Error States
  const [loading, setLoading] = useState(true)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Filtering & Pagination
  const [requestStatusFilter, setRequestStatusFilter] = useState('ALL')
  const [txTypeFilter, setTxTypeFilter] = useState('ALL')
  const [currentPage, setCurrentPage] = useState(1)
  const pageSize = 10

  // Modals
  const [isBankModalOpen, setIsBankModalOpen] = useState(false)
  const [confirmModalData, setConfirmModalData] = useState(null)
  const [rejectModalData, setRejectModalData] = useState(null)
  const [reportIssueModalData, setReportIssueModalData] = useState(null)
  const [viewProofModalData, setViewProofModalData] = useState(null)
  const [breakdownData, setBreakdownData] = useState(null)
  const [refundModalData, setRefundModalData] = useState(null)
  const [refundProofFile, setRefundProofFile] = useState(null)
  const [refundBankReference, setRefundBankReference] = useState('')
  const [refundNote, setRefundNote] = useState('')
  const [submittingRefund, setSubmittingRefund] = useState(false)

  // Form States for Bank Account
  const [formBankCode, setFormBankCode] = useState('VCB')
  const [formAccountNumber, setFormAccountNumber] = useState('')
  const [formAccountHolder, setFormAccountHolder] = useState('')
  const [formErrors, setFormErrors] = useState({})
  const [isSubmittingBank, setIsSubmittingBank] = useState(false)

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

  // Fetch Core Data
  const fetchData = useCallback(async () => {
    try {
      setLoading(true)

      const [summaryRes, bankRes, commRes] = await Promise.all([
        providerFinanceService.getFinanceSummary().catch(() => null),
        providerFinanceService.getBankAccount().catch(() => null),
        providerFinanceService.getCommissions().catch(() => null),
      ])

      setSummary(summaryRes)
      setBankAccount(bankRes)
      setCommissions(commRes)

      // Fetch payment requests or transactions based on activeTab
      if (activeTab === 'incoming') {
        const prRes = await providerFinanceService.listProviderPaymentRequests({
          status: requestStatusFilter,
          offset: (currentPage - 1) * pageSize,
          limit: pageSize,
        })
        setPaymentRequests(prRes.items || [])
        setTotalPaymentRequests(prRes.total || 0)
      } else if (activeTab === 'transactions') {
        const txRes = await providerFinanceService.listTransactions({
          type: txTypeFilter,
          offset: (currentPage - 1) * pageSize,
          limit: pageSize,
        })
        setTransactions(txRes.items || [])
        setTotalTransactions(txRes.total || 0)
      }
    } catch (err) {
      console.error('Failed to load finance data:', err)
      setToast({ message: err.message || 'Không thể tải dữ liệu tài chính.', type: 'error' })
    } finally {
      setLoading(false)
    }
  }, [activeTab, requestStatusFilter, txTypeFilter, currentPage, pageSize])

  useEffect(() => {
    const timer = window.setTimeout(fetchData, 0)
    return () => window.clearTimeout(timer)
  }, [fetchData])

  async function handleCompleteRefund(event) {
    event.preventDefault()
    if (!refundProofFile || !refundBankReference.trim()) return
    if (refundProofFile.size > 5 * 1024 * 1024) {
      setToast({ message: 'Minh chứng hoàn tiền không được vượt quá 5 MB.', type: 'error' })
      return
    }
    try {
      setSubmittingRefund(true)
      await providerFinanceService.completeRefund(refundModalData.id, {
        file: refundProofFile,
        bankReference: refundBankReference.trim(),
        note: refundNote.trim(),
      })
      setRefundModalData(null)
      setRefundProofFile(null)
      setRefundBankReference('')
      setRefundNote('')
      setToast({ message: 'Đã ghi nhận hoàn tiền kèm minh chứng.', type: 'success' })
      await fetchData()
    } catch (err) {
      setToast({ message: err.message || 'Không thể ghi nhận hoàn tiền.', type: 'error' })
    } finally {
      setSubmittingRefund(false)
    }
  }

  // Bank Form Validation
  const validateBankForm = useCallback(() => {
    const errors = {}
    const selectedBank = SUPPORTED_BANKS.find((b) => b.code === formBankCode)

    if (!formBankCode) {
      errors.bankCode = 'Vui lòng chọn ngân hàng thụ hưởng'
    } else if (!selectedBank) {
      errors.bankCode = 'Ngân hàng chưa được hệ thống hỗ trợ'
    }

    const cleanAcc = (formAccountNumber || '').trim()
    if (!cleanAcc) {
      errors.accountNumber = 'Số tài khoản không được để trống'
    } else if (cleanAcc.includes('*')) {
      errors.accountNumber = 'Vui lòng nhập số tài khoản đầy đủ, không sử dụng ký tự che (*)'
    } else if (!/^[0-9]+$/.test(cleanAcc)) {
      errors.accountNumber = 'Số tài khoản chỉ bao gồm các chữ số'
    } else if (selectedBank) {
      if (cleanAcc.length < selectedBank.minLength || cleanAcc.length > selectedBank.maxLength) {
        errors.accountNumber = `Ngân hàng ${selectedBank.shortName} yêu cầu số tài khoản từ ${selectedBank.minLength} đến ${selectedBank.maxLength} chữ số`
      }
    }

    const cleanHolder = (formAccountHolder || '').trim()
    if (!cleanHolder) {
      errors.accountHolder = 'Tên chủ tài khoản không được để trống'
    } else if (cleanHolder.length < 2 || cleanHolder.length > 150) {
      errors.accountHolder = 'Tên chủ tài khoản phải từ 2 đến 150 ký tự'
    } else if (!ACCOUNT_HOLDER_REGEX.test(cleanHolder)) {
      errors.accountHolder = 'Tên chủ tài khoản chứa ký tự không hợp lệ'
    }

    setFormErrors(errors)
    return Object.keys(errors).length === 0
  }, [formBankCode, formAccountNumber, formAccountHolder])

  const handleOpenBankModal = () => {
    setFormBankCode(bankAccount?.bankCode || 'VCB')
    setFormAccountNumber('')
    setFormAccountHolder(bankAccount?.accountHolderName || '')
    setFormErrors({})
    setIsBankModalOpen(true)
  }

  const handleSubmitBankForm = async (e) => {
    e.preventDefault()
    if (!validateBankForm() || isSubmittingBank) return

    try {
      setIsSubmittingBank(true)
      const payload = {
        bankCode: formBankCode,
        accountNumber: formAccountNumber.trim(),
        accountHolderName: formAccountHolder.trim().toUpperCase(),
      }
      const updated = await providerFinanceService.createBankAccount(payload)
      setBankAccount(updated)
      setToast({ message: 'Lưu tài khoản ngân hàng nhận tiền thành công!', type: 'success' })
      setIsBankModalOpen(false)
      fetchData()
    } catch (err) {
      console.error('Failed to save bank account:', err)
      setToast({ message: err.message || 'Không thể lưu tài khoản ngân hàng.', type: 'error' })
    } finally {
      setIsSubmittingBank(false)
    }
  }

  // Handle Confirm Payment
  const handleOpenConfirmModal = (pr) => {
    setConfirmModalData(pr)
    setReceivedAmountInput(String(pr.remainingAmount || pr.amount || ''))
    setBankRefInput('')
    setConfirmNoteInput('')
  }

  const handleConfirmPayment = async (e) => {
    e.preventDefault()
    if (!confirmModalData || isConfirming) return

    try {
      setIsConfirming(true)
      const payload = {
        receivedAmount: Number(receivedAmountInput) || confirmModalData.amount,
        bankTransactionRef: bankRefInput.trim() || undefined,
        note: confirmNoteInput.trim() || undefined,
      }
      await providerFinanceService.confirmPayment(confirmModalData.id, payload)
      setToast({ message: 'Xác nhận đã nhận tiền thành công! Giao dịch tài chính đã được cập nhật.', type: 'success' })
      setConfirmModalData(null)
      fetchData()
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
      fetchData()
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
      fetchData()
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
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        {/* Breadcrumb & Title */}
        <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <nav aria-label="Breadcrumb" className="mb-1 flex items-center gap-1.5 text-xs text-slate-500">
              <Link to="/provider" className="hover:text-slate-800">Đối tác</Link>
              <span>/</span>
              <span className="font-semibold text-slate-800">Tài chính & Thanh toán</span>
            </nav>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Quản lý Tài chính (Direct Payment)
            </h1>
            <p className="mt-0.5 text-xs text-slate-500">
              Khách hàng chuyển khoản trực tiếp vào tài khoản ngân hàng của bạn qua VietQR.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 font-mono text-[11px] font-semibold text-emerald-800">
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
              Mô hình Chuyển khoản trực tiếp
            </span>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="mb-6 border-b border-slate-200">
          <nav className="-mb-px flex gap-6 text-sm font-semibold">
            {[
              { id: 'overview', label: 'Tổng quan tài chính', icon: 'account_balance' },
              { id: 'incoming', label: 'Yêu cầu thanh toán nhận tiền', icon: 'payments', count: totalPaymentRequests },
              { id: 'transactions', label: 'Lịch sử giao dịch', icon: 'receipt_long', count: totalTransactions },
              { id: 'commissions', label: 'Hoa hồng nền tảng', icon: 'price_check' },
            ].map((tab) => {
              const isActive = activeTab === tab.id
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => {
                    setActiveTab(tab.id)
                    setCurrentPage(1)
                  }}
                  className={`flex items-center gap-2 pb-3 pt-1 border-b-2 text-xs font-semibold transition-colors ${
                    isActive
                      ? 'border-[#059669] text-[#059669]'
                      : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
                  }`}
                >
                  <Icon name={tab.icon} className="text-base" />
                  {tab.label}
                  {tab.count != null && tab.count > 0 && (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-600">
                      {tab.count}
                    </span>
                  )}
                </button>
              )
            })}
          </nav>
        </div>

        {/* TAB 1: OVERVIEW & BANK ACCOUNT & COMMISSION CARDS */}
        {activeTab === 'overview' && (
          <div className="space-y-8">
            {/* SCREEN A: METRIC CARDS */}
            <section aria-labelledby="metrics-heading">
              <h2 id="metrics-heading" className="sr-only">Chỉ số tài chính</h2>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
                {/* 1. Confirmed Revenue */}
                <div className="rounded-xl border border-emerald-200 bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-emerald-700">Thực nhận đã xác nhận</span>
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-100 text-emerald-800">
                      <Icon name="check_circle" className="text-lg" />
                    </span>
                  </div>
                  <p className="mt-3 text-2xl font-bold tracking-tight text-emerald-700">
                    {formatVnd(summary?.totalConfirmedRevenue)}
                  </p>
                  <div className="mt-2 text-[11px] text-slate-500">
                    Doanh thu khách đã chuyển & bạn đã xác nhận
                  </div>
                </div>

                {/* 2. Pending Payments */}
                <div className="rounded-xl border border-amber-200 bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-amber-700">Khách đang chuyển</span>
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-100 text-amber-800">
                      <Icon name="pending" className="text-lg" />
                    </span>
                  </div>
                  <p className="mt-3 text-2xl font-bold tracking-tight text-slate-900">
                    {formatVnd(summary?.pendingPayments)}
                  </p>
                  <div className="mt-2 text-[11px] text-slate-500">
                    Số tiền cọc & quyết toán đang chờ nhận
                  </div>
                </div>

                {/* 3. Approved Surcharges */}
                <div className="rounded-xl border border-blue-200 bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-blue-700">Phụ phí đã duyệt</span>
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-100 text-blue-800">
                      <Icon name="add_circle" className="text-lg" />
                    </span>
                  </div>
                  <p className="mt-3 text-2xl font-bold tracking-tight text-slate-900">
                    {formatVnd(summary?.approvedSurcharges)}
                  </p>
                  <div className="mt-2 text-[11px] text-slate-500">
                    Quá giờ, phát sinh, vệ sinh đã chốt
                  </div>
                </div>

                {/* 4. Refunded Amount */}
                <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-slate-600">Đã hoàn tiền</span>
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                      <Icon name="assignment_return" className="text-lg" />
                    </span>
                  </div>
                  <p className="mt-3 text-2xl font-bold tracking-tight text-slate-900">
                    {formatVnd(summary?.refundedAmount)}
                  </p>
                  <div className="mt-2 text-[11px] text-slate-500">
                    Hoàn trả cọc sau khi trừ phụ phí
                  </div>
                </div>

                {/* 5. Estimated Commission */}
                <div className="rounded-xl border border-purple-200 bg-white p-5 shadow-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-purple-700">Hoa hồng dự kiến</span>
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-100 text-purple-800">
                      <Icon name="percent" className="text-lg" />
                    </span>
                  </div>
                  <p className="mt-3 text-2xl font-bold tracking-tight text-purple-700">
                    {formatVnd(summary?.estimatedCommission)}
                  </p>
                  <div className="mt-2 text-[11px] text-slate-500">
                    Phí dịch vụ nền tảng EcoMotion
                  </div>
                </div>
              </div>
            </section>

            {/* SCREEN B: BANK ACCOUNT SECTION */}
            <section aria-labelledby="bank-account-heading">
              <div className="mb-3 flex items-center justify-between">
                <h2 id="bank-account-heading" className="text-base font-bold text-slate-900">
                  Tài khoản ngân hàng nhận tiền trực tiếp
                </h2>
                {bankAccount && (
                  <button
                    type="button"
                    onClick={handleOpenBankModal}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Icon name="edit" className="text-sm" />
                    Thay đổi tài khoản
                  </button>
                )}
              </div>

              {!bankAccount ? (
                <div className="rounded-xl border-2 border-dashed border-amber-300 bg-amber-50/40 p-6 sm:p-8 text-center">
                  <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 text-amber-800">
                    <Icon name="account_balance" className="text-2xl" />
                  </div>
                  <h3 className="mt-3 text-base font-bold text-slate-900">
                    Bạn chưa thiết lập tài khoản ngân hàng nhận tiền.
                  </h3>
                  <p className="mx-auto mt-1 max-w-lg text-xs text-slate-600">
                    Để khách hàng có thể quét mã VietQR chuyển khoản trực tiếp khi đặt xe, bạn cần cung cấp số tài khoản và ngân hàng thụ hưởng.
                  </p>
                  <div className="mt-5">
                    <button
                      type="button"
                      onClick={handleOpenBankModal}
                      className="inline-flex items-center gap-2 rounded-lg bg-[#059669] px-4 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-[#047857]"
                    >
                      <Icon name="add" className="text-lg" />
                      Thêm tài khoản ngân hàng
                    </button>
                  </div>
                </div>
              ) : (
                <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs sm:p-6">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex items-start gap-4">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-emerald-50 border border-emerald-200 text-[#059669] font-bold">
                        <Icon name="account_balance" className="text-2xl" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-slate-900 text-base">{bankAccount.bankName}</span>
                          <span className="rounded bg-emerald-100 px-2 py-0.5 font-mono text-[11px] font-bold text-emerald-800">
                            {bankAccount.bankCode}
                          </span>
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600">
                            Đang hoạt động
                          </span>
                        </div>

                        <div className="mt-2 flex items-center gap-3">
                          <span className="text-xs text-slate-500">Số tài khoản: </span>
                          <span className="font-mono text-sm font-bold tracking-wider text-slate-800">
                            {bankAccount.accountNumberMasked}
                          </span>
                          <button
                            type="button"
                            onClick={() => handleCopy(bankAccount.accountNumberMasked, 'số tài khoản')}
                            className="text-slate-400 hover:text-slate-600"
                            title="Sao chép"
                          >
                            <Icon name="content_copy" className="text-sm" />
                          </button>
                        </div>

                        <div className="mt-1">
                          <span className="text-xs text-slate-500">Chủ tài khoản: </span>
                          <span className="text-sm font-bold text-slate-800 uppercase tracking-wide">
                            {bankAccount.accountHolderName}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="border-t border-slate-100 pt-3 sm:border-t-0 sm:pt-0 sm:text-right">
                      <div className="text-[11px] text-slate-400">Cập nhật lần cuối:</div>
                      <div className="text-xs font-medium text-slate-600">{formatDateTime(bankAccount.updatedAt)}</div>
                      <div className="mt-1 inline-flex items-center gap-1 text-[11px] text-emerald-700 font-medium">
                        <Icon name="verified_user" className="text-xs" />
                        Được snapshot tự động vào VietQR của đơn thuê
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </section>
          </div>
        )}

        {/* TAB 2: INCOMING PAYMENT REQUESTS (SCREEN C) */}
        {activeTab === 'incoming' && (
          <section aria-labelledby="incoming-payments-heading">
            <div className="rounded-xl border border-slate-200 bg-white shadow-xs">
              <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 id="incoming-payments-heading" className="text-base font-bold text-slate-900">
                    Yêu cầu thanh toán nhận tiền
                  </h2>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Danh sách các khoản tiền cọc và quyết toán khách hàng chuyển vào tài khoản của bạn.
                  </p>
                </div>

                {/* Filter Tabs */}
                <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
                  {['ALL', 'PENDING', 'PROOF_SUBMITTED', 'CONFIRMED', 'REVIEW_REQUIRED', 'REJECTED'].map((st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => setRequestStatusFilter(st)}
                      className={`shrink-0 rounded-lg px-3 py-1.5 font-medium transition-colors ${
                        requestStatusFilter === st
                          ? 'bg-[#064e3b] text-white shadow-xs font-semibold'
                          : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      }`}
                    >
                      {st === 'ALL' ? 'Tất cả' : REQUEST_STATUS_CONFIG[st]?.label || st}
                    </button>
                  ))}
                </div>
              </div>

              {/* Table */}
              {loading ? (
                <div className="p-8 text-center text-xs text-slate-500">Đang tải danh sách yêu cầu thanh toán...</div>
              ) : paymentRequests.length === 0 ? (
                <div className="p-10 text-center">
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                    <Icon name="payments" className="text-2xl" />
                  </div>
                  <h3 className="mt-2 text-sm font-bold text-slate-900">Chưa có yêu cầu thanh toán nào</h3>
                  <p className="mt-1 text-xs text-slate-500">Khi bạn chấp nhận đơn thuê, hệ thống sẽ tự động tạo yêu cầu thanh toán cọc tại đây.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs text-slate-700">
                    <thead className="border-b border-slate-100 bg-slate-50 font-semibold text-slate-600">
                      <tr>
                        <th className="px-5 py-3.5">Mã đơn</th>
                        <th className="px-4 py-3.5">Loại yêu cầu</th>
                        <th className="px-4 py-3.5 text-right">Số tiền yêu cầu</th>
                        <th className="px-4 py-3.5 text-right">Đã nhận</th>
                        <th className="px-4 py-3.5">Nội dung đối chiếu</th>
                        <th className="px-4 py-3.5">Trạng thái</th>
                        <th className="px-4 py-3.5">Ngày tạo</th>
                        <th className="px-5 py-3.5 text-center">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {paymentRequests.map((pr) => {
                        const statusMeta = REQUEST_STATUS_CONFIG[pr.status] || {
                          label: pr.status,
                          badgeClass: 'bg-slate-100 text-slate-700',
                          dotClass: 'bg-slate-400',
                        }
                        const hasProof = pr.evidences && pr.evidences.length > 0
                        return (
                          <tr key={pr.id} className="hover:bg-slate-50/80 transition-colors">
                            <td className="px-5 py-4 font-mono font-bold text-slate-900">
                              <button
                                type="button"
                                onClick={() => handleViewBreakdown(pr.bookingId)}
                                className="text-[#059669] hover:underline"
                              >
                                #{pr.bookingCode}
                              </button>
                            </td>
                            <td className="px-4 py-4 font-semibold text-slate-700">
                              {pr.paymentType === 'DEPOSIT' ? 'Tiền cọc' : 'Quyết toán cuối chuyến'}
                            </td>
                            <td className="px-4 py-4 text-right font-bold text-slate-900">
                              {formatVnd(pr.amount)}
                            </td>
                            <td className="px-4 py-4 text-right font-bold text-emerald-700">
                              {formatVnd(pr.paidAmount)}
                            </td>
                            <td className="px-4 py-4 font-mono text-[11px] font-bold text-slate-600">
                              {pr.transferReference}
                            </td>
                            <td className="px-4 py-4">
                              <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-semibold ${statusMeta.badgeClass}`}>
                                <span className={`h-1.5 w-1.5 rounded-full ${statusMeta.dotClass}`} />
                                {statusMeta.label}
                              </span>
                            </td>
                            <td className="px-4 py-4 text-slate-500 whitespace-nowrap">
                              {formatDateTime(pr.createdAt)}
                            </td>
                            <td className="px-5 py-4 text-center">
                              <div className="flex items-center justify-center gap-1.5 flex-wrap">
                                {hasProof && (
                                  <button
                                    type="button"
                                    onClick={() => setViewProofModalData(pr)}
                                    className="rounded-md border border-slate-200 bg-white px-2 py-1 text-[11px] font-semibold text-slate-700 hover:bg-slate-50"
                                    title="Xem biên lai khách gửi"
                                  >
                                    Xem biên lai
                                  </button>
                                )}
                                {pr.status !== 'CONFIRMED' && (
                                  <>
                                    <button
                                      type="button"
                                      onClick={() => handleOpenConfirmModal(pr)}
                                      className="rounded-md bg-[#059669] px-2.5 py-1 text-[11px] font-semibold text-white hover:bg-[#047857]"
                                      title="Xác nhận đã nhận tiền"
                                    >
                                      Xác nhận đã nhận
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => handleOpenRejectModal(pr)}
                                      className="rounded-md border border-red-200 bg-white px-2 py-1 text-[11px] font-semibold text-red-600 hover:bg-red-50"
                                      title="Từ chối biên lai"
                                    >
                                      Từ chối
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => handleOpenReportIssueModal(pr)}
                                      className="rounded-md border border-purple-200 bg-white px-2 py-1 text-[11px] font-semibold text-purple-700 hover:bg-purple-50"
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
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>
        )}

        {/* TAB 3: PAYMENT HISTORY (SCREEN E) */}
        {activeTab === 'transactions' && (
          <section aria-labelledby="transactions-heading">
            <div className="rounded-xl border border-slate-200 bg-white shadow-xs">
              <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 id="transactions-heading" className="text-base font-bold text-slate-900">
                    Lịch sử giao dịch tài chính
                  </h2>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Bản ghi các khoản tiền thực nhận đã được đối chiếu và xác nhận.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <select
                    value={txTypeFilter}
                    onChange={(e) => setTxTypeFilter(e.target.value)}
                    className="rounded-lg border border-slate-300 bg-slate-50 py-1.5 px-3 text-xs font-semibold text-slate-700 focus:border-[#059669] focus:outline-hidden"
                  >
                    <option value="ALL">Tất cả loại giao dịch</option>
                    <option value="DEPOSIT_PAY">Tiền cọc</option>
                    <option value="SETTLEMENT">Quyết toán</option>
                    <option value="REFUND">Hoàn tiền</option>
                  </select>
                </div>
              </div>

              {transactions.length === 0 ? (
                <div className="p-10 text-center">
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                    <Icon name="receipt" className="text-2xl" />
                  </div>
                  <h3 className="mt-2 text-sm font-bold text-slate-900">Chưa có giao dịch tài chính nào</h3>
                  <p className="mt-1 text-xs text-slate-500">Các giao dịch đã xác nhận thành công sẽ xuất hiện tại đây.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs text-slate-700">
                    <thead className="border-b border-slate-100 bg-slate-50 font-semibold text-slate-600">
                      <tr>
                        <th className="px-5 py-3.5">Mã GD</th>
                        <th className="px-4 py-3.5">Đơn thuê</th>
                        <th className="px-4 py-3.5">Loại giao dịch</th>
                        <th className="px-4 py-3.5 text-right">Số tiền</th>
                        <th className="px-4 py-3.5">Mã tham chiếu</th>
                        <th className="px-4 py-3.5">Phương thức xác minh</th>
                        <th className="px-4 py-3.5">Thời gian nhận</th>
                        <th className="px-4 py-3.5">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {transactions.map((tx) => (
                        <tr key={tx.id} className="hover:bg-slate-50/80 transition-colors">
                          <td className="px-5 py-4 font-mono font-bold text-slate-900">#TX-{tx.id}</td>
                          <td className="px-4 py-4">
                            <button
                              type="button"
                              onClick={() => handleViewBreakdown(tx.bookingId)}
                              className="font-mono font-bold text-[#059669] hover:underline"
                            >
                              {tx.bookingCode}
                            </button>
                          </td>
                          <td className="px-4 py-4 font-semibold text-slate-700">{TX_TYPE_LABELS[tx.type] || 'Giao dịch'}</td>
                          <td className="px-4 py-4 text-right font-bold text-[#059669]">
                            {tx.direction === 'OUT' ? '-' : '+'}{formatVnd(tx.amount)}
                          </td>
                          <td className="px-4 py-4 font-mono text-[11px] text-slate-600">{tx.gatewayRef || '—'}</td>
                          <td className="px-4 py-4">
                            <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700">
                              {TX_VERIFICATION_LABELS[tx.verificationMethod] || tx.verificationMethod || 'Chủ xe xác nhận'}
                            </span>
                          </td>
                          <td className="px-4 py-4 text-slate-500 whitespace-nowrap">{formatDateTime(tx.paidAt || tx.createdAt)}</td>
                          <td className="px-4 py-4">
                            {tx.type === 'REFUND' && tx.status === 'PENDING' ? (
                              <button type="button" onClick={() => setRefundModalData(tx)} className="rounded-lg bg-rose-600 px-3 py-1.5 text-[11px] font-bold text-white">Hoàn tiền</button>
                            ) : tx.type === 'REFUND' && tx.receiptUrl ? (
                              <a href={tx.receiptUrl} target="_blank" rel="noreferrer" className="text-[11px] font-bold text-emerald-700 hover:underline">Xem minh chứng</a>
                            ) : '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>
        )}

        {refundModalData ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
            <form onSubmit={handleCompleteRefund} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
              <h3 className="text-base font-bold text-slate-900">Xác nhận đã hoàn tiền</h3>
              <p className="mt-1 text-xs text-slate-500">Đơn {refundModalData.bookingCode} · {formatVnd(refundModalData.amount)}</p>
              <label className="mt-4 block text-xs font-bold text-slate-700">Mã giao dịch ngân hàng *</label>
              <input required value={refundBankReference} onChange={(event) => setRefundBankReference(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm" />
              <label className="mt-3 block text-xs font-bold text-slate-700">Minh chứng hoàn tiền *</label>
              <input required type="file" accept="image/jpeg,image/png,image/webp,application/pdf" onChange={(event) => setRefundProofFile(event.target.files?.[0] ?? null)} className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs" />
              <p className="mt-1 text-[10px] text-slate-400">JPG, PNG, WebP hoặc PDF tối đa 5 MB.</p>
              <label className="mt-3 block text-xs font-bold text-slate-700">Ghi chú</label>
              <textarea value={refundNote} onChange={(event) => setRefundNote(event.target.value)} maxLength={500} rows={2} className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm" />
              <div className="mt-5 flex justify-end gap-2">
                <button type="button" onClick={() => setRefundModalData(null)} disabled={submittingRefund} className="rounded-lg border border-slate-300 px-4 py-2 text-xs font-semibold">Đóng</button>
                <button type="submit" disabled={submittingRefund} className="rounded-lg bg-rose-600 px-4 py-2 text-xs font-bold text-white disabled:opacity-50">{submittingRefund ? 'Đang lưu...' : 'Xác nhận hoàn tiền'}</button>
              </div>
            </form>
          </div>
        ) : null}

        {/* TAB 4: COMMISSIONS OVERVIEW (SCREEN F) */}
        {activeTab === 'commissions' && (
          <section aria-labelledby="commissions-heading">
            <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between border-b border-slate-100 pb-5 mb-5">
                <div>
                  <h2 id="commissions-heading" className="text-base font-bold text-slate-900">Hoa hồng nền tảng EcoMotion</h2>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Chính sách tỷ lệ hoa hồng áp dụng: <span className="font-bold text-slate-800">{Number(commissions?.averageCommissionRate || 0.1) * 100}%</span> trên doanh thu đơn thuê hoàn tất.
                  </p>
                </div>
                <div className="mt-3 sm:mt-0 text-right">
                  <span className="text-xs text-slate-500">Tổng hoa hồng phải thanh toán:</span>
                  <div className="text-2xl font-extrabold text-purple-700">{formatVnd(commissions?.totalCommissionDue)}</div>
                </div>
              </div>

              {!commissions?.commissions || commissions.commissions.length === 0 ? (
                <div className="p-8 text-center text-xs text-slate-500">Chưa có đơn thuê hoàn tất nào để tính hoa hồng.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs text-slate-700">
                    <thead className="border-b border-slate-100 bg-slate-50 font-semibold text-slate-600">
                      <tr>
                        <th className="px-4 py-3">Mã đơn thuê</th>
                        <th className="px-4 py-3 text-right">Doanh thu chuyến</th>
                        <th className="px-4 py-3 text-right">Tỷ lệ</th>
                        <th className="px-4 py-3 text-right">Hoa hồng phát sinh</th>
                        <th className="px-4 py-3">Trạng thái</th>
                        <th className="px-4 py-3">Ngày tạo</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {commissions.commissions.map((c) => (
                        <tr key={c.bookingId} className="hover:bg-slate-50/80">
                          <td className="px-4 py-3.5 font-mono font-bold text-[#059669]">#{c.bookingCode}</td>
                          <td className="px-4 py-3.5 text-right font-medium">{formatVnd(c.totalConfirmedRevenue)}</td>
                          <td className="px-4 py-3.5 text-right font-mono">{Number(c.commissionRate) * 100}%</td>
                          <td className="px-4 py-3.5 text-right font-bold text-purple-700">{formatVnd(c.commissionAmount)}</td>
                          <td className="px-4 py-3.5">
                            <span className="rounded bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-800">
                              Cần thanh toán
                            </span>
                          </td>
                          <td className="px-4 py-3.5 text-slate-500">{formatDateTime(c.bookingDate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>
        )}

        {/* ======================================================== */}
        {/* MODAL 1: ADD/EDIT BANK ACCOUNT (SCREEN B)                */}
        {/* ======================================================== */}
        {isBankModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <h3 className="text-base font-bold text-slate-900">Thiết lập tài khoản nhận tiền</h3>
                <button
                  type="button"
                  onClick={() => setIsBankModalOpen(false)}
                  className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
                >
                  <Icon name="close" className="text-lg" />
                </button>
              </div>

              <form onSubmit={handleSubmitBankForm} className="mt-4 space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700">Ngân hàng thụ hưởng *</label>
                  <select
                    value={formBankCode}
                    onChange={(e) => setFormBankCode(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden focus:ring-1 focus:ring-[#059669]"
                  >
                    {SUPPORTED_BANKS.map((b) => (
                      <option key={b.code} value={b.code}>
                        {b.shortName} — {b.fullName}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Số tài khoản đầy đủ *</label>
                  <input
                    type="text"
                    required
                    value={formAccountNumber}
                    onChange={(e) => setFormAccountNumber(e.target.value)}
                    placeholder="Nhập số tài khoản ngân hàng..."
                    className={`mt-1 w-full rounded-lg border p-2 text-xs focus:outline-hidden ${
                      formErrors.accountNumber ? 'border-red-500 bg-red-50' : 'border-slate-300 focus:border-[#059669]'
                    }`}
                  />
                  {formErrors.accountNumber && (
                    <p className="mt-1 text-[11px] text-red-600">{formErrors.accountNumber}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Tên chủ tài khoản (In hoa, không dấu) *</label>
                  <input
                    type="text"
                    required
                    value={formAccountHolder}
                    onChange={(e) => setFormAccountHolder(e.target.value)}
                    placeholder="NGUYEN VAN A"
                    className={`mt-1 w-full rounded-lg border p-2 text-xs focus:outline-hidden ${
                      formErrors.accountHolder ? 'border-red-500 bg-red-50' : 'border-slate-300 focus:border-[#059669]'
                    }`}
                  />
                  {formErrors.accountHolder && (
                    <p className="mt-1 text-[11px] text-red-600">{formErrors.accountHolder}</p>
                  )}
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={() => setIsBankModalOpen(false)}
                    className="rounded-lg border border-slate-300 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmittingBank}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-[#059669] px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-[#047857]"
                  >
                    {isSubmittingBank && <Icon name="sync" className="animate-spin text-sm" />}
                    Lưu tài khoản
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
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
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
                <div className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800">
                  <div className="font-semibold">Đơn thuê #{confirmModalData.bookingCode}</div>
                  <div>Nội dung tham chiếu: <span className="font-mono font-bold">{confirmModalData.transferReference}</span></div>
                  <div>Số tiền yêu cầu: <span className="font-bold">{formatVnd(confirmModalData.amount)}</span></div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Số tiền thực tế bạn đã nhận (VND) *</label>
                  <input
                    type="number"
                    required
                    min="1000"
                    value={receivedAmountInput}
                    onChange={(e) => setReceivedAmountInput(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden"
                  />
                  <span className="text-[11px] text-slate-500">Nếu khách chuyển thiếu, hệ thống sẽ ghi nhận thanh toán một phần.</span>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Mã giao dịch ngân hàng (Tùy chọn)</label>
                  <input
                    type="text"
                    value={bankRefInput}
                    onChange={(e) => setBankRefInput(e.target.value)}
                    placeholder="Mã tra cứu trên SMS / App ngân hàng"
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Ghi chú xác nhận (Tùy chọn)</label>
                  <input
                    type="text"
                    value={confirmNoteInput}
                    onChange={(e) => setConfirmNoteInput(e.target.value)}
                    placeholder="Ghi chú đối soát nội bộ..."
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden"
                  />
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={() => setConfirmModalData(null)}
                    className="rounded-lg border border-slate-300 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={isConfirming}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-[#059669] px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-[#047857]"
                  >
                    {isConfirming && <Icon name="sync" className="animate-spin text-sm" />}
                    Xác nhận nhận tiền
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* MODAL 3: REJECT PROOF (SCREEN C)                         */}
        {/* ======================================================== */}
        {rejectModalData && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <h3 className="text-base font-bold text-red-700">Từ chối bằng chứng chuyển khoản</h3>
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
                    className="rounded-lg border border-slate-300 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={isRejecting}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-red-700"
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
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
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
                    className="rounded-lg border border-slate-300 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={isReportingIssue}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-purple-700 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-purple-800"
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
            <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
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
                        Số tiền khai báo: <span className="font-bold text-emerald-700">{formatVnd(ev.transferredAmount)}</span>
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
        {/* DRAWER / MODAL: BOOKING FINANCIAL BREAKDOWN (SCREEN D)   */}
        {/* ======================================================== */}
        {breakdownData && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
            <div className="w-full max-w-2xl rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <div>
                  <h3 className="text-base font-bold text-slate-900">
                    Đối soát tài chính đơn #{breakdownData.bookingCode}
                  </h3>
                  <span className="text-xs text-slate-500">Trạng thái chuyến: <span className="font-bold">{BOOKING_STATUS_LABELS[breakdownData.bookingStatus] || 'Đang xử lý'}</span></span>
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
                            <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600">{CHARGE_LABELS[c.chargeType] || 'Phụ phí'}</span>
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
                            <div className="font-semibold text-slate-800">{TX_TYPE_LABELS[tx.type] || 'Giao dịch'} • {TX_METHOD_LABELS[tx.method] || 'Khác'}</div>
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
      </div>
    </ProviderLayout>
  )
}
