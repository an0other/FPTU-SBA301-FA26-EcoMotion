import { uploadPercentage, processingPresentation } from '../src/components/identity/identityProcessing.js'
import test from 'node:test'
import assert from 'node:assert/strict'
import { documentNotices, evidenceReason, finiteScore, qrLabel, documentCompletion, verificationPresentation } from '../src/components/identity/evidencePresentation.js'

test('QR disabled and missing evidence never imply a valid old document or a passed check', () => {
  assert.match(qrLabel('DISABLED'), /chưa thực hiện/)
  assert.match(qrLabel('NOT_READ'), /chưa xác định là không có mã/)
  assert.match(qrLabel('FUTURE_STATUS'), /Chưa có kết quả/)
  assert.deepEqual(documentNotices(null), [])
  assert.match(evidenceReason('UNKNOWN_REASON'), /kiểm tra thêm/)
})

test('invalid confidence is absent and conflicting fields produce actionable notices', () => {
  for (const value of [null, undefined, '0.9', NaN, Infinity, -1, 1.1]) assert.equal(finiteScore(value), null)
  assert.equal(finiteScore(0), 0)
  assert.equal(finiteScore(-.1, -1, 1), -.1)
  const notices = documentNotices({ statusReasons: ['OCR_QR_MISMATCH', 'OCR_QR_MISMATCH'],
    missingFields: ['expiryDate'], inconsistentFields: ['fullName'] })
  assert.equal(notices.length, 3)
  assert.match(notices[1], /Ngày hết hạn/)
  assert.match(notices[2], /Họ tên/)
})

const completion = {
  policyVersion: 'document-completeness/v1', completedCount: 3, totalCount: 4, percentage: 75,
  fields: [{ name: 'documentNumber', status: 'READ' }, { name: 'fullName', status: 'READ' },
    { name: 'dateOfBirth', status: 'READ' }, { name: 'expiryDate', status: 'MISSING' }],
}

test('user meter validates backend counts and yields to submitted or final decisions', () => {
  const evidence = { completeness: completion }
  assert.equal(documentCompletion(evidence).percentage, 75)
  assert.equal(documentCompletion({ completeness: { ...completion, percentage: 100 } }), null)
  assert.equal(documentCompletion({ completeness: { ...completion, completedCount: 4 } }), null)
  assert.equal(documentCompletion({}), null)
  const pending = verificationPresentation({ evidence, status: 'SUBMITTED' })
  assert.equal(pending.showMeter, false)
  assert.match(pending.title, /đang chờ xét duyệt/)
  assert.equal(verificationPresentation({ evidence, status: 'APPROVED' }).tone, 'approved')
})

test('100 percent never hides an image flag and unavailable manual routes are not promised', () => {
  const full = { ...completion, completedCount: 4, percentage: 100,
    fields: completion.fields.map(field => ({ ...field, status: 'READ' })) }
  const result = verificationPresentation({ evidence: { completeness: full,
    checks: [{ scope: 'IMAGE_SIGNAL_ONLY', status: 'FLAGGED' }] }, status: 'READY_TO_SUBMIT', canSubmitForReview: true })
  assert.equal(result.tone, 'review')
  assert.match(result.title, /kiểm tra bổ sung/)
  assert.equal(result.completion.percentage, 100)
  assert.equal(result.notices.length, 1)
  const incomplete = verificationPresentation({ evidence: { completeness: completion }, status: 'DRAFT' })
  assert.doesNotMatch(incomplete.description, /có thể xác nhận gửi/i)
  assert.equal(verificationPresentation({ evidence: { completeness: completion, readingStatus: 'PROCESSING_ERROR' } }).showMeter, false)
})

test('upload progress never becomes a fabricated model percentage and slow polling is not declared hung', () => {
  assert.equal(uploadPercentage({ loaded: 50, total: 100 }), 50)
  assert.equal(uploadPercentage({ loaded: 5 }), null)
  const task = { active: true, kind: 'VIDEO', startedAt: 0, uploadPercent: 100, phase: 'WAIT' }
  const waiting = processingPresentation(task, null, 50000)
  assert.equal(waiting.processing, false)
  assert.equal(waiting.uploading, false)
  assert.equal(waiting.slow, true)
  const processing = processingPresentation(task, { status: 'PROCESSING', processingStage: 'VIDEO',
    observedAt: '2026-11-11T00:01:00Z', startedAt: '2026-11-11T00:00:00Z', receivedAt: 50000 }, 52000)
  assert.equal(processing.seconds, 62)
  assert.equal(processing.step, 1)
  assert.match(processing.title, /video/)
  assert.equal(processingPresentation(task, { status: 'ERROR' }, 52000).failed, true)
  assert.equal(processingPresentation(task, { status: 'ERROR' }, 52000).slow, false)
  assert.equal(processingPresentation(task, { status: 'DOCUMENT_READY', seenProcessing: true }, 52000).returning, true)
  assert.equal(processingPresentation(task, { status: 'DOCUMENT_READY', seenProcessing: true }, 52000).slow, false)
})

test('AI step labels expose supported stages without guessing unknown capabilities', async () => {
  const { aiStageLabel } = await import('../src/components/identity/aiStageLabels.js')
  assert.equal(aiStageLabel({ stage: 'READING_FIELDS', side: 'FRONT' }), 'Đọc chữ trên giấy tờ — mặt trước')
  assert.equal(aiStageLabel({ stage: 'ANALYZING_VIDEO' }), 'Kiểm tra động tác và so sánh khuôn mặt')
  assert.equal(aiStageLabel({ stage: 'UNSUPPORTED_STAGE' }), null)
})

test('pipeline timeline follows reported events, including document sides and unrecognized future stages', async () => {
  const { aiReportedSteps } = await import('../src/components/identity/aiStageLabels.js')
  assert.deepEqual(aiReportedSteps(null), [])
  const steps = aiReportedSteps({ steps: [{ stage: 'READING_FIELDS', side: 'FRONT' }, { stage: 'READING_FIELDS', side: 'BACK' }, { stage: 'FUTURE_STEP' }, null] })
  assert.equal(steps.length, 3)
  assert.equal(steps[0].label, 'Đọc chữ trên giấy tờ — mặt trước')
  assert.equal(steps[1].label, 'Đọc chữ trên giấy tờ — mặt sau')
  assert.equal(steps[2].label, 'Bước xử lý khác do AI báo cáo')
})

test('face analysis and motion are independent and do not claim identity from detection', async () => {
  const { videoCheckSummary } = await import('../src/components/identity/evidencePresentation.js')
  const checks = videoCheckSummary({ motionChallenge: { status: 'INCOMPLETE' }, faceMatching: { scoredFrames: 5 } })
  assert.match(checks.motion, /chưa đầy đủ/)
  assert.match(checks.face, /5 khung hình/)
  assert.match(checks.explanation, /không đồng nghĩa đúng người/)
  assert.match(videoCheckSummary({ motionChallenge: { status: 'PASS' }, faceMatching: { scoredFrames: 0 } }).face, /Chưa có khung hình/)
})

test('admin sees license identity mismatches before approval, with Vietnamese name normalization', async () => {
  const { compareLicenseIdentity } = await import('../src/components/identity/licenseReviewPresentation.js')
  const applicant = { identityName: 'Nguyễn Văn Đạt', identityBirthDate: '2000-01-01', identityStatus: 'VERIFIED' }
  assert.equal(compareLicenseIdentity({ fullName: 'NGUYEN VAN DAT', dateOfBirth: '2000-01-01' }, applicant).matches, true)
  assert.equal(compareLicenseIdentity({ fullName: 'Nguyễn Văn Đạt', dateOfBirth: '2000-01-02' }, applicant).matches, false)
  assert.equal(compareLicenseIdentity({ fullName: 'Một Người Khác', dateOfBirth: '2000-01-01' }, applicant).matches, false)
})

test('uncalibrated face assessment is not presented as identity success', async () => {
  const { faceAssessmentPresentation } = await import('../src/components/identity/evidencePresentation.js')
  assert.match(faceAssessmentPresentation({status:'NOT_CALIBRATED'}).title,/Admin/)
  assert.equal(faceAssessmentPresentation({status:'LOW_SIMILARITY'}).tone,'review')
  assert.equal(faceAssessmentPresentation({status:'UNKNOWN'}).tone,'review')
})
test('annual calendar blocks a whole year conflict but permits pickup after an occupied interval ends', async () => {
  const { yearlyPickupAvailable, yearlyFirstAvailablePickup } = await import('../src/utils/yearlyRental.js')
  const slots=[{startAt:'2026-11-12T00:00:00+07:00',endAt:'2026-11-12T14:00:00+07:00'}]
  assert.equal(yearlyPickupAvailable('2026-11-12T13:00:00+07:00',slots),false)
  assert.equal(yearlyPickupAvailable('2026-11-12T14:00:00+07:00',slots),true)
  assert.equal(yearlyFirstAvailablePickup('2026-11-12','2026-11-12T10:00:00+07:00','2026-11-13T23:00:00+07:00',slots),Date.parse('2026-11-12T14:00:00+07:00'))
  assert.equal(yearlyFirstAvailablePickup('2026-11-12','2026-11-12T10:00:00+07:00','2026-11-13T23:00:00+07:00',[{startAt:'2027-01-01T00:00:00+07:00',endAt:'2027-01-02T00:00:00+07:00'}]),null)
  assert.equal(yearlyPickupAvailable('2026-11-12T14:00:00+07:00',null),false)
})


test('annual booking cancellation overrides a stale terms-draft workflow for both roles', async () => {
  const {yearlyRequestState}=await import('../src/utils/yearlyRental.js')
  assert.deepEqual(yearlyRequestState('CANCELLED','TERMS_DRAFT'),{terminal:true,pending:false,label:'Đơn thuê đã hủy'})
  assert.equal(yearlyRequestState('PENDING','TERMS_DRAFT').label,'Chờ Provider chấp nhận yêu cầu')
  assert.equal(yearlyRequestState('ACCEPTED','TERMS_DRAFT').terminal,false)
  assert.equal(yearlyRequestState('CANCELLATION_REVIEW','TERMS_ACCEPTED').terminal,true)
})
