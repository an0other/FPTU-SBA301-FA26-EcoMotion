import { useRef } from 'react'

function Icon({ name, className = '' }) {
  return <span aria-hidden="true" className={`material-symbols-rounded select-none leading-none ${className}`}>{name}</span>
}

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// Khu vực chọn nhiều ảnh hiện trường. Không chứa logic upload: trạng thái do controller trong utils quản lý.
export default function IncidentPhotoUpload({ items, maxCount, disabled = false, onAdd, onRemove, onRetry }) {
  const inputRef = useRef(null)
  const canAddMore = items.length < maxCount

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold text-slate-700">
          Ảnh hiện trường / biên bản <span className="font-normal text-slate-400">(tùy chọn)</span>
        </span>
        <span className="text-[11px] text-slate-400">{items.length}/{maxCount} ảnh</span>
      </div>

      <button
        type="button"
        disabled={disabled || !canAddMore}
        onClick={() => inputRef.current?.click()}
        className="flex w-full items-center gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50/60 p-3 text-left transition hover:border-red-300 hover:bg-red-50/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:cursor-not-allowed disabled:opacity-60"
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 shadow-2xs">
          <Icon name="add_photo_alternate" className="text-[20px]" />
        </span>
        <span className="min-w-0">
          <span className="block text-xs font-semibold text-slate-800">Tải ảnh hiện trường hoặc biên bản</span>
          <span className="block text-[11px] text-slate-500">
            {canAddMore ? 'Nhấn để chọn ảnh từ thiết bị (JPEG, PNG, WEBP, tối đa 10 MB mỗi ảnh)' : `Đã đủ ${maxCount} ảnh`}
          </span>
        </span>
      </button>

      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        className="hidden"
        onChange={(event) => {
          onAdd?.(event.target.files)
          event.target.value = ''
        }}
      />

      {items.length > 0 && (
        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3" aria-label="Danh sách ảnh đã chọn">
          {items.map((item) => (
            <li key={item.id} className="relative overflow-hidden rounded-xl border border-slate-200 bg-white text-xs">
              <div className="aspect-[4/3] w-full bg-slate-100">
                {item.previewUrl ? (
                  <img src={item.previewUrl} alt={`Xem trước ${item.file.name}`} className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-slate-400">
                    <Icon name="broken_image" className="text-[28px]" />
                  </div>
                )}
              </div>
              <div className="space-y-1 p-2">
                <div className="flex items-start justify-between gap-1">
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-slate-800" title={item.file.name}>{item.file.name}</p>
                    <p className="text-[10px] text-slate-400">{formatBytes(item.file.size)}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => onRemove?.(item.id)}
                    disabled={disabled && item.status === 'uploading'}
                    aria-label={`Xóa ảnh ${item.file.name}`}
                    className="shrink-0 rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-rose-600 disabled:opacity-40"
                  >
                    <Icon name="close" className="text-[16px]" />
                  </button>
                </div>

                {item.status === 'uploading' && (
                  <div className="space-y-0.5" role="status">
                    <div className="h-1 w-full overflow-hidden rounded bg-slate-200">
                      <div className="h-full bg-red-500 transition-all" style={{ width: `${item.progress || 0}%` }} />
                    </div>
                    <p className="text-[10px] text-slate-500">Đang tải lên {item.progress || 0}%</p>
                  </div>
                )}

                {item.status === 'ready' && (
                  <p className="flex items-center gap-1 text-[10px] font-semibold text-emerald-700">
                    <Icon name="check_circle" className="text-[13px]" /> Đã tải lên
                  </p>
                )}

                {item.status === 'error' && (
                  <div className="space-y-1" role="alert">
                    <p className="text-[10px] text-rose-700">{item.error}</p>
                    {item.uploadable && (
                      <button
                        type="button"
                        onClick={() => onRetry?.(item.id)}
                        className="rounded border border-slate-200 px-2 py-0.5 text-[10px] font-semibold text-slate-700 hover:bg-slate-50"
                      >
                        Thử lại
                      </button>
                    )}
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
