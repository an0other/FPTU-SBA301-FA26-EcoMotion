import { useEffect } from 'react'
import { IconCheckCircle, IconAlertTriangle, IconInfo } from './icons/Icons'

const VARIANT_CLASS = {
  error: 'border-red-200 bg-red-50 text-red-800 shadow-red-900/10',
  success: 'border-brand-500 bg-brand-700 text-white shadow-emerald-900/20',
  info: 'border-slate-600 bg-slate-900 text-white shadow-slate-900/20',
}

export default function Toast({ message, type = 'error', duration = 4000, onClose }) {
  useEffect(() => {
    if (!message) return undefined
    const timer = setTimeout(() => onClose?.(), duration)
    return () => clearTimeout(timer)
  }, [message, duration, onClose])

  if (!message) return null

  return (
    <div
      role="alert"
      className={`fixed right-4 left-4 top-20 z-[9999] flex items-center gap-3 rounded-lg border px-4 py-3 text-xs font-semibold shadow-md transition-all sm:left-auto sm:right-6 sm:max-w-[400px] ${
        VARIANT_CLASS[type] ?? VARIANT_CLASS.info
      }`}
    >
      {type === 'error' ? (
        <IconAlertTriangle className="h-5 w-5 shrink-0 text-red-600" />
      ) : type === 'success' ? (
        <IconCheckCircle className="h-5 w-5 shrink-0 text-white" />
      ) : (
        <IconInfo className="h-5 w-5 shrink-0 text-slate-300" />
      )}
      <span className="flex-1 leading-snug">{message}</span>
      <button
        type="button"
        onClick={onClose}
        aria-label="Đóng"
        className="px-1 py-0.5 text-base leading-none opacity-70 hover:opacity-100 cursor-pointer"
      >
        ✕
      </button>
    </div>
  )
}

