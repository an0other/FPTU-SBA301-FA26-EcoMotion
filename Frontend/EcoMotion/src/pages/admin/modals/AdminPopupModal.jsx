import { useEffect } from 'react'
import {
  IconX,
  IconAlertTriangle,
  IconCheckCircle,
  IconLock,
  IconShield,
} from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'

const TONE_CONFIG = {
  danger: {
    iconBg: 'bg-rose-50 text-rose-600 border-rose-100',
    confirmBtn: 'bg-rose-600 text-white hover:bg-rose-700 shadow-rose-600/20',
    Icon: IconLock,
  },
  error: {
    iconBg: 'bg-rose-50 text-rose-600 border-rose-100',
    confirmBtn: 'bg-rose-600 text-white hover:bg-rose-700 shadow-rose-600/20',
    Icon: IconAlertTriangle,
  },
  warning: {
    iconBg: 'bg-amber-50 text-amber-600 border-amber-100',
    confirmBtn: 'bg-amber-600 text-white hover:bg-amber-700 shadow-amber-600/20',
    Icon: IconAlertTriangle,
  },
  success: {
    iconBg: 'bg-emerald-50 text-emerald-600 border-emerald-100',
    confirmBtn: 'bg-emerald-600 text-white hover:bg-emerald-700 shadow-emerald-600/20',
    Icon: IconCheckCircle,
  },
  info: {
    iconBg: 'bg-brand-50 text-brand-600 border-brand-100',
    confirmBtn: 'bg-brand-600 text-white hover:bg-brand-700 shadow-brand-600/20',
    Icon: IconShield,
  },
}

/**
 * Reusable Popup Modal replacing native browser alert() and window.confirm() across Admin pages.
 *
 * @param {object} props
 * @param {boolean} props.open
 * @param {'alert'|'confirm'} [props.mode='alert']
 * @param {'danger'|'error'|'warning'|'success'|'info'} [props.tone='info']
 * @param {string} props.title
 * @param {string|import('react').ReactNode} props.message
 * @param {string} [props.confirmText]
 * @param {string} [props.cancelText='Hủy']
 * @param {boolean} [props.loading=false]
 * @param {() => void} props.onClose
 * @param {() => void} [props.onConfirm]
 */
export default function AdminPopupModal({
  open,
  mode = 'alert',
  tone = 'info',
  title,
  message,
  confirmText,
  cancelText = 'Hủy',
  loading = false,
  onClose,
  onConfirm,
}) {
  useEffect(() => {
    if (!open) return undefined
    function handleKeyDown(e) {
      if (e.key === 'Escape' && !loading) {
        onClose?.()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, loading, onClose])

  if (!open) return null

  const config = TONE_CONFIG[tone] || TONE_CONFIG.info
  const ToneIcon = config.Icon
  const defaultConfirmLabel = mode === 'confirm' ? 'Xác nhận' : 'Đã hiểu'

  return (
    <AdminModalShell
      maxWidth="max-w-md"
      zIndex="z-70"
      role="dialog"
      ariaModal="true"
      ariaLabel={title || 'Thông báo hệ thống'}
      onBackdropClick={() => {
        if (!loading) onClose?.()
      }}
      bodyClassName="px-6 py-4"
      header={(
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span
              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border ${config.iconBg}`}
            >
              <ToneIcon className="h-5 w-5" />
            </span>
            <h3 className="text-base font-bold text-slate-900">
              {title || (mode === 'confirm' ? 'Xác nhận thao tác' : 'Thông báo')}
            </h3>
          </div>

          <button
            type="button"
            disabled={loading}
            onClick={onClose}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 disabled:opacity-50"
          >
            <IconX className="h-4 w-4" />
          </button>
        </div>
      )}
      footer={(
        <div className="flex items-center justify-end gap-2.5">
          {mode === 'confirm' && (
            <button
              type="button"
              disabled={loading}
              onClick={onClose}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition-all hover:bg-slate-50 disabled:opacity-50"
            >
              {cancelText}
            </button>
          )}

          <button
            type="button"
            disabled={loading}
            onClick={() => {
              if (mode === 'confirm' && onConfirm) {
                onConfirm()
              } else {
                onClose?.()
              }
            }}
            className={`rounded-xl px-5 py-2 text-xs font-bold shadow-xs transition-all disabled:opacity-50 ${config.confirmBtn}`}
          >
            {loading ? 'Đang xử lý...' : confirmText || defaultConfirmLabel}
          </button>
        </div>
      )}
    >
      <div className="text-xs leading-relaxed text-slate-600">
        {message}
      </div>
    </AdminModalShell>
  )
}
