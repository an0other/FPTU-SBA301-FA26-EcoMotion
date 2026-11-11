import { useState, useEffect, useCallback } from 'react'
import { adminService } from '../../services/adminService'
import {
  IconUsers,
  IconShield,
  IconSearch,
  IconCheckCircle,
  IconX,
} from '../../components/icons/Icons'
import ProviderDetailModal from './modals/ProviderDetailModal'
import DriverDetailModal from './modals/DriverDetailModal'
import EkycDetailModal from './modals/EkycDetailModal'
import UserDetailModal from './modals/UserDetailModal'
import AdminModalShell from './modals/AdminModalShell'
import AdminPopupModal from './modals/AdminPopupModal'

const ROLE_LABELS = {
  RENTER: 'Khách thuê',
  PROVIDER: 'Chủ xe / NCC',
  DRIVER: 'Tài xế',
  ADMIN: 'Quản trị viên',
}

const STATUS_BADGES = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  VERIFIED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  LOCKED: 'bg-rose-50 text-rose-700 border-rose-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
}

function resolveValidImageUrl(url) {
  if (!url || typeof url !== 'string') return null
  if (/^\/images\/(portrait|license)-\d+\.webp$/i.test(url.trim())) return null
  return url
}

export default function AdminUsersTab({
  initialSubTab = 'users',
  initialFilter = {},
  onStatsUpdate,
}) {
  const [subTab, setSubTab] = useState(initialSubTab)
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [message, setMessage] = useState(null)

  // Inspection Modals
  const [selectedUser, setSelectedUser] = useState(null)
  const [selectedProvider, setSelectedProvider] = useState(null)
  const [selectedDriver, setSelectedDriver] = useState(null)
  const [selectedEkyc, setSelectedEkyc] = useState(null)

  // SubTab 1: Users
  const [users, setUsers] = useState([])
  const [userFilters, setUserFilters] = useState({
    role: 'ALL',
    status: initialFilter.status || 'ALL',
    keyword: '',
  })

  // SubTab 2: Providers
  const [providers, setProviders] = useState([])
  const [providerStatus, setProviderStatus] = useState(initialFilter.status || 'ALL')

  // SubTab 3: Drivers
  const [drivers, setDrivers] = useState([])
  const [driverStatus, setDriverStatus] = useState(initialFilter.status || 'ALL')
  const [driverRejectModal, setDriverRejectModal] = useState(null)
  const [driverRejectReason, setDriverRejectReason] = useState('')

  // SubTab 4: eKYC
  const [ekycList, setEkycList] = useState([])
  const [ekycStatus, setEkycStatus] = useState(initialFilter.status || 'ALL')
  const [previewImage, setPreviewImage] = useState(null)
  const [popupModal, setPopupModal] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setMessage(null)
    try {
      if (subTab === 'users') {
        const data = await adminService.getUsers(userFilters)
        setUsers(data)
      } else if (subTab === 'providers') {
        const data = await adminService.getProviders(providerStatus)
        setProviders(data)
      } else if (subTab === 'drivers') {
        const data = await adminService.getDrivers(driverStatus)
        setDrivers(data)
      } else if (subTab === 'ekyc') {
        const data = await adminService.getEkyc(ekycStatus)
        setEkycList(data)
      }
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không thể tải dữ liệu' })
    } finally {
      setLoading(false)
    }
  }, [subTab, userFilters, providerStatus, driverStatus, ekycStatus])

  useEffect(() => {
    queueMicrotask(loadData)
  }, [loadData])

  // Deep-link / Cross-Navigation Helpers
  async function openUserById(userId, email) {
    setSelectedProvider(null)
    setSelectedDriver(null)
    setSelectedEkyc(null)
    setSubTab('users')
    setUserFilters({ role: 'ALL', status: 'ALL', keyword: '' })
    try {
      const list = await adminService.getUsers({})
      setUsers(list)
      const found = list.find(
        (u) =>
          (userId && Number(u.id) === Number(userId)) ||
          (email && u.email && u.email.toLowerCase() === email.toLowerCase()),
      )
      if (found) {
        setSelectedUser(found)
      }
    } catch (err) {
      console.error('Failed to open user detail:', err)
    }
  }

  async function openProviderById(providerId) {
    setSelectedUser(null)
    setSelectedDriver(null)
    setSelectedEkyc(null)
    setSubTab('providers')
    setProviderStatus('ALL')
    try {
      const list = await adminService.getProviders('ALL')
      setProviders(list)
      const found = list.find((p) => Number(p.id) === Number(providerId))
      if (found) {
        setSelectedProvider(found)
      }
    } catch (err) {
      console.error('Failed to open provider detail:', err)
    }
  }

  async function openDriverById(driverId, userId) {
    setSelectedUser(null)
    setSelectedProvider(null)
    setSelectedEkyc(null)
    setSubTab('drivers')
    setDriverStatus('ALL')
    try {
      const list = await adminService.getDrivers('ALL')
      setDrivers(list)
      const found = list.find(
        (d) =>
          (driverId && Number(d.id) === Number(driverId)) ||
          (userId && Number(d.userId) === Number(userId)),
      )
      if (found) {
        setSelectedDriver(found)
      }
    } catch (err) {
      console.error('Failed to open driver detail:', err)
    }
  }

  async function openEkycByUserId(userId) {
    setSelectedUser(null)
    setSelectedProvider(null)
    setSelectedDriver(null)
    setSubTab('ekyc')
    setEkycStatus('ALL')
    try {
      const list = await adminService.getEkyc('ALL')
      setEkycList(list)
      const found = list.find((e) => Number(e.userId) === Number(userId))
      if (found) {
        setSelectedEkyc(found)
      }
    } catch (err) {
      console.error('Failed to open eKYC detail:', err)
    }
  }

  // Watch incoming navigation target from AdminDashboard / other tabs
  useEffect(() => {
    let cancelled = false
    queueMicrotask(() => {
      if (cancelled) return
      if (initialFilter?.targetUserId || initialFilter?.targetUserEmail) {
        openUserById(initialFilter.targetUserId, initialFilter.targetUserEmail)
      } else if (initialFilter?.targetProviderId) {
        openProviderById(initialFilter.targetProviderId)
      } else if (initialFilter?.targetDriverId) {
        openDriverById(initialFilter.targetDriverId)
      } else if (initialFilter?.targetEkycUserId) {
        openEkycByUserId(initialFilter.targetEkycUserId)
      } else if (initialFilter?.subTab) {
        setSubTab(initialFilter.subTab)
        if (initialFilter.status) {
          if (initialFilter.subTab === 'providers') setProviderStatus(initialFilter.status)
          if (initialFilter.subTab === 'drivers') setDriverStatus(initialFilter.status)
          if (initialFilter.subTab === 'ekyc') setEkycStatus(initialFilter.status)
        }
      }
    })
    return () => { cancelled = true }
  }, [
    initialFilter?.navKey,
    initialFilter?.targetUserId,
    initialFilter?.targetUserEmail,
    initialFilter?.targetProviderId,
    initialFilter?.targetDriverId,
    initialFilter?.targetEkycUserId,
    initialFilter?.subTab,
    initialFilter?.status,
  ])

  // Handle User Status toggle (ACTIVE <-> LOCKED) via Popup Modal
  function handleToggleUserStatus(user) {
    const nextStatus = user.status === 'LOCKED' ? 'ACTIVE' : 'LOCKED'
    const isLocking = nextStatus === 'LOCKED'
    setPopupModal({
      mode: 'confirm',
      tone: isLocking ? 'danger' : 'success',
      title: isLocking ? 'Xác nhận khóa tài khoản' : 'Xác nhận mở khóa tài khoản',
      message: isLocking
        ? `Bạn có chắc muốn KHÓA tài khoản ${user.email}? Tài khoản này sẽ bị từ chối đăng nhập cho đến khi được mở khóa lại.`
        : `Bạn có muốn MỞ KHÓA cho tài khoản ${user.email}? Người dùng sẽ có thể đăng nhập lại bình thường.`,
      confirmText: isLocking ? 'Xác nhận khóa' : 'Xác nhận mở khóa',
      onConfirm: async () => {
        setActionLoading(true)
        try {
          await adminService.updateUserStatus(user.id, nextStatus)
          setMessage({ type: 'success', text: `Đã cập nhật trạng thái người dùng sang ${nextStatus}` })
          setPopupModal(null)
          setSelectedUser(null)
          loadData()
          if (onStatsUpdate) onStatsUpdate()
        } catch (err) {
          setMessage({ type: 'error', text: err.message || 'Lỗi khi cập nhật tài khoản' })
          setPopupModal({
            mode: 'alert',
            tone: 'error',
            title: 'Không thể cập nhật tài khoản',
            message: err.message || 'Lỗi khi cập nhật tài khoản.',
          })
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  // Handle Provider Approval / Lock
  async function handleUpdateProvider(id, status, displayName = '') {
    const normalizedStatus = status === 'REJECTED' ? 'LOCKED' : status
    if (normalizedStatus === 'LOCKED') {
      const targetName = displayName ? `"${displayName}"` : `#${id}`
      setPopupModal({
        mode: 'confirm',
        tone: 'danger',
        title: 'Xác nhận khóa nhà cung cấp',
        message: `Bạn có chắc muốn từ chối / khóa nhà cung cấp ${targetName}?`,
        confirmText: 'Khóa nhà cung cấp',
        cancelText: 'Hủy',
        onConfirm: async () => {
          setActionLoading(true)
          try {
            await adminService.updateProviderStatus(id, 'LOCKED')
            setMessage({ type: 'success', text: 'Đã khóa nhà cung cấp thành công' })
            setPopupModal(null)
            setSelectedProvider(null)
            loadData()
            if (onStatsUpdate) onStatsUpdate()
          } catch (err) {
            setMessage({ type: 'error', text: err.message || 'Lỗi cập nhật nhà cung cấp' })
            setPopupModal({
              mode: 'alert',
              tone: 'error',
              title: 'Không thể cập nhật nhà cung cấp',
              message: err.message || 'Lỗi cập nhật nhà cung cấp.',
            })
          } finally {
            setActionLoading(false)
          }
        },
      })
      return
    }

    setActionLoading(true)
    try {
      await adminService.updateProviderStatus(id, normalizedStatus)
      setMessage({ type: 'success', text: 'Đã phê duyệt nhà cung cấp thành công' })
      setSelectedProvider(null)
      loadData()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi cập nhật nhà cung cấp' })
    } finally {
      setActionLoading(false)
    }
  }

  // Handle Driver Approval / Rejection
  async function handleApproveDriver(driverId) {
    setActionLoading(true)
    try {
      await adminService.updateDriverStatus(driverId, 'APPROVED')
      setMessage({ type: 'success', text: 'Đã phê duyệt tài xế thành công' })
      loadData()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi phê duyệt tài xế' })
    } finally {
      setActionLoading(false)
    }
  }

  async function handleConfirmRejectDriver(e) {
    e.preventDefault()
    if (!driverRejectReason.trim()) {
      setPopupModal({
        mode: 'alert',
        tone: 'warning',
        title: 'Thiếu lý do từ chối',
        message: 'Vui lòng nhập lý do từ chối.',
      })
      return
    }
    setActionLoading(true)
    try {
      await adminService.updateDriverStatus(driverRejectModal.id, 'REJECTED', driverRejectReason.trim())
      setMessage({ type: 'success', text: 'Đã từ chối tài xế với lý do đã nhập' })
      setDriverRejectModal(null)
      setSelectedDriver(null)
      setDriverRejectReason('')
      loadData()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi khi từ chối tài xế' })
    } finally {
      setActionLoading(false)
    }
  }

  // Handle eKYC Approval
  async function handleUpdateEkyc(userId, status, ekycItem = null) {
    const targetEkyc =
      ekycItem ||
      (selectedEkyc && Number(selectedEkyc.userId) === Number(userId) ? selectedEkyc : null) ||
      ekycList.find((item) => Number(item.userId) === Number(userId))

    if (status === 'VERIFIED' && targetEkyc) {
      const missingFields = []
      if (!targetEkyc.cccdNo || !String(targetEkyc.cccdNo).trim()) missingFields.push('Số CCCD/CMND')
      if (!targetEkyc.dateOfBirth) missingFields.push('Ngày sinh')
      if (!targetEkyc.gender || !String(targetEkyc.gender).trim()) missingFields.push('Giới tính')
      if (!targetEkyc.address || !String(targetEkyc.address).trim()) missingFields.push('Địa chỉ thường trú')
      if (!targetEkyc.cccdImageUrl) missingFields.push('Ảnh mặt trước CCCD')
      if (!targetEkyc.portraitUrl) missingFields.push('Ảnh chân dung đối chiếu')

      if (missingFields.length > 0) {
        const errorText = `Không thể xác nhận eKYC hợp lệ: Hồ sơ đang trống thông tin bắt buộc (${missingFields.join(', ')}). Vui lòng từ chối hồ sơ hoặc yêu cầu khách thuê bổ sung đầy đủ!`
        setMessage({ type: 'error', text: errorText })
        setPopupModal({
          mode: 'alert',
          tone: 'error',
          title: 'Không thể duyệt hồ sơ eKYC thiếu thông tin',
          message: errorText,
          confirmText: 'Đã hiểu',
        })
        return false
      }
    }

    setActionLoading(true)
    try {
      await adminService.updateEkycStatus(userId, status)
      setMessage({ type: 'success', text: `Đã cập nhật trạng thái eKYC: ${status}` })
      loadData()
      if (onStatsUpdate) onStatsUpdate()
      return true
    } catch (err) {
      const errMsg = err.message || 'Lỗi xử lý eKYC'
      setMessage({ type: 'error', text: errMsg })
      setPopupModal({
        mode: 'alert',
        tone: 'error',
        title: 'Không thể cập nhật trạng thái eKYC',
        message: errMsg,
        confirmText: 'Đóng',
      })
      return false
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Sub Navigation Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-4">
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setSubTab('users')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'users'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconUsers className="h-4 w-4" />
            <span>Tất cả người dùng</span>
          </button>

          <button
            type="button"
            onClick={() => setSubTab('providers')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'providers'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconShield className="h-4 w-4" />
            <span>Duyệt Nhà cung cấp</span>
          </button>

          <button
            type="button"
            onClick={() => setSubTab('drivers')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'drivers'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconUsers className="h-4 w-4" />
            <span>Duyệt Tài xế</span>
          </button>

          <button
            type="button"
            onClick={() => setSubTab('ekyc')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'ekyc'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconCheckCircle className="h-4 w-4" />
            <span>Duyệt hồ sơ eKYC</span>
          </button>
        </div>

        <button
          type="button"
          onClick={loadData}
          disabled={loading}
          className="rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 transition-all hover:bg-slate-50"
        >
          {loading ? 'Đang tải...' : 'Tải lại danh sách'}
        </button>
      </div>

      {/* Inline Feedback Message */}
      {message && (
        <div
          className={`flex items-center justify-between gap-3 rounded-xl border p-4 text-xs font-medium ${
            message.type === 'error'
              ? 'border-red-200 bg-red-50 text-red-800'
              : 'border-emerald-200 bg-emerald-50 text-emerald-800'
          }`}
        >
          <span>{message.text}</span>
          <button type="button" onClick={() => setMessage(null)} className="p-1 hover:opacity-75">
            <IconX className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* SubTab 1: ALL USERS */}
      {subTab === 'users' && (
        <div className="space-y-4">
          {/* Filters */}
          <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-card">
            <div className="relative min-w-[220px] flex-1">
              <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                <IconSearch className="h-4 w-4" />
              </span>
              <input
                type="text"
                placeholder="Tìm theo email, tên, số điện thoại..."
                value={userFilters.keyword}
                onChange={(e) => setUserFilters((prev) => ({ ...prev, keyword: e.target.value }))}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 pl-9 pr-3 text-xs outline-none focus:border-brand-500 focus:bg-white"
              />
            </div>

            <select
              value={userFilters.role}
              onChange={(e) => setUserFilters((prev) => ({ ...prev, role: e.target.value }))}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 outline-none"
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="RENTER">Khách thuê (RENTER)</option>
              <option value="PROVIDER">Nhà cung cấp (PROVIDER)</option>
              <option value="DRIVER">Tài xế (DRIVER)</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
            </select>

            <select
              value={userFilters.status}
              onChange={(e) => setUserFilters((prev) => ({ ...prev, status: e.target.value }))}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 outline-none"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">ACTIVE (Hoạt động)</option>
              <option value="LOCKED">LOCKED (Bị khóa)</option>
              <option value="PENDING">PENDING (Chờ duyệt/kích hoạt)</option>
            </select>
          </div>

          {/* Users Table */}
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-slate-200 bg-slate-50 font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3.5">ID</th>
                    <th className="px-5 py-3.5">Họ và tên</th>
                    <th className="px-5 py-3.5">Email</th>
                    <th className="px-5 py-3.5">Số điện thoại</th>
                    <th className="px-5 py-3.5">Vai trò</th>
                    <th className="px-5 py-3.5">Trạng thái</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="py-8 text-center text-slate-400">
                        {loading ? 'Đang tải dữ liệu...' : 'Không tìm thấy người dùng nào phù hợp'}
                      </td>
                    </tr>
                  ) : (
                    users.map((u) => (
                      <tr key={u.id} className="transition-colors hover:bg-slate-50/70">
                        <td className="px-5 py-3.5 font-bold text-slate-900">#{u.id}</td>
                        <td className="px-5 py-3.5 font-semibold text-slate-800">
                          <div
                            onClick={() => setSelectedUser(u)}
                            className="flex cursor-pointer items-center gap-2.5 group"
                          >
                            <div className="h-8 w-8 shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-slate-100 shadow-2xs">
                              {resolveValidImageUrl(u.avatarUrl || u.portraitUrl) ? (
                                <img
                                  src={resolveValidImageUrl(u.avatarUrl || u.portraitUrl)}
                                  alt={u.fullName}
                                  className="h-full w-full object-cover"
                                  onError={(e) => { e.currentTarget.style.display = 'none' }}
                                />
                              ) : (
                                <div className="flex h-full w-full items-center justify-center text-slate-400">
                                  <IconUsers className="h-4 w-4" />
                                </div>
                              )}
                            </div>
                            <span className="font-bold text-slate-900 group-hover:text-brand-600 group-hover:underline">
                              {u.fullName || '—'}
                            </span>
                          </div>
                        </td>
                        <td className="px-5 py-3.5 text-slate-600">{u.email}</td>
                        <td className="px-5 py-3.5 text-slate-600">{u.phone || '—'}</td>
                        <td className="px-5 py-3.5">
                          <span className="inline-block rounded-md bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-700">
                            {ROLE_LABELS[u.role] || u.role}
                          </span>
                        </td>
                        <td className="px-5 py-3.5">
                          <span
                            className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] font-bold tracking-wider ${
                              STATUS_BADGES[u.status] || 'bg-slate-100 text-slate-700 border-slate-200'
                            }`}
                          >
                            {u.status}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 text-right whitespace-nowrap">
                          <button
                            type="button"
                            onClick={() => setSelectedUser(u)}
                            className="mr-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                          >
                            Chi tiết
                          </button>
                          {u.role !== 'ADMIN' && (
                            <button
                              type="button"
                              disabled={actionLoading}
                              onClick={() => handleToggleUserStatus(u)}
                              className={`rounded-lg px-3 py-1.5 text-xs font-bold transition-all ${
                                u.status === 'LOCKED'
                                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100'
                                  : 'bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100'
                              }`}
                            >
                              {u.status === 'LOCKED' ? 'Mở khóa' : 'Khóa user'}
                            </button>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* SubTab 2: PROVIDERS APPROVAL */}
      {subTab === 'providers' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-card">
            <span className="text-xs font-bold text-slate-600">Lọc theo trạng thái phê duyệt:</span>
            <div className="flex gap-2">
              {['ALL', 'PENDING', 'APPROVED', 'LOCKED'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setProviderStatus(st)}
                  className={`rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                    providerStatus === st
                      ? 'bg-brand-600 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {st === 'ALL' ? 'Tất cả' : st}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {providers.length === 0 ? (
              <div className="col-span-2 rounded-2xl border border-slate-200 bg-white p-12 text-center text-slate-400">
                {loading ? 'Đang tải danh sách nhà cung cấp...' : 'Không có nhà cung cấp nào ở trạng thái này'}
              </div>
            ) : (
              providers.map((p) => (
                <div
                  key={p.id}
                  className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-card"
                >
                  <div className="space-y-2.5">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h4 className="text-base font-bold text-slate-900">
                          {p.displayName || p.brandName || 'Đối tác cá nhân'}
                        </h4>
                        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                          <span>Mã NCC #{p.id} •</span>
                          {p.userId ? (
                            <button
                              type="button"
                              onClick={() => openUserById(p.userId)}
                              className="font-semibold text-brand-700 hover:underline"
                            >
                              Đại diện: {p.userFullName || `User #${p.userId}`} →
                            </button>
                          ) : (
                            <span>User #{p.userId}</span>
                          )}
                        </div>
                      </div>
                      <span
                        className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] font-bold tracking-wider ${
                          STATUS_BADGES[p.status] || 'bg-slate-100 text-slate-700 border-slate-200'
                        }`}
                      >
                        {p.status}
                      </span>
                    </div>

                    <div className="space-y-1.5 rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600">
                      <div><strong className="text-slate-800">Mã số thuế:</strong> {p.taxCode || '—'}</div>
                      <div><strong className="text-slate-800">Email:</strong> {p.userEmail || p.email || '—'}</div>
                      <div><strong className="text-slate-800">Hotline:</strong> {p.hotline || p.userPhone || p.phone || '—'}</div>
                      <div><strong className="text-slate-800">Địa chỉ:</strong> {p.depotAddress || p.address || '—'}</div>
                    </div>
                  </div>

                  <div className="mt-4 flex items-center justify-end gap-2 border-t border-slate-100 pt-3">
                    <button
                      type="button"
                      onClick={() => setSelectedProvider(p)}
                      className="rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                    >
                      Xem chi tiết hồ sơ
                    </button>
                    {p.status !== 'APPROVED' && (
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => handleUpdateProvider(p.id, 'APPROVED', p.displayName)}
                        className="rounded-xl bg-emerald-600 px-3.5 py-2 text-xs font-bold text-white transition-all hover:bg-emerald-700"
                      >
                        {p.status === 'LOCKED' ? 'Mở khóa đối tác' : 'Duyệt đối tác'}
                      </button>
                    )}
                    {p.status !== 'LOCKED' && (
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => handleUpdateProvider(p.id, 'LOCKED', p.displayName)}
                        className="rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2 text-xs font-bold text-rose-700 transition-all hover:bg-rose-100"
                      >
                        {p.status === 'PENDING' ? 'Từ chối / Khóa' : 'Tạm khóa'}
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* SubTab 3: DRIVERS APPROVAL */}
      {subTab === 'drivers' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-card">
            <span className="text-xs font-bold text-slate-600">Lọc theo trạng thái duyệt tài xế:</span>
            <div className="flex gap-2">
              {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setDriverStatus(st)}
                  className={`rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                    driverStatus === st
                      ? 'bg-brand-600 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {st === 'ALL' ? 'Tất cả' : st}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {drivers.length === 0 ? (
              <div className="col-span-3 rounded-2xl border border-slate-200 bg-white p-12 text-center text-slate-400">
                {loading ? 'Đang tải danh sách tài xế...' : 'Không có tài xế nào'}
              </div>
            ) : (
              drivers.map((d) => (
                <div
                  key={d.id}
                  className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-5 shadow-card"
                >
                  <div className="space-y-3">
                    <div className="flex items-center gap-3">
                      <div className="h-14 w-14 shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-slate-100">
                        {resolveValidImageUrl(d.portraitUrl) ? (
                          <img
                            src={resolveValidImageUrl(d.portraitUrl)}
                            alt={d.userFullName || d.fullName || 'Tài xế'}
                            className="h-full w-full object-cover cursor-pointer hover:opacity-90"
                            onClick={() => setPreviewImage(resolveValidImageUrl(d.portraitUrl))}
                            onError={(e) => { e.currentTarget.style.display = 'none' }}
                          />
                        ) : (
                          <div className="flex h-full w-full items-center justify-center text-slate-400">
                            <IconUsers className="h-6 w-6" />
                          </div>
                        )}
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-1">
                          <h4 className="truncate text-sm font-bold text-slate-900">
                            {d.userFullName || d.fullName || 'Tài xế'}
                          </h4>
                          <span
                            className={`inline-block rounded-full border px-2 py-0.5 text-[10px] font-bold ${
                              STATUS_BADGES[d.status] || 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {d.status}
                          </span>
                        </div>
                        <div className="truncate text-xs text-slate-500">{d.userEmail || d.email}</div>
                        <div className="text-xs text-slate-500">SĐT: {d.userPhone || d.phone || '—'}</div>
                      </div>
                    </div>

                    <div className="space-y-1 rounded-xl border border-slate-100 bg-slate-50 p-2.5 text-xs text-slate-600">
                      <div><strong className="text-slate-800">Số GPLX:</strong> {d.licenseNo || d.licenseNumber || '—'}</div>
                      <div><strong className="text-slate-800">Hạng bằng:</strong> {d.licenseClass || '—'}</div>
                      <div><strong className="text-slate-800">Hết hạn:</strong> {d.licenseExpiry || '—'}</div>
                      {d.providerId && (
                        <div className="flex items-center gap-1">
                          <strong className="text-slate-800">Đơn vị chủ quản:</strong>
                          <button
                            type="button"
                            onClick={() => openProviderById(d.providerId)}
                            className="font-bold text-indigo-700 hover:underline truncate"
                          >
                            {d.providerDisplayName || `NCC #${d.providerId}`} →
                          </button>
                        </div>
                      )}
                      {d.rejectReason && (
                        <div className="text-red-700">
                          <strong>Lý do từ chối:</strong> {d.rejectReason}
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="mt-4 flex items-center justify-end gap-2 border-t border-slate-100 pt-3">
                    <button
                      type="button"
                      onClick={() => setSelectedDriver(d)}
                      className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                    >
                      Xem chi tiết hồ sơ
                    </button>
                    {d.status !== 'APPROVED' && (
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => handleApproveDriver(d.id)}
                        className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white transition-all hover:bg-emerald-700"
                      >
                        Phê duyệt
                      </button>
                    )}
                    {d.status !== 'REJECTED' && (
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => {
                          setDriverRejectModal(d)
                          setDriverRejectReason('')
                        }}
                        className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-bold text-rose-700 transition-all hover:bg-rose-100"
                      >
                        Từ chối
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* SubTab 4: EKYC APPROVAL */}
      {subTab === 'ekyc' && (
        <div className="space-y-4">
          <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-card sm:flex-row sm:items-center sm:justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 block">
                Thẩm định hồ sơ định danh eKYC Khách thuê (Renter)
              </span>
              <span className="text-[11px] text-slate-500">
                Áp dụng khi khách thuê cập nhật lại giấy tờ CCCD / ảnh chân dung hoặc khi hệ thống chuyển thẩm định thủ công.
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-bold text-slate-600">Trạng thái:</span>
              {['ALL', 'PENDING', 'VERIFIED', 'REJECTED'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setEkycStatus(st)}
                  className={`rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                    ekycStatus === st
                      ? 'bg-brand-600 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {st === 'ALL' ? 'Tất cả' : st}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6">
            {ekycList.length === 0 ? (
              <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center text-slate-400">
                {loading ? 'Đang tải hồ sơ eKYC...' : 'Không có hồ sơ eKYC nào ở trạng thái này'}
              </div>
            ) : (
              ekycList.map((e) => (
                <div
                  key={e.userId}
                  className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 bg-slate-50/70 px-6 py-4">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h4 className="text-base font-bold text-slate-900">{e.fullName || 'Khách thuê'}</h4>
                        {(e.ekycStatus || e.status) === 'PENDING' && (
                          <span className="rounded-md border border-indigo-200 bg-indigo-50 px-2 py-0.5 text-[10px] font-bold text-indigo-700">
                            Cập nhật hồ sơ / Chờ thẩm định CCCD
                          </span>
                        )}
                      </div>
                      <span className="text-xs text-slate-500 flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => openUserById(e.userId)}
                          className="font-bold text-brand-700 hover:underline"
                        >
                          User ID #{e.userId} →
                        </button>
                        <span>• Email: {e.email} • SĐT: {e.phone || '—'}</span>
                      </span>
                    </div>

                    <span
                      className={`inline-block rounded-full border px-3 py-1 text-xs font-bold tracking-wider ${
                        STATUS_BADGES[e.ekycStatus || e.status] || 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      eKYC: {e.ekycStatus || e.status}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 gap-6 p-6 lg:grid-cols-3">
                    {/* Identity Data */}
                    <div className="space-y-2 rounded-xl border border-slate-100 bg-slate-50/50 p-4 text-xs min-w-0">
                      <div className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                        Thông tin giấy tờ
                      </div>
                      <div className="break-all">
                        <strong className="text-slate-800">Số CCCD / CMND:</strong>{' '}
                        <span className="font-mono font-semibold text-slate-900">{e.cccdNo || '—'}</span>
                      </div>
                      <div><strong className="text-slate-800">Ngày sinh:</strong> {e.dateOfBirth || '—'}</div>
                      <div><strong className="text-slate-800">Giới tính:</strong> {e.gender || '—'}</div>
                      <div className="break-words"><strong className="text-slate-800">Địa chỉ thường trú:</strong> {e.address || '—'}</div>
                    </div>

                    {/* CCCD Photos */}
                    <div className="col-span-2 space-y-2">
                      <div className="text-xs font-bold uppercase tracking-wider text-slate-500">
                        Hình ảnh đối chiếu (Bấm để xem phóng to)
                      </div>
                      <div className="grid grid-cols-3 gap-3">
                        {/* CCCD Front */}
                        <div
                          onClick={() => e.cccdImageUrl && setPreviewImage(e.cccdImageUrl)}
                          className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100 hover:border-brand-500"
                        >
                          {e.cccdImageUrl ? (
                            <img src={e.cccdImageUrl} alt="CCCD Mặt trước" className="h-full w-full object-cover" />
                          ) : (
                            <span className="text-[11px] text-slate-400">Không có ảnh mặt trước</span>
                          )}
                          <span className="absolute bottom-1 rounded bg-black/60 px-1.5 text-[10px] text-white">
                            Mặt trước
                          </span>
                        </div>

                        {/* CCCD Back */}
                        <div
                          onClick={() => e.cccdImageBackUrl && setPreviewImage(e.cccdImageBackUrl)}
                          className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100 hover:border-brand-500"
                        >
                          {e.cccdImageBackUrl ? (
                            <img src={e.cccdImageBackUrl} alt="CCCD Mặt sau" className="h-full w-full object-cover" />
                          ) : (
                            <span className="text-[11px] text-slate-400">Không có ảnh mặt sau</span>
                          )}
                          <span className="absolute bottom-1 rounded bg-black/60 px-1.5 text-[10px] text-white">
                            Mặt sau
                          </span>
                        </div>

                        {/* Portrait */}
                        <div
                          onClick={() => e.portraitUrl && setPreviewImage(e.portraitUrl)}
                          className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100 hover:border-brand-500"
                        >
                          {e.portraitUrl ? (
                            <img src={e.portraitUrl} alt="Ảnh chân dung" className="h-full w-full object-cover" />
                          ) : (
                            <span className="text-[11px] text-slate-400">Không có chân dung</span>
                          )}
                          <span className="absolute bottom-1 rounded bg-black/60 px-1.5 text-[10px] text-white">
                            Chân dung
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center justify-end gap-3 border-t border-slate-100 bg-slate-50/30 px-6 py-3.5">
                    <button
                      type="button"
                      onClick={() => setSelectedEkyc(e)}
                      className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                    >
                      Thẩm định chi tiết
                    </button>
                    {(e.ekycStatus || e.status) !== 'VERIFIED' && (
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => handleUpdateEkyc(e.userId, 'VERIFIED', e)}
                        className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-bold text-white shadow-xs transition-all hover:bg-emerald-700"
                      >
                        Xác nhận eKYC hợp lệ
                      </button>
                    )}
                    {(e.ekycStatus || e.status) !== 'REJECTED' && (
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => handleUpdateEkyc(e.userId, 'REJECTED', e)}
                        className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-xs font-bold text-rose-700 transition-all hover:bg-rose-100"
                      >
                        Từ chối eKYC
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Driver Reject Reason Modal */}
      {driverRejectModal && (
        <AdminModalShell
          maxWidth="max-w-md"
          zIndex="z-60"
          panelClassName="rounded-2xl border border-slate-200 bg-white shadow-xl"
          header={(
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-900">Từ chối tài xế</h3>
              <button
                type="button"
                onClick={() => setDriverRejectModal(null)}
                className="rounded-lg p-1 text-slate-400 hover:text-slate-600"
              >
                <IconX className="h-5 w-5" />
              </button>
            </div>
          )}
          footer={(
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setDriverRejectModal(null)}
                className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
              >
                Hủy
              </button>
              <button
                type="submit"
                form="admin-driver-reject-form"
                disabled={actionLoading}
                className="rounded-xl bg-rose-600 px-4 py-2 text-xs font-bold text-white transition-all hover:bg-rose-700 disabled:opacity-50"
              >
                {actionLoading ? 'Đang gửi...' : 'Xác nhận từ chối'}
              </button>
            </div>
          )}
        >
          <form id="admin-driver-reject-form" noValidate onSubmit={handleConfirmRejectDriver} className="space-y-3">
            <p className="text-xs text-slate-600">
              Nhập lý do từ chối hồ sơ của tài xế <strong>{driverRejectModal.userFullName || driverRejectModal.fullName}</strong>. Lý do sẽ được lưu vào hệ thống và hiển thị cho tài xế/chủ xe.
            </p>

            <div>
              <label className="mb-1 block text-xs font-bold text-slate-700">
                Lý do từ chối <span className="text-red-500">*</span>
              </label>
              <textarea
                rows="3"
                required
                placeholder="Ví dụ: Giấy phép lái xe hết hạn, ảnh chân dung không rõ mặt..."
                value={driverRejectReason}
                onChange={(e) => setDriverRejectReason(e.target.value)}
                className="w-full rounded-xl border border-slate-300 p-2.5 text-xs outline-none focus:border-rose-500 focus:ring-1 focus:ring-rose-500"
              />
            </div>
          </form>
        </AdminModalShell>
      )}

      {/* Image Preview Modal */}
      {previewImage && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center bg-black/80 p-4"
          onClick={() => setPreviewImage(null)}
        >
          <div className="relative max-h-[90vh] max-w-[90vw] overflow-hidden rounded-2xl bg-white p-2">
            <button
              type="button"
              onClick={() => setPreviewImage(null)}
              className="absolute right-3 top-3 rounded-full bg-black/70 p-2 text-white hover:bg-black"
            >
              <IconX className="h-5 w-5" />
            </button>
            <img src={previewImage} alt="Xem phóng to" className="max-h-[85vh] max-w-[85vw] object-contain" />
          </div>
        </div>
      )}

      {/* Dedicated Inspection Modals */}
      {selectedUser && (
        <UserDetailModal
          user={selectedUser}
          onClose={() => setSelectedUser(null)}
          onToggleStatus={(u) => handleToggleUserStatus(u)}
          actionLoading={actionLoading}
          onPreviewImage={(url) => setPreviewImage(url)}
          onViewDriverDetail={(u) => openDriverById(u.driverId, u.id)}
          onViewProviderDetail={(providerId) => openProviderById(providerId)}
          onViewEkycDetail={(userId) => openEkycByUserId(userId)}
        />
      )}

      {selectedProvider && (
        <ProviderDetailModal
          provider={selectedProvider}
          onClose={() => setSelectedProvider(null)}
          onStatusUpdate={(id, status) => handleUpdateProvider(id, status, selectedProvider.displayName)}
          actionLoading={actionLoading}
          onSelectUser={(userId) => openUserById(userId)}
        />
      )}

      {selectedDriver && (
        <DriverDetailModal
          driver={selectedDriver}
          onClose={() => setSelectedDriver(null)}
          onApprove={async (id) => {
            await handleApproveDriver(id)
            setSelectedDriver(null)
          }}
          onRejectClick={(d) => {
            setDriverRejectModal(d)
            setDriverRejectReason('')
          }}
          actionLoading={actionLoading}
          onPreviewImage={(url) => setPreviewImage(url)}
          onSelectProvider={(providerId) => openProviderById(providerId)}
          onSelectUser={(userId) => openUserById(userId)}
        />
      )}

      {selectedEkyc && (
        <EkycDetailModal
          ekyc={selectedEkyc}
          onClose={() => setSelectedEkyc(null)}
          onStatusUpdate={async (userId, status) => {
            const ok = await handleUpdateEkyc(userId, status, selectedEkyc)
            if (ok) {
              setSelectedEkyc(null)
            }
          }}
          actionLoading={actionLoading}
          onPreviewImage={(url) => setPreviewImage(url)}
          onSelectUser={(userId) => openUserById(userId)}
        />
      )}

      <AdminPopupModal
        open={Boolean(popupModal)}
        mode={popupModal?.mode}
        tone={popupModal?.tone}
        title={popupModal?.title}
        message={popupModal?.message}
        confirmText={popupModal?.confirmText}
        cancelText={popupModal?.cancelText}
        loading={actionLoading}
        onClose={() => setPopupModal(null)}
        onConfirm={popupModal?.onConfirm}
      />
    </div>
  )
}
