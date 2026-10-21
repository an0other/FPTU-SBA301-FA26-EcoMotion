const BY_STATUS = {
  400: 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.',
  401: 'Email hoặc mật khẩu không đúng.',
  403: 'Xác thực chống bot thất bại. Vui lòng tải lại trang và thử lại.',
  404: 'Không tìm thấy tài nguyên yêu cầu.',
  409: 'Dữ liệu đã tồn tại.',
  422: 'Xác thực thông tin thất bại. Vui lòng kiểm tra lại ảnh và thử lại.',
  429: 'Bạn thao tác quá nhanh. Vui lòng thử lại sau.',
  500: 'Máy chủ gặp sự cố. Vui lòng thử lại sau.',
  502: 'Không kết nối được máy chủ.',
  503: 'Máy chủ tạm thời không khả dụng.',
}

const BY_PHRASE = [
  [/captcha/i, 'Xác thực reCAPTCHA thất bại. Vui lòng tải lại trang và thử lại.'],
  [/invalid email or password/i, 'Email hoặc mật khẩu không đúng.'],
  [/email already exists/i, 'Email này đã được đăng ký. Vui lòng đăng nhập hoặc dùng email khác.'],
  [/must be a well-formed email/i, 'Email không đúng định dạng.'],
  [/must not be blank/i, 'Vui lòng không để trống trường bắt buộc.'],
  [/access denied/i, 'Bạn không có quyền truy cập.'],
  [/no pending activation/i, 'Không tìm thấy tài khoản đang chờ kích hoạt với email này.'],
  [/account is already active/i, 'Tài khoản đã được kích hoạt. Vui lòng đăng nhập.'],
  [/account is pending/i, 'Tài khoản chưa được kích hoạt. Vui lòng hoàn tất xác thực.'],
  [/account (is )?(inactive|locked)/i, 'Tài khoản của bạn chưa được kích hoạt hoặc đã bị khoá.'],
  [/invalid otp/i, 'Mã OTP không đúng hoặc đã hết hạn.'],
  [/otp has expired/i, 'Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.'],
  [/too many otp attempts/i, 'Bạn đã nhập sai OTP quá nhiều lần. Vui lòng gửi lại mã mới.'],
  [/please wait before requesting a new otp/i, 'Vui lòng chờ một lát trước khi yêu cầu mã OTP mới.'],
  [/phone number already exists/i, 'Số điện thoại này đã được sử dụng.'],
  [/phone number must be verified/i, 'Vui lòng xác thực số điện thoại trước khi sang bước tiếp theo.'],
  [/cccd number is already registered/i, 'Số CCCD này đã được đăng ký cho một tài khoản khác.'],
  [/invalid or expired refresh token/i, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'],
  [/malformed request body/i, 'Dữ liệu gửi lên không hợp lệ.'],
  [/validation failed/i, 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.'],
  [/internal server error/i, 'Máy chủ gặp sự cố. Vui lòng thử lại sau.'],
]

const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Vui lòng kiểm tra backend.'
const FALLBACK_MESSAGE = 'Đã xảy ra lỗi, vui lòng thử lại.'

const VIETNAMESE = /[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]/i

export function toVietnameseError(error) {
  if (error?.code === 'ERR_NETWORK') return NETWORK_MESSAGE

  const status = error?.response?.status
  const raw = error?.response?.data?.message

  if (typeof raw === 'string' && raw.trim()) {
    for (const [pattern, vi] of BY_PHRASE) {
      if (pattern.test(raw)) return vi
    }
    if (VIETNAMESE.test(raw)) return raw
  }

  if (status && BY_STATUS[status]) return BY_STATUS[status]

  return FALLBACK_MESSAGE
}

export function getErrorMessage(error, fallback = FALLBACK_MESSAGE) {
  if (!error) return fallback
  const vi = toVietnameseError(error)
  if (vi && vi !== FALLBACK_MESSAGE) return vi
  if (error?.response?.data?.message) return error.response.data.message
  if (error?.message) return error.message
  return fallback
}

