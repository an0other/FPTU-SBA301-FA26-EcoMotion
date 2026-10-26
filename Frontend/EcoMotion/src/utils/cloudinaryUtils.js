/**
 * Cloudinary & Trip Evidence Utilities
 */

export const EVIDENCE_TYPES = [
  { value: 'PICKUP_ARRIVAL', label: 'Ảnh có mặt tại điểm đón' },
  { value: 'VEHICLE_HANDOVER', label: 'Ảnh bàn giao xe' },
  { value: 'PRE_TRIP_CONDITION', label: 'Ảnh tình trạng xe trước chuyến' },
  { value: 'POST_TRIP_CONDITION', label: 'Ảnh tình trạng xe sau chuyến' },
  { value: 'INCIDENT', label: 'Ảnh phát sinh sự cố' },
  { value: 'OTHER', label: 'Ảnh khác' },
]

export const EVIDENCE_TYPE_LABELS = {
  PICKUP_ARRIVAL: 'Ảnh có mặt tại điểm đón',
  VEHICLE_HANDOVER: 'Ảnh bàn giao xe',
  PRE_TRIP_CONDITION: 'Ảnh tình trạng xe trước chuyến',
  POST_TRIP_CONDITION: 'Ảnh tình trạng xe sau chuyến',
  INCIDENT: 'Ảnh phát sinh sự cố',
  INCIDENT_PROOF: 'Ảnh phát sinh sự cố',
  TRIP_COMPLETION: 'Ảnh hoàn tất chuyến đi',
  OTHER: 'Ảnh khác',
}

/**
 * Transforms a Cloudinary secure_url to an optimized thumbnail URL.
 * Example transformation: w_400,c_fill,q_auto,f_auto
 */
export function getOptimizedImageUrl(url, { width = 400, crop = 'fill', quality = 'auto', format = 'auto' } = {}) {
  if (!url || typeof url !== 'string') return url
  if (url.includes('res.cloudinary.com') && url.includes('/upload/')) {
    const transform = `w_${width},c_${crop},q_${quality},f_${format}`
    return url.replace('/upload/', `/upload/${transform}/`)
  }
  return url
}

/**
 * Formats bytes into human-readable string (KB, MB).
 */
export function formatFileSize(bytes) {
  if (!bytes || Number.isNaN(Number(bytes))) return ''
  const b = Number(bytes)
  if (b < 1024) return `${b} B`
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`
  return `${(b / (1024 * 1024)).toFixed(1)} MB`
}
