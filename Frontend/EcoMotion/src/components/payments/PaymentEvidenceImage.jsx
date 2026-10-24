import { useEffect, useState } from 'react'
import { providerFinanceService } from '../../services/providerFinanceService'

export function PaymentEvidenceImage({ evidence, className = '', alt = 'Biên lai chuyển khoản' }) {
  const protectedEvidence = evidence?.proofImageUrl?.startsWith('/api/v1/payment-evidences/')
  const [source, setSource] = useState(
    protectedEvidence ? '' : evidence?.proofImageUrl,
  )
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!evidence?.id || !protectedEvidence) return undefined
    let active = true
    let objectUrl = ''
    providerFinanceService.getPaymentEvidenceImage(evidence.id)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setSource(objectUrl)
      })
      .catch(() => { if (active) setFailed(true) })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [evidence?.id, protectedEvidence])

  if (failed) {
    return <div className={`flex items-center justify-center bg-slate-100 text-xs text-slate-500 ${className}`}>Không tải được ảnh</div>
  }
  if (!evidence?.proofImageUrl) {
    return <div className={`flex items-center justify-center bg-slate-100 text-xs text-slate-500 ${className}`}>Không có ảnh để hiển thị</div>
  }
  if (!source) {
    return <div className={`animate-pulse bg-slate-100 ${className}`} aria-label="Đang tải biên lai" />
  }
  return <img src={source} alt={alt} className={className} />
}
