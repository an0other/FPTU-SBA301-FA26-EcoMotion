import { useId, useState } from 'react'

const ACCEPT = 'image/jpeg,image/png,image/webp'
const MAX_BYTES = 5 * 1024 * 1024

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`} aria-hidden="true">{name}</span>
}

export function ReceiptDropzone({ file, previewUrl, onFile, error, disabled = false }) {
  const inputId = useId()
  const [dragOver, setDragOver] = useState(false)

  const applyFile = (next) => {
    if (!next) {
      onFile(null)
      return
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(next.type) || next.size > MAX_BYTES) {
      onFile(null, 'JPG, PNG hoặc WebP tối đa 5 MB')
      return
    }
    onFile(next)
  }

  return (
    <div className="space-y-2">
      <label htmlFor={inputId} className="block text-sm font-semibold text-[#14221A]">
        Ảnh sao kê hoặc màn hình chuyển khoản thành công
      </label>
      <input
        id={inputId}
        type="file"
        accept={ACCEPT}
        className="sr-only"
        disabled={disabled}
        onChange={(event) => applyFile(event.target.files?.[0] ?? null)}
      />
      <label
        htmlFor={inputId}
        onDragOver={(event) => {
          event.preventDefault()
          if (!disabled) setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragOver(false)
          if (!disabled) applyFile(event.dataTransfer.files?.[0] ?? null)
        }}
        className={`flex min-h-[9.5rem] cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-4 py-6 text-center transition-colors focus-within:ring-2 focus-within:ring-brand-500/40 ${
          dragOver
            ? 'border-brand-600 bg-brand-50'
            : file
              ? 'border-brand-300 bg-brand-50/60'
              : 'border-slate-300 bg-slate-50 hover:border-brand-500 hover:bg-brand-50/40'
        } ${disabled ? 'cursor-not-allowed opacity-50' : ''}`}
      >
        {previewUrl ? (
          <img src={previewUrl} alt="Xem trước biên lai" className="mb-3 h-28 w-full rounded-xl object-contain bg-white" />
        ) : (
          <span className="mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-brand-100 text-brand-800">
            <Icon name="upload_file" className="text-2xl" />
          </span>
        )}
        <span className="text-sm font-semibold text-[#14221A]">
          {file ? file.name : 'Kéo thả ảnh vào đây hoặc bấm để chọn tệp'}
        </span>
        <span className="mt-1 text-xs text-slate-500">JPG, PNG hoặc WebP · tối đa 5 MB</span>
      </label>
      {error ? <p className="text-xs font-medium text-red-700">{error}</p> : null}
    </div>
  )
}
