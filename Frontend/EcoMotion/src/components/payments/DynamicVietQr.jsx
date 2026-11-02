import { useEffect, useState } from 'react'
import QRCode from 'qrcode'

export function DynamicVietQr({ payload }) {
  const [image, setImage] = useState(null)
  useEffect(() => {
    let active = true
    if (payload) QRCode.toDataURL(payload, { width: 320, margin: 2 }).then(url => {
      if (active) setImage({ payload, url })
    }).catch(() => { if (active) setImage(null) })
    return () => { active = false }
  }, [payload])
  return image && image.payload === payload
    ? <img src={image.url} alt="VietQR động" className="h-64 w-64 max-w-full rounded-lg bg-white object-contain" />
    : <p className="flex h-64 w-64 items-center justify-center text-sm text-slate-600">{payload ? 'Đang hiển thị QR' : 'Bấm tạo mã QR để thanh toán'}</p>
}
