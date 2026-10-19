import { useRef, useState } from 'react'
import { yearlyRentalService } from '../../services/yearlyRentalService'
import { BOOKING_STATUS_LABELS } from '../../utils/paymentDisplay'

export default function YearlyContractPanel({ contract, onUploaded }) {
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const inputRef = useRef(null)
  const operationInFlight = useRef(false)

  async function handleUpload(event) {
    event.preventDefault()
    if (operationInFlight.current) return

    if (!file) {
      setError('Vui lòng chọn hợp đồng PDF.')
      return
    }

    if (file.type && file.type !== 'application/pdf') {
      setError('Hợp đồng phải là file PDF.')
      return
    }

    operationInFlight.current = true
    setBusy('upload')
    setError('')

    try {
      await yearlyRentalService.uploadContract(contract.bookingCode, file)
      setFile(null)
      if (inputRef.current) inputRef.current.value = ''
      onUploaded()
    } catch (error) {
      setError(error.message || 'Không upload được hợp đồng.')
    } finally {
      operationInFlight.current = false
      setBusy('')
    }
  }

  async function handleDownload() {
    if (operationInFlight.current) return

    operationInFlight.current = true
    setBusy('download')
    setError('')

    try {
      const blob = await yearlyRentalService.downloadContract(contract.bookingCode)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')

      link.href = url
      link.download = `contract-${contract.bookingCode}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()

      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (error) {
      setError(error.message || 'Không tải được hợp đồng.')
    } finally {
      operationInFlight.current = false
      setBusy('')
    }
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
      <h2 className="text-lg font-bold text-slate-900">Hợp đồng gói năm</h2>

      <div className="mt-4 rounded-xl bg-slate-50 p-4">
        <p className="font-semibold text-slate-900">{contract.vehicleName}</p>
        <p className="mt-1 text-sm text-slate-600">{contract.bookingCode}</p>
        <p className="mt-1 text-xs text-slate-500">
          {BOOKING_STATUS_LABELS[contract.bookingStatus] ?? contract.bookingStatus}
        </p>

        <p className="mt-3 text-sm font-semibold text-brand-800">
          {contract.contractUploaded ? 'Đã có hợp đồng' : 'Chưa upload hợp đồng'}
        </p>

        {contract.contractUploaded && !contract.canDownload && (
          <p className="mt-2 text-xs text-amber-800">
            Hợp đồng đang lưu chưa hỗ trợ tải qua chức năng này.
          </p>
        )}
      </div>

      {contract.canUpload ? (
        <form onSubmit={handleUpload} className="mt-5 space-y-4">
          <label className="block">
            <span className="text-sm font-semibold text-slate-700">
              {contract.contractUploaded ? 'Thay hợp đồng PDF' : 'Chọn hợp đồng PDF'}
            </span>

            <input
              ref={inputRef}
              type="file"
              accept=".pdf,application/pdf"
              disabled={Boolean(busy)}
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null)
                setError('')
              }}
              className="mt-2 block w-full rounded-lg border border-slate-200 p-3 text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-2 file:font-semibold file:text-brand-700"
            />
          </label>

          <p className="text-xs leading-relaxed text-slate-500">
            Upload bản hợp đồng đã ký. Nếu thay file, lần tải tiếp theo sẽ nhận
            bản mới. Giới hạn dung lượng được backend kiểm tra khi upload.
          </p>

          <button
            type="submit"
            disabled={Boolean(busy) || !file}
            className="rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
          >
            {busy === 'upload' ? 'Đang upload…' : 'Lưu hợp đồng'}
          </button>
        </form>
      ) : (
        <p className="mt-5 rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
          {contract.restrictionReason || 'Đơn hiện không cho phép thay hợp đồng.'}
        </p>
      )}

      {contract.canDownload && (
        <button
          type="button"
          onClick={handleDownload}
          disabled={Boolean(busy)}
          className="mt-4 rounded-lg border border-brand-200 px-4 py-2.5 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-50"
        >
          {busy === 'download' ? 'Đang tải…' : 'Tải hợp đồng PDF'}
        </button>
      )}

      {error && (
        <p role="alert" className="mt-4 text-sm text-on-error-container">{error}</p>
      )}
    </section>
  )
}