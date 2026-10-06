import { Link } from 'react-router-dom'
import { IconSparkles } from './icons/Icons'

export const INPUT_CLASS =
  'w-full rounded-lg border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:opacity-60'

export const LABEL_CLASS =
  'mb-1.5 block text-[11px] font-semibold uppercase tracking-wider text-slate-700'

export function FieldError({ children }) {
  if (!children) return null
  return (
    <div
      role="alert"
      className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-[13px] font-medium text-on-error-container"
    >
      {children}
    </div>
  )
}

export default function AuthLayout({ title, subtitle, children }) {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <div className="flex w-full flex-1">
        <aside className="relative hidden flex-col justify-between overflow-hidden bg-ink p-12 text-white lg:flex lg:w-1/2">
          <div
            className="absolute inset-0 z-0 bg-cover bg-center transition-transform duration-700 hover:scale-105"
            style={{
              backgroundImage:
                "url('https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1600&q=80')",
            }}
          >
            <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/75 to-slate-900/60 backdrop-blur-[1px]" />
          </div>

          <div className="relative z-10 flex items-center">
            <Link
              to="/"
              className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/10 px-3 py-1.5 text-xs font-medium text-slate-200 backdrop-blur-md transition-all hover:bg-white/20"
            >
              ← Về trang chủ
            </Link>
          </div>

          <div className="relative z-10 my-auto max-w-lg py-10">
            <span className="mb-5 block text-4xl font-extrabold leading-tight tracking-tight text-white">
              EcoMotion
            </span>
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-brand-500/30 bg-brand-500/20 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-brand-300">
              <IconSparkles className="h-3.5 w-3.5 text-brand-400" />
              Chuẩn mực thuê xe kỷ nguyên mới
            </div>
            <h1 className="text-4xl font-bold leading-tight tracking-tight text-white">
              Trải nghiệm thuê xe <span className="font-extrabold text-brand-400">Minh Bạch, An Toàn</span> &amp;
              Tiện Lợi Nhất.
            </h1>
          </div>

          <div />
        </aside>

        <main className="flex w-full items-center justify-center overflow-y-auto bg-white px-4 py-10 sm:px-8 lg:w-1/2 lg:px-12 lg:py-16">
          <div className="mx-auto w-full max-w-md">
            <div className="mb-8 flex items-center justify-between border-b border-slate-100 pb-4 lg:hidden">
              <span className="text-xl font-extrabold tracking-tight text-slate-900">
                EcoMotion <span className="text-[11px] font-semibold text-brand-600">VN</span>
              </span>
              <Link to="/" className="text-xs font-medium text-slate-500 hover:text-brand-600">
                Trang chủ →
              </Link>
            </div>

            <div className="mb-6">
              <h2 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-[28px]">{title}</h2>
              {subtitle && <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>}
            </div>

            {children}
          </div>
        </main>
      </div>
    </div>
  )
}
