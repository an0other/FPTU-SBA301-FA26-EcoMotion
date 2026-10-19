import React, { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom'
import ProviderHeader from '../../components/provider/ProviderHeader'
import Toast from '../../components/Toast'
import { providerService } from '../../services/providerService'

const DOC_SPECS = [
  {
    type: 'REGISTRATION',
    title: '1. Giấy đăng ký xe (Cà vẹt)',
    code: 'REGISTRATION',
    icon: 'badge',
    iconBg: 'bg-emerald-100 text-emerald-800',
    description: 'Chứng nhận quyền sở hữu và lưu hành phương tiện do CSGT cấp.',
    expiryHint: 'Thường vô thời hạn đối với xe cá nhân/doanh nghiệp'
  },
  {
    type: 'INSPECTION',
    title: '2. Giấy chứng nhận kiểm định',
    code: 'INSPECTION',
    icon: 'fact_check',
    iconBg: 'bg-amber-100 text-amber-800',
    description: 'Chứng nhận an toàn kỹ thuật & bảo vệ môi trường định kỳ (Sổ đăng kiểm).',
    expiryHint: 'Bắt buộc nhập ngày hết hạn ghi trên tem kiểm định'
  },
  {
    type: 'INSURANCE',
    title: '3. Bảo hiểm TNDS bắt buộc',
    code: 'INSURANCE',
    icon: 'health_and_safety',
    iconBg: 'bg-blue-100 text-blue-700',
    description: 'Bảo hiểm trách nhiệm dân sự bắt buộc của chủ xe cơ giới còn hiệu lực.',
    expiryHint: 'Bắt buộc nhập ngày hết hạn chứng nhận bảo hiểm'
  },
  {
    type: 'BUSINESS_BADGE',
    title: '4. Phù hiệu xe hợp đồng',
    code: 'BUSINESS_BADGE',
    icon: 'drive_eta',
    iconBg: 'bg-purple-100 text-purple-700',
    description: 'Phù hiệu xe hợp đồng vận tải do Sở GTVT cấp để đủ điều kiện kinh doanh cho thuê.',
    expiryHint: 'Bắt buộc nhập ngày hết hạn ghi trên phù hiệu'
  }
]

export default function ProviderVehicleHubPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const fileInputRef = useRef(null)

  const tabParam = searchParams.get('tab')
  const activeTab = (tabParam === 'documents' || tabParam === 'pricing') ? tabParam : 'profile'

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState(null)
  const [showRuleModal, setShowRuleModal] = useState(false)
  const [vehicle, setVehicle] = useState(null)
  const [providerInfo, setProviderInfo] = useState({ displayName: '', depotAddress: '' })

  const [documents, setDocuments] = useState([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [docModal, setDocModal] = useState({
    isOpen: false,
    docType: null,
    docNumber: '',
    expiryDate: '',
    fileUrl: '',
    fileName: '',
    uploading: false,
    saving: false
  })

  const [initialData, setInitialData] = useState(null)
  const [form, setForm] = useState({
    licensePlate: '',
    brand: '',
    model: '',
    manufactureYear: 2024,
    seatCount: 5,
    transmission: 'AUTOMATIC',
    fuelType: 'EV',
    color: '',
    maintenanceBufferMinutes: 120,
    coverImageUrl: ''
  })

  const [pricingPlans, setPricingPlans] = useState({
    HOURLY: { status: 'ACTIVE', slotPrice: 120000, hourlyToDailyThresholdMinutes: 480 },
    DAILY: { status: 'ACTIVE', slotPrice: 850000 },
    MONTHLY: { status: 'ACTIVE', slotPrice: 22500000 },
    YEARLY: { status: 'INACTIVE', slotPrice: 240000000, upfrontAmount: 0 }
  })
  const [initialPricingPlans, setInitialPricingPlans] = useState(null)

  const [surcharges, setSurcharges] = useState({
    allowedOvertimeMinutes: 15,
    overtimeSurchargeRatio: 0.15,
    freeRadiusKm: 10,
    outOfRadiusRate: 12000,
    waitingFreeMinutes: 15,
    waitingRate: 2000,
    weekendSurchargeRatio: 0.15
  })
  const [initialSurcharges, setInitialSurcharges] = useState(null)
  const [savingPlans, setSavingPlans] = useState(false)

  useEffect(() => {
    fetchVehicleData()
    fetchDashboardSummary()
  }, [id])

  async function fetchVehicleData() {
    try {
      setLoading(true)
      const data = await providerService.getVehicleById(id)
      setVehicle(data)
      const initialForm = {
        licensePlate: data.licensePlate || '',
        brand: data.brand || '',
        model: data.model || '',
        manufactureYear: data.manufactureYear || 2024,
        seatCount: data.seatCount || 5,
        transmission: data.transmission || 'AUTOMATIC',
        fuelType: data.fuelType || 'EV',
        color: data.color || '',
        maintenanceBufferMinutes: data.maintenanceBufferMinutes || 120,
        coverImageUrl: data.coverImageUrl || ''
      }
      setForm(initialForm)
      setInitialData(initialForm)

      if (data.plans && data.plans.length > 0) {
        const loadedPlans = {
          HOURLY: { status: 'INACTIVE', slotPrice: '', hourlyToDailyThresholdMinutes: 480 },
          DAILY: { status: 'INACTIVE', slotPrice: '' },
          MONTHLY: { status: 'INACTIVE', slotPrice: '' },
          YEARLY: { status: 'INACTIVE', slotPrice: '', upfrontAmount: '' }
        }
        let foundSurcharges = null

        data.plans.forEach(p => {
          if (loadedPlans[p.planType]) {
            loadedPlans[p.planType] = {
              ...loadedPlans[p.planType],
              status: p.status || 'INACTIVE',
              slotPrice: p.slotPrice != null ? Number(p.slotPrice) : '',
              upfrontAmount: p.upfrontAmount != null ? Number(p.upfrontAmount) : '',
              hourlyToDailyThresholdMinutes: p.hourlyToDailyThresholdMinutes != null ? p.hourlyToDailyThresholdMinutes : 480
            }
          }
          if (p.planType !== 'YEARLY' && !foundSurcharges && p.allowedOvertimeMinutes != null) {
            foundSurcharges = {
              allowedOvertimeMinutes: p.allowedOvertimeMinutes ?? 15,
              overtimeSurchargeRatio: p.overtimeSurchargeRatio != null ? Number(p.overtimeSurchargeRatio) : 0.15,
              freeRadiusKm: p.freeRadiusKm ?? 10,
              outOfRadiusRate: p.outOfRadiusRate != null ? Number(p.outOfRadiusRate) : 12000,
              waitingFreeMinutes: p.waitingFreeMinutes ?? 15,
              waitingRate: p.waitingRate != null ? Number(p.waitingRate) : 2000,
              weekendSurchargeRatio: p.weekendSurchargeRatio != null ? Number(p.weekendSurchargeRatio) : 0.15
            }
          }
        })

        setPricingPlans(loadedPlans)
        setInitialPricingPlans(loadedPlans)
        if (foundSurcharges) {
          setSurcharges(foundSurcharges)
          setInitialSurcharges(foundSurcharges)
        }
      }

      // Fetch documents
      try {
        setLoadingDocs(true)
        const docs = await providerService.getVehicleDocuments(id)
        setDocuments(docs || [])
      } catch (e) {
        console.error('Failed to load vehicle documents', e)
      } finally {
        setLoadingDocs(false)
      }
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || err.message || 'Không thể tải thông tin phương tiện'
      })
    } finally {
      setLoading(false)
    }
  }

  function openUploadDocModal(docType, existingDoc = null) {
    setDocModal({
      isOpen: true,
      docType,
      docNumber: existingDoc?.docNumber || '',
      expiryDate: existingDoc?.expiryDate || '',
      fileUrl: existingDoc?.fileUrl || '',
      fileName: existingDoc?.fileUrl ? existingDoc.fileUrl.split('/').pop() : '',
      uploading: false,
      saving: false
    })
  }

  async function handleFileUpload(e) {
    const file = e.target.files?.[0]
    if (!file) return

    if (file.size > 8 * 1024 * 1024) {
      setToast({ type: 'error', message: 'Dung lượng tệp vượt quá 8MB. Vui lòng chọn tệp nhỏ hơn.' })
      e.target.value = ''
      return
    }

    const validTypes = ['image/jpeg', 'image/png', 'application/pdf']
    const ext = file.name.split('.').pop()?.toLowerCase()
    const validExts = ['jpg', 'jpeg', 'png', 'pdf']
    if (!validTypes.includes(file.type) && !validExts.includes(ext)) {
      setToast({ type: 'error', message: 'Hệ thống chỉ chấp nhận hình ảnh JPG, PNG hoặc tài liệu PDF.' })
      e.target.value = ''
      return
    }

    try {
      setDocModal(prev => ({ ...prev, uploading: true }))
      const res = await providerService.uploadDocumentFile(file)
      setDocModal(prev => ({
        ...prev,
        fileUrl: res.fileUrl,
        fileName: res.fileName || file.name,
        uploading: false
      }))
      setToast({ type: 'success', message: 'Tải tệp lên thành công!' })
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || err.message || 'Lỗi khi tải tệp lên'
      })
      setDocModal(prev => ({ ...prev, uploading: false }))
    } finally {
      e.target.value = ''
    }
  }

  async function handleSaveDocument(e) {
    if (e) e.preventDefault()
    if (!docModal.docNumber.trim()) {
      setToast({ type: 'warning', message: 'Vui lòng nhập số hiệu giấy tờ' })
      return
    }
    if (!docModal.fileUrl.trim()) {
      setToast({ type: 'warning', message: 'Vui lòng tải lên tệp đính kèm (JPG, PNG hoặc PDF)' })
      return
    }

    try {
      setDocModal(prev => ({ ...prev, saving: true }))
      const payload = {
        docType: docModal.docType,
        docNumber: docModal.docNumber.trim(),
        expiryDate: docModal.expiryDate ? docModal.expiryDate : null,
        fileUrl: docModal.fileUrl.trim()
      }
      const savedDoc = await providerService.updateVehicleDocument(id, payload)
      setDocuments(prev => {
        const filtered = prev.filter(d => d.docType !== docModal.docType)
        return [...filtered, savedDoc]
      })
      setToast({ type: 'success', message: 'Cập nhật giấy tờ thành công! Hồ sơ đã được đồng bộ.' })
      setDocModal(prev => ({ ...prev, isOpen: false, saving: false }))
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || err.message || 'Lỗi khi lưu giấy tờ'
      })
      setDocModal(prev => ({ ...prev, saving: false }))
    }
  }

  async function fetchDashboardSummary() {
    try {
      const summary = await providerService.getDashboardSummary()
      if (summary) {
        setProviderInfo({
          displayName: summary.displayName || '',
          depotAddress: summary.depotAddress || ''
        })
      }
    } catch {
      // Ignored
    }
  }

  function handleTabChange(tabKey) {
    const newParams = new URLSearchParams(searchParams)
    if (tabKey === 'profile') {
      newParams.delete('tab')
    } else {
      newParams.set('tab', tabKey)
    }
    setSearchParams(newParams, { replace: true })
  }

  const isPlateChanged = initialData && form.licensePlate.trim().toUpperCase() !== initialData.licensePlate.trim().toUpperCase()

  function handlePlateInput(e) {
    setForm(prev => ({ ...prev, licensePlate: e.target.value.toUpperCase() }))
  }

  function resetPlate() {
    if (initialData) {
      setForm(prev => ({ ...prev, licensePlate: initialData.licensePlate }))
      setToast({ type: 'info', message: 'Đã khôi phục biển số ban đầu: ' + initialData.licensePlate })
    }
  }

  function adjustBuffer(delta) {
    setForm(prev => {
      let val = (parseInt(prev.maintenanceBufferMinutes, 10) || 120) + delta
      if (val < 30) val = 30
      if (val > 240) val = 240
      return { ...prev, maintenanceBufferMinutes: val }
    })
  }

  function handleResetForm() {
    if (initialData) {
      setForm({ ...initialData })
      setToast({ type: 'info', message: 'Đã hủy các thay đổi chưa lưu' })
    }
  }

  async function handleSaveProfile(e) {
    if (e) e.preventDefault()
    try {
      setSaving(true)
      const payload = {
        licensePlate: form.licensePlate.trim().toUpperCase(),
        brand: form.brand.trim(),
        model: form.model.trim(),
        manufactureYear: parseInt(form.manufactureYear, 10),
        seatCount: parseInt(form.seatCount, 10),
        transmission: form.transmission,
        fuelType: form.fuelType,
        color: form.color.trim(),
        maintenanceBufferMinutes: parseInt(form.maintenanceBufferMinutes, 10),
        coverImageUrl: form.coverImageUrl.trim()
      }

      const updated = await providerService.updateVehicle(id, payload)
      setVehicle(updated)
      const newInit = {
        licensePlate: updated.licensePlate || '',
        brand: updated.brand || '',
        model: updated.model || '',
        manufactureYear: updated.manufactureYear || 2024,
        seatCount: updated.seatCount || 5,
        transmission: updated.transmission || 'AUTOMATIC',
        fuelType: updated.fuelType || 'EV',
        color: updated.color || '',
        maintenanceBufferMinutes: updated.maintenanceBufferMinutes || 120,
        coverImageUrl: updated.coverImageUrl || ''
      }
      setForm(newInit)
      setInitialData(newInit)

      if (isPlateChanged) {
        setToast({
          type: 'warning',
          message: 'Biển số mới đã gửi: Xe chuyển về trạng thái CHỜ DUYỆT (PENDING)'
        })
      } else {
        setToast({
          type: 'success',
          message: 'Cập nhật thông số Tầng 3 thành công! Dữ liệu đã đồng bộ tức thì.'
        })
      }
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || err.message || 'Lỗi khi cập nhật thông tin xe'
      })
    } finally {
      setSaving(false)
    }
  }

  function formatMoney(amount) {
    if (amount == null || amount === '') return '0'
    return new Intl.NumberFormat('vi-VN').format(amount)
  }

  function togglePlanStatus(planType) {
    setPricingPlans(prev => ({
      ...prev,
      [planType]: {
        ...prev[planType],
        status: prev[planType]?.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
      }
    }))
  }

  function handlePlanPriceChange(planType, value) {
    const num = value === '' ? '' : Math.max(0, parseInt(value, 10) || 0)
    setPricingPlans(prev => ({
      ...prev,
      [planType]: {
        ...prev[planType],
        slotPrice: num
      }
    }))
  }

  function handleHourlyThresholdChange(value) {
    const num = value === '' ? '' : Math.max(1, parseInt(value, 10) || 1)
    setPricingPlans(prev => ({
      ...prev,
      HOURLY: {
        ...prev.HOURLY,
        hourlyToDailyThresholdMinutes: num
      }
    }))
  }

  function handleYearlyUpfrontChange(value) {
    const num = value === '' ? '' : Math.max(0, parseInt(value, 10) || 0)
    setPricingPlans(prev => ({
      ...prev,
      YEARLY: {
        ...prev.YEARLY,
        upfrontAmount: num
      }
    }))
  }

  function handleSurchargeChange(field, value) {
    setSurcharges(prev => ({
      ...prev,
      [field]: value === '' ? '' : (field.includes('Ratio') ? parseFloat(value) : (parseInt(value, 10) || 0))
    }))
  }

  function handleResetPricing() {
    if (initialPricingPlans) setPricingPlans({ ...initialPricingPlans })
    if (initialSurcharges) setSurcharges({ ...initialSurcharges })
    setToast({ type: 'info', message: 'Đã khôi phục cấu hình bảng giá ban đầu' })
  }

  async function handleSavePricing(e) {
    if (e) e.preventDefault()
    try {
      setSavingPlans(true)

      const activeWithoutPrice = ['HOURLY', 'DAILY', 'MONTHLY', 'YEARLY'].filter(
        type => pricingPlans[type]?.status === 'ACTIVE' && (!pricingPlans[type]?.slotPrice || Number(pricingPlans[type]?.slotPrice) <= 0)
      )
      if (activeWithoutPrice.length > 0) {
        const typeLabels = { HOURLY: 'Theo giờ', DAILY: 'Theo ngày', MONTHLY: 'Theo tháng', YEARLY: 'Theo năm' }
        const missingLabels = activeWithoutPrice.map(t => typeLabels[t] || t).join(', ')
        setToast({
          type: 'error',
          message: `Vui lòng nhập đơn giá lớn hơn 0 cho các gói đang kích hoạt (${missingLabels})`
        })
        setSavingPlans(false)
        return
      }

      const payload = {
        plans: [
          {
            planType: 'HOURLY',
            slotPrice: pricingPlans.HOURLY?.slotPrice ? Number(pricingPlans.HOURLY?.slotPrice) : null,
            status: pricingPlans.HOURLY?.status || 'INACTIVE',
            hourlyToDailyThresholdMinutes: Number(pricingPlans.HOURLY?.hourlyToDailyThresholdMinutes) || 480
          },
          {
            planType: 'DAILY',
            slotPrice: pricingPlans.DAILY?.slotPrice ? Number(pricingPlans.DAILY?.slotPrice) : null,
            status: pricingPlans.DAILY?.status || 'INACTIVE'
          },
          {
            planType: 'MONTHLY',
            slotPrice: pricingPlans.MONTHLY?.slotPrice ? Number(pricingPlans.MONTHLY?.slotPrice) : null,
            status: pricingPlans.MONTHLY?.status || 'INACTIVE'
          },
          {
            planType: 'YEARLY',
            slotPrice: pricingPlans.YEARLY?.slotPrice ? Number(pricingPlans.YEARLY?.slotPrice) : null,
            status: pricingPlans.YEARLY?.status || 'INACTIVE',
            upfrontAmount: pricingPlans.YEARLY?.upfrontAmount ? Number(pricingPlans.YEARLY?.upfrontAmount) : 0
          }
        ],
        allowedOvertimeMinutes: Number(surcharges.allowedOvertimeMinutes) || 0,
        overtimeSurchargeRatio: Number(surcharges.overtimeSurchargeRatio) || 0,
        freeRadiusKm: Number(surcharges.freeRadiusKm) || 0,
        outOfRadiusRate: Number(surcharges.outOfRadiusRate) || 0,
        waitingFreeMinutes: Number(surcharges.waitingFreeMinutes) || 0,
        waitingRate: Number(surcharges.waitingRate) || 0,
        weekendSurchargeRatio: Number(surcharges.weekendSurchargeRatio) || 0
      }

      const updated = await providerService.updateVehicleRentalPlans(id, payload)
      if (updated) {
        setInitialPricingPlans(pricingPlans)
        setInitialSurcharges(surcharges)
        setToast({
          type: 'success',
          message: 'Cập nhật bảng giá gói cước & phụ phí vận hành thành công!'
        })
      }
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || err.message || 'Không thể lưu cấu hình bảng giá'
      })
    } finally {
      setSavingPlans(false)
    }
  }

  async function handleToggleVisibility() {
    if (!vehicle) return
    const isCurrentlyApproved = vehicle.status === 'APPROVED'
    const newVisible = !isCurrentlyApproved

    try {
      const res = await providerService.toggleVehicleVisibility(vehicle.id, newVisible)
      setVehicle(prev => ({ ...prev, status: res.status }))
      setToast({
        type: 'success',
        message: newVisible ? 'Xe đã mở nhận khách trên hệ thống' : 'Xe đã tạm ẩn, tạm dừng nhận cuốc'
      })
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || err.message || 'Không thể đổi trạng thái ẩn/hiện xe'
      })
    }
  }

  function renderStatusBadge(status) {
    switch (status) {
      case 'APPROVED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>ĐÃ DUYỆT (APPROVED)</span>
          </span>
        )
      case 'PENDING':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse"></span>
            <span>CHỜ DUYỆT (PENDING)</span>
          </span>
        )
      case 'REJECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-rose-500"></span>
            <span>TỪ CHỐI (REJECTED)</span>
          </span>
        )
      case 'HIDDEN':
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-300 shadow-sm">
            <span className="w-2 h-2 rounded-full bg-slate-400"></span>
            <span>TẠM ẨN (HIDDEN)</span>
          </span>
        )
      default:
        return null
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-[#f8f9ff] text-slate-800 font-sans flex flex-col">
        <ProviderHeader activeTab="vehicles" providerName={providerInfo.displayName} depotAddress={providerInfo.depotAddress} />
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16 flex flex-col items-center justify-center flex-1">
          <div className="w-12 h-12 rounded-full border-4 border-emerald-200 border-t-emerald-600 animate-spin mb-4"></div>
          <p className="text-slate-600 font-medium text-sm">Đang tải hồ sơ phương tiện...</p>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[#f8f9ff] text-slate-800 font-sans flex flex-col pb-32 antialiased">
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* 1. Provider Top Navigation Bar */}
      <ProviderHeader
        activeTab="vehicles"
        providerName={providerInfo.displayName}
        depotAddress={providerInfo.depotAddress}
      />

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 w-full flex-1">
        {/* Breadcrumb */}
        <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-xs text-slate-500 mb-4">
          <Link to="/provider/vehicles" className="hover:text-emerald-700 transition-colors flex items-center gap-1">
            <span className="material-symbols-outlined text-sm">home</span>
            Đội xe của tôi
          </Link>
          <span className="material-symbols-outlined text-xs text-slate-400">chevron_right</span>
          <span className="text-slate-800 font-medium">{form.brand} {form.model}</span>
          <span className="text-slate-400">/</span>
          <span className="text-emerald-800 font-semibold px-2 py-0.5 rounded bg-emerald-50 border border-emerald-200 font-mono">
            {form.licensePlate || 'Chưa có biển'}
          </span>
        </nav>

        {/* 2. Header Summary Card */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 sm:p-6 mb-6">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-5">
            {/* Left: Car identity */}
            <div className="flex items-start sm:items-center gap-4">
              <div className="relative w-16 h-16 sm:w-20 sm:h-20 rounded-xl bg-slate-100 border border-slate-200 overflow-hidden flex-shrink-0 flex items-center justify-center group shadow-sm">
                <img
                  src={form.coverImageUrl || 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80'}
                  alt={form.model}
                  className="w-full h-full object-cover"
                  onError={(e) => {
                    e.target.onerror = null
                    e.target.src = 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80'
                  }}
                />
                <span className="absolute bottom-1 right-1 bg-emerald-700 text-white text-[9px] px-1 py-0.5 rounded font-mono">16:9</span>
              </div>
              <div>
                <div className="flex flex-wrap items-center gap-2.5 mb-1.5">
                  <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                    {form.brand} {form.model} {form.manufactureYear}
                  </h1>
                  {renderStatusBadge(vehicle?.status)}
                  {vehicle?.status === 'APPROVED' ? (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">
                      <span className="material-symbols-outlined text-xs">radar</span>
                      Đang nhận khách
                    </span>
                  ) : vehicle?.status === 'HIDDEN' ? (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-300">
                      <span className="material-symbols-outlined text-xs">visibility_off</span>
                      Đang tạm ẩn
                    </span>
                  ) : null}
                </div>
                <div className="flex flex-wrap items-center gap-y-1 gap-x-4 text-xs text-slate-500">
                  <span className="flex items-center gap-1 font-mono text-slate-600 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                    <span className="material-symbols-outlined text-xs text-slate-400">fingerprint</span>
                    Mã định danh: <strong className="text-slate-800">VEH-{vehicle?.id}</strong>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-xs text-emerald-600">
                      {form.fuelType === 'EV' && 'electric_bolt'}
                      {form.fuelType === 'GASOLINE' && 'local_gas_station'}
                      {form.fuelType === 'DIESEL' && 'oil_barrel'}
                      {form.fuelType === 'HYBRID' && 'battery_charging_full'}
                    </span>
                    {form.fuelType === 'EV' && 'Điện EV'}
                    {form.fuelType === 'GASOLINE' && 'Xăng'}
                    {form.fuelType === 'DIESEL' && 'Dầu'}
                    {form.fuelType === 'HYBRID' && 'Hybrid'}
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-xs text-slate-400">event</span>
                    Đăng kiểm: <span className="text-amber-700 font-medium">Hợp lệ</span>
                  </span>
                </div>
              </div>
            </div>

            {/* Right: Quick actions */}
            <div className="flex flex-wrap items-center gap-2.5 pt-3 lg:pt-0 border-t lg:border-t-0 border-slate-100">
              <button
                type="button"
                onClick={handleToggleVisibility}
                disabled={vehicle?.status === 'PENDING' || vehicle?.status === 'REJECTED'}
                className="px-3.5 py-2 text-xs font-semibold text-slate-700 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-xl transition-all flex items-center gap-1.5 border border-slate-200 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <span className="material-symbols-outlined text-sm">
                  {vehicle?.status === 'HIDDEN' ? 'visibility' : 'visibility_off'}
                </span>
                <span>{vehicle?.status === 'HIDDEN' ? 'Mở nhận khách' : 'Tạm ẩn xe'}</span>
              </button>
              <button
                type="button"
                onClick={() => setShowRuleModal(true)}
                className="px-3.5 py-2 text-xs font-medium text-slate-600 hover:text-emerald-700 hover:bg-slate-50 border border-slate-200 rounded-xl transition-colors flex items-center gap-1"
              >
                <span className="material-symbols-outlined text-sm text-emerald-600">help_outline</span>
                <span className="hidden sm:inline">Quy tắc 3 tầng</span>
              </button>
            </div>
          </div>
        </div>

        {/* 3. Tabbed Navigation Bar (Synced with URL ?tab=...) */}
        <div className="border-b border-slate-200 mb-6 bg-white rounded-xl px-3 pt-2 shadow-sm">
          <div className="flex space-x-2 sm:space-x-4 overflow-x-auto">
            <button
              type="button"
              onClick={() => handleTabChange('profile')}
              className={`px-4 py-3 text-sm font-semibold flex items-center gap-2 whitespace-nowrap transition-all border-b-2 ${activeTab === 'profile'
                  ? 'text-emerald-700 border-emerald-600'
                  : 'text-slate-500 border-transparent hover:text-slate-800'
                }`}
            >
              <span className="material-symbols-outlined text-lg">description</span>
              <span>Tab 1: Hồ sơ & Thông số xe (3 Tầng)</span>
              <span className="px-1.5 py-0.5 text-[10px] rounded bg-emerald-100 text-emerald-800 font-mono">v10.1</span>
            </button>

            <button
              type="button"
              onClick={() => handleTabChange('documents')}
              className={`px-4 py-3 text-sm font-semibold flex items-center gap-2 whitespace-nowrap transition-all border-b-2 ${activeTab === 'documents'
                  ? 'text-emerald-700 border-emerald-600'
                  : 'text-slate-500 border-transparent hover:text-slate-800'
                }`}
            >
              <span className="material-symbols-outlined text-lg">folder_shared</span>
              <span>Tab 2: Giấy tờ pháp lý</span>
              <span className="px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[11px] font-mono font-bold">
                {documents.length}/{DOC_SPECS.length}
              </span>
            </button>

            <button
              type="button"
              onClick={() => handleTabChange('pricing')}
              className={`px-4 py-3 text-sm font-semibold flex items-center gap-2 whitespace-nowrap transition-all border-b-2 ${activeTab === 'pricing'
                  ? 'text-emerald-700 border-emerald-600'
                  : 'text-slate-500 border-transparent hover:text-slate-800'
                }`}
            >
              <span className="material-symbols-outlined text-lg">payments</span>
              <span>Tab 3: Bảng giá & Gói cước (Rental Plans)</span>
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
            </button>
          </div>
        </div>

        {/* ======================================================== */}
        {/* TAB 1: HỒ SƠ XE THEO KIẾN TRÚC 3 TẦNG v10.1               */}
        {/* ======================================================== */}
        {activeTab === 'profile' && (
          <section className="space-y-6">
            {/* Rule Banner */}
            <div className="rounded-2xl bg-gradient-to-r from-emerald-50 via-teal-50 to-white border border-emerald-200 p-4 sm:p-5 flex flex-col md:flex-row items-start md:items-center justify-between gap-4 shadow-sm">
              <div className="flex items-start gap-3.5">
                <div className="w-10 h-10 rounded-xl bg-white text-emerald-700 border border-emerald-200 flex items-center justify-center flex-shrink-0 shadow-sm">
                  <span className="material-symbols-outlined text-2xl">verified</span>
                </div>
                <div>
                  <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    Kiến trúc Cập nhật Hồ sơ 3 Tầng (3-Tier Legal Rules)
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800">Quy chuẩn v10.1</span>
                  </h2>
                  <p className="text-xs text-slate-600 mt-0.5 leading-relaxed">
                    Phân tách rủi ro pháp lý giữa <strong>Tầng 1 (Bất biến)</strong>, <strong>Tầng 2 (Tái kiểm duyệt)</strong> và <strong>Tầng 3 (Tự do vận hành)</strong> nhằm bảo vệ pháp lý trọn đời xe.
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowRuleModal(true)}
                className="text-xs font-semibold text-emerald-700 hover:text-emerald-900 bg-white hover:bg-emerald-50 border border-emerald-300 px-3.5 py-2 rounded-xl transition-all shadow-sm flex items-center gap-1.5 whitespace-nowrap flex-shrink-0"
              >
                <span className="material-symbols-outlined text-sm">info</span>
                Xem chi tiết quy chuẩn
              </button>
            </div>

            {/* TẦNG 1: Định danh vật lý (Physical Identity) */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 sm:p-6 transition-all">
              <div className="flex flex-wrap items-center justify-between gap-2 pb-4 mb-4 border-b border-slate-100">
                <div className="flex items-center gap-3">
                  <span className="w-8 h-8 rounded-lg bg-slate-100 text-slate-700 font-bold text-sm flex items-center justify-center border border-slate-200">01</span>
                  <div>
                    <h3 className="text-base font-bold text-slate-900 tracking-tight">TẦNG 1: Định danh vật lý</h3>
                    <p className="text-xs text-slate-500">Gắn liền với thân vỏ kim loại và cụm mô tơ truyền động gốc.</p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-300">
                  <span className="material-symbols-outlined text-xs">lock</span>
                  BẤT BIẾN SAU KHI TẠO
                </span>
              </div>

              <div className="mb-5 p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600 flex items-start gap-2.5">
                <span className="material-symbols-outlined text-slate-500 text-base flex-shrink-0 mt-0.5">policy</span>
                <p className="leading-relaxed">
                  <strong>Quy định bất biến:</strong> Số khung và Số máy đã được đăng ký và khóa cứng vĩnh viễn trên CSDL EcoMotion VN. Mọi sai lệch kỹ thuật thực tế yêu cầu lập biên bản giám định hiện trường cùng Cục Đăng kiểm.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* VIN / Chassis Number */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-bold text-slate-700 flex items-center gap-1">
                      Số khung (VIN / Chassis Number) <span className="text-rose-500">*</span>
                    </label>
                    <span className="text-[11px] text-slate-400 flex items-center gap-1">
                      <span className="material-symbols-outlined text-xs">lock</span> Đã khóa bất biến
                    </span>
                  </div>
                  <div className="relative">
                    <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-base">lock</span>
                    <input
                      type="text"
                      disabled
                      readOnly
                      value={vehicle?.chassisNo || ''}
                      className="w-full pl-10 pr-10 py-2.5 bg-slate-100/80 border border-slate-200 rounded-xl text-slate-700 font-mono text-sm cursor-not-allowed select-all focus:outline-none"
                    />
                  </div>
                  <p className="text-[11px] text-slate-400 mt-1">Đã khóa bất biến theo hồ sơ đăng kiểm gốc</p>
                </div>

                {/* Engine / Motor Number */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-bold text-slate-700 flex items-center gap-1">
                      Số máy / Mã mô tơ điện (Engine / Motor No.) <span className="text-rose-500">*</span>
                    </label>
                    <span className="text-[11px] text-slate-400 flex items-center gap-1">
                      <span className="material-symbols-outlined text-xs">lock</span> Đã khóa bất biến
                    </span>
                  </div>
                  <div className="relative">
                    <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-base">lock</span>
                    <input
                      type="text"
                      disabled
                      readOnly
                      value={vehicle?.engineNo || ''}
                      className="w-full pl-10 pr-10 py-2.5 bg-slate-100/80 border border-slate-200 rounded-xl text-slate-700 font-mono text-sm cursor-not-allowed select-all focus:outline-none"
                    />
                  </div>
                  <p className="text-[11px] text-slate-400 mt-1">Đã khóa bất biến theo bàn giao chính hãng</p>
                </div>
              </div>
            </div>

            {/* TẦNG 2: Định danh pháp lý (Legal Identity) */}
            <div className="bg-white rounded-2xl border-2 border-amber-300/80 shadow-sm p-5 sm:p-6 transition-all relative overflow-hidden">
              <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-amber-400 to-amber-500"></div>
              <div className="flex flex-wrap items-center justify-between gap-2 pb-4 mb-4 border-b border-slate-100">
                <div className="flex items-center gap-3">
                  <span className="w-8 h-8 rounded-lg bg-amber-100 text-amber-800 font-bold text-sm flex items-center justify-center border border-amber-300">02</span>
                  <div>
                    <h3 className="text-base font-bold text-slate-900 tracking-tight">TẦNG 2: Định danh pháp lý</h3>
                    <p className="text-xs text-slate-500">Biển số đăng ký lưu hành giao thông đường bộ.</p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                  <span className="material-symbols-outlined text-xs">warning</span>
                  DUYỆT LẠI KHI THAY ĐỔI
                </span>
              </div>

              <div className="mb-5 p-3.5 rounded-xl bg-amber-50/70 border border-amber-200 text-xs text-amber-900 flex items-start gap-2.5">
                <span className="material-symbols-outlined text-amber-600 text-base flex-shrink-0 mt-0.5">verified_user</span>
                <p className="leading-relaxed">
                  <strong>Quy chuẩn xét duyệt:</strong> Khi chỉnh sửa Biển số xe kiểm soát, phương tiện sẽ tự động chuyển sang trạng thái <strong>CHỜ DUYỆT (PENDING)</strong> để Chuyên viên kiểm duyệt hồ sơ cà-vẹt trước khi đưa vào vận hành thực tế.
                </p>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
                {/* Left: Input */}
                <div className="lg:col-span-6 space-y-3">
                  <div>
                    <label className="text-xs font-bold text-slate-700 block mb-1.5" htmlFor="plate-input">
                      Biển số xe kiểm soát <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative">
                      <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-amber-600 text-base">badge</span>
                      <input
                        id="plate-input"
                        type="text"
                        value={form.licensePlate}
                        onChange={handlePlateInput}
                        placeholder="VD: 51K-334.12"
                        className="w-full pl-10 pr-12 py-3 bg-white border-2 border-amber-300 rounded-xl text-slate-900 font-bold text-base focus:border-amber-500 focus:ring-4 focus:ring-amber-100 transition-all uppercase tracking-wider"
                      />
                      <button
                        type="button"
                        onClick={resetPlate}
                        title="Khôi phục biển gốc"
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400 hover:text-slate-600 p-1"
                      >
                        <span className="material-symbols-outlined text-base">history</span>
                      </button>
                    </div>
                  </div>

                  {/* Warning chip if changed */}
                  {isPlateChanged && (
                    <div className="p-3 rounded-xl bg-amber-100/70 border border-amber-300 text-xs text-amber-900 flex items-start gap-2">
                      <span className="material-symbols-outlined text-amber-700 text-base flex-shrink-0">report</span>
                      <div>
                        <strong className="font-bold">⚠️ Biển mới: Sẽ chuyển về CHỜ DUYỆT (PENDING)</strong>
                        <p className="text-[11px] text-amber-800 mt-0.5">Xe sẽ tạm dừng nhận khách cho đến khi Quản trị viên duyệt lại hồ sơ biển số mới.</p>
                      </div>
                    </div>
                  )}

                  <p className="text-[11px] text-slate-500 flex items-center gap-1">
                    <span className="material-symbols-outlined text-xs text-slate-400">check_circle</span>
                    Quy chuẩn định dạng: [Mã tỉnh][Seri]-[Số thứ tự] (VD: 51K-334.12 hoặc 30E-123.45)
                  </p>
                </div>

                {/* Right: Live Preview VN License Plate */}
                <div className="lg:col-span-6 bg-slate-50/80 p-5 rounded-2xl border border-slate-200 flex flex-col items-center justify-center">
                  <span className="text-xs font-semibold text-slate-500 mb-3 self-start">Mô phỏng biển số thực tế (Live Preview):</span>
                  <div className="vn-plate w-64 sm:w-72 h-24 rounded-2xl px-4 py-2 flex flex-col items-center justify-center relative select-none bg-gradient-to-b from-white to-slate-100 border-[3.5px] border-slate-800 shadow-md">
                    {/* Screws */}
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-300 border border-slate-500 absolute top-2 left-3 shadow-inner"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-300 border border-slate-500 absolute top-2 right-3 shadow-inner"></span>
                    {/* Vietnam flag */}
                    <div className="flex items-center gap-1.5 mb-0.5">
                      <svg className="w-4 h-3 rounded-[1px] shadow-sm" viewBox="0 0 30 20">
                        <rect fill="#da251d" width="30" height="20" />
                        <polygon fill="#ffff00" points="15,4 16.5,8.5 21,8.5 17.5,11.5 19,16 15,13 11,16 12.5,11.5 9,8.5 13.5,8.5" />
                      </svg>
                      <span className="text-[9px] font-bold tracking-widest text-slate-700 font-mono">VIETNAM</span>
                    </div>
                    {/* License Plate number */}
                    <div className="text-xl sm:text-2xl font-black font-mono tracking-widest text-slate-900 drop-shadow-[0_1px_1px_rgba(255,255,255,1)]">
                      {form.licensePlate || '51K - XXXXX'}
                    </div>
                    {/* Bottom accent stripe */}
                    <div className="absolute bottom-1 left-4 right-4 h-[3px] bg-emerald-600 rounded-full"></div>
                  </div>
                  <div className="mt-3 flex items-center gap-1 text-[11px] text-emerald-700 font-medium">
                    <span className="material-symbols-outlined text-xs">
                      {isPlateChanged ? 'published_with_changes' : 'check_circle'}
                    </span>
                    <span>
                      {isPlateChanged ? 'Biển số mới chờ xác minh hồ sơ' : 'Trùng khớp giấy đăng ký xe gốc'}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* TẦNG 3: Thông số kỹ thuật & Vận hành (Operational Info) */}
            <div className="bg-white rounded-2xl border border-emerald-200 shadow-sm p-5 sm:p-6 transition-all">
              <div className="flex flex-wrap items-center justify-between gap-2 pb-4 mb-4 border-b border-slate-100">
                <div className="flex items-center gap-3">
                  <span className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 font-bold text-sm flex items-center justify-center border border-emerald-300">03</span>
                  <div>
                    <h3 className="text-base font-bold text-slate-900 tracking-tight">TẦNG 3: Thông tin mô tả & Vận hành</h3>
                    <p className="text-xs text-slate-500">Cấu hình trải nghiệm người dùng, hình ảnh thương mại & thời gian đệm trạm depot.</p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                  <span className="material-symbols-outlined text-xs">auto_mode</span>
                  ✦ TỰ DO CẬP NHẬT (KHÔNG CẦN DUYỆT LẠI)
                </span>
              </div>

              <div className="mb-5 p-3.5 rounded-xl bg-emerald-50/60 border border-emerald-200 text-xs text-emerald-900 flex items-start gap-2.5">
                <span className="material-symbols-outlined text-emerald-600 text-base flex-shrink-0 mt-0.5">auto_awesome</span>
                <p className="leading-relaxed">
                  <strong>Đặc quyền Tầng 3:</strong> Các thông số dưới đây (hãng, dòng xe, màu sơn, ảnh bìa, buffer bảo trì) có thể cập nhật linh hoạt bất kỳ lúc nào mà không làm gián đoạn trạng thái hoạt động của xe.
                </p>
              </div>

              {/* 4 Cột: Hãng, Model, Năm SX, Số chỗ */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1.5">
                    Hãng xe (Brand) <span className="text-rose-500">*</span>
                  </label>
                  <div className="relative">
                    <select
                      value={form.brand}
                      onChange={(e) => setForm(prev => ({ ...prev, brand: e.target.value }))}
                      className="w-full px-3 py-2.5 bg-white border border-slate-200 rounded-xl text-slate-800 text-xs font-medium focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100 transition-all appearance-none cursor-pointer"
                    >
                      <option value="VinFast">VinFast (Việt Nam)</option>
                      <option value="Hyundai">Hyundai</option>
                      <option value="Toyota">Toyota</option>
                      <option value="Tesla">Tesla</option>
                      <option value="BMW">BMW</option>
                      <option value="Mercedes-Benz">Mercedes-Benz</option>
                      <option value="Kia">Kia</option>
                      <option value="Mazda">Mazda</option>
                      <option value="Honda">Honda</option>
                    </select>
                    <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 text-base pointer-events-none">expand_more</span>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1.5">
                    Dòng xe (Model) <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={form.model}
                    onChange={(e) => setForm(prev => ({ ...prev, model: e.target.value }))}
                    className="w-full px-3 py-2.5 bg-white border border-slate-200 rounded-xl text-slate-800 text-xs font-medium focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100 transition-all"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1.5">
                    Năm sản xuất <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="number"
                    min="2015"
                    max="2026"
                    value={form.manufactureYear}
                    onChange={(e) => setForm(prev => ({ ...prev, manufactureYear: e.target.value }))}
                    className="w-full px-3 py-2.5 bg-white border border-slate-200 rounded-xl text-slate-800 text-xs font-medium focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100 transition-all"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1.5">
                    Số chỗ ngồi <span className="text-rose-500">*</span>
                  </label>
                  <div className="relative">
                    <select
                      value={form.seatCount}
                      onChange={(e) => setForm(prev => ({ ...prev, seatCount: e.target.value }))}
                      className="w-full px-3 py-2.5 bg-white border border-slate-200 rounded-xl text-slate-800 text-xs font-medium focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100 transition-all appearance-none cursor-pointer"
                    >
                      <option value="4">4 chỗ</option>
                      <option value="5">5 chỗ</option>
                      <option value="7">7 chỗ</option>
                      <option value="8">8 chỗ</option>
                    </select>
                    <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 text-base pointer-events-none">airline_seat_recline_normal</span>
                  </div>
                </div>
              </div>

              {/* Hộp số, Nhiên liệu & Màu sắc */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-5 p-4 bg-slate-50/70 rounded-xl border border-slate-200/80">
                {/* Hộp số */}
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-2">Hộp số truyền động <span className="text-rose-500">*</span></label>
                  <div className="grid grid-cols-2 gap-2">
                    <label className={`flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold cursor-pointer transition-all ${form.transmission === 'AUTOMATIC'
                        ? 'bg-emerald-50 border-2 border-emerald-600 text-emerald-800'
                        : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 font-medium'
                      }`}>
                      <input
                        type="radio"
                        name="transmission"
                        value="AUTOMATIC"
                        checked={form.transmission === 'AUTOMATIC'}
                        onChange={(e) => setForm(prev => ({ ...prev, transmission: e.target.value }))}
                        className="hidden"
                      />
                      <span className="material-symbols-outlined text-sm">auto_transmission</span>
                      Tự động (AT)
                    </label>

                    <label className={`flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold cursor-pointer transition-all ${form.transmission === 'MANUAL'
                        ? 'bg-emerald-50 border-2 border-emerald-600 text-emerald-800'
                        : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 font-medium'
                      }`}>
                      <input
                        type="radio"
                        name="transmission"
                        value="MANUAL"
                        checked={form.transmission === 'MANUAL'}
                        onChange={(e) => setForm(prev => ({ ...prev, transmission: e.target.value }))}
                        className="hidden"
                      />
                      <span className="material-symbols-outlined text-sm">settings</span>
                      Số sàn (MT)
                    </label>
                  </div>
                </div>

                {/* Nhiên liệu */}
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-2">Loại nhiên liệu <span className="text-rose-500">*</span></label>
                  <div className="grid grid-cols-4 gap-1.5">
                    {[
                      { key: 'EV', label: 'Điện EV', icon: 'electric_bolt' },
                      { key: 'GASOLINE', label: 'Xăng', icon: 'local_gas_station' },
                      { key: 'DIESEL', label: 'Dầu', icon: 'oil_barrel' },
                      { key: 'HYBRID', label: 'Hybrid', icon: 'battery_charging_full' }
                    ].map(f => (
                      <label
                        key={f.key}
                        className={`flex flex-col items-center justify-center p-2 rounded-lg text-[11px] cursor-pointer transition-all ${form.fuelType === f.key
                            ? 'bg-emerald-50 border-2 border-emerald-600 text-emerald-800 font-bold'
                            : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 font-medium'
                          }`}
                      >
                        <input
                          type="radio"
                          name="fuel"
                          value={f.key}
                          checked={form.fuelType === f.key}
                          onChange={(e) => setForm(prev => ({ ...prev, fuelType: e.target.value }))}
                          className="hidden"
                        />
                        <span className="material-symbols-outlined text-sm">{f.icon}</span>
                        {f.label}
                      </label>
                    ))}
                  </div>
                </div>

                {/* Màu sơn */}
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-2">Màu sắc ngoại thất <span className="text-rose-500">*</span></label>
                  <div className="flex items-center gap-2.5">
                    <div className="relative flex items-center gap-2 flex-1 px-3 py-2 bg-white border border-slate-200 rounded-lg">
                      <input
                        type="text"
                        value={form.color}
                        onChange={(e) => setForm(prev => ({ ...prev, color: e.target.value }))}
                        className="text-xs text-slate-800 font-medium w-full focus:outline-none"
                        placeholder="VD: Trắng Ngọc Trai / Đen"
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Maintenance Buffer Stepper (+/- 15 mins) */}
              <div className="mb-5 p-4 rounded-xl bg-emerald-50/50 border border-emerald-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                <div className="flex items-start gap-3">
                  <div className="w-9 h-9 rounded-lg bg-white text-emerald-700 border border-emerald-200 flex items-center justify-center flex-shrink-0 shadow-sm">
                    <span className="material-symbols-outlined text-xl">timer</span>
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-slate-900">Khoảng đệm bảo trì giữa các chuyến (Maintenance Buffer) <span className="text-rose-500">*</span></span>
                      <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800">Đặc quyền EcoMotion</span>
                    </div>
                    <p className="text-xs text-slate-600 mt-0.5">
                      Khoảng thời gian hệ thống tự động khóa lịch giữa 2 lượt khách đặt liên tiếp để nhà xe vệ sinh nội thất, sạc pin và kiểm tra xe tại trạm Depot.
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 bg-white px-3 py-1.5 rounded-xl border border-slate-200 shadow-sm flex-shrink-0">
                  <button
                    type="button"
                    onClick={() => adjustBuffer(-15)}
                    className="w-8 h-8 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center font-bold text-sm transition-colors"
                  >
                    -
                  </button>
                  <div className="text-center min-w-[70px]">
                    <span className="text-base font-bold text-slate-900">{form.maintenanceBufferMinutes}</span>
                    <span className="text-[11px] text-slate-500 block -mt-1">phút</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => adjustBuffer(15)}
                    className="w-8 h-8 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center font-bold text-sm transition-colors"
                  >
                    +
                  </button>
                </div>
              </div>

              {/* Cover Image 16:9 */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold text-slate-700">Ảnh bìa xe chính thức (Hero Display Image) <span className="text-rose-500">*</span></label>
                  <span className="text-[11px] text-slate-400">Bảng &apos;vehicle_image&apos; (display_order = 0)</span>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-stretch">
                  <div className="md:col-span-5 relative rounded-xl overflow-hidden border border-slate-200 bg-slate-900 aspect-video shadow-sm group">
                    <img
                      src={form.coverImageUrl || 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=600&q=80'}
                      alt={form.model}
                      className="w-full h-full object-cover"
                      onError={(e) => {
                        e.target.onerror = null
                        e.target.src = 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=600&q=80'
                      }}
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent"></div>
                    <span className="absolute top-2.5 left-2.5 px-2 py-0.5 rounded bg-black/60 backdrop-blur-sm text-white text-[10px] font-semibold flex items-center gap-1 border border-white/20">
                      <span className="material-symbols-outlined text-xs">photo_camera</span>
                      ẢNH BÌA CHÍNH THỨC · 16:9
                    </span>
                    <div className="absolute bottom-2.5 left-2.5 right-2.5 text-white">
                      <div className="text-xs font-bold">{form.brand} {form.model} {form.manufactureYear}</div>
                      <div className="text-[10px] text-slate-300">Hiển thị làm ảnh đại diện trên ứng dụng thuê xe</div>
                    </div>
                  </div>

                  <div className="md:col-span-7 flex flex-col justify-center space-y-2 p-4 bg-slate-50/70 border border-slate-200 rounded-xl">
                    <label className="text-xs font-bold text-slate-700">Đường dẫn ảnh bìa (Image URL):</label>
                    <input
                      type="url"
                      value={form.coverImageUrl}
                      onChange={(e) => setForm(prev => ({ ...prev, coverImageUrl: e.target.value }))}
                      placeholder="https://example.com/car-image.jpg"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs font-mono text-slate-800 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500"
                    />
                    <p className="text-[11px] text-slate-500">
                      Hỗ trợ định dạng WEBP, PNG, JPG. Khuyến nghị ảnh chụp thực tế góc 3/4 mặt trước với tỷ lệ 16:9.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </section>
        )}

        {/* ======================================================== */}
        {/* TAB 2: GIẤY TỜ PHÁP LÝ (VEHICLE DOCUMENTS)                 */}
        {/* ======================================================== */}
        {activeTab === 'documents' && (
          <section className="space-y-6">
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 sm:p-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
                <div>
                  <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    Bộ Hồ Sơ Pháp Lý Phương Tiện
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800">
                      {DOC_SPECS.length} Giấy tờ quy chuẩn
                    </span>
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Hệ thống lưu trữ tài liệu máy chủ bảo mật. Hỗ trợ tệp hình ảnh (JPG, PNG) và tệp PDF. Nhấp vào tệp đính kèm để mở toàn màn hình xem trên tab mới.
                  </p>
                </div>
              </div>

              {/* Document Cards Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mt-5">
                {DOC_SPECS.map(spec => {
                  const doc = documents.find(d => d.docType === spec.type)
                  const isPdf = doc?.fileUrl?.toLowerCase().endsWith('.pdf')
                  const fileName = doc?.fileUrl ? doc.fileUrl.split('/').pop() : ''

                  return (
                    <div
                      key={spec.type}
                      className="bg-slate-50 rounded-xl border border-slate-200 p-4 hover:border-emerald-300 transition-all flex flex-col justify-between"
                    >
                      <div>
                        {/* Header: Icon & Status */}
                        <div className="flex items-start justify-between gap-2 mb-3">
                          <div className="flex items-center gap-2.5">
                            <div className={`w-9 h-9 rounded-lg ${spec.iconBg} flex items-center justify-center font-bold text-sm shadow-xs`}>
                              <span className="material-symbols-outlined text-lg">{spec.icon}</span>
                            </div>
                            <div>
                              <h4 className="text-xs font-bold text-slate-900">{spec.title}</h4>
                              <span className="text-[11px] text-slate-500 font-mono">{spec.code}</span>
                            </div>
                          </div>

                          {doc ? (
                            doc.status === 'VERIFIED' ? (
                              <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1">
                                <span className="material-symbols-outlined text-xs">verified</span> Đã xác thực
                              </span>
                            ) : doc.status === 'PENDING' ? (
                              <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200 flex items-center gap-1">
                                <span className="material-symbols-outlined text-xs">hourglass_top</span> Chờ duyệt
                              </span>
                            ) : (
                              <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200 flex items-center gap-1">
                                <span className="material-symbols-outlined text-xs">cancel</span> Từ chối
                              </span>
                            )
                          ) : (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-slate-100 text-slate-500 border border-slate-200 flex items-center gap-1">
                              <span className="material-symbols-outlined text-xs">pending</span> Chưa có
                            </span>
                          )}
                        </div>

                        {/* Body Details */}
                        {doc ? (
                          <div className="space-y-2 text-xs text-slate-600 mb-4 bg-white p-3 rounded-lg border border-slate-200/80 shadow-xs">
                            <div className="flex justify-between items-center">
                              <span className="text-slate-400">Số hiệu văn bản:</span>
                              <span className="font-mono font-semibold text-slate-800">{doc.docNumber}</span>
                            </div>
                            <div className="flex justify-between items-center">
                              <span className="text-slate-400">Ngày hết hạn:</span>
                              <span className="font-medium text-slate-700">
                                {doc.expiryDate ? new Date(doc.expiryDate).toLocaleDateString('vi-VN') : 'Vô thời hạn'}
                              </span>
                            </div>
                            <div className="flex flex-col gap-1.5 pt-2 border-t border-slate-100">
                              <span className="text-slate-400 text-[11px]">Tệp đính kèm:</span>
                              <button
                                type="button"
                                onClick={() => window.open(doc.fileUrl, '_blank', 'noopener,noreferrer')}
                                title="Click để mở xem tài liệu trên tab mới"
                                className="inline-flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-emerald-50/80 hover:bg-emerald-100/90 text-emerald-800 border border-emerald-200 transition-all text-xs font-medium group cursor-pointer"
                              >
                                <span className="inline-flex items-center gap-1.5 min-w-0">
                                  <span className={`material-symbols-outlined text-base ${isPdf ? 'text-rose-600' : 'text-emerald-600'}`}>
                                    {isPdf ? 'picture_as_pdf' : 'image'}
                                  </span>
                                  <span className="truncate max-w-[140px] font-mono text-[11px] text-slate-700 group-hover:text-emerald-950 font-semibold">
                                    {fileName}
                                  </span>
                                </span>
                                <span className="inline-flex items-center gap-0.5 text-[10px] text-emerald-700 font-bold group-hover:text-emerald-900 bg-white/70 px-1.5 py-0.5 rounded border border-emerald-200/60 ml-1">
                                  Mở tab mới
                                  <span className="material-symbols-outlined text-[12px] group-hover:translate-x-0.5 transition-transform">
                                    open_in_new
                                  </span>
                                </span>
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="mb-4 bg-amber-50/50 p-3.5 rounded-lg border border-amber-200 text-xs text-amber-800 flex items-start gap-2">
                            <span className="material-symbols-outlined text-amber-600 text-base mt-0.5 flex-shrink-0">info</span>
                            <p className="leading-relaxed">
                              {spec.description}
                            </p>
                          </div>
                        )}
                      </div>

                      {/* Footer Actions */}
                      <div className="flex items-center justify-between pt-2 border-t border-slate-200/70">
                        {doc ? (
                          <>
                            <span className="text-[11px] text-slate-500 flex items-center gap-1">
                              <span className="material-symbols-outlined text-xs text-emerald-600">check_circle</span>
                              {doc.status === 'VERIFIED' ? 'Đã duyệt hợp lệ' : 'Đã nộp hồ sơ'}
                            </span>
                            <button
                              type="button"
                              onClick={() => openUploadDocModal(spec.type, doc)}
                              className="text-xs font-semibold text-emerald-700 hover:text-emerald-900 hover:underline flex items-center gap-1"
                            >
                              <span className="material-symbols-outlined text-xs">edit</span>
                              Cập nhật bản mới
                            </button>
                          </>
                        ) : (
                          <>
                            <span className="text-[11px] text-amber-600 font-medium">Bắt buộc bổ sung</span>
                            <button
                              type="button"
                              onClick={() => openUploadDocModal(spec.type)}
                              className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-xs flex items-center gap-1 transition-colors"
                            >
                              <span className="material-symbols-outlined text-xs">upload</span>
                              Tải lên ngay
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </section>
        )}

        {/* ======================================================== */}
        {/* TAB 3: BẢNG GIÁ & GÓI CƯỚC (RENTAL PLANS)                  */}
        {/* ======================================================== */}
        {activeTab === 'pricing' && (
          <section className="space-y-6">
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 sm:p-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
                <div>
                  <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <span className="material-symbols-outlined text-emerald-600">payments</span>
                    Bảng Cấu Hình Gói Giá Thuê & Phụ Phí Vận Hành
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Tùy chỉnh đơn giá niêm yết, bật/tắt từng gói thuê và điều chỉnh các định mức phụ phí cho xe <strong>{form.licensePlate}</strong>.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleResetPricing}
                    disabled={savingPlans}
                    className="px-3.5 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors border border-slate-200"
                  >
                    Khôi phục
                  </button>
                  <button
                    type="button"
                    onClick={handleSavePricing}
                    disabled={savingPlans}
                    className="inline-flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-bold text-white bg-emerald-600 hover:bg-emerald-700 shadow-md shadow-emerald-600/20 active:scale-95 transition-all disabled:opacity-50"
                  >
                    {savingPlans ? (
                      <>
                        <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                        <span>Đang lưu...</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-sm">save</span>
                        <span>Lưu Cấu Hình Gói Giá</span>
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* 4 Plan Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mt-6">
                {/* Gói 1: DAILY */}
                <div className={`rounded-xl border-2 p-4 relative flex flex-col justify-between shadow-sm transition-all ${pricingPlans.DAILY?.status === 'ACTIVE'
                    ? 'border-emerald-500 bg-emerald-50/20'
                    : 'border-slate-200 bg-slate-50/60 opacity-80'
                  }`}>
                  <span className="absolute -top-2.5 left-4 px-2 py-0.5 rounded-full bg-emerald-600 text-white text-[10px] font-bold uppercase tracking-wider">
                    Phổ biến nhất
                  </span>
                  <div>
                    <div className="flex items-center justify-between mb-3 mt-1">
                      <span className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-base text-emerald-600">calendar_today</span>
                        Gói Theo Ngày (DAILY)
                      </span>
                      {/* Toggle Switch */}
                      <button
                        type="button"
                        onClick={() => togglePlanStatus('DAILY')}
                        className={`relative inline-flex h-5 w-10 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${pricingPlans.DAILY?.status === 'ACTIVE' ? 'bg-emerald-600' : 'bg-slate-300'
                          }`}
                        title="Bật/Tắt gói ngày"
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${pricingPlans.DAILY?.status === 'ACTIVE' ? 'translate-x-5' : 'translate-x-0'
                            }`}
                        />
                      </button>
                    </div>

                    <div className="mb-3">
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-semibold text-slate-700">Đơn giá niêm yết (VNĐ/ngày)</label>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${pricingPlans.DAILY?.status === 'ACTIVE'
                            ? 'text-emerald-700 bg-emerald-100'
                            : 'text-slate-500 bg-slate-200'
                          }`}>
                          {pricingPlans.DAILY?.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </div>
                      <div className="relative">
                        <input
                          type="number"
                          min="0"
                          step="10000"
                          placeholder="Chưa thiết lập giá"
                          value={pricingPlans.DAILY?.slotPrice ?? ''}
                          onChange={(e) => handlePlanPriceChange('DAILY', e.target.value)}
                          className="w-full pl-3 pr-16 py-2 bg-white border border-slate-300 rounded-lg text-slate-900 font-bold text-sm focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400">đ/ngày</span>
                      </div>
                      <p className="text-[10px] text-emerald-700 font-semibold mt-1">
                        {pricingPlans.DAILY?.slotPrice ? `≈ ${formatMoney(pricingPlans.DAILY.slotPrice)} VNĐ / ngày (24h)` : 'Chưa thiết lập giá'}
                      </p>
                    </div>

                    <ul className="text-[11px] text-slate-600 space-y-1.5 mb-4">
                      <li className="flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-xs text-emerald-600">check</span> Chu kỳ thuê: 24 giờ / ngày
                      </li>
                      <li className="flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-xs text-emerald-600">all_inclusive</span>
                        <strong className="text-emerald-700">Không giới hạn quãng đường (Unlimited km)</strong>
                      </li>
                    </ul>
                  </div>
                  <div className={`text-[10px] p-2 rounded-lg border font-medium ${pricingPlans.DAILY?.status === 'ACTIVE'
                      ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
                      : 'text-slate-500 bg-slate-100 border-slate-200'
                    }`}>
                    {pricingPlans.DAILY?.status === 'ACTIVE' ? 'Đang nhận khách trên ứng dụng' : 'Tạm tắt gói cước này'}
                  </div>
                </div>

                {/* Gói 2: HOURLY */}
                <div className={`rounded-xl border p-4 flex flex-col justify-between shadow-sm transition-all ${pricingPlans.HOURLY?.status === 'ACTIVE'
                    ? 'border-emerald-500 bg-emerald-50/10'
                    : 'border-slate-200 bg-slate-50/60 opacity-80'
                  }`}>
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-base text-slate-600">schedule</span>
                        Gói Theo Giờ (HOURLY)
                      </span>
                      {/* Toggle Switch */}
                      <button
                        type="button"
                        onClick={() => togglePlanStatus('HOURLY')}
                        className={`relative inline-flex h-5 w-10 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${pricingPlans.HOURLY?.status === 'ACTIVE' ? 'bg-emerald-600' : 'bg-slate-300'
                          }`}
                        title="Bật/Tắt gói giờ"
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${pricingPlans.HOURLY?.status === 'ACTIVE' ? 'translate-x-5' : 'translate-x-0'
                            }`}
                        />
                      </button>
                    </div>

                    <div className="mb-3">
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-semibold text-slate-700">Đơn giá niêm yết (VNĐ/giờ)</label>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${pricingPlans.HOURLY?.status === 'ACTIVE'
                            ? 'text-emerald-700 bg-emerald-100'
                            : 'text-slate-500 bg-slate-200'
                          }`}>
                          {pricingPlans.HOURLY?.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </div>
                      <div className="relative">
                        <input
                          type="number"
                          min="0"
                          step="5000"
                          placeholder="Chưa thiết lập giá"
                          value={pricingPlans.HOURLY?.slotPrice ?? ''}
                          onChange={(e) => handlePlanPriceChange('HOURLY', e.target.value)}
                          className="w-full pl-3 pr-12 py-2 bg-white border border-slate-300 rounded-lg text-slate-900 font-bold text-sm focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400">đ/h</span>
                      </div>
                      <p className="text-[10px] text-emerald-700 font-semibold mt-1">
                        {pricingPlans.HOURLY?.slotPrice ? `≈ ${formatMoney(pricingPlans.HOURLY.slotPrice)} VNĐ / giờ` : 'Chưa thiết lập giá'}
                      </p>
                    </div>

                    {/* Ngưỡng quy đổi ngày */}
                    <div className="mb-3">
                      <label className="text-[11px] font-semibold text-slate-700 block mb-1">
                        Ngưỡng quy đổi trần giá ngày (phút)
                      </label>
                      <input
                        type="number"
                        min="60"
                        step="60"
                        value={pricingPlans.HOURLY?.hourlyToDailyThresholdMinutes ?? 480}
                        onChange={(e) => handleHourlyThresholdChange(e.target.value)}
                        className="w-full px-3 py-1.5 bg-white border border-slate-300 rounded-lg text-slate-900 font-medium text-xs focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                      />
                      <p className="text-[10px] text-slate-500 mt-1">
                        Sau {pricingPlans.HOURLY?.hourlyToDailyThresholdMinutes ?? 480} phút (~{(Number(pricingPlans.HOURLY?.hourlyToDailyThresholdMinutes ?? 480) / 60).toFixed(1)} tiếng) tự động chốt trần giá ngày.
                      </p>
                    </div>

                    <ul className="text-[11px] text-slate-600 space-y-1.5 mb-4">
                      <li className="flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-xs text-emerald-600">all_inclusive</span>
                        <strong className="text-emerald-700">Không giới hạn quãng đường (Unlimited km)</strong>
                      </li>
                    </ul>
                  </div>
                  <div className={`text-[10px] p-2 rounded-lg border font-medium ${pricingPlans.HOURLY?.status === 'ACTIVE'
                      ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
                      : 'text-slate-500 bg-slate-100 border-slate-200'
                    }`}>
                    {pricingPlans.HOURLY?.status === 'ACTIVE' ? 'Đang nhận khách trên ứng dụng' : 'Tạm tắt gói cước này'}
                  </div>
                </div>

                {/* Gói 3: MONTHLY */}
                <div className={`rounded-xl border p-4 flex flex-col justify-between shadow-sm transition-all ${pricingPlans.MONTHLY?.status === 'ACTIVE'
                    ? 'border-emerald-500 bg-emerald-50/10'
                    : 'border-slate-200 bg-slate-50/60 opacity-80'
                  }`}>
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-base text-slate-600">date_range</span>
                        Gói Theo Tháng (MONTHLY)
                      </span>
                      {/* Toggle Switch */}
                      <button
                        type="button"
                        onClick={() => togglePlanStatus('MONTHLY')}
                        className={`relative inline-flex h-5 w-10 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${pricingPlans.MONTHLY?.status === 'ACTIVE' ? 'bg-emerald-600' : 'bg-slate-300'
                          }`}
                        title="Bật/Tắt gói tháng"
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${pricingPlans.MONTHLY?.status === 'ACTIVE' ? 'translate-x-5' : 'translate-x-0'
                            }`}
                        />
                      </button>
                    </div>

                    <div className="mb-3">
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-semibold text-slate-700">Đơn giá niêm yết (VNĐ/tháng)</label>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${pricingPlans.MONTHLY?.status === 'ACTIVE'
                            ? 'text-emerald-700 bg-emerald-100'
                            : 'text-slate-500 bg-slate-200'
                          }`}>
                          {pricingPlans.MONTHLY?.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </div>
                      <div className="relative">
                        <input
                          type="number"
                          min="0"
                          step="100000"
                          placeholder="Chưa thiết lập giá"
                          value={pricingPlans.MONTHLY?.slotPrice ?? ''}
                          onChange={(e) => handlePlanPriceChange('MONTHLY', e.target.value)}
                          className="w-full pl-3 pr-16 py-2 bg-white border border-slate-300 rounded-lg text-slate-900 font-bold text-sm focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400">đ/tháng</span>
                      </div>
                      <p className="text-[10px] text-emerald-700 font-semibold mt-1">
                        {pricingPlans.MONTHLY?.slotPrice ? `≈ ${formatMoney(pricingPlans.MONTHLY.slotPrice)} VNĐ / tháng` : 'Chưa thiết lập giá'}
                      </p>
                    </div>

                    <ul className="text-[11px] text-slate-600 space-y-1.5 mb-4">
                      <li className="flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-xs text-emerald-600">check</span> Chu kỳ 30 ngày liên tục
                      </li>
                      <li className="flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-xs text-emerald-600">all_inclusive</span>
                        <strong className="text-emerald-700">Không giới hạn quãng đường (Unlimited km)</strong>
                      </li>
                    </ul>
                  </div>
                  <div className={`text-[10px] p-2 rounded-lg border font-medium ${pricingPlans.MONTHLY?.status === 'ACTIVE'
                      ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
                      : 'text-slate-500 bg-slate-100 border-slate-200'
                    }`}>
                    {pricingPlans.MONTHLY?.status === 'ACTIVE' ? 'Gói thuê dài hạn doanh nghiệp đang mở' : 'Tạm tắt gói cước này'}
                  </div>
                </div>

                {/* Gói 4: YEARLY */}
                <div className={`rounded-xl border p-4 flex flex-col justify-between shadow-sm transition-all ${pricingPlans.YEARLY?.status === 'ACTIVE'
                    ? 'border-emerald-500 bg-emerald-50/10'
                    : 'border-slate-200 bg-slate-50/60 opacity-80'
                  }`}>
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-base text-slate-600">stars</span>
                        Gói Năm (YEARLY)
                      </span>
                      {/* Toggle Switch */}
                      <button
                        type="button"
                        onClick={() => togglePlanStatus('YEARLY')}
                        className={`relative inline-flex h-5 w-10 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${pricingPlans.YEARLY?.status === 'ACTIVE' ? 'bg-emerald-600' : 'bg-slate-300'
                          }`}
                        title="Bật/Tắt gói năm"
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${pricingPlans.YEARLY?.status === 'ACTIVE' ? 'translate-x-5' : 'translate-x-0'
                            }`}
                        />
                      </button>
                    </div>

                    <div className="mb-3">
                      <div className="flex items-center justify-between mb-1">
                        <label className="text-[11px] font-semibold text-slate-700">Đơn giá niêm yết (VNĐ/năm)</label>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${pricingPlans.YEARLY?.status === 'ACTIVE'
                            ? 'text-emerald-700 bg-emerald-100'
                            : 'text-slate-500 bg-slate-200'
                          }`}>
                          {pricingPlans.YEARLY?.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </div>
                      <div className="relative">
                        <input
                          type="number"
                          min="0"
                          step="1000000"
                          placeholder="Chưa thiết lập giá"
                          value={pricingPlans.YEARLY?.slotPrice ?? ''}
                          onChange={(e) => handlePlanPriceChange('YEARLY', e.target.value)}
                          className="w-full pl-3 pr-16 py-2 bg-white border border-slate-300 rounded-lg text-slate-900 font-bold text-sm focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400">đ/năm</span>
                      </div>
                      <p className="text-[10px] text-emerald-700 font-semibold mt-1">
                        {pricingPlans.YEARLY?.slotPrice ? `≈ ${formatMoney(pricingPlans.YEARLY.slotPrice)} VNĐ / năm` : 'Chưa thiết lập giá'}
                      </p>
                    </div>

                    {/* Tiền cọc/trả trước cho YEARLY */}
                    <div className="mb-3">
                      <label className="text-[11px] font-semibold text-slate-700 block mb-1">
                        Tiền trả trước / Cọc ký quỹ (VNĐ)
                      </label>
                      <div className="relative">
                        <input
                          type="number"
                          min="0"
                          step="1000000"
                          value={pricingPlans.YEARLY?.upfrontAmount ?? 0}
                          onChange={(e) => handleYearlyUpfrontChange(e.target.value)}
                          className="w-full pl-3 pr-10 py-1.5 bg-white border border-slate-300 rounded-lg text-slate-900 font-medium text-xs focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">đ</span>
                      </div>
                      <p className="text-[10px] text-slate-500 mt-1">
                        ≈ {formatMoney(pricingPlans.YEARLY?.upfrontAmount ?? 0)} VNĐ
                      </p>
                    </div>

                    <ul className="text-[11px] text-slate-600 space-y-1.5 mb-4">
                      <li className="flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-xs text-emerald-600">all_inclusive</span>
                        <strong className="text-emerald-700">Không giới hạn quãng đường (Unlimited km)</strong>
                      </li>
                    </ul>
                  </div>
                  <div className={`text-[10px] p-2 rounded-lg border font-medium ${pricingPlans.YEARLY?.status === 'ACTIVE'
                      ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
                      : 'text-amber-700 bg-amber-50 border-amber-200'
                    }`}>
                    {pricingPlans.YEARLY?.status === 'ACTIVE' ? 'Đang mở gói hợp đồng năm' : 'Tạm khóa cấu hình gói năm'}
                  </div>
                </div>
              </div>

              {/* Phụ phí vận hành (CSDL rental_plan) */}
              <div className="mt-8 pt-6 border-t border-slate-200">
                <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider mb-4 flex items-center gap-2">
                  <span className="material-symbols-outlined text-base text-emerald-600">tune</span>
                  Cấu hình phụ phí vận hành & điều khoản vượt hạn mức (CSDL rental_plan)
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                  {/* Phụ phí vượt giờ */}
                  <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-200">
                    <label className="text-xs font-bold text-slate-800 block mb-1">Phụ phí vượt giờ</label>
                    <p className="text-[10px] text-slate-500 mb-2 font-mono">allowed_overtime & overtime_surcharge_ratio</p>
                    <div className="space-y-2">
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Ân hạn miễn phí (phút):</span>
                        <input
                          type="number"
                          min="0"
                          value={surcharges.allowedOvertimeMinutes ?? 15}
                          onChange={(e) => handleSurchargeChange('allowedOvertimeMinutes', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-slate-800"
                        />
                      </div>
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Tỉ lệ phụ thu mỗi giờ (0.15 = 15%):</span>
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          value={surcharges.overtimeSurchargeRatio ?? 0.15}
                          onChange={(e) => handleSurchargeChange('overtimeSurchargeRatio', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-emerald-700"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Bán kính đón */}
                  <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-200">
                    <label className="text-xs font-bold text-slate-800 block mb-1">Bán kính đón miễn phí</label>
                    <p className="text-[10px] text-slate-500 mb-2 font-mono">free_radius_km & out_of_radius_rate</p>
                    <div className="space-y-2">
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Đón miễn phí (km):</span>
                        <input
                          type="number"
                          min="0"
                          value={surcharges.freeRadiusKm ?? 10}
                          onChange={(e) => handleSurchargeChange('freeRadiusKm', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-slate-800"
                        />
                      </div>
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Đơn giá ngoài bán kính (VNĐ/km):</span>
                        <input
                          type="number"
                          min="0"
                          step="1000"
                          value={surcharges.outOfRadiusRate ?? 12000}
                          onChange={(e) => handleSurchargeChange('outOfRadiusRate', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-emerald-700"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Phí chờ */}
                  <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-200">
                    <label className="text-xs font-bold text-slate-800 block mb-1">Phí chờ tại điểm đón</label>
                    <p className="text-[10px] text-slate-500 mb-2 font-mono">waiting_free_minutes & waiting_rate</p>
                    <div className="space-y-2">
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Chờ miễn phí (phút):</span>
                        <input
                          type="number"
                          min="0"
                          value={surcharges.waitingFreeMinutes ?? 15}
                          onChange={(e) => handleSurchargeChange('waitingFreeMinutes', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-slate-800"
                        />
                      </div>
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Phí mỗi phút chờ (VNĐ/phút):</span>
                        <input
                          type="number"
                          min="0"
                          step="500"
                          value={surcharges.waitingRate ?? 2000}
                          onChange={(e) => handleSurchargeChange('waitingRate', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-emerald-700"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Phụ thu cuối tuần & lễ */}
                  <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-200">
                    <label className="text-xs font-bold text-slate-800 block mb-1">Phụ thu cuối tuần & Lễ</label>
                    <p className="text-[10px] text-slate-500 mb-2 font-mono">weekend_surcharge_ratio</p>
                    <div className="space-y-2">
                      <div>
                        <span className="text-[11px] text-slate-500 block mb-0.5">Tỉ lệ phụ thu (+15% = 0.15):</span>
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          value={surcharges.weekendSurchargeRatio ?? 0.15}
                          onChange={(e) => handleSurchargeChange('weekendSurchargeRatio', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-bold text-amber-700"
                        />
                      </div>
                      <div className="pt-2 text-[10px] text-slate-400">
                        <span>Cơ chế tính: Tự động cộng vào giá gốc khi khách đặt chuyến thứ 7, CN hoặc ngày Lễ.</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        )}
      </main>

      {/* ======================================================== */}
      {/* FIXED BOTTOM TASKBAR (Active on Tab 1 & Tab 3)           */}
      {/* ======================================================== */}
      {activeTab === 'profile' && (
        <aside className="fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-md border-t border-slate-200 shadow-xl z-30 py-3 px-4 sm:px-8">
          <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-xs text-slate-600 w-full sm:w-auto">
              <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${isPlateChanged ? 'bg-amber-500 animate-ping' : 'bg-emerald-500'
                }`}></span>
              <span className="leading-tight">
                {isPlateChanged ? (
                  <span>
                    <strong className="text-amber-700">Lưu ý Tầng 2:</strong> Thay đổi Biển số xe sẽ chuyển phương tiện về <strong>CHỜ DUYỆT (PENDING)</strong> và tạm dừng nhận khách cho đến khi xác minh.
                  </span>
                ) : (
                  <span>
                    <strong>Chế độ an toàn:</strong> Thay đổi Tầng 3 sẽ cập nhật ngay lập tức mà không làm gián đoạn lịch xe.
                  </span>
                )}
              </span>
            </div>

            <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
              <button
                type="button"
                onClick={handleResetForm}
                disabled={saving}
                className="px-4 py-2 text-xs font-semibold text-slate-700 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors disabled:opacity-50"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleSaveProfile}
                disabled={saving}
                className={`px-5 py-2 text-xs font-bold text-white rounded-xl transition-all shadow-md flex items-center gap-2 disabled:opacity-50 ${isPlateChanged
                    ? 'bg-amber-600 hover:bg-amber-700 shadow-amber-500/25'
                    : 'bg-emerald-600 hover:bg-emerald-700 shadow-emerald-500/25'
                  }`}
              >
                <span className="material-symbols-outlined text-sm">save</span>
                <span>{saving ? 'Đang lưu...' : isPlateChanged ? 'Lưu và Gửi duyệt lại' : 'Cập nhật hồ sơ xe'}</span>
              </button>
            </div>
          </div>
        </aside>
      )}

      {activeTab === 'pricing' && (
        <aside className="fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-md border-t border-slate-200 shadow-xl z-30 py-3 px-4 sm:px-8">
          <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-xs text-slate-600 w-full sm:w-auto">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 flex-shrink-0"></span>
              <span className="leading-tight">
                <strong>Cấu hình bảng giá:</strong> Cho phép tùy chỉnh đơn giá và phụ phí cho xe <strong>{form.licensePlate}</strong> mà không cần duyệt lại hồ sơ.
              </span>
            </div>

            <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
              <button
                type="button"
                onClick={handleResetPricing}
                disabled={savingPlans}
                className="px-4 py-2 text-xs font-semibold text-slate-700 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors disabled:opacity-50"
              >
                Khôi phục
              </button>
              <button
                type="button"
                onClick={handleSavePricing}
                disabled={savingPlans}
                className="px-5 py-2 text-xs font-bold text-white bg-emerald-600 hover:bg-emerald-700 rounded-xl transition-all shadow-md shadow-emerald-500/25 flex items-center gap-2 disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-sm">save</span>
                <span>{savingPlans ? 'Đang lưu...' : 'Lưu cấu hình gói giá'}</span>
              </button>
            </div>
          </div>
        </aside>
      )}

      {/* ======================================================== */}
      {/* MODAL: CHI TIẾT QUY CHUẨN KIẾN TRÚC 3 TẦNG               */}
      {/* ======================================================== */}
      {showRuleModal && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-2xl w-full shadow-2xl border border-slate-200 overflow-hidden transform transition-all animate-fadeIn">
            <div className="p-5 bg-gradient-to-r from-emerald-800 to-emerald-600 text-white flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-2xl">policy</span>
                <div>
                  <h3 className="text-base font-bold">Quy Chuẩn Kiến Trúc Cập Nhật 3 Tầng v10.1</h3>
                  <p className="text-xs text-emerald-100">Bảo vệ pháp lý vòng đời xe cho Cổng đối tác EcoMotion VN</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowRuleModal(false)}
                className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors"
              >
                <span className="material-symbols-outlined text-lg">close</span>
              </button>
            </div>

            <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto text-xs text-slate-700">
              {/* Tầng 1 */}
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                <div className="flex items-center gap-2 text-slate-900 font-bold mb-1">
                  <span className="w-5 h-5 rounded bg-slate-200 text-slate-800 text-[10px] flex items-center justify-center">01</span>
                  TẦNG 1: Định danh vật lý (Physical Identity)
                </div>
                <p className="text-slate-600 leading-relaxed">
                  Bao gồm <strong>Số khung (VIN)</strong> và <strong>Số máy (Engine/Motor)</strong>. Đây là định danh vật lý vĩnh cửu đi theo xe suốt vòng đời. Hệ thống khóa cứng 100% sau khi tạo xe nhằm chống nạn tráo vỏ, giả mạo danh tính phương tiện. Mọi chỉnh sửa bắt buộc phải lập biên bản thẩm định pháp lý offline tại Depot.
                </p>
              </div>

              {/* Tầng 2 */}
              <div className="p-4 rounded-xl bg-amber-50/80 border border-amber-200">
                <div className="flex items-center gap-2 text-amber-900 font-bold mb-1">
                  <span className="w-5 h-5 rounded bg-amber-200 text-amber-900 text-[10px] flex items-center justify-center">02</span>
                  TẦNG 2: Định danh pháp lý (Legal Identity)
                </div>
                <p className="text-amber-800 leading-relaxed">
                  Bao gồm <strong>Biển số xe kiểm soát</strong>. Được phép thay đổi khi chủ xe đổi biển hoặc chuyển vùng lưu hành. Tuy nhiên, khi đổi biển số, xe sẽ lập tức chuyển về trạng thái <strong>CHỜ DUYỆT (PENDING)</strong> và tạm ngừng nhận cuốc mới cho đến khi chuyên viên kiểm duyệt xác minh giấy đăng ký xe mới.
                </p>
              </div>

              {/* Tầng 3 */}
              <div className="p-4 rounded-xl bg-emerald-50/80 border border-emerald-200">
                <div className="flex items-center gap-2 text-emerald-900 font-bold mb-1">
                  <span className="w-5 h-5 rounded bg-emerald-200 text-emerald-900 text-[10px] flex items-center justify-center">03</span>
                  TẦNG 3: Thông tin mô tả & Vận hành (Operational Info)
                </div>
                <p className="text-emerald-800 leading-relaxed">
                  Bao gồm: <strong>Hãng xe, Dòng xe, Năm sản xuất, Số chỗ, Hộp số, Loại nhiên liệu, Màu sơn, Ảnh bìa 16:9, Buffer đệm bảo trì</strong>. Nhà xe có toàn quyền cập nhật tức thì 24/7 mà không cần duyệt lại, dữ liệu đồng bộ ngay lập tức lên ứng dụng đặt xe của khách hàng.
                </p>
              </div>
            </div>

            <div className="p-4 bg-slate-50 border-t border-slate-200 flex justify-end">
              <button
                type="button"
                onClick={() => setShowRuleModal(false)}
                className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl font-bold text-xs transition-colors"
              >
                Đã hiểu quy chuẩn
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODAL: TẢI LÊN / CẬP NHẬT GIẤY TỜ PHÁP LÝ (JPG, PNG, PDF) */}
      {/* ======================================================== */}
      {docModal.isOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl border border-slate-200 overflow-hidden transform transition-all animate-fadeIn">
            {/* Header */}
            <div className="p-5 bg-gradient-to-r from-emerald-800 to-emerald-600 text-white flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-2xl">upload_file</span>
                <div>
                  <h3 className="text-base font-bold">
                    Cập nhật {DOC_SPECS.find(s => s.type === docModal.docType)?.title || 'Giấy tờ'}
                  </h3>
                  <p className="text-xs text-emerald-100">Chỉ chấp nhận JPG, PNG hoặc tài liệu PDF (tối đa 8MB)</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setDocModal(prev => ({ ...prev, isOpen: false }))}
                className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center transition-colors"
              >
                <span className="material-symbols-outlined text-lg">close</span>
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleSaveDocument} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Số hiệu văn bản (doc_number) <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={docModal.docNumber}
                  onChange={(e) => setDocModal(prev => ({ ...prev, docNumber: e.target.value }))}
                  placeholder="Ví dụ: DK-883921 hoặc KD-771829"
                  className="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs font-mono text-slate-800 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Ngày hết hạn (expiry_date)
                  {docModal.docType !== 'REGISTRATION' && <span className="text-rose-500"> *</span>}
                </label>
                <input
                  type="date"
                  value={docModal.expiryDate}
                  onChange={(e) => setDocModal(prev => ({ ...prev, expiryDate: e.target.value }))}
                  required={docModal.docType !== 'REGISTRATION'}
                  className="w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-xs font-mono text-slate-800 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500"
                />
                <p className="text-[11px] text-slate-400 mt-1">
                  {DOC_SPECS.find(s => s.type === docModal.docType)?.expiryHint}
                </p>
              </div>

              {/* File Attachment Upload */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Tệp đính kèm văn bản <span className="text-rose-500">*</span>
                </label>
                <input
                  type="file"
                  ref={fileInputRef}
                  accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
                  onChange={handleFileUpload}
                  className="hidden"
                />

                {docModal.uploading ? (
                  <div className="border-2 border-dashed border-emerald-300 bg-emerald-50/50 rounded-xl p-6 flex flex-col items-center justify-center text-center">
                    <div className="w-8 h-8 rounded-full border-3 border-emerald-200 border-t-emerald-600 animate-spin mb-2"></div>
                    <p className="text-xs font-semibold text-emerald-800">Đang tải tệp lên máy chủ lưu trữ...</p>
                    <p className="text-[11px] text-emerald-600 mt-0.5">Vui lòng chờ trong giây lát</p>
                  </div>
                ) : docModal.fileUrl ? (
                  <div className="border border-emerald-200 bg-emerald-50/40 rounded-xl p-3.5 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className={`material-symbols-outlined text-2xl ${docModal.fileUrl.toLowerCase().endsWith('.pdf') ? 'text-rose-600' : 'text-emerald-600'}`}>
                          {docModal.fileUrl.toLowerCase().endsWith('.pdf') ? 'picture_as_pdf' : 'image'}
                        </span>
                        <div className="min-w-0">
                          <p className="text-xs font-bold text-slate-800 truncate font-mono">
                            {docModal.fileName || docModal.fileUrl.split('/').pop()}
                          </p>
                          <p className="text-[10px] text-emerald-700">Đã lưu trữ an toàn trên máy chủ Backend</p>
                        </div>
                      </div>
                      <span className="material-symbols-outlined text-emerald-600 text-lg">check_circle</span>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-emerald-200/60 text-xs">
                      <button
                        type="button"
                        onClick={() => window.open(docModal.fileUrl, '_blank', 'noopener,noreferrer')}
                        className="text-xs font-bold text-emerald-800 hover:text-emerald-950 inline-flex items-center gap-1 hover:underline cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-sm">open_in_new</span>
                        Xem thử tệp (Mở tab mới)
                      </button>

                      <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        className="text-xs font-semibold text-slate-600 hover:text-slate-900 bg-white hover:bg-slate-50 border border-slate-300 px-2.5 py-1 rounded-lg transition-colors cursor-pointer"
                      >
                        Đổi tệp khác
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="w-full border-2 border-dashed border-slate-300 hover:border-emerald-500 hover:bg-emerald-50/20 rounded-xl p-6 flex flex-col items-center justify-center text-center transition-all cursor-pointer group"
                  >
                    <div className="w-10 h-10 rounded-full bg-slate-100 group-hover:bg-emerald-100 text-slate-600 group-hover:text-emerald-700 flex items-center justify-center mb-2 transition-colors">
                      <span className="material-symbols-outlined text-xl">cloud_upload</span>
                    </div>
                    <p className="text-xs font-bold text-slate-800 group-hover:text-emerald-800">
                      Nhấp để chọn tệp từ máy tính
                    </p>
                    <p className="text-[11px] text-slate-500 mt-1">
                      Chấp nhận hình ảnh JPG, PNG hoặc tài liệu PDF (Tối đa 8MB)
                    </p>
                  </button>
                )}
              </div>

              {/* Actions */}
              <div className="pt-3 border-t border-slate-100 flex items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={() => setDocModal(prev => ({ ...prev, isOpen: false }))}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={docModal.uploading || docModal.saving}
                  className="px-5 py-2 text-xs font-bold text-white bg-emerald-600 hover:bg-emerald-700 rounded-xl transition-all shadow-md shadow-emerald-500/20 flex items-center gap-1.5 disabled:opacity-50 cursor-pointer"
                >
                  {docModal.saving ? (
                    <>
                      <div className="w-3.5 h-3.5 rounded-full border-2 border-white border-t-transparent animate-spin"></div>
                      <span>Đang lưu...</span>
                    </>
                  ) : (
                    <>
                      <span className="material-symbols-outlined text-sm">save</span>
                      <span>Lưu hồ sơ giấy tờ</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

