const BY_STATUS = {
  400: 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.',
  401: 'Email hoặc mật khẩu không đúng.',
  403: 'Tài khoản của bạn chưa được kích hoạt hoặc đã bị khoá.',
  404: 'Không tìm thấy tài nguyên yêu cầu.',
  409: 'Dữ liệu đã tồn tại.',
  429: 'Bạn thao tác quá nhanh. Vui lòng thử lại sau.',
  500: 'Máy chủ gặp sự cố. Vui lòng thử lại sau.',
  502: 'Không kết nối được máy chủ.',
  503: 'Máy chủ tạm thời không khả dụng.',
}

const BY_PHRASE = [
  [/invalid email or password/i, 'Email hoặc mật khẩu không đúng.'],
  [/must be a well-formed email/i, 'Email không đúng định dạng.'],
  [/must not be blank/i, 'Vui lòng không để trống trường bắt buộc.'],
  [/access denied/i, 'Bạn không có quyền truy cập.'],
  [/account (is )?(inactive|locked)/i, 'Tài khoản của bạn chưa được kích hoạt hoặc đã bị khoá.'],
  [/invalid refresh token/i, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'],
  [/malformed request body/i, 'Dữ liệu gửi lên không hợp lệ.'],
  [/validation failed/i, 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.'],
  [/internal server error/i, 'Máy chủ gặp sự cố. Vui lòng thử lại sau.'],
]

const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Vui lòng kiểm tra backend.'
const FALLBACK_MESSAGE = 'Đã xảy ra lỗi, vui lòng thử lại.'

export function toVietnameseError(error) {
  if (error?.code === 'ERR_NETWORK') return NETWORK_MESSAGE

  const status = error?.response?.status
  const raw = error?.response?.data?.message

  if (typeof raw === 'string' && raw.trim()) {
    for (const [pattern, vi] of BY_PHRASE) {
      if (pattern.test(raw)) return vi
    }
  }

  if (status && BY_STATUS[status]) return BY_STATUS[status]

  return FALLBACK_MESSAGE
}
