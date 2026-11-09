import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import AppHeader from '../components/AppHeader'
import AccountLinkPanel from '../components/identity/AccountLinkPanel'
import { identityVerificationService } from '../services/identityVerificationService'
import { identityMessage } from '../components/identity/identityUi'

export default function IdentityAccountsPage() {
  const { user } = useAuth()
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => { let alive = true; identityVerificationService.status().then(data => { if (alive) setSummary(data) }).catch(err => { if (alive) setError(identityMessage(err)) }); return () => { alive = false } }, [])
  return <div className="min-h-screen bg-slate-50"><AppHeader /><main className="mx-auto max-w-3xl space-y-5 px-4 py-8"><Link className="text-sm font-semibold text-brand-700" to="/me">← Tài khoản của tôi</Link><h1 className="text-2xl font-bold">Liên kết hai tài khoản</h1>{error && <p role="alert" className="text-red-700">{error}</p>}<AccountLinkPanel userId={user.userId} canInitiate={!!summary?.canInitiateLink} onChanged={async () => setSummary(await identityVerificationService.status())} /></main></div>
}
