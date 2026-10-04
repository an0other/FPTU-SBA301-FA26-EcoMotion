import { useEffect } from 'react'
import { IconCheckCircle } from './icons/Icons'

const VARIANT_CLASS = {
  error: 'border-red-200 bg-red-50 text-on-error-container',
  success: 'border-brand-500 bg-brand-700 text-white',
  info: 'border-slate-600 bg-slate-800 text-white',
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
      className={`fixed right-4 left-4 top-5 z-50 flex items-center gap-3 rounded-lg border px-4 py-3.5 text-[13.5px] font-medium shadow-xl sm:left-auto sm:right-5 sm:max-w-[380px] ${
        VARIANT_CLASS[type] ?? VARIANT_CLASS.info
      }`}
    >
      <IconCheckCircle className="h-5 w-5 shrink-0" />
      <span className="flex-1">{message}</span>
      <button
        type="button"
        onClick={onClose}
        aria-label="Đóng"
        className="px-0.5 text-sm leading-none opacity-70 hover:opacity-100"
      >
        ✕
      </button>
    </div>
  )
}
