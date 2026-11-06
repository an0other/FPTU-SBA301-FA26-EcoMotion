// Ảnh hiện trường cho báo cáo sự cố (nhiều ảnh): kiểm tra file, tải lên qua cơ chế upload minh chứng hiện có,
// và quyết định có được gửi báo cáo hay không. Không có React ở đây để kiểm thử bằng node:test.

// Trùng với giới hạn backend: CloudinaryServiceImpl (10 MB, JPEG/PNG/WEBP) và IncidentEvidenceUrls (tối đa 10 ảnh, https).
export const INCIDENT_PHOTO_MAX_BYTES = 10 * 1024 * 1024
export const INCIDENT_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const INCIDENT_PHOTO_TYPE = 'INCIDENT_PROOF'
export const INCIDENT_PHOTO_MAX_COUNT = 10

function ascii(bytes, start, end) {
  let out = ''
  for (let i = start; i < end && i < bytes.length; i += 1) out += String.fromCharCode(bytes[i])
  return out
}

// Nhận diện định dạng theo chữ ký (magic bytes), không theo đuôi file hay Content-Type do trình duyệt đặt.
export function sniffImageMime(bytes) {
  if (!bytes || bytes.length < 3) return null
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg'
  const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
  if (bytes.length >= png.length && png.every((b, i) => bytes[i] === b)) return 'image/png'
  if (bytes.length >= 12 && ascii(bytes, 0, 4) === 'RIFF' && ascii(bytes, 8, 12) === 'WEBP') return 'image/webp'
  return null
}

export function validateIncidentPhoto(file, headBytes) {
  if (!file || !file.size) return { ok: false, message: 'Tệp ảnh rỗng. Vui lòng chọn ảnh khác.' }
  if (!INCIDENT_PHOTO_TYPES.includes(file.type)) {
    return { ok: false, message: 'Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP.' }
  }
  if (file.size > INCIDENT_PHOTO_MAX_BYTES) {
    return { ok: false, message: 'Ảnh vượt quá 10 MB. Vui lòng chọn ảnh nhỏ hơn.' }
  }
  if (sniffImageMime(headBytes) !== file.type) {
    return { ok: false, message: 'Tệp không phải ảnh JPEG, PNG hoặc WEBP hợp lệ.' }
  }
  return { ok: true, message: null }
}

// Chỉ nhận URL https do máy chủ trả về; không chấp nhận chuỗi người dùng nhập.
export function isTrustedEvidenceUrl(url) {
  return typeof url === 'string' && /^https:\/\/\S+$/.test(url) && url.length <= 190
}

// Đọc danh sách ảnh từ dữ liệu báo cáo: hỗ trợ mảng (mới) và chuỗi đơn/phân tách dấu phẩy (dữ liệu cũ).
// Chỉ giữ URL https hợp lệ, bỏ trùng.
export function normalizeEvidenceUrls(value) {
  const raw = Array.isArray(value)
    ? value
    : typeof value === 'string'
      ? value.split(/[,;\n\r]+/)
      : []
  const seen = new Set()
  const out = []
  for (const item of raw) {
    const url = typeof item === 'string' ? item.trim() : ''
    if (isTrustedEvidenceUrl(url) && !seen.has(url)) {
      seen.add(url)
      out.push(url)
    }
  }
  return out
}

function messageOf(err) {
  return err?.response?.data?.message
    || err?.original?.response?.data?.message
    || err?.message
    || 'Không thể tải ảnh lên. Vui lòng thử lại.'
}

/**
 * Danh sách ảnh. Mỗi phần tử: { id, file, previewUrl, uploadable, status: 'uploading'|'ready'|'error', progress, url, error }
 * Chỉ phần tử 'ready' có url https hợp lệ mới được đưa vào báo cáo. Kết quả upload cũ (sau khi xóa hoặc thử lại)
 * bị bỏ qua nhờ mã lượt (attempt).
 */
export function createIncidentPhotoUploader({
  upload,
  createPreviewUrl = (file) => URL.createObjectURL(file),
  revokePreviewUrl = (url) => URL.revokeObjectURL(url),
  readHead = async (file) => new Uint8Array(await file.slice(0, 16).arrayBuffer()),
  onChange = () => {},
  maxCount = INCIDENT_PHOTO_MAX_COUNT,
}) {
  let items = []
  let nextId = 1

  const emit = () => onChange(items.slice())
  const find = (id) => items.find((item) => item.id === id)
  const patch = (id, changes) => {
    items = items.map((item) => (item.id === id ? { ...item, ...changes } : item))
    emit()
  }
  const release = (item) => {
    if (item?.previewUrl) revokePreviewUrl(item.previewUrl)
  }

  async function runUpload(id, attempt) {
    const current = find(id)
    if (!current) return
    try {
      // context (ví dụ mã chuyến) được truyền nguyên vẹn từ lúc thêm ảnh, kể cả khi thử lại.
      const response = await upload(current.file, (percent) => {
        const latest = find(id)
        if (latest && latest.attempt === attempt) patch(id, { progress: percent })
      }, current.context)
      const latest = find(id)
      if (!latest || latest.attempt !== attempt) return
      const url = response?.imageUrl
      if (!isTrustedEvidenceUrl(url)) throw new Error('Máy chủ không trả về đường dẫn ảnh hợp lệ.')
      patch(id, { status: 'ready', url, progress: 100, error: null })
    } catch (err) {
      const latest = find(id)
      if (!latest || latest.attempt !== attempt) return
      patch(id, { status: 'error', url: null, progress: 0, error: messageOf(err) })
    }
  }

  return {
    maxCount,

    async add(files, context) {
      const list = Array.from(files || [])
      const room = Math.max(0, maxCount - items.length)
      const accepted = list.slice(0, room)
      if (list.length > accepted.length) {
        // Ảnh vượt số lượng được báo rõ ràng, không lặng lẽ bỏ qua.
        items = [...items, { id: nextId++, file: list[accepted.length], previewUrl: null, uploadable: false,
          status: 'error', attempt: 0, progress: 0, url: null, error: `Chỉ được đính kèm tối đa ${maxCount} ảnh.` }]
        emit()
      }
      // Các ảnh hợp lệ tải song song, mỗi ảnh có trạng thái riêng.
      const uploads = []
      for (const file of accepted) {
        const id = nextId++
        const head = await readHead(file)
        const check = validateIncidentPhoto(file, head)
        if (!check.ok) {
          items = [...items, { id, file, previewUrl: null, uploadable: false, status: 'error', attempt: 0,
            progress: 0, url: null, error: check.message }]
          emit()
          continue
        }
        items = [...items, { id, file, context, previewUrl: createPreviewUrl(file), uploadable: true, status: 'uploading',
          attempt: 1, progress: 0, url: null, error: null }]
        emit()
        uploads.push(runUpload(id, 1))
      }
      await Promise.all(uploads)
    },

    async retry(id) {
      const item = find(id)
      if (!item || item.status !== 'error' || !item.uploadable) return
      const attempt = item.attempt + 1
      patch(id, { status: 'uploading', attempt, progress: 0, error: null })
      await runUpload(id, attempt)
    },

    remove(id) {
      const item = find(id)
      if (!item) return
      release(item)
      items = items.filter((candidate) => candidate.id !== id)
      emit()
    },

    dispose() {
      items.forEach(release)
      items = []
      emit()
    },

    items() {
      return items.slice()
    },

    // Được phép gửi báo cáo khi không có ảnh (tùy chọn) hoặc mọi ảnh đã tải xong.
    canSubmit() {
      return items.every((item) => item.status === 'ready')
    },

    // Danh sách URL gửi cùng báo cáo, theo thứ tự chọn.
    evidenceUrls() {
      return items.filter((item) => item.status === 'ready').map((item) => item.url)
    },
  }
}

export function buildIncidentPhotoForm(file) {
  const form = new FormData()
  form.append('file', file)
  form.append('type', INCIDENT_PHOTO_TYPE)
  return form
}
