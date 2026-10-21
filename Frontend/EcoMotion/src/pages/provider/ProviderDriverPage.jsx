import { useEffect, useState, useMemo, useRef } from 'react'
import ProviderHeader from '../../components/provider/ProviderHeader'
import Toast from '../../components/Toast'
import { providerService } from '../../services/providerService'
import { getErrorMessage } from '../../utils/errorMessage'

export default function ProviderDriverPage() {
  const [drivers, setDrivers] = useState([])
  const [stats, setStats] = useState({
    total: 0,
    approved: 0,
    pending: 0,
    suspended: 0,
    rejected: 0,
  })
  const [loading, setLoading] = useState(true)
  const [toast, setToast] = useState(null)

  // Filters & Controls
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedStatus, setSelectedStatus] = useState('ALL')
  const [sortBy, setSortBy] = useState('newest')
  const [viewMode, setViewMode] = useState('grid') // 'grid' | 'table'

  // Modal State (Create / Edit)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editingDriverId, setEditingDriverId] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  // Form Fields
  const [formFullName, setFormFullName] = useState('')
  const [formEmail, setFormEmail] = useState('')
  const [formPhone, setFormPhone] = useState('')
  const [formPortraitUrl, setFormPortraitUrl] = useState('')
  const [formLicenseNo, setFormLicenseNo] = useState('')
  const [formLicenseClass, setFormLicenseClass] = useState('B2')
  const [formLicenseExpiry, setFormLicenseExpiry] = useState('')
  const [formLicenseImageUrl, setFormLicenseImageUrl] = useState('')
  const [formInitialPassword, setFormInitialPassword] = useState('EcoMotion@123')
  const [uploadingPortrait, setUploadingPortrait] = useState(false)
  const [uploadingLicense, setUploadingLicense] = useState(false)

  // Lightbox Preview Modal
  const [previewData, setPreviewData] = useState(null)

  // File Inputs Ref
  const portraitInputRef = useRef(null)
  const licenseInputRef = useRef(null)

  // Load drivers and stats
  const loadData = async () => {
    try {
      setLoading(true)
      const [driversRes, statsRes] = await Promise.all([
        providerService.getDrivers({
          search: searchTerm || undefined,
          status: selectedStatus !== 'ALL' ? selectedStatus : undefined,
          sort: sortBy,
        }),
        providerService.getDriverStats(),
      ])

      if (driversRes) setDrivers(driversRes)
      if (statsRes) setStats(statsRes)
    } catch (err) {
      setToast({
        type: 'error',
        message: getErrorMessage(err, 'Không thể tải danh sách tài xế của nhà xe'),
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [selectedStatus, sortBy])

  // Client-side search for instant filtering
  const filteredDrivers = useMemo(() => {
    if (!searchTerm.trim()) return drivers
    const term = searchTerm.toLowerCase().trim()
    return drivers.filter(
      (d) =>
        (d.fullName && d.fullName.toLowerCase().includes(term)) ||
        (d.phone && d.phone.toLowerCase().includes(term)) ||
        (d.email && d.email.toLowerCase().includes(term)) ||
        (d.licenseNo && d.licenseNo.toLowerCase().includes(term))
    )
  }, [drivers, searchTerm])

  // Open modal for Create
  const handleOpenCreateModal = () => {
    setIsEditing(false)
    setEditingDriverId(null)
    setFormFullName('')
    setFormEmail('')
    setFormPhone('')
    setFormPortraitUrl('')
    setFormLicenseNo('')
    setFormLicenseClass('B2')
    setFormLicenseExpiry('')
    setFormLicenseImageUrl('')
    setFormInitialPassword('EcoMotion@123')
    setIsModalOpen(true)
  }

  // Open modal for Edit
  const handleOpenEditModal = (driver) => {
    setIsEditing(true)
    setEditingDriverId(driver.id)
    setFormFullName(driver.fullName || '')
    setFormEmail(driver.email || '')
    setFormPhone(driver.phone || '')
    setFormPortraitUrl(driver.portraitUrl || '')
    setFormLicenseNo(driver.licenseNo || '')
    setFormLicenseClass(driver.licenseClass || 'B2')
    setFormLicenseExpiry(driver.licenseExpiry || '')
    setFormLicenseImageUrl(driver.licenseImageUrl || '')
    setIsModalOpen(true)
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setIsEditing(false)
    setEditingDriverId(null)
  }

  // Handle Image Upload
  const handleFileUpload = async (file, type) => {
    if (!file) return
    try {
      if (type === 'portrait') setUploadingPortrait(true)
      if (type === 'license') setUploadingLicense(true)

      const result = await providerService.uploadDriverImage(file)
      if (result?.url) {
        if (type === 'portrait') setFormPortraitUrl(result.url)
        if (type === 'license') setFormLicenseImageUrl(result.url)
        setToast({ type: 'success', message: 'Tải ảnh lên thành công' })
      }
    } catch (err) {
      setToast({
        type: 'error',
        message: getErrorMessage(err, 'Lỗi khi tải tệp ảnh lên'),
      })
    } finally {
      if (type === 'portrait') setUploadingPortrait(false)
      if (type === 'license') setUploadingLicense(false)
    }
  }

  // Handle Form Submit (Create or Update)
  const handleSubmitForm = async (e) => {
    e.preventDefault()
    if (!formPortraitUrl) {
      setToast({ type: 'error', message: 'Vui lòng tải lên ảnh chân dung của tài xế' })
      return
    }
    if (!formLicenseImageUrl) {
      setToast({ type: 'error', message: 'Vui lòng tải lên ảnh chụp giấy phép lái xe (GPLX)' })
      return
    }

    try {
      setSubmitting(true)
      if (isEditing) {
        await providerService.updateDriver(editingDriverId, {
          fullName: formFullName,
          phone: formPhone,
          portraitUrl: formPortraitUrl,
          licenseNo: formLicenseNo,
          licenseClass: formLicenseClass,
          licenseExpiry: formLicenseExpiry,
          licenseImageUrl: formLicenseImageUrl,
        })
        setToast({
          type: 'success',
          message: 'Đã cập nhật hồ sơ tài xế thành công! Nếu sửa đổi GPLX, hồ sơ sẽ chuyển sang Chờ duyệt.',
        })
      } else {
        await providerService.createDriver({
          fullName: formFullName,
          email: formEmail,
          phone: formPhone,
          portraitUrl: formPortraitUrl,
          licenseNo: formLicenseNo,
          licenseClass: formLicenseClass,
          licenseExpiry: formLicenseExpiry,
          licenseImageUrl: formLicenseImageUrl,
          initialPassword: formInitialPassword,
        })
        setToast({
          type: 'success',
          message: 'Tạo hộ tài khoản tài xế thành công! Hồ sơ đang ở trạng thái Chờ duyệt (PENDING).',
        })
      }
      handleCloseModal()
      loadData()
    } catch (err) {
      setToast({
        type: 'error',
        message: getErrorMessage(err, 'Thao tác không thành công, vui lòng thử lại'),
      })
    } finally {
      setSubmitting(false)
    }
  }

  // Handle Toggle Status (APPROVED <-> SUSPENDED)
  const handleToggleStatus = async (driver) => {
    if (driver.status === 'PENDING' || driver.status === 'REJECTED') {
      setToast({
        type: 'error',
        message: 'Tài xế đang Chờ duyệt hoặc Bị từ chối không thể thay đổi trạng thái hoạt động',
      })
      return
    }

    const newStatus = driver.status === 'APPROVED' ? 'SUSPENDED' : 'APPROVED'
    try {
      await providerService.toggleDriverStatus(driver.id, newStatus)
      setToast({
        type: 'success',
        message: `Đã ${newStatus === 'APPROVED' ? 'kích hoạt hoạt động' : 'tạm ngưng'} tài xế ${driver.fullName}`,
      })
      loadData()
    } catch (err) {
      setToast({
        type: 'error',
        message: getErrorMessage(err, 'Không thể cập nhật trạng thái tài xế'),
      })
    }
  }

  // Format date helper
  const formatDate = (dateStr) => {
    if (!dateStr) return 'N/A'
    try {
      const d = new Date(dateStr)
      if (isNaN(d.getTime())) return dateStr
      return d.toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      })
    } catch {
      return dateStr
    }
  }

  return (
    <div className="bg-slate-50 min-h-screen flex flex-col font-sans text-slate-800">
      {/* Provider Header Component */}
      <ProviderHeader activeTab="drivers" />

      {/* Main Content Area */}
      <main className="w-full flex-1 max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Top Header & Action */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
              Quản lý Đội tài xế
            </h1>
            <p className="text-sm text-slate-500 max-w-2xl">
              Tạo hộ tài khoản tài xế, quản lý hồ sơ GPLX và theo dõi trạng thái phê duyệt từ Quản trị viên.
            </p>
          </div>

          <div className="flex-shrink-0 flex items-center gap-3">
            <button
              onClick={handleOpenCreateModal}
              className="bg-emerald-700 hover:bg-emerald-800 text-white font-semibold text-sm px-5 py-3 rounded-xl shadow-md hover:shadow-lg transition-all flex items-center gap-2 cursor-pointer group"
            >
              <span className="material-symbols-outlined text-[20px] group-hover:rotate-90 transition-transform duration-300">
                person_add
              </span>
              <span>+ Tạo hộ tài xế mới</span>
            </button>
          </div>
        </div>

        {/* 4 Metric KPI Mosaic Row */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-8">
          {/* Card 1: Tổng số tài xế */}
          <div className="bg-white rounded-2xl p-5 shadow-sm border border-slate-200/80 hover:shadow-md transition-all relative overflow-hidden flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Tổng số tài xế</span>
              <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-600">
                <span className="material-symbols-outlined text-[22px]">groups</span>
              </div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-slate-900 tracking-tight mb-1">
                {String(stats.total).padStart(2, '0')}{' '}
                <span className="text-sm font-normal text-slate-500">tài xế</span>
              </div>
              <p className="text-xs text-slate-500">Đang trong danh sách đội xe</p>
            </div>
          </div>

          {/* Card 2: Đang hoạt động */}
          <div className="bg-white rounded-2xl p-5 shadow-sm border border-slate-200/80 hover:shadow-md transition-all relative overflow-hidden flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Đang hoạt động</span>
              <div className="w-10 h-10 rounded-xl bg-emerald-50 flex items-center justify-center text-emerald-700">
                <span className="material-symbols-outlined text-[22px]">verified</span>
              </div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-slate-900 tracking-tight mb-1">
                {String(stats.approved).padStart(2, '0')}{' '}
                <span className="text-sm font-normal text-slate-500">tài xế</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-xs font-semibold">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
                Sẵn sàng nhận chuyến
              </div>
            </div>
          </div>

          {/* Card 3: Chờ Admin duyệt */}
          <div className="bg-white rounded-2xl p-5 shadow-sm border border-slate-200/80 hover:shadow-md transition-all relative overflow-hidden flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Chờ Admin duyệt</span>
              <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center text-amber-600">
                <span className="material-symbols-outlined text-[22px]">hourglass_top</span>
              </div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-slate-900 tracking-tight mb-1">
                {String(stats.pending).padStart(2, '0')}{' '}
                <span className="text-sm font-normal text-slate-500">tài xế</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 text-xs font-semibold">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                Đang thẩm định GPLX
              </div>
            </div>
          </div>

          {/* Card 4: Tạm ngưng hoạt động */}
          <div className="bg-white rounded-2xl p-5 shadow-sm border border-slate-200/80 hover:shadow-md transition-all relative overflow-hidden flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Tạm ngưng hoạt động</span>
              <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-500">
                <span className="material-symbols-outlined text-[22px]">pause_circle</span>
              </div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-slate-900 tracking-tight mb-1">
                {String(stats.suspended).padStart(2, '0')}{' '}
                <span className="text-sm font-normal text-slate-500">tài xế</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-medium">
                Đã vô hiệu hóa
              </div>
            </div>
          </div>
        </div>

        {/* Filter Toolbar & Search Bar */}
        <div className="bg-white rounded-2xl p-4 shadow-sm border border-slate-200 mb-8 flex flex-col lg:flex-row items-center justify-between gap-4">
          {/* Left: Search Box */}
          <div className="relative w-full lg:w-96 flex-shrink-0">
            <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-[20px]">
              search
            </span>
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Tìm kiếm theo tên, SĐT, email, GPLX..."
              className="w-full bg-slate-50 pl-10 pr-4 py-2.5 rounded-xl text-sm text-slate-800 placeholder:text-slate-400 border border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500/40 transition-all"
            />
          </div>

          {/* Center: Filter Pills */}
          <div className="flex items-center gap-2 overflow-x-auto w-full lg:w-auto pb-2 lg:pb-0 scrollbar-none">
            <button
              onClick={() => setSelectedStatus('ALL')}
              className={`px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                selectedStatus === 'ALL'
                  ? 'bg-emerald-700 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              <span>Tất cả</span>
              <span
                className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                  selectedStatus === 'ALL' ? 'bg-white/20 text-white' : 'bg-slate-200 text-slate-700'
                }`}
              >
                {stats.total}
              </span>
            </button>

            <button
              onClick={() => setSelectedStatus('APPROVED')}
              className={`px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                selectedStatus === 'APPROVED'
                  ? 'bg-emerald-700 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              <span>Đang hoạt động</span>
              <span className="bg-emerald-100 text-emerald-800 px-1.5 py-0.5 rounded-full text-[10px] font-bold">
                {stats.approved}
              </span>
            </button>

            <button
              onClick={() => setSelectedStatus('PENDING')}
              className={`px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                selectedStatus === 'PENDING'
                  ? 'bg-emerald-700 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              <span>Chờ duyệt</span>
              <span className="bg-amber-100 text-amber-800 px-1.5 py-0.5 rounded-full text-[10px] font-bold">
                {stats.pending}
              </span>
            </button>

            <button
              onClick={() => setSelectedStatus('REJECTED')}
              className={`px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                selectedStatus === 'REJECTED'
                  ? 'bg-emerald-700 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              <span>Bị từ chối</span>
              <span className="bg-rose-100 text-rose-800 px-1.5 py-0.5 rounded-full text-[10px] font-bold">
                {stats.rejected}
              </span>
            </button>

            <button
              onClick={() => setSelectedStatus('SUSPENDED')}
              className={`px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                selectedStatus === 'SUSPENDED'
                  ? 'bg-emerald-700 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              <span>Tạm ngưng</span>
              <span className="bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded-full text-[10px] font-bold">
                {stats.suspended}
              </span>
            </button>
          </div>

          {/* Right: View Controls & Sort */}
          <div className="flex items-center gap-3 w-full lg:w-auto justify-end">
            <div className="relative">
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="bg-slate-50 border border-slate-200 text-slate-700 text-xs font-semibold py-2.5 pl-3.5 pr-8 rounded-xl appearance-none focus:outline-none focus:ring-2 focus:ring-emerald-500/40 cursor-pointer"
              >
                <option value="newest">Mới nhất</option>
                <option value="rating">Đánh giá cao nhất</option>
                <option value="expiry">GPLX sắp hết hạn</option>
              </select>
              <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none text-[18px]">
                expand_more
              </span>
            </div>

            <div className="flex items-center bg-slate-100 p-1 rounded-xl">
              <button
                onClick={() => setViewMode('grid')}
                className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                  viewMode === 'grid' ? 'bg-white text-emerald-700 shadow-sm' : 'text-slate-400 hover:text-slate-700'
                }`}
                title="Chế độ Thẻ (Grid)"
              >
                <span className="material-symbols-outlined text-[18px]">grid_view</span>
              </button>
              <button
                onClick={() => setViewMode('table')}
                className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                  viewMode === 'table' ? 'bg-white text-emerald-700 shadow-sm' : 'text-slate-400 hover:text-slate-700'
                }`}
                title="Chế độ Bảng (Table)"
              >
                <span className="material-symbols-outlined text-[18px]">view_list</span>
              </button>
            </div>
          </div>
        </div>

        {/* Content Body: Grid or Table */}
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200 animate-pulse h-72">
                <div className="flex items-center gap-3 mb-4">
                  <div className="w-14 h-14 rounded-2xl bg-slate-200"></div>
                  <div className="flex-1 space-y-2">
                    <div className="h-4 bg-slate-200 rounded w-3/4"></div>
                    <div className="h-3 bg-slate-200 rounded w-1/2"></div>
                  </div>
                </div>
                <div className="space-y-3 pt-4 border-t border-slate-100">
                  <div className="h-3 bg-slate-200 rounded"></div>
                  <div className="h-3 bg-slate-200 rounded w-5/6"></div>
                </div>
              </div>
            ))}
          </div>
        ) : filteredDrivers.length === 0 ? (
          <div className="bg-white rounded-3xl p-12 text-center shadow-sm border border-slate-200 mb-8 flex flex-col items-center justify-center">
            <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-4">
              <span className="material-symbols-outlined text-[32px]">person_search</span>
            </div>
            <h3 className="text-lg font-bold text-slate-800 mb-1">Không tìm thấy tài xế nào</h3>
            <p className="text-sm text-slate-500 max-w-md mb-6">
              Không có tài xế nào khớp với điều kiện lọc hoặc từ khóa tìm kiếm của bạn. Hãy thử thay đổi bộ lọc hoặc tạo hộ tài xế mới.
            </p>
            <button
              onClick={handleOpenCreateModal}
              className="bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-bold px-4 py-2.5 rounded-xl transition-all"
            >
              + Tạo hộ tài xế mới
            </button>
          </div>
        ) : viewMode === 'grid' ? (
          /* GRID VIEW */
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
            {filteredDrivers.map((driver) => (
              <div
                key={driver.id}
                className={`bg-white rounded-2xl p-6 shadow-sm hover:shadow-xl transition-all duration-300 flex flex-col justify-between group border ${
                  driver.status === 'REJECTED' ? 'border-rose-300/80' : 'border-slate-200/90'
                }`}
              >
                <div>
                  {/* Driver Header Info */}
                  <div className="flex items-start justify-between gap-3 mb-4">
                    <div className="flex items-center gap-3.5">
                      <div className="relative">
                        {driver.portraitUrl ? (
                          <img
                            src={driver.portraitUrl}
                            alt={driver.fullName}
                            className="w-14 h-14 rounded-2xl object-cover shadow-sm ring-2 ring-emerald-500/20"
                            onError={(e) => {
                              e.target.onerror = null
                              e.target.src = 'https://ui-avatars.com/api/?name=' + encodeURIComponent(driver.fullName)
                            }}
                          />
                        ) : (
                          <div className="w-14 h-14 rounded-2xl bg-emerald-100 text-emerald-800 font-bold flex items-center justify-center text-lg">
                            {driver.fullName ? driver.fullName.charAt(0) : 'TX'}
                          </div>
                        )}
                        {driver.status === 'APPROVED' && (
                          <span className="absolute -bottom-1 -right-1 w-4 h-4 rounded-full bg-emerald-500 border-2 border-white"></span>
                        )}
                        {driver.status === 'PENDING' && (
                          <span className="absolute -bottom-1 -right-1 w-4 h-4 rounded-full bg-amber-500 border-2 border-white animate-pulse"></span>
                        )}
                        {driver.status === 'REJECTED' && (
                          <span className="absolute -bottom-1 -right-1 w-4 h-4 rounded-full bg-rose-500 border-2 border-white"></span>
                        )}
                      </div>
                      <div>
                        <h3 className="font-bold text-base text-slate-900 leading-tight group-hover:text-emerald-700 transition-colors">
                          {driver.fullName}
                        </h3>
                        <div className="flex items-center gap-1.5 mt-1">
                          {driver.ratingCount > 0 ? (
                            <>
                              <span className="px-2 py-0.5 rounded-md bg-amber-50 text-amber-700 text-xs font-bold flex items-center gap-1">
                                <span className="material-symbols-outlined text-[13px] text-amber-500">star</span>
                                {Number(driver.ratingAvg || 0).toFixed(1)}
                              </span>
                              <span className="text-xs text-slate-400">({driver.ratingCount} đánh giá)</span>
                            </>
                          ) : (
                            <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-500 text-xs font-medium">
                              Mới tạo (0 đánh giá)
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Status Badge */}
                    {driver.status === 'APPROVED' && (
                      <span className="px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold flex items-center gap-1.5">
                        <span className="w-2 h-2 rounded-full bg-emerald-600"></span>
                        Hoạt động
                      </span>
                    )}
                    {driver.status === 'PENDING' && (
                      <span className="px-3 py-1 rounded-full bg-amber-50 text-amber-700 text-xs font-bold flex items-center gap-1.5">
                        <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse"></span>
                        Chờ duyệt
                      </span>
                    )}
                    {driver.status === 'REJECTED' && (
                      <span className="px-3 py-1 rounded-full bg-rose-50 text-rose-700 text-xs font-bold flex items-center gap-1.5">
                        <span className="w-2 h-2 rounded-full bg-rose-600"></span>
                        Bị từ chối
                      </span>
                    )}
                    {driver.status === 'SUSPENDED' && (
                      <span className="px-3 py-1 rounded-full bg-slate-100 text-slate-600 text-xs font-bold flex items-center gap-1.5">
                        <span className="w-2 h-2 rounded-full bg-slate-400"></span>
                        Tạm ngưng
                      </span>
                    )}
                  </div>

                  {/* Contact details */}
                  <div className="space-y-1.5 mb-5 text-slate-600 text-xs">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[17px] text-slate-400">call</span>
                      <span className="font-semibold text-slate-700">{driver.phone || 'Chưa có SĐT'}</span>
                    </div>
                    <div className="flex items-center gap-2 truncate">
                      <span className="material-symbols-outlined text-[17px] text-slate-400">mail</span>
                      <span className="truncate">{driver.email}</span>
                    </div>
                  </div>

                  {/* GPLX Information Box */}
                  <div className="bg-slate-50 border border-slate-200/60 rounded-xl p-3.5 mb-4">
                    <div className="flex items-center justify-between mb-2 pb-2 border-b border-slate-200/80">
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 bg-emerald-100 text-emerald-800 text-xs rounded font-bold">
                          Hạng {driver.licenseClass}
                        </span>
                        <span className="text-xs text-slate-800 font-mono font-bold">{driver.licenseNo}</span>
                      </div>
                      <button
                        onClick={() =>
                          setPreviewData({
                            name: driver.fullName,
                            licenseNo: driver.licenseNo,
                            licenseClass: driver.licenseClass,
                            expiry: driver.licenseExpiry,
                            imageUrl: driver.licenseImageUrl,
                            status: driver.status,
                          })
                        }
                        className="text-emerald-700 hover:text-emerald-800 text-xs font-bold flex items-center gap-1 transition-colors cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[16px]">visibility</span>
                        Xem ảnh GPLX
                      </button>
                    </div>

                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-500">Hạn sử dụng:</span>
                      <span
                        className={`font-semibold flex items-center gap-1 ${
                          driver.status === 'REJECTED' ? 'text-rose-600' : 'text-slate-700'
                        }`}
                      >
                        <span className="material-symbols-outlined text-[14px]">
                          {driver.status === 'REJECTED' ? 'warning' : 'event_available'}
                        </span>
                        Hết hạn: {formatDate(driver.licenseExpiry)}
                      </span>
                    </div>
                  </div>

                  {/* Reject Reason Alert Box (when REJECTED) */}
                  {driver.status === 'REJECTED' && (
                    <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-xl p-3 mb-4 text-xs leading-relaxed">
                      <div className="flex items-start gap-2">
                        <span className="material-symbols-outlined text-[18px] text-rose-600 flex-shrink-0 mt-0.5">
                          error
                        </span>
                        <div>
                          <span className="font-bold block mb-0.5">Lý do từ chối (reject_reason):</span>
                          <span>
                            {driver.rejectReason ||
                              'Ảnh chụp GPLX bị mờ hoặc thông tin chưa chính xác, vui lòng chụp lại rõ nét.'}
                          </span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>

                {/* Card Footer Actions */}
                <div className="pt-3 border-t border-slate-100 flex items-center justify-between">
                  {driver.status === 'APPROVED' || driver.status === 'SUSPENDED' ? (
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={driver.status === 'APPROVED'}
                        onChange={() => handleToggleStatus(driver)}
                        className="sr-only peer"
                      />
                      <div className="w-9 h-5 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-emerald-600"></div>
                      <span className="ml-2 text-xs font-medium text-slate-500 select-none">
                        {driver.status === 'APPROVED' ? 'Hoạt động' : 'Tạm ngưng'}
                      </span>
                    </label>
                  ) : driver.status === 'PENDING' ? (
                    <span className="text-xs text-amber-700 font-semibold flex items-center gap-1">
                      <span className="material-symbols-outlined text-[16px]">schedule</span>
                      Chờ Admin duyệt
                    </span>
                  ) : (
                    <span className="text-xs text-rose-600 font-semibold flex items-center gap-1">
                      <span className="material-symbols-outlined text-[16px]">cancel</span>
                      Cần sửa đổi
                    </span>
                  )}

                  <button
                    onClick={() => handleOpenEditModal(driver)}
                    className="px-3.5 py-1.5 rounded-lg text-emerald-700 hover:bg-emerald-50 text-xs font-bold transition-colors flex items-center gap-1 cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[16px]">edit_document</span>
                    {driver.status === 'REJECTED' ? 'Cập nhật lại hồ sơ' : 'Sửa hồ sơ'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          /* TABLE VIEW */
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden mb-8">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-600">
                <thead className="bg-slate-50 text-slate-700 uppercase font-bold border-b border-slate-200">
                  <tr>
                    <th className="px-6 py-4">Tài xế</th>
                    <th className="px-6 py-4">Liên hệ</th>
                    <th className="px-6 py-4">GPLX & Hạng</th>
                    <th className="px-6 py-4">Ngày hết hạn</th>
                    <th className="px-6 py-4">Đánh giá</th>
                    <th className="px-6 py-4">Trạng thái</th>
                    <th className="px-6 py-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredDrivers.map((driver) => (
                    <tr key={driver.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <img
                            src={driver.portraitUrl}
                            alt=""
                            className="w-10 h-10 rounded-xl object-cover ring-1 ring-slate-200"
                            onError={(e) => {
                              e.target.onerror = null
                              e.target.src = 'https://ui-avatars.com/api/?name=' + encodeURIComponent(driver.fullName)
                            }}
                          />
                          <div>
                            <div className="font-bold text-slate-900">{driver.fullName}</div>
                            <div className="text-[11px] text-slate-400 font-mono">ID #{driver.id}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div>{driver.phone}</div>
                        <div className="text-slate-400">{driver.email}</div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="font-bold text-slate-800 font-mono mr-1">{driver.licenseNo}</span>
                        <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 font-semibold text-[11px]">
                          {driver.licenseClass}
                        </span>
                      </td>
                      <td className="px-6 py-4">{formatDate(driver.licenseExpiry)}</td>
                      <td className="px-6 py-4">
                        <span className="font-semibold text-slate-700">★ {Number(driver.ratingAvg || 0).toFixed(1)}</span>
                        <span className="text-slate-400 ml-1">({driver.ratingCount})</span>
                      </td>
                      <td className="px-6 py-4">
                        {driver.status === 'APPROVED' && (
                          <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-bold text-[11px]">
                            Hoạt động
                          </span>
                        )}
                        {driver.status === 'PENDING' && (
                          <span className="px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 font-bold text-[11px]">
                            Chờ duyệt
                          </span>
                        )}
                        {driver.status === 'REJECTED' && (
                          <span className="px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 font-bold text-[11px]">
                            Bị từ chối
                          </span>
                        )}
                        {driver.status === 'SUSPENDED' && (
                          <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 font-bold text-[11px]">
                            Tạm ngưng
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button
                          onClick={() => handleOpenEditModal(driver)}
                          className="px-3 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-bold text-xs transition-colors cursor-pointer mr-2"
                        >
                          Sửa
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Banner: Cơ chế Điều phối Linh hoạt */}
        <div className="bg-gradient-to-r from-white via-white to-emerald-50/60 rounded-3xl p-6 sm:p-8 shadow-sm border border-slate-200 flex flex-col md:flex-row items-start md:items-center justify-between gap-6 relative overflow-hidden">
          <div className="flex items-start gap-4 z-10">
            <div className="w-12 h-12 rounded-2xl bg-emerald-100 flex items-center justify-center text-emerald-700 flex-shrink-0 shadow-sm">
              <span className="material-symbols-outlined text-[28px]">hub</span>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <h4 className="text-base sm:text-lg font-bold text-slate-900">Cơ chế Điều phối Linh hoạt</h4>
                <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-extrabold uppercase tracking-wider">
                  Chuẩn EcoMotion
                </span>
              </div>
              <p className="text-xs sm:text-sm text-slate-600 max-w-4xl leading-relaxed">
                Tài xế <strong className="text-slate-800 font-semibold">không gán cố định</strong> vào bất kỳ phương tiện nào. Khi khách hàng đặt xe kèm tài xế, chủ xe có toàn quyền chọn điều phối bất kỳ tài xế nào có trạng thái <strong className="text-emerald-700 font-semibold">"ĐÃ DUYỆT (APPROVED)"</strong> và có hạng giấy phép lái xe phù hợp với phân khúc dòng xe điện.
              </p>
            </div>
          </div>
        </div>
      </main>

      {/* MODAL: Tạo hộ tài khoản tài xế / Cập nhật hồ sơ */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm transition-opacity duration-300">
          <div className="bg-white rounded-3xl shadow-2xl max-w-3xl w-full max-h-[92vh] overflow-y-auto transform transition-all flex flex-col">
            {/* Modal Header */}
            <div className="p-6 border-b border-slate-200 flex items-center justify-between sticky top-0 bg-white z-20">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-50 flex items-center justify-center text-emerald-700">
                  <span className="material-symbols-outlined text-[24px]">
                    {isEditing ? 'edit_document' : 'person_add'}
                  </span>
                </div>
                <div>
                  <h2 className="text-lg font-bold text-slate-900">
                    {isEditing ? 'Cập nhật Hồ sơ tài xế' : 'Tạo hộ tài khoản & Hồ sơ tài xế'}
                  </h2>
                  <p className="text-xs text-slate-500">
                    {isEditing
                      ? 'Điều chỉnh thông tin bằng lái và gửi Quản trị viên thẩm định lại nếu sửa đổi GPLX'
                      : 'Nhập thông tin ban đầu để gửi Quản trị viên thẩm định bằng lái'}
                  </p>
                </div>
              </div>
              <button
                onClick={handleCloseModal}
                className="w-9 h-9 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-700 flex items-center justify-center transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 space-y-6">
              {/* Notice Alert */}
              <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200/80 flex items-start gap-3">
                <span className="material-symbols-outlined text-amber-600 text-[20px] mt-0.5">info</span>
                <p className="text-xs text-amber-800 leading-relaxed">
                  Tài xế do nhà xe tạo hộ sẽ ở trạng thái <strong className="font-bold">Chờ duyệt (PENDING)</strong>. Sau khi Quản trị viên (Admin) thẩm định bằng lái, tài xế mới có thể nhận cuốc xe.
                </p>
              </div>

              <form onSubmit={handleSubmitForm}>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {/* Column 1: User Info */}
                  <div className="space-y-4">
                    <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-emerald-600"></span>
                      1. Thông tin tài khoản người dùng
                    </h3>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Họ và tên tài xế *
                      </label>
                      <input
                        type="text"
                        required
                        value={formFullName}
                        onChange={(e) => setFormFullName(e.target.value)}
                        placeholder="Ví dụ: Hoàng Minh Đức"
                        className="w-full bg-slate-50 border border-slate-200 px-3.5 py-2.5 rounded-xl text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500/40"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Email đăng nhập *
                      </label>
                      <input
                        type="email"
                        required
                        disabled={isEditing}
                        value={formEmail}
                        onChange={(e) => setFormEmail(e.target.value)}
                        placeholder="duc.driver@ecomotion.vn"
                        className={`w-full border px-3.5 py-2.5 rounded-xl text-sm ${
                          isEditing
                            ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed'
                            : 'bg-slate-50 text-slate-800 border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-500/40'
                        }`}
                      />
                      {isEditing && (
                        <p className="text-[11px] text-slate-400 mt-1">Email đăng nhập không thể thay đổi sau khi tạo.</p>
                      )}
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Số điện thoại *
                      </label>
                      <input
                        type="tel"
                        required
                        value={formPhone}
                        onChange={(e) => setFormPhone(e.target.value)}
                        placeholder="0987 654 321"
                        className="w-full bg-slate-50 border border-slate-200 px-3.5 py-2.5 rounded-xl text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500/40"
                      />
                    </div>

                    {!isEditing && (
                      <div>
                        <label className="block text-xs font-bold text-slate-700 mb-1.5">
                          Mật khẩu khởi tạo ban đầu
                        </label>
                        <div className="relative">
                          <input
                            type="text"
                            readOnly
                            value={formInitialPassword}
                            className="w-full bg-slate-100 border border-slate-200 px-3.5 py-2.5 rounded-xl font-mono text-sm text-slate-500 cursor-not-allowed"
                          />
                          <span className="material-symbols-outlined absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-[18px]">
                            lock
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-400 mt-1">
                          Tài xế sẽ được yêu cầu đổi mật khẩu ở lần đăng nhập đầu tiên.
                        </p>
                      </div>
                    )}

                    {/* Portrait Upload */}
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Ảnh chân dung đại diện (portrait_url) *
                      </label>
                      <input
                        type="file"
                        ref={portraitInputRef}
                        accept="image/png,image/jpeg,image/webp"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e.target.files[0], 'portrait')}
                      />
                      {formPortraitUrl ? (
                        <div className="relative rounded-2xl overflow-hidden border border-slate-200 group h-36 bg-slate-100 flex items-center justify-center">
                          <img src={formPortraitUrl} alt="Portrait preview" className="h-full w-full object-cover" />
                          <button
                            type="button"
                            onClick={() => portraitInputRef.current?.click()}
                            className="absolute inset-0 bg-slate-900/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white font-bold text-xs gap-1 cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-[18px]">cached</span>
                            Đổi ảnh khác
                          </button>
                        </div>
                      ) : (
                        <div
                          onClick={() => portraitInputRef.current?.click()}
                          className="border-2 border-dashed border-slate-300 rounded-2xl p-4 text-center hover:bg-slate-50 transition-colors cursor-pointer group"
                        >
                          {uploadingPortrait ? (
                            <div className="py-2 flex flex-col items-center justify-center">
                              <span className="material-symbols-outlined text-[28px] text-emerald-700 animate-spin">
                                progress_activity
                              </span>
                              <p className="text-xs text-slate-500 mt-1">Đang tải ảnh lên...</p>
                            </div>
                          ) : (
                            <>
                              <span className="material-symbols-outlined text-[32px] text-slate-400 group-hover:text-emerald-700 transition-colors">
                                add_a_photo
                              </span>
                              <p className="text-xs font-semibold text-slate-700 mt-1">Nhấp tải ảnh chân dung chính diện</p>
                              <p className="text-[11px] text-slate-400">PNG, JPG, WEBP tối đa 5MB</p>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Column 2: License Info */}
                  <div className="space-y-4">
                    <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-emerald-600"></span>
                      2. Hồ sơ Giấy phép lái xe (GPLX)
                    </h3>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Số GPLX (license_no) *
                      </label>
                      <input
                        type="text"
                        required
                        value={formLicenseNo}
                        onChange={(e) => setFormLicenseNo(e.target.value)}
                        placeholder="Ví dụ: 790184920194"
                        className="w-full bg-slate-50 border border-slate-200 px-3.5 py-2.5 rounded-xl font-mono text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500/40"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Hạng bằng lái (license_class) *
                      </label>
                      <select
                        required
                        value={formLicenseClass}
                        onChange={(e) => setFormLicenseClass(e.target.value)}
                        className="w-full bg-slate-50 border border-slate-200 px-3.5 py-2.5 rounded-xl text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500/40 cursor-pointer"
                      >
                        <option value="B1">Hạng B1 (Xe số tự động đến 9 chỗ)</option>
                        <option value="B2">Hạng B2 (Xe ô tô kinh doanh dưới 9 chỗ)</option>
                        <option value="C">Hạng C (Xe tải và ô tô trên 3.5 tấn)</option>
                        <option value="D">Hạng D (Chở người từ 10 đến 30 chỗ)</option>
                        <option value="E">Hạng E (Chở người trên 30 chỗ)</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Ngày hết hạn bằng lái (license_expiry) *
                      </label>
                      <input
                        type="date"
                        required
                        value={formLicenseExpiry}
                        onChange={(e) => setFormLicenseExpiry(e.target.value)}
                        className="w-full bg-slate-50 border border-slate-200 px-3.5 py-2.5 rounded-xl text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500/40 cursor-pointer"
                      />
                    </div>

                    {/* License Image Upload */}
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        Ảnh chụp bằng lái 2 mặt (license_image_url) *
                      </label>
                      <input
                        type="file"
                        ref={licenseInputRef}
                        accept="image/png,image/jpeg,image/webp"
                        className="hidden"
                        onChange={(e) => handleFileUpload(e.target.files[0], 'license')}
                      />
                      {formLicenseImageUrl ? (
                        <div className="relative rounded-2xl overflow-hidden border border-slate-200 group h-36 bg-slate-100 flex items-center justify-center">
                          <img src={formLicenseImageUrl} alt="License preview" className="h-full w-full object-cover" />
                          <button
                            type="button"
                            onClick={() => licenseInputRef.current?.click()}
                            className="absolute inset-0 bg-slate-900/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white font-bold text-xs gap-1 cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-[18px]">cached</span>
                            Đổi ảnh GPLX
                          </button>
                        </div>
                      ) : (
                        <div
                          onClick={() => licenseInputRef.current?.click()}
                          className="border-2 border-dashed border-slate-300 rounded-2xl p-4 text-center hover:bg-slate-50 transition-colors cursor-pointer group"
                        >
                          {uploadingLicense ? (
                            <div className="py-2 flex flex-col items-center justify-center">
                              <span className="material-symbols-outlined text-[28px] text-emerald-700 animate-spin">
                                progress_activity
                              </span>
                              <p className="text-xs text-slate-500 mt-1">Đang tải ảnh GPLX...</p>
                            </div>
                          ) : (
                            <>
                              <span className="material-symbols-outlined text-[32px] text-slate-400 group-hover:text-emerald-700 transition-colors">
                                badge
                              </span>
                              <p className="text-xs font-semibold text-slate-700 mt-1">
                                Kéo thả hoặc nhấn để tải ảnh mặt trước/sau
                              </p>
                              <p className="text-[11px] text-slate-400">Ảnh chụp rõ số và mộc đỏ kiểm định</p>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Modal Action Buttons */}
                <div className="pt-6 mt-6 border-t border-slate-200 flex items-center justify-end gap-3">
                  <button
                    type="button"
                    onClick={handleCloseModal}
                    className="px-5 py-2.5 rounded-xl text-xs font-bold text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={submitting}
                    className="bg-emerald-700 hover:bg-emerald-800 disabled:opacity-50 text-white text-xs font-bold px-6 py-2.5 rounded-xl shadow-md transition-all flex items-center gap-2 cursor-pointer"
                  >
                    {submitting ? (
                      <>
                        <span className="material-symbols-outlined text-[18px] animate-spin">progress_activity</span>
                        <span>Đang xử lý...</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[18px]">verified_user</span>
                        <span>{isEditing ? 'Cập nhật & Gửi duyệt lại' : 'Xác nhận & Gửi duyệt Admin'}</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* LIGHTBOX PREVIEW MODAL FOR GPLX */}
      {previewData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/70 backdrop-blur-sm">
          <div className="bg-white rounded-3xl shadow-2xl max-w-lg w-full p-6">
            <div className="flex items-center justify-between pb-4 border-b border-slate-200 mb-4">
              <div>
                <h3 className="text-base font-bold text-slate-900">{previewData.name}</h3>
                <p className="text-xs text-slate-500">
                  GPLX Hạng {previewData.licenseClass} - Số: {previewData.licenseNo}
                </p>
              </div>
              <button
                onClick={() => setPreviewData(null)}
                className="w-8 h-8 rounded-full hover:bg-slate-100 flex items-center justify-center text-slate-400 hover:text-slate-700 cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">close</span>
              </button>
            </div>

            <div className="rounded-2xl overflow-hidden bg-slate-100 mb-4 aspect-[16/10] relative flex items-center justify-center">
              <img
                src={previewData.imageUrl || 'https://lh3.googleusercontent.com/aida-public/AB6AXuDwAeaBGOzou7JdNsnsO4BmTXrG974YzwszeQhZB68gHcoEHWVnQz8GNzWsRIBDJfam7ATjixTkJLlDTrDPh71EcGN6HIZclYZhavztVCGWhPF3xM7i6EARO5yKgBFJy__tAggqgVp1fQXzwa9JOqAocHu21WcfWG8UwBM0I4Ou4ULq10ucD-A8gUU0e5edLuSOeU_5lSN1Gr6aYssYXmcA6f1w62hJe_Vk2Uyz-t3DFM-E-z14jH1J'}
                alt="License Document Preview"
                className="w-full h-full object-cover"
                onError={(e) => {
                  e.target.onerror = null
                  e.target.src = 'https://lh3.googleusercontent.com/aida-public/AB6AXuDwAeaBGOzou7JdNsnsO4BmTXrG974YzwszeQhZB68gHcoEHWVnQz8GNzWsRIBDJfam7ATjixTkJLlDTrDPh71EcGN6HIZclYZhavztVCGWhPF3xM7i6EARO5yKgBFJy__tAggqgVp1fQXzwa9JOqAocHu21WcfWG8UwBM0I4Ou4ULq10ucD-A8gUU0e5edLuSOeU_5lSN1Gr6aYssYXmcA6f1w62hJe_Vk2Uyz-t3DFM-E-z14jH1J'
                }}
              />
            </div>

            <div className="flex items-center justify-between text-xs text-slate-500">
              <span>Hết hạn: {formatDate(previewData.expiry)}</span>
              {previewData.status === 'APPROVED' ? (
                <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-bold text-[11px] flex items-center gap-1">
                  <span className="material-symbols-outlined text-[13px]">verified</span>
                  Đã duyệt GPLX
                </span>
              ) : (
                <span className="px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 font-bold text-[11px] flex items-center gap-1">
                  <span className="material-symbols-outlined text-[13px]">schedule</span>
                  Đang thẩm định
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Toast Notification */}
      {toast && (
        <Toast
          type={toast.type}
          message={toast.message}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  )
}
