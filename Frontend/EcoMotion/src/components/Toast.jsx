import { useEffect } from 'react'
import { IconCheckCircle } from './icons/Icons'
import './Toast.css'

/**
 * Top-right notification used for login errors and success messages.
 * Renders nothing when `message` is empty.
 */
export default function Toast({ message, type = 'error', duration = 4000, onClose }) {
  useEffect(() => {
    if (!message) return undefined
    const timer = setTimeout(() => onClose?.(), duration)
    return () => clearTimeout(timer)
  }, [message, duration, onClose])

  if (!message) return null

  return (
    <div className={`toast toast--${type}`} role="alert">
      <IconCheckCircle className="toast__icon" />
      <span className="toast__message">{message}</span>
      <button type="button" className="toast__close" onClick={onClose} aria-label="Đóng">
        ✕
      </button>
    </div>
  )
}
