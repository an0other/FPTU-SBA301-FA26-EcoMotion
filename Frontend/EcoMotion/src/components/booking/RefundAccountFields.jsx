export function RefundAccountFields({ value, onChange, disabled = false, className = 'space-y-3' }) {
  const update = (field, next) => onChange({ ...value, [field]: next })
  const inputClass = 'mt-1.5 block min-h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 focus:border-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-700/20 disabled:bg-slate-50'
  return <div className={className}>
    <label className="block text-sm font-medium text-slate-800">Ngân hàng nhận hoàn
      <input required maxLength={150} autoComplete="organization" value={value.bankName} disabled={disabled}
        onChange={e => update('bankName', e.target.value)} placeholder="Ví dụ: Vietcombank" className={inputClass} />
    </label>
    <label className="block text-sm font-medium text-slate-800">Tên chủ tài khoản
      <input required maxLength={150} autoComplete="name" value={value.accountHolder} disabled={disabled}
        onChange={e => update('accountHolder', e.target.value)} placeholder="Nhập đúng tên đăng ký tại ngân hàng" className={inputClass} />
    </label>
    <label className="block text-sm font-medium text-slate-800">Số tài khoản nhận hoàn
      <input required inputMode="numeric" pattern="[0-9]{6,30}" maxLength={30} value={value.accountNumber} disabled={disabled}
        onChange={e => update('accountNumber', e.target.value.replace(/\s/g, ''))} className={`${inputClass} tabular-nums`} />
    </label>
  </div>
}
