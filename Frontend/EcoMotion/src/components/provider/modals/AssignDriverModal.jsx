import { useState, useEffect, useMemo, useCallback } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'
import { useAvailableDrivers } from '../../../hooks/useAvailableDrivers'
import AssignmentDriverStatus from '../AssignmentDriverStatus'
import { assignmentStatuses, filterAssignmentDrivers } from '../../../utils/assignmentAvailability'

function Icon({ name, className = '' }) {
  return (
    <span className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}>
      {name}
    </span>
  )
}

function initialsOf(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

export default function AssignDriverModal({ booking, currentDriverId, isOpen, onClose, onSuccess, onRefresh }) {
  const bookingCode = booking?.bookingCode
  const [activeMode, setActiveMode] = useState('MANUAL') // 'MANUAL' | 'SUGGESTION' | 'AUTO'

  // Manual mode state
  const { drivers, loading: loadingDrivers, error: driversError, refetch: refreshDrivers } = useAvailableDrivers(booking?.bookingCode, isOpen)
  const [selectedDriverId, setSelectedDriverId] = useState(currentDriverId || null)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  // Suggestion mode state
  const [suggestionsLoading, setSuggestionsLoading] = useState(false)
  const [suggestionResult, setSuggestionResult] = useState(null)
  const [suggestionError, setSuggestionError] = useState(null)
  const [selectedCandidateId, setSelectedCandidateId] = useState(null)

  // Auto assign mode state
  const [autoAssignLoading, setAutoAssignLoading] = useState(false)
  const [autoAssignResult, setAutoAssignResult] = useState(null)
  const [autoAssignError, setAutoAssignError] = useState(null)

  // Global submit loading & errors
  const [submitting, setSubmitting] = useState(false)
  const [actionError, setActionError] = useState(null)

  // Fetch suggestions when opening modal in SUGGESTION mode
  const fetchSuggestions = useCallback(async () => {
    if (!bookingCode) return
    setSuggestionsLoading(true)
    setSuggestionError(null)
    try {
      const data = await providerBookingService.suggestDriverAssignment(bookingCode)
      setSuggestionResult(data)
      if (data?.suggestedDriver?.driverId) {
        setSelectedCandidateId(data.suggestedDriver.driverId)
      } else if (data?.candidates?.length > 0) {
        const firstEligible = data.candidates.find((c) => c.eligible)
        setSelectedCandidateId(firstEligible?.driverId || null)
      }
    } catch (err) {
      console.error('Failed to load assignment suggestions:', err)
      setSuggestionError(err?.response?.data?.message || err?.message || 'Không thể tải đề xuất phân công tài xế.')
    } finally {
      setSuggestionsLoading(false)
    }
  }, [bookingCode])

  useEffect(() => {
    if (!isOpen || !bookingCode) return
    const timer = setTimeout(() => {
      setSelectedDriverId(currentDriverId || null)
      setActionError(null)
      setAutoAssignResult(null)
      setAutoAssignError(null)
      if (activeMode === 'SUGGESTION') {
        fetchSuggestions()
      }
    }, 0)
    return () => clearTimeout(timer)
  }, [isOpen, bookingCode, currentDriverId, activeMode, fetchSuggestions])

  // Filter available drivers for Manual mode
  const filteredDrivers = useMemo(() => {
    return filterAssignmentDrivers(drivers || [], searchQuery, statusFilter)
  }, [drivers, searchQuery, statusFilter])
  const selectedDriver = drivers.find(driver => driver.id === selectedDriverId)

  if (!isOpen || !booking) return null

  // Handler for manual assignment
  const handleConfirmManual = async () => {
    if (!drivers.some(driver => driver.id === selectedDriverId && driver.eligible)) {
      setActionError('Vui lòng chọn một tài xế trong danh sách để phân công.')
      return
    }

    setSubmitting(true)
    setActionError(null)
    try {
      const updated = await providerBookingService.assignDriver(booking.bookingCode, selectedDriverId)
      refreshDrivers()
      onSuccess?.(updated, 'Đã phân công tài xế thành công theo phương thức thủ công!')
      onClose()
    } catch (err) {
      onRefresh?.()
      setSelectedDriverId(null)
      setSelectedCandidateId(null)
      refreshDrivers()
      fetchSuggestions()
      setActionError(err?.response?.data?.message || err?.message || 'Phân công tài xế thất bại. Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  // Handler for suggested candidate assignment
  const handleConfirmCandidate = async (candidateId) => {
    const targetId = candidateId || selectedCandidateId
    if (!drivers.some(driver => driver.driverId === targetId && driver.eligible) || loadingDrivers || driversError) {
      setActionError('Vui lòng chọn một tài xế đề xuất để xác nhận phân công.')
      return
    }

    setSubmitting(true)
    setActionError(null)
    try {
      const updated = await providerBookingService.assignDriver(booking.bookingCode, targetId)
      refreshDrivers()
      onSuccess?.(updated, 'Đã phân công tài xế theo danh sách đề xuất thành công!')
      onClose()
    } catch (err) {
      onRefresh?.()
      setSelectedCandidateId(null)
      refreshDrivers()
      fetchSuggestions()
      setActionError(err?.response?.data?.message || err?.message || 'Không thể xác nhận phân công tài xế.')
    } finally {
      setSubmitting(false)
    }
  }

  // Handler for Full Auto Assign
  const handleRunAutoAssign = async () => {
    setAutoAssignLoading(true)
    setAutoAssignError(null)
    setActionError(null)
    try {
      const updated = await providerBookingService.autoAssignDriver(booking.bookingCode)
      refreshDrivers()
      setAutoAssignResult(updated)
      onSuccess?.(updated, 'Đã phân công tài xế; đang chờ tài xế xác nhận.')
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Hệ thống không tìm thấy tài xế khả dụng phù hợp.'
      setAutoAssignError(msg)
    } finally {
      setAutoAssignLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div role="dialog" aria-modal="true" aria-label="Phân công tài xế" className="w-full max-w-2xl overflow-hidden rounded-lg bg-white shadow-md flex flex-col max-h-[92vh] border border-slate-200">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 bg-slate-50">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-100 text-brand-800">
              <Icon name="assignment_ind" className="text-2xl" />
            </span>
            <div className="min-w-0">
              <h2 className="text-base font-bold text-slate-900">
                Điều phối &amp; Phân công tài xế
              </h2>
              <p className="break-words text-xs text-slate-500 font-medium">
                Mã đơn thuê: <span className="font-mono font-bold text-slate-800">{booking.bookingCode}</span> •{' '}
                Điểm đón: <span className="text-slate-700">{booking.pickupAddress || 'Đang cập nhật'}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting || autoAssignLoading}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition"
            aria-label="Đóng cửa sổ"
          >
            <Icon name="close" className="text-xl" />
          </button>
        </div>

        {/* Dispatch Mode Tabs */}
        <div className="flex flex-wrap border-b border-slate-200 bg-white px-3 sm:px-6 pt-3 gap-2">
          <button
            type="button"
            onClick={() => setActiveMode('SUGGESTION')}
            className={`pb-3 px-3 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeMode === 'SUGGESTION'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="recommend" className="text-[16px]" />
            <span>Gợi ý tài xế</span>
          </button>

          <button
            type="button"
            disabled={booking.canAutoAssignDriver === false}
            onClick={() => setActiveMode('AUTO')}
            className={`pb-3 px-3 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeMode === 'AUTO'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="auto_mode" className="text-[16px]" />
            <span>Tự động điều phối</span>
          </button>

          <button
            type="button"
            disabled={booking.canAssignDriver === false}
            onClick={() => setActiveMode('MANUAL')}
            className={`pb-3 px-3 text-xs font-bold transition-all border-b-2 flex items-center gap-1.5 ${
              activeMode === 'MANUAL'
                ? 'border-brand-700 text-brand-800'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Icon name="touch_app" className="text-[16px]" />
            <span>Thủ công</span>
          </button>
        </div>

        {/* Global Error Banner */}
        {actionError && (
          <div className="mx-6 mt-4 flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-800">
            <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
            <span className="font-medium">{actionError}</span>
          </div>
        )}

        {/* Body content based on activeMode */}
        <div className="p-6 overflow-y-auto flex-1 space-y-4">
          {/* ========================================================
              MODE 1: GỢI Ý TÀI XẾ (OR-Tools Candidate Suggestions)
              ======================================================== */}
          {activeMode === 'SUGGESTION' && (
            <div className="space-y-2"><p className="text-xs text-slate-600">Gợi ý chưa lưu phân công. {suggestionResult?.solverStatus?.startsWith('FALLBACK') ? 'Đang dùng xếp hạng dự phòng: ' : 'Trạng thái bộ giải: '}{suggestionResult?.solverStatus || 'Đang chờ'}</p>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                    Danh sách ứng viên tài xế đề xuất
                  </h3>
                  <p className="text-xs text-slate-500">
                    Chọn một tài xế cho đơn này theo ETA ước tính, tải chuyến và đánh giá; không có dữ liệu giao thông.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={fetchSuggestions}
                  disabled={suggestionsLoading}
                  className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  <Icon name="refresh" className={`text-[14px] ${suggestionsLoading ? 'animate-spin' : ''}`} />
                  <span>Cập nhật</span>
                </button>
              </div>

              {suggestionsLoading ? (
                <div className="py-12 text-center text-xs text-slate-500">
                  <Icon name="sync" className="animate-spin text-2xl text-brand-600 mx-auto mb-2" />
                  Đang kiểm tra điều kiện và lựa chọn tài xế cho đơn này...
                </div>
              ) : suggestionError ? (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-800">
                  <div className="font-bold flex items-center gap-1.5 mb-1">
                    <Icon name="warning" className="text-[16px] text-amber-600" />
                    Không thể lấy danh sách đề xuất
                  </div>
                  <p>{suggestionError}</p>
                </div>
              ) : !suggestionResult?.candidates || suggestionResult.candidates.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-xs text-slate-500">
                  <Icon name="person_off" className="text-3xl text-slate-300 mx-auto mb-2" />
                  <div className="font-semibold text-slate-800 text-sm">Chưa có tài xế phù hợp với lịch thuê này.</div>
                  <p className="mt-1 text-slate-500">Hệ thống chưa tìm thấy tài xế khả dụng đáp ứng đầy đủ điều kiện vận hành trong khung giờ này.</p>
                  <div className="mt-3">
                    <button
                      type="button"
                      onClick={fetchSuggestions}
                      className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                    >
                      Thử lại tìm kiếm
                    </button>
                  </div>
                  <p className="mt-3 text-[11px] text-slate-400">Bạn có thể chuyển sang chế độ "Thủ công" để xem toàn bộ danh sách tài xế và nguyên nhân không khả dụng.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {suggestionResult.explanation && (
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 flex items-start gap-2">
                      <Icon name="info" className="text-slate-500 text-[16px] shrink-0 mt-0.5" />
                      <div>
                        <span className="font-semibold">Đánh giá hệ thống điều phối: </span>
                        {suggestionResult.explanation}
                      </div>
                    </div>
                  )}

                  <div className="space-y-2.5 max-h-[380px] overflow-y-auto pr-1">
                    {suggestionResult.candidates.map((suggestedCandidate) => {
                      const candidate = drivers.find(driver => driver.driverId === suggestedCandidate.driverId) || { ...suggestedCandidate, eligible: false }
                      const isSelected = selectedCandidateId === candidate.driverId
                      const isBest = candidate.isBestMatch

                      return (
                        <div
                          key={candidate.driverId}
                          onClick={() => candidate.eligible && setSelectedCandidateId(candidate.driverId)}
                          className={`rounded-xl border p-4 transition-all ${
                            !candidate.eligible
                              ? 'border-slate-200 bg-slate-50 opacity-60 cursor-not-allowed'
                              : isSelected
                              ? 'border-brand-600 bg-brand-50/50 ring-2 ring-brand-600/20 cursor-pointer shadow-xs'
                              : 'border-slate-200 bg-white hover:border-slate-300 cursor-pointer'
                          }`}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex items-center gap-3">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-bold text-slate-700">
                                {initialsOf(candidate.fullName)}
                              </div>
                              <div>
                                <div className="flex items-center gap-2">
                                  <span className="text-sm font-bold text-slate-900">{candidate.fullName}</span>
                                  {isBest && (
                                    <span className="rounded bg-brand-100 px-2 py-0.5 text-[10px] font-bold text-brand-800">
                                      Đề xuất ưu tiên
                                    </span>
                                  )}
                                  {candidate.licenseClass && (
                                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-mono font-semibold text-slate-600">
                                      Hạng {candidate.licenseClass}
                                    </span>
                                  )}
                                </div>
                                <div className="mt-0.5 text-xs text-slate-500 flex items-center gap-2">
                                  <span>{candidate.phone || 'Chưa cập nhật SĐT'}</span>
                                  {candidate.ratingAvg != null && (
                                    <span className="flex items-center gap-0.5 text-amber-600 font-semibold">
                                      <Icon name="star" className="text-[13px] fill-current" />
                                      {candidate.ratingAvg} ({candidate.ratingCount || 0})
                                    </span>
                                  )}
                                </div>
                              </div>
                            </div>

                            {/* Operational Metric Badges */}
                            <div className="text-right shrink-0">
                              <div className="text-xs font-bold text-slate-800">
                                ETA: {candidate.etaMinutes != null ? `${candidate.etaMinutes} phút` : '—'}
                              </div>
                              <div className="text-[11px] text-slate-500">
                                Khoảng cách: {candidate.distanceKm != null ? `${candidate.distanceKm} km` : '—'}
                              </div>
                            </div>
                          </div>

                          <AssignmentDriverStatus driver={candidate} />
                          {/* Recommendation explanation or ineligibility reason */}
                          <div className="mt-2.5 pt-2 border-t border-slate-100 flex items-center justify-between text-xs">
                            <div className="text-slate-600 text-[11px] leading-tight">
                              {candidate.eligible ? (
                                <span className="text-brand-900 font-medium">
                                  {candidate.recommendationReason ||
                                    `Số cuốc hiện tại: ${candidate.activeTripsCount || 0} • Điểm phù hợp: ${candidate.suitabilityScore || 0}`}
                                </span>
                              ) : (
                                <span className="text-red-700 font-medium">
                                  Không khả dụng: {candidate.ineligibilityReason || 'Đang bận chuyến khác'}
                                </span>
                              )}
                            </div>

                            {candidate.eligible && booking.canAssignDriver !== false && (
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation()
                                  handleConfirmCandidate(candidate.driverId)
                                }}
                                disabled={submitting || loadingDrivers || !!driversError}
                                className="rounded-lg bg-brand-700 px-3 py-1 text-xs font-semibold text-white hover:bg-brand-800 transition disabled:opacity-50 shrink-0"
                              >
                                {submitting && selectedCandidateId === candidate.driverId ? 'Đang phân công...' : 'Chọn tài xế này'}
                              </button>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
            </div>
          )}

          {/* ========================================================
              MODE 2: TỰ ĐỘNG ĐIỀU PHỐI (Auto Assign via OR-Tools)
              ======================================================== */}
          {activeMode === 'AUTO' && (
            <div className="space-y-4">
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <h3 className="text-sm font-bold text-slate-900 mb-1 flex items-center gap-2">
                  <Icon name="auto_mode" className="text-brand-700" />
                  Quy trình phân công tự động
                </h3>
                <p className="text-xs text-slate-600 leading-relaxed">
                  Hệ thống sẽ chạy thuật toán điều phối để chọn ra tài xế khả dụng phù hợp nhất cho chuyến đi này dựa trên khoảng cách di chuyển, điểm uy tín và thời gian tiếp cận khách.
                </p>
                <div className="mt-3 grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                  <div className="rounded-md bg-white p-2.5 border border-slate-200">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">1. Tìm ứng viên</span>
                    <span className="font-semibold text-slate-800">Lọc theo vị trí &amp; bằng lái</span>
                  </div>
                  <div className="rounded-md bg-white p-2.5 border border-slate-200">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">2. Tối ưu phân bổ</span>
                    <span className="font-semibold text-slate-800">Tối thiểu hóa ETA</span>
                  </div>
                  <div className="rounded-md bg-white p-2.5 border border-slate-200">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">3. Gửi phân công</span>
                    <span className="font-semibold text-slate-800">Chờ tài xế xác nhận 15p</span>
                  </div>
                </div>
              </div>

              {autoAssignError && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-xs text-red-800">
                  <div className="font-bold flex items-center gap-1.5 mb-1">
                    <Icon name="error" className="text-[16px] text-red-600" />
                    Không thể hoàn tất phân công tự động
                  </div>
                  <p>{autoAssignError}</p>
                  <p className="mt-2 text-slate-600">
                    Gợi ý: Kiểm tra lại các tài xế hiện có hoặc sử dụng chế độ "Thủ công" để chỉ định trực tiếp.
                  </p>
                </div>
              )}

              {autoAssignResult ? (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-xs text-emerald-900">
                  <div className="font-bold flex items-center gap-1.5 mb-2 text-sm text-emerald-800">
                    <Icon name="check_circle" className="text-[18px] text-emerald-600" />
                    Đã lưu phân công — chờ tài xế xác nhận
                  </div>
                  <p className="text-xs">Bộ giải: {autoAssignResult.solverStatus}. {autoAssignResult.solverStatus?.startsWith('FALLBACK') ? 'Đã dùng xếp hạng dự phòng có kiểm tra điều kiện.' : autoAssignResult.solverStatus === 'FEASIBLE' ? 'Phương án khả thi, chưa chứng minh tối ưu.' : ''}</p>
                  <div className="rounded-md bg-white p-3 border border-emerald-200 text-slate-800">
                    <div className="flex justify-between items-center">
                      <span className="text-slate-500">Tài xế được phân công:</span>
                      <span className="font-bold text-slate-900">{autoAssignResult.driverName || 'Đã phân công'}</span>
                    </div>
                    {autoAssignResult.driverPhone && (
                      <div className="flex justify-between items-center mt-1">
                        <span className="text-slate-500">Số điện thoại:</span>
                        <span className="font-mono font-semibold">{autoAssignResult.driverPhone}</span>
                      </div>
                    )}
                    <div className="flex justify-between items-center mt-1">
                      <span className="text-slate-500">Trạng thái:</span>
                      <span className="font-semibold text-amber-700">Chờ tài xế xác nhận (thời hạn cấu hình)</span>
                    </div>
                  </div>
                  <div className="mt-4 flex justify-end">
                    <button
                      type="button"
                      onClick={onClose}
                      className="rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800"
                    >
                      Đóng cửa sổ
                    </button>
                  </div>
                </div>
              ) : (
                <div className="pt-2 flex justify-center">
                  <button
                    type="button"
                    onClick={handleRunAutoAssign}
                    disabled={autoAssignLoading || booking.canAutoAssignDriver === false}
                    className="inline-flex items-center gap-2 rounded-lg bg-brand-700 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition disabled:opacity-50"
                  >
                    {autoAssignLoading && <Icon name="sync" className="animate-spin text-[16px]" />}
                    <span>{autoAssignLoading ? 'Đang chạy thuật toán tối ưu phân công...' : 'Kích hoạt tự động phân công ngay'}</span>
                  </button>
                </div>
              )}
            </div>
          )}

          {/* ========================================================
              MODE 3: THỦ CÔNG (Manual Selection)
              ======================================================== */}
          {activeMode === 'MANUAL' && booking.canAssignDriver !== false && (
            <div className="space-y-3">
              {driversError && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
                  {driversError}
                </div>
              )}

              {/* Search & Status Filter */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                <div>
                  <label htmlFor="assign-driver-search" className="mb-1.5 block text-xs font-semibold text-slate-700">
                    Tìm kiếm tài xế
                  </label>
                  <div className="relative h-10 w-full">
                    <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                      <Icon name="search" className="text-[18px]" />
                    </div>
                    <input
                      id="assign-driver-search"
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Tên, SĐT, số bằng lái..."
                      className="h-10 w-full rounded-lg border border-slate-200 bg-white pl-10 pr-3.5 text-xs text-slate-800 placeholder-slate-400 hover:border-slate-300 focus:border-brand-600 focus:ring-1 focus:ring-brand-600 focus:outline-none transition"
                    />
                  </div>
                </div>

                <label className="block text-xs font-semibold text-slate-700">
                  Trạng thái tài xế
                  <div className="relative mt-1.5 h-10 w-full">
                    <select
                      value={statusFilter}
                      onChange={(event) => setStatusFilter(event.target.value)}
                      className="h-10 w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3.5 pr-9 text-xs font-medium text-slate-700 hover:border-slate-300 focus:border-brand-600 focus:ring-1 focus:ring-brand-600 focus:outline-none transition"
                    >
                      <option value="ALL">Tất cả</option>
                      <option value="ELIGIBLE">Có thể phân công</option>
                      {Object.entries(assignmentStatuses).map(([value, status]) => (
                        <option key={value} value={value}>
                          {status.label}
                        </option>
                      ))}
                    </select>
                    <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                      <Icon name="expand_more" className="text-[18px]" />
                    </div>
                  </div>
                </label>
              </div>
              <div className="space-y-2">
                <label className="block text-xs font-bold text-slate-700">
                  Danh sách tài xế thuộc nhà xe ({filteredDrivers.length}):
                </label>

                {loadingDrivers ? (
                  <div className="py-8 text-center text-xs text-slate-500">
                    <Icon name="sync" className="animate-spin text-xl text-brand-600 mx-auto mb-1" />
                    Đang tải danh sách tài xế...
                  </div>
                ) : filteredDrivers.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-xs text-slate-500">
                    <Icon name="person_off" className="text-3xl text-slate-300 mx-auto mb-2" />
                    <div className="font-semibold text-slate-800 text-sm">Chưa có tài xế phù hợp với lịch thuê này.</div>
                    <p className="mt-1 text-slate-500">
                      {statusFilter === 'ELIGIBLE'
                        ? 'Hiện không có tài xế nào đủ điều kiện nhận cuốc trong khung giờ này.'
                        : 'Không tìm thấy tài xế khớp với bộ lọc tìm kiếm.'}
                    </p>
                    <div className="mt-3 flex items-center justify-center gap-2">
                      <button
                        type="button"
                        onClick={() => { refreshDrivers(); setStatusFilter('ALL'); setSearchQuery(''); }}
                        className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                      >
                        Đặt lại bộ lọc &amp; Thử lại
                      </button>
                    </div>
                    <p className="mt-3 text-[11px] text-slate-400">
                      Đơn thuê vẫn được giữ ở trạng thái chờ phân công. Bạn có thể kiểm tra danh sách đầy đủ hoặc liên hệ Renter để thống nhất dời lịch đón.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-2 max-h-[300px] overflow-y-auto pr-1">
                    {filteredDrivers.map((driver) => {
                      const isSelected = selectedDriverId === driver.id
                      return (
                        <button type="button"
                          key={driver.id}
                          aria-pressed={isSelected}
                          disabled={!driver.eligible || loadingDrivers || !!driversError}
                          onClick={() => driver.eligible && setSelectedDriverId(driver.id)}
                          className={`w-full min-w-0 break-words text-left disabled:cursor-not-allowed disabled:opacity-70 flex items-center justify-between p-3 rounded-xl border transition-all ${
                            isSelected
                              ? 'border-brand-600 bg-brand-50/60 ring-2 ring-brand-600/20'
                              : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-slate-200 text-xs font-bold text-slate-700">
                              {initialsOf(driver.fullName)}
                            </div>
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="text-xs font-bold text-slate-900">{driver.fullName}</span>
                                {driver.licenseClass && (
                                  <span className="rounded bg-slate-100 px-1.5 py-0.2 text-[10px] font-mono text-slate-600">
                                    {driver.licenseClass}
                                  </span>
                                )}
                              </div>
                              <div className="text-[11px] text-slate-500 flex items-center gap-2 mt-0.5">
                                <span>{driver.phone || 'Chưa cập nhật SĐT'}</span>
                                {driver.licenseNo && <span>• GPLX: {driver.licenseNo}</span>}
                              </div>
                              <AssignmentDriverStatus driver={driver} />
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            <div
                              className={`h-4 w-4 rounded-full border flex items-center justify-center ${
                                isSelected ? 'border-brand-600 bg-brand-600' : 'border-slate-300'
                              }`}
                            >
                              {isSelected && <div className="h-1.5 w-1.5 rounded-full bg-white" />}
                            </div>
                          </div>
                        </button>
                      )
                    })}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="flex shrink-0 flex-col gap-3 sm:flex-row sm:items-center justify-between border-t border-slate-200 px-6 py-4 bg-slate-50">
          <div className="max-h-28 overflow-y-auto text-xs text-slate-500">
            {activeMode === 'MANUAL' && selectedDriver?.eligible && <div><p className="font-semibold">Xác nhận lịch trình: {selectedDriver.fullName}</p><AssignmentDriverStatus driver={selectedDriver} /></div>}
            {activeMode === 'SUGGESTION' && selectedCandidateId && 'Chọn tài xế từ danh sách đề xuất.'}
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting || autoAssignLoading}
              className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
            >
              Hủy
            </button>
            {activeMode === 'MANUAL' && (
              <button
                type="button"
                onClick={handleConfirmManual}
                disabled={submitting || loadingDrivers || !!driversError || !drivers.some(driver => driver.id === selectedDriverId && driver.eligible)}
                className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-brand-700 px-4 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition disabled:opacity-50"
              >
                {submitting && <Icon name="sync" className="animate-spin text-[16px]" />}
                <span>{submitting ? 'Đang lưu...' : 'Xác nhận phân công'}</span>
              </button>
            )}
            {activeMode === 'SUGGESTION' && selectedCandidateId && booking.canAssignDriver !== false && (
              <button
                type="button"
                onClick={() => handleConfirmCandidate(selectedCandidateId)}
                disabled={submitting || loadingDrivers || !!driversError || !drivers.some(driver => driver.driverId === selectedCandidateId && driver.eligible)}
                className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-brand-700 px-4 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition disabled:opacity-50"
              >
                {submitting && <Icon name="sync" className="animate-spin text-[16px]" />}
                <span>{submitting ? 'Đang lưu...' : 'Xác nhận tài xế đã chọn'}</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
