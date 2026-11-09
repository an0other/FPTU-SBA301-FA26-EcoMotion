import { useEffect, useState } from 'react'
import apiClient from '../../services/apiClient'

export default function EvidenceImage({ src, alt = 'Ảnh minh chứng', openable = false, ...props }) {
  const [loaded, setLoaded] = useState(null)
  const protectedImage = /^\/api\/(?:v1\/)?bookings\/[^/]+\/evidences\/\d+\/file$/.test(src || '')
  useEffect(() => {
    if (!protectedImage) return undefined
    let active = true
    let objectUrl
    apiClient.get(src.replace(/^\/api/, ''), { responseType: 'blob' }).then(response => {
      if (!active) return
      objectUrl = URL.createObjectURL(response.data)
      setLoaded({ url: src, source: objectUrl })
    }).catch(() => { if (active) setLoaded({ url: src, error: true }) })
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [src, protectedImage])
  if (protectedImage && loaded?.url === src && loaded.error) return <p role="alert" className="text-sm text-red-700">Không tải được ảnh minh chứng.</p>
  const source = protectedImage ? (loaded?.url === src ? loaded.source : null) : src
  if (!source) return <p role="status" className="text-sm text-slate-500">Đang tải ảnh…</p>
  const picture = <img {...props} src={source} alt={alt} />
  return openable ? <a href={source} target="_blank" rel="noreferrer" aria-label={alt}>{picture}<span className="text-sm underline">Xem ảnh đầy đủ</span></a> : picture
}
