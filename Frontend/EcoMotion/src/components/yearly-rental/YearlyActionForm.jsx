import { yearlyInputClass, yearlyButtonClass } from '../../utils/yearlyRental'

export default function YearlyActionForm({ title, description, fields = [], submitLabel = 'Xác nhận', busy, onSubmit, confirm = false }) {
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const success = await onSubmit(new FormData(form))
    if (success) form.reset()
  }
  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <h4 className="font-semibold text-slate-900">{title}</h4>
      {description && <p className="text-sm text-slate-600">{description}</p>}
      <fieldset disabled={busy} className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => (
          <label key={field.name} className={field.type === 'textarea' ? 'sm:col-span-2 text-sm text-slate-700' : 'text-sm text-slate-700'}>
            <span>{field.label}{field.required === false ? ' (tùy chọn)' : ''}</span>
            {field.type === 'select' ? (
              <select name={field.name} defaultValue={field.value ?? ''} required={field.required !== false} className={yearlyInputClass}>
                <option value="" disabled>Chọn…</option>
                {field.options?.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            ) : field.type === 'textarea' ? (
              <textarea name={field.name} defaultValue={field.value || ''} required={field.required !== false} maxLength={1000} rows={3} className={yearlyInputClass} />
            ) : (
              <input name={field.name} type={field.type || 'text'} defaultValue={field.type === 'file' ? undefined : field.value}
                required={field.required !== false} min={field.min ?? (field.type === 'number' ? 0 : undefined)}
                step={field.type === 'number' ? (field.step ?? 1) : undefined} maxLength={field.type === 'text' ? 80 : undefined}
                accept={field.type === 'file' ? 'image/jpeg,image/png' : undefined}
                className={yearlyInputClass} />
            )}
          </label>
        ))}
      </fieldset>
      {confirm && <label className="flex gap-2 text-sm text-slate-700"><input type="checkbox" required disabled={busy} />Tôi đã kiểm tra thông tin và đồng ý xác nhận thao tác này.</label>}
      <button type="submit" disabled={busy} className={yearlyButtonClass}>{busy ? 'Đang xử lý…' : submitLabel}</button>
    </form>
  )
}
