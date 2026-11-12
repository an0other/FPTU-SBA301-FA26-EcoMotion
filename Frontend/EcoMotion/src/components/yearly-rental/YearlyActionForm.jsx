import { useState } from 'react'
import { yearlyMoney, parseYearlyVnd, yearlyInputClass, yearlyButtonClass } from '../../utils/yearlyRental'

function MoneyInput({ field }) {
  const [value, setValue] = useState(String(field.value ?? ''))
  const amount = parseYearlyVnd(value)
  const minimum = field.min ?? 1000
  const error = value.trim() === '' ? '' : amount == null ? 'Nhập số tiền nguyên đồng, ví dụ 50000000 hoặc 50.000.000.'
    : amount < minimum ? 'Số tiền tối thiểu là ' + yearlyMoney(minimum)
    : field.max != null && amount > Number(field.max) ? 'Số tiền không được vượt ' + yearlyMoney(field.max) : ''
  return <><input name={field.name} type="text" inputMode="numeric" autoComplete="off" required={field.required !== false}
    value={value} onChange={e => setValue(e.target.value)} aria-invalid={!!error} maxLength={32}
    ref={node => { if (node) node.setCustomValidity(error) }} className={yearlyInputClass} />
    <span className={'mt-1 block text-sm font-semibold ' + (error ? 'text-red-700' : 'text-emerald-800')} aria-live="polite">{error || (amount == null ? 'Nhập số thực chuyển / thực nhận, không mặc định bằng số phải trả.' : yearlyMoney(amount))}</span></>
}

export default function YearlyActionForm({ title, description, fields = [], submitLabel = 'Xác nhận', busy, disabled = false, submissionDisabled = false, confirmationKey, onSubmit, confirm = false }) {
  const locked = busy || disabled || submissionDisabled
  async function submit(event) {
    event.preventDefault()
    if (locked) return
    const form = event.currentTarget
    const data = new FormData(form)
    for (const field of fields.filter(f => f.type === 'money')) {
      const amount = parseYearlyVnd(data.get(field.name))
      if (amount == null) return
      data.set(field.name, String(amount))
    }
    const success = await onSubmit(data)
    if (success) form.reset()
  }
  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <h4 className="font-semibold text-slate-900">{title}</h4>
      {description && <p className="text-sm text-slate-600">{description}</p>}
      <fieldset disabled={busy || disabled} className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => (
          <label key={field.name} className={field.type === 'textarea' ? 'sm:col-span-2 text-sm text-slate-700' : 'text-sm text-slate-700'}>
            <span>{field.label}{field.required === false ? ' (tùy chọn)' : ''}</span>
            {field.type === 'money' ? <MoneyInput field={field} /> : field.type === 'select' ? (
              <select name={field.name} defaultValue={field.value ?? ''} required={field.required !== false} className={yearlyInputClass}>
                <option value="" disabled>Chọn…</option>
                {field.options?.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            ) : field.type === 'textarea' ? (
              <textarea name={field.name} defaultValue={field.value || ''} required={field.required !== false} maxLength={1000} rows={3} className={yearlyInputClass} />
            ) : (
              <input name={field.name} type={field.type || 'text'} defaultValue={field.type === 'file' ? undefined : field.value}
                required={field.required !== false} min={field.min ?? (field.type === 'number' ? 0 : undefined)}
                max={field.max}
                step={field.type === 'number' ? (field.step ?? 1) : undefined} maxLength={field.type === 'text' ? 80 : undefined}
                accept={field.type === 'file' ? 'image/jpeg,image/png' : undefined}
                className={yearlyInputClass} />
            )}
          </label>
        ))}
      </fieldset>
      {confirm && <label className="flex gap-2 text-sm text-slate-700"><input key={confirmationKey} type="checkbox" required disabled={locked} />Tôi đã kiểm tra thông tin và đồng ý xác nhận thao tác này.</label>}
      <button type="submit" disabled={locked} className={yearlyButtonClass}>{busy ? 'Đang xử lý…' : submitLabel}</button>
    </form>
  )
}
