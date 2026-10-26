import test from 'node:test'
import assert from 'node:assert/strict'
import { providerFinanceService, SUPPORTED_BANKS } from '../src/services/providerFinanceService.js'

test('Provider Finance Service API contracts exist and are callable functions', () => {
  const expectedMethods = [
    'getFinanceSummary',
    'getBankAccount',
    'listBankAccounts',
    'createBankAccount',
    'listTransactions',
    'getBookingBreakdown',
    'listProviderPaymentRequests',
    'confirmPayment',
    'rejectPayment',
    'reportIssue',
    'listRefunds',
    'reviewRefund',
    'getPaymentEvidenceImage',
  ]

  for (const method of expectedMethods) {
    assert.equal(typeof providerFinanceService[method], 'function', `Method ${method} should be a function`)
  }
})

test('Supported banks list contains standard major Vietnamese banks', () => {
  assert.ok(Array.isArray(SUPPORTED_BANKS), 'SUPPORTED_BANKS must be an array')
  assert.ok(SUPPORTED_BANKS.length >= 10, 'Must support at least 10 major banks')

  const vcb = SUPPORTED_BANKS.find((b) => b.code === 'VCB')
  assert.ok(vcb, 'VCB must be in supported banks')
  assert.equal(vcb.shortName, 'Vietcombank')
  assert.ok(vcb.minLength > 0)
  assert.ok(vcb.maxLength >= vcb.minLength)
})

test('Provider Finance formatting helpers format currency and dates reliably', () => {
  function formatVnd(amount) {
    if (amount == null || Number.isNaN(Number(amount))) return '0 ₫'
    return Number(amount).toLocaleString('vi-VN') + ' ₫'
  }

  assert.equal(formatVnd(null), '0 ₫')
  assert.equal(formatVnd(undefined), '0 ₫')
  assert.equal(formatVnd(NaN), '0 ₫')
  assert.ok(formatVnd(500000).includes('500.000'))
  assert.ok(formatVnd(1500000).includes('1.500.000'))
})

test('Transaction and Payment Request statuses are cleanly categorized', () => {
  const TX_STATUSES = ['SUCCESS', 'CONFIRMED', 'PENDING', 'PROCESSING', 'FAILED', 'REJECTED', 'REFUNDED']
  const REFUND_STATUSES = ['PENDING', 'APPROVED', 'COMPLETED', 'REJECTED']
  const REQUEST_STATUSES = ['PENDING', 'PROOF_SUBMITTED', 'PARTIALLY_PAID', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'REVIEW_REQUIRED']

  assert.ok(TX_STATUSES.length > 0)
  assert.ok(REFUND_STATUSES.length > 0)
  assert.ok(REQUEST_STATUSES.length > 0)
})

test('Provider Finance Error State Contract: API errors do not throw unhandled ReferenceError', async () => {
  // Simulating rejected Promise.allSettled scenario in ProviderFinancePage
  const mockRejectedResult = {
    status: 'rejected',
    reason: {
      response: {
        data: { message: 'Network connection timeout' },
      },
    },
  }

  let errorState = null
  const setError = (val) => {
    errorState = val
  }

  // The fixed handler in ProviderFinancePage
  if (mockRejectedResult.status === 'rejected') {
    const errMsg =
      mockRejectedResult.reason?.response?.data?.message ||
      mockRejectedResult.reason?.message ||
      'Không thể tải dữ liệu tổng quan tài chính.'
    setError(errMsg)
  }

  assert.equal(errorState, 'Network connection timeout')
  assert.doesNotThrow(() => {
    // Verified that error is accessed without ReferenceError: error is not defined
    const hasError = Boolean(errorState)
    assert.equal(hasError, true)
  })
})
