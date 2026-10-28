import { useEffect, useId, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import {
  IconBell,
  IconChevronDown,
  IconLayoutDashboard,
  IconList,
  IconMap,
  IconMapPin,
  IconSearch,
  IconUser,
  IconX,
  IconZap,
} from './icons/Icons'

const ROLE_LABELS = {
  RENTER: 'Khách thuê',
  PROVIDER: 'Chủ xe',
  DRIVER: 'Tài xế',
  ADMIN: 'Quản trị',
}

function initialsOf(user) {
  const source = user?.fullName?.trim() || user?.email || ''
  if (!source) return 'EM'
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

const policyLinkClass = ({ isActive }) =>
  `hidden whitespace-nowrap items-center rounded-lg border px-3 py-1.5 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 sm:inline-flex ${
    isActive
      ? 'border-brand-200 bg-brand-50 text-brand-700'
      : 'border-slate-200 bg-white text-slate-600 hover:border-brand-200 hover:bg-brand-50 hover:text-brand-700'
  }`

const panelItemClass = ({ isActive }) =>
  `flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
    isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-700 hover:bg-slate-50'
  }`

function AccountDisclosure({ user, onLogout }) {
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const buttonRef = useRef(null)
  const location = useLocation()
  const panelId = useId()

  useEffect(() => {
    setOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!open) return undefined

    function onPointerDown(event) {
      if (wrapRef.current && !wrapRef.current.contains(event.target)) {
        setOpen(false)
      }
    }

    function onKeyDown(event) {
      if (event.key !== 'Escape') return
      setOpen(false)
      buttonRef.current?.focus()
    }

    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={wrapRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        aria-haspopup="true"
        aria-controls={panelId}
        aria-label="Tài khoản"
        onClick={() => setOpen((current) => !current)}
        className={`flex items-center gap-2 rounded-full border py-1 pl-1 pr-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 ${
          open
            ? 'border-slate-200 bg-slate-50'
            : 'border-transparent hover:border-slate-200 hover:bg-slate-50'
        }`}
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100 text-[12px] font-bold text-brand-700 ring-2 ring-brand-500/20">
          {initialsOf(user)}
        </span>
        <span className="hidden text-left xl:block">
          <span className="block max-w-[140px] truncate text-xs font-bold leading-tight text-slate-800">
            {user.fullName || user.email}
          </span>
          <span className="inline-block rounded border border-brand-200/60 bg-brand-50 px-1.5 text-[10px] font-semibold text-brand-700">
            {ROLE_LABELS[user.role] ?? 'Thành viên'}
          </span>
        </span>
        <IconChevronDown
          className={`hidden h-4 w-4 shrink-0 text-slate-400 transition-transform duration-150 xl:block ${
            open ? 'rotate-180' : ''
          }`}
        />
      </button>

      {open ? (
        <div
          id={panelId}
          className="absolute right-0 top-full z-40 mt-1.5 w-60 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-lg"
        >
          <div className="border-b border-slate-100 px-3 py-2.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
              Tài khoản
            </p>
            {user.email ? (
              <p className="mt-0.5 truncate text-xs font-medium text-slate-600">{user.email}</p>
            ) : null}
          </div>
          <div className="p-1.5">
            {user.role === 'ADMIN' ? (
              <NavLink to="/admin" end className={panelItemClass} onClick={() => setOpen(false)}>
                <IconLayoutDashboard className="h-4 w-4 shrink-0 text-brand-600" />
                Dashboard
              </NavLink>
            ) : null}
            {user.role === 'RENTER' ? (
              <NavLink to="/renter/bookings" end className={panelItemClass} onClick={() => setOpen(false)}>
                <IconList className="h-4 w-4 shrink-0 text-brand-600" />
                Đơn của tôi
              </NavLink>
            ) : null}
            <NavLink to="/me" className={panelItemClass} onClick={() => setOpen(false)}>
              <IconUser className="h-4 w-4 shrink-0 text-brand-600" />
              Trang của tôi
            </NavLink>
            <div className="my-1 h-px bg-slate-100" />
            <button
              type="button"
              onClick={onLogout}
              className="flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-sm font-semibold text-red-600 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              Đăng xuất
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function ViewSwitch({ view, onViewChange }) {
  return (
    <div aria-label="Chế độ xem xe" className="flex items-center rounded-xl border border-slate-200/80 bg-slate-100 p-1">
      {[['list', 'Danh sách', IconList], ['map', 'Bản đồ', IconMap]].map(([value, label, Icon]) => (
        <button key={value} type="button" aria-pressed={view === value}
          onClick={() => onViewChange(value)}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold ${view === value
            ? 'bg-white text-brand-700 shadow-xs' : 'text-slate-600 hover:text-brand-700'}`}>
          <Icon className="h-4 w-4" />{label}
        </button>
      ))}
    </div>
  )
}

export default function AppHeader({ filters, onSearchSubmit, showViewSwitch = false,
  view = 'list', onViewChange }) {
  const { user, loading, logout } = useAuth()
  const navigate = useNavigate()
  const searchable = Boolean(filters && onSearchSubmit)

  const initialDraft = { keyword: filters?.keyword ?? '', location: filters?.location ?? '' }
  const [draft, setDraft] = useState(initialDraft)
  const [synced, setSynced] = useState(initialDraft)
  if (filters && (filters.keyword !== synced.keyword || filters.location !== synced.location)) {
    const next = { keyword: filters.keyword ?? '', location: filters.location ?? '' }
    setSynced(next)
    setDraft(next)
  }

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  const handleSearch = (event) => {
    event.preventDefault()
    onSearchSubmit(draft)
  }

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white shadow-xs">
      <div className="mx-auto flex h-20 max-w-[1400px] items-center justify-between gap-4 px-4 sm:px-6">
        <Link to="/" className="flex shrink-0 items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-600 text-white shadow-xs">
            <IconZap className="h-5 w-5" />
          </span>
          <span className="hidden sm:block">
            <span className="flex items-center gap-1.5">
              <span className="text-xl font-bold tracking-tight text-slate-900">EcoMotion</span>
              <span className="rounded-sm bg-brand-100 px-1.5 py-0.5 text-[10px] font-bold tracking-wider text-brand-800">
                VN
              </span>
            </span>
            <span className="block text-[11px] font-medium leading-none text-slate-500">
              Car Rental &amp; EV Hub
            </span>
          </span>
        </Link>

        {searchable && (
          <form
            onSubmit={handleSearch}
            className="mx-4 hidden max-w-2xl flex-1 items-center rounded-full border border-slate-200/80 bg-slate-50 py-1.5 pl-4 pr-1.5 transition-all hover:bg-slate-100/70 lg:flex"
          >
            <label className="flex min-w-0 flex-1 items-center gap-2.5 px-2">
              <IconMapPin className="h-4 w-4 shrink-0 text-brand-600" />
              <span className="w-full min-w-0 text-left leading-tight">
                <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Địa điểm nhận xe
                </span>
                <input
                  type="text"
                  value={draft.location}
                  onChange={(event) =>
                    setDraft((prev) => ({ ...prev, location: event.target.value }))
                  }
                  placeholder="TP. Hồ Chí Minh, Hà Nội…"
                  className="w-full truncate border-none bg-transparent p-0 text-xs font-bold text-slate-800 outline-none placeholder:font-normal placeholder:text-slate-400"
                />
              </span>
            </label>

            <span className="mx-1 h-6 w-px shrink-0 bg-slate-200" />

            <label className="flex min-w-0 flex-1 items-center gap-2 px-2">
              <span className="w-full min-w-0 text-left leading-tight">
                <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Tìm theo tên / Hãng xe
                </span>
                <input
                  type="text"
                  value={draft.keyword}
                  onChange={(event) =>
                    setDraft((prev) => ({ ...prev, keyword: event.target.value }))
                  }
                  placeholder="VinFast, Toyota, VF 8…"
                  className="w-full truncate border-none bg-transparent p-0 text-xs font-bold text-slate-800 outline-none placeholder:font-normal placeholder:text-slate-400"
                />
              </span>
              {draft.keyword && (
                <button
                  type="button"
                  onClick={() => setDraft((prev) => ({ ...prev, keyword: '' }))}
                  title="Xoá từ khoá"
                  className="shrink-0 p-0.5 text-slate-400 transition-colors hover:text-slate-600"
                >
                  <IconX className="h-3.5 w-3.5" />
                </button>
              )}
            </label>

            <button
              type="submit"
              title="Tìm kiếm"
              className="flex shrink-0 items-center justify-center rounded-full bg-brand-600 p-2.5 text-white shadow-sm transition-all hover:bg-brand-700 active:bg-brand-800"
            >
              <IconSearch className="h-4 w-4" />
            </button>
          </form>
        )}

        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          {showViewSwitch && (
            <div className="hidden xl:block"><ViewSwitch view={view} onViewChange={onViewChange} /></div>
          )}

          <NavLink to="/policy" className={policyLinkClass}>
            Chính sách
          </NavLink>

          {loading ? (
            <span className="h-10 w-28 animate-pulse rounded-lg bg-slate-100" />
          ) : user ? (
            <>
              <button
                type="button"
                title="Thông báo"
                className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition-colors hover:bg-slate-50 hover:text-brand-600"
              >
                <IconBell className="h-4 w-4" />
                <span className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full border-2 border-white bg-red-500" />
              </button>

              {user.role === 'DRIVER' && (
                <Link
                  to="/driver/schedule"
                  className="rounded-lg bg-teal-50 border border-teal-200 px-3 py-1.5 text-xs font-bold text-teal-700 transition hover:bg-teal-100"
                >
                  Lịch chuyến
                </Link>
              )}

              {user.role === 'PROVIDER' && (
                <Link
                  to="/provider/bookings"
                  className="rounded-lg bg-brand-50 border border-brand-200 px-3 py-1.5 text-xs font-bold text-brand-700 transition-colors hover:bg-brand-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2"
                >
                  Quản lý đơn
                </Link>
              )}

              <AccountDisclosure user={user} onLogout={handleLogout} />
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
              >
                Đăng nhập
              </Link>
              <Link
                to="/register"
                className="rounded-lg bg-brand-600 px-3.5 py-2 text-[13px] font-semibold text-white shadow-sm transition-all hover:bg-brand-700"
              >
                Đăng ký
              </Link>
            </>
          )}
        </div>
      </div>
      {showViewSwitch && (
        <div className="flex justify-center border-t border-slate-100 py-2 xl:hidden">
          <ViewSwitch view={view} onViewChange={onViewChange} />
        </div>
      )}
    </header>
  )
}
