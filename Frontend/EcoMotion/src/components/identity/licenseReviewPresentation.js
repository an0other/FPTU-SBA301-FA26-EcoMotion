export const licenseStatusLabels = { READY_TO_SUBMIT: 'Chờ bạn xác nhận gửi', SUBMITTED: 'Chờ Admin xét duyệt', APPROVED: 'Đã được chấp nhận', NEEDS_CHANGES: 'Cần bổ sung bản mới', REJECTED: 'Đã từ chối', CANCELLED: 'Đã hủy bản chuẩn bị' }
export const licenseFields = { documentNumber: 'Số GPLX', fullName: 'Họ tên', dateOfBirth: 'Ngày sinh', licenseClass: 'Hạng GPLX', issueDate: 'Ngày cấp', expiryDate: 'Ngày hết hạn' }

export function compareLicenseIdentity(document, applicant) {
  const name = value => typeof value === 'string' ? value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').replace(/\s+/g, ' ').trim().toUpperCase() : ''
  const fullName = !!name(document?.fullName) && name(document.fullName) === name(applicant?.identityName)
  const birthDate = !!document?.dateOfBirth && document.dateOfBirth === applicant?.identityBirthDate
  return { fullName, birthDate, matches: fullName && birthDate && applicant?.identityStatus === 'VERIFIED' }
}
