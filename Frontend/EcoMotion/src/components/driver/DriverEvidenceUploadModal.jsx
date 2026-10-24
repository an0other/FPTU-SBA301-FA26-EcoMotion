import { useState, useRef, useEffect, useCallback } from 'react'
import { driverTripService } from '../../services/driverTripService'
import { EVIDENCE_TYPES, formatFileSize } from '../../utils/cloudinaryUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp']

export default function DriverEvidenceUploadModal({ trip, isOpen, onClose, onSuccess }) {
  const [evidenceType, setEvidenceType] = useState('PICKUP_ARRIVAL')
  const [file, setFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [note, setNote] = useState('')
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState(null)
  const [isDragging, setIsDragging] = useState(false)

  const fileInputRef = useRef(null)

  // Clean up object URL when component unmounts or preview changes
  const cleanupPreview = useCallback(() => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
      setPreviewUrl(null)
    }
  }, [previewUrl])

  useEffect(() => {
    return () => {
      cleanupPreview()
    }
  }, [cleanupPreview])

  // Reset form when modal closes or opens
  useEffect(() => {
    if (!isOpen) {
      cleanupPreview()
      setFile(null)
      setNote('')
      setError(null)
      setUploading(false)
      setIsDragging(false)
    }
  }, [isOpen, cleanupPreview])

  if (!isOpen || !trip) return null

  const validateAndSetFile = (selectedFile) => {
    setError(null)

    if (!selectedFile) return

    // Validate MIME type
    const isMimeValid = ALLOWED_MIME_TYPES.includes(selectedFile.type.toLowerCase())
    const ext = selectedFile.name.slice(selectedFile.name.lastIndexOf('.')).toLowerCase()
    const isExtValid = ALLOWED_EXTENSIONS.includes(ext)

    if (!isMimeValid && !isExtValid) {
      setError('Định dạng file không hợp lệ. Vui lòng chọn ảnh JPG, PNG hoặc WEBP.')
      return
    }

    // Prevent executable/executable-like extensions
    const forbiddenExts = ['.exe', '.sh', '.bat', '.cmd', '.js', '.jar']
    if (forbiddenExts.some((fe) => selectedFile.name.toLowerCase().endsWith(fe))) {
      setError('Không được phép tải lên file thực thi hoặc script.')
      return
    }

    // Validate size (max 10MB)
    if (selectedFile.size > MAX_FILE_SIZE_BYTES) {
      setError('Dung lượng ảnh vượt quá giới hạn 10 MB. Vui lòng chọn ảnh có dung lượng nhỏ hơn.')
      return
    }

    if (selectedFile.size === 0) {
      setError('File ảnh được chọn không có dữ liệu (0 byte).')
      return
    }

    // Valid: cleanup previous preview and create new one
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl)
    }
    const newPreview = URL.createObjectURL(selectedFile)
    setPreviewUrl(newPreview)
    setFile(selectedFile)
  }

  const handleFileChange = (e) => {
    const selected = e.target.files?.[0]
    if (selected) {
      validateAndSetFile(selected)
    }
    // reset input so the same file could be selected again if replaced
    e.target.value = ''
  }

  const handleDragOver = (e) => {
    e.preventDefault()
    e.stopPropagation()
    if (!isDragging) setIsDragging(true)
  }

  const handleDragLeave = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)

    const dropped = e.dataTransfer.files?.[0]
    if (dropped) {
      validateAndSetFile(dropped)
    }
  }

  const handleRemoveFile = () => {
    cleanupPreview()
    setFile(null)
    setError(null)
  }

  const handleTriggerSelect = () => {
    fileInputRef.current?.click()
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    if (!file) {
      setError('Vui lòng chọn hình ảnh minh chứng trước khi lưu.')
      return
    }

    setUploading(true)
    setError(null)

    try {
      const tripId = trip.tripId || trip.bookingCode

      const formData = new FormData()
      formData.append('file', file)
      formData.append('type', evidenceType)
      if (note.trim()) {
        formData.append('note', note.trim())
      }

      await driverTripService.uploadEvidence(tripId, formData)

      onSuccess?.('Đã lưu minh chứng thành công!')
      onClose()
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          err?.original?.response?.data?.message ||
          err?.message ||
          'Không thể lưu minh chứng. Vui lòng thử lại.'
      )
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200 animate-in fade-in duration-150">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-3">
          <div className="flex items-center gap-2 text-slate-800">
            <Icon name="photo_camera" className="text-xl text-brand-700" />
            <h3 className="text-base font-bold text-slate-900">Tải ảnh minh chứng chuyến đi</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={uploading}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
          >
            <Icon name="close" className="text-lg" />
          </button>
        </div>

        <p className="text-xs text-slate-500 mb-3.5">
          Lưu hình ảnh làm căn cứ giải quyết phụ phí hoặc xác nhận bàn giao cho chuyến #{trip.bookingCode}.
        </p>

        {error && (
          <div className="mb-3.5 rounded-lg bg-rose-50 p-2.5 text-xs text-rose-800 border border-rose-200 flex items-start gap-2">
            <Icon name="error" className="text-base text-rose-600 shrink-0 mt-0.5" />
            <div className="leading-relaxed">{error}</div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3.5">
          {/* Phân loại tài liệu */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Phân loại tài liệu <span className="text-rose-500">*</span>
            </label>
            <select
              value={evidenceType}
              onChange={(e) => setEvidenceType(e.target.value)}
              disabled={uploading}
              className="w-full rounded-lg border border-slate-200 bg-white p-2 text-xs font-semibold text-slate-800 focus:border-brand-600 focus:outline-none disabled:bg-slate-50"
            >
              {EVIDENCE_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          {/* Upload ảnh zone */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Ảnh minh chứng <span className="text-rose-500">*</span>
            </label>

            {/* Hidden file input */}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handleFileChange}
              className="hidden"
            />

            {!file ? (
              /* Drag & Drop Dropzone */
              <div
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={handleTriggerSelect}
                className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-6 text-center cursor-pointer transition ${
                  isDragging
                    ? 'border-brand-600 bg-brand-50/50'
                    : 'border-slate-300 hover:border-brand-500 hover:bg-slate-50'
                }`}
              >
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-600">
                  <Icon name="add_a_photo" className="text-2xl" />
                </div>
                <div className="text-xs font-semibold text-slate-700">
                  Kéo thả ảnh vào đây hoặc{' '}
                  <span className="text-brand-700 underline font-bold">Chọn ảnh</span>
                </div>
                <p className="mt-1 text-[11px] text-slate-400">
                  JPG, PNG, WEBP • tối đa 10 MB
                </p>
              </div>
            ) : (
              /* Selected File Preview Box */
              <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="h-12 w-12 shrink-0 overflow-hidden rounded-md border border-slate-200 bg-white shadow-2xs">
                    {previewUrl ? (
                      <img
                        src={previewUrl}
                        alt="Preview"
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center bg-slate-100 text-slate-400">
                        <Icon name="image" className="text-lg" />
                      </div>
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-xs font-bold text-slate-800" title={file.name}>
                      {file.name}
                    </p>
                    <p className="text-[11px] text-slate-500">{formatFileSize(file.size)}</p>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0 pl-2">
                  <button
                    type="button"
                    onClick={handleTriggerSelect}
                    disabled={uploading}
                    className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                  >
                    Thay ảnh
                  </button>
                  <button
                    type="button"
                    onClick={handleRemoveFile}
                    disabled={uploading}
                    className="rounded-md p-1 text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                    title="Xóa ảnh"
                  >
                    <Icon name="delete" className="text-lg" />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Ghi chú mô tả */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Ghi chú mô tả (tùy chọn)
            </label>
            <textarea
              rows="2"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              disabled={uploading}
              placeholder="Ghi chú thêm về tình trạng phương tiện hoặc bối cảnh..."
              className="w-full rounded-lg border border-slate-200 p-2 text-xs text-slate-800 placeholder-slate-400 focus:border-brand-600 focus:outline-none disabled:bg-slate-50"
            />
          </div>

          {/* Action buttons */}
          <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={uploading}
              className="rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={uploading || !file}
              className="inline-flex items-center gap-1.5 rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800 transition disabled:opacity-50"
            >
              {uploading && <Icon name="sync" className="animate-spin text-[16px]" />}
              <span>{uploading ? 'Đang tải ảnh lên...' : 'Lưu minh chứng'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
