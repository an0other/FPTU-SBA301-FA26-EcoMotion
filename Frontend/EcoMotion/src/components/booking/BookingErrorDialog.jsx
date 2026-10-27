import { useEffect, useId, useRef } from 'react'
import { IconAlertTriangle, IconX } from '../icons/Icons'

export function BookingErrorDialog({ message, title = 'Không thể hoàn tất thao tác', onClose, onRetry }) {
  const dialogRef = useRef(null)
  const closeButtonRef = useRef(null)
  const lastFocusedRef = useRef(null)
  const titleId = useId()
  const messageId = useId()

  useEffect(() => {
    const rememberFocus = (event) => {
      if (event.target instanceof HTMLElement && event.target !== document.body
        && !dialogRef.current?.contains(event.target)) lastFocusedRef.current = event.target
    }
    document.addEventListener('focusin', rememberFocus)
    return () => document.removeEventListener('focusin', rememberFocus)
  }, [])

  useEffect(() => {
    if (!message) return undefined
    const dialog = dialogRef.current
    const previousOverflow = document.body.style.overflow
    const previousFocus = lastFocusedRef.current
    dialog.showModal()
    closeButtonRef.current?.focus({ preventScroll: true })
    document.body.style.overflow = 'hidden'
    return () => {
      dialog.close()
      document.body.style.overflow = previousOverflow
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
    }
  }, [message])

  return (
    <dialog
      ref={dialogRef}
      role="alertdialog"
      aria-labelledby={titleId}
      aria-describedby={messageId}
      onCancel={(event) => { event.preventDefault(); onClose() }}
      onKeyDown={(event) => {
        if (event.key !== 'Tab') return
        const buttons = event.currentTarget.querySelectorAll('button:not(:disabled)')
        const first = buttons[0]
        const last = buttons[buttons.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }}
      className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-md overflow-y-auto rounded-2xl border border-[#E6EBE8] bg-white p-0 text-[#14221A] shadow-xl backdrop:bg-[#14221A]/45"
    >
      <div className="relative p-5 sm:p-6">
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng thông báo lỗi"
          className="absolute right-3 top-3 flex size-10 items-center justify-center rounded-xl text-[#475569] hover:bg-[#F7F8F6] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#15803D]"
        >
          <IconX className="size-5" />
        </button>
        <div className="mb-4 flex size-12 items-center justify-center rounded-xl bg-red-50 text-[#B42318]">
          <IconAlertTriangle className="size-6" />
        </div>
        <h2 id={titleId} className="pr-8 text-lg font-bold tracking-tight">{title}</h2>
        <p id={messageId} className="mt-2 whitespace-pre-line break-words text-sm leading-6 text-[#475569]">{message}</p>
        <div className="mt-6 flex flex-wrap justify-end gap-3">
          {onRetry ? (
            <button
              type="button"
              onClick={onRetry}
              className="min-h-11 whitespace-nowrap rounded-xl border border-[#E6EBE8] px-4 py-2.5 text-sm font-semibold hover:bg-[#F7F8F6] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#15803D]"
            >
              Thử lại
            </button>
          ) : null}
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            className="min-h-11 whitespace-nowrap rounded-xl bg-[#15803D] px-5 py-2.5 text-sm font-semibold text-white hover:bg-[#166534] active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#15803D]"
          >
            Đã hiểu
          </button>
        </div>
      </div>
    </dialog>
  )
}
