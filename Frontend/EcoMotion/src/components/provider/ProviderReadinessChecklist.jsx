import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { providerFinanceService } from '../../services/providerFinanceService'

export default function ProviderReadinessChecklist() {
  const [state, setState] = useState({ loading: true, account: null, error: '' })
  useEffect(() => {
    let alive = true
    providerFinanceService.getBankAccount().then(account => { if (alive) setState({ loading: false, account, error: '' }) })
      .catch(error => { if (alive) setState({ loading: false, account: null, error: error.message }) })
    return () => { alive = false }
  }, [])
  if (state.loading || state.account) return null
  return <section className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><h2 className="font-semibold">Hoàn thiện thiết lập nhận tiền</h2><p className="mt-1">{state.error ? 'Chưa kiểm tra được tài khoản nhận tiền. Hãy mở mục Tài chính để kiểm tra.' : 'Bạn có thể quản lý hồ sơ và xe. Trước khi chấp nhận đơn thuê, cần thiết lập tài khoản ngân hàng nhận tiền.'}</p><Link className="mt-2 inline-block font-semibold underline" to="/provider/finance">Thiết lập tài khoản nhận tiền →</Link></section>
}
