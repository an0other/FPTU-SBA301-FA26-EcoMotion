const STEPS = [
  { id: 1, label: 'Chọn gói & Thời gian' },
  { id: 2, label: 'Điểm đón & Khách' },
  { id: 3, label: 'Xác nhận & Gửi' },
]

const FOCUS =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2'

function CheckIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
    </svg>
  )
}

export function BookingStepper({ current = 1, onStepSelect }) {
  return (
    <nav aria-label="Các bước đặt xe">
      <ol className="flex items-center">
        {STEPS.map((step, index) => {
          const done = step.id < current
          const active = step.id === current
          const reachable = step.id < current
          const isLast = index === STEPS.length - 1
          return (
            <li key={step.id} className={`flex min-w-0 items-center ${isLast ? '' : 'flex-1'}`}>
              <button
                type="button"
                disabled={!reachable}
                onClick={() => reachable && onStepSelect?.(step.id)}
                aria-current={active ? 'step' : undefined}
                className={`group flex shrink-0 items-center gap-2 rounded-xl px-1.5 py-1.5 transition-colors ${FOCUS} ${
                  reachable
                    ? 'cursor-pointer hover:bg-brand-50/60'
                    : 'cursor-default'
                }`}
              >
                <span
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold transition-all duration-200 ${
                    done
                      ? 'bg-brand-700 text-white shadow-[0_2px_6px_rgba(21,128,61,0.25)]'
                      : active
                        ? 'bg-brand-700 text-white ring-4 ring-brand-100'
                        : 'border border-[#E6EBE8] bg-white text-[#64748B]'
                  }`}
                >
                  {done ? <CheckIcon /> : step.id}
                </span>
                <span
                  className={`hidden whitespace-nowrap text-sm sm:inline ${
                    active
                      ? 'font-semibold text-brand-800'
                      : done
                        ? 'font-medium text-brand-700'
                        : 'font-medium text-[#14221A]/55'
                  }`}
                >
                  {step.label}
                </span>
              </button>
              {!isLast && (
                <div className="mx-1 h-0.5 min-w-3 flex-1" aria-hidden="true">
                  <div
                    className={`h-full rounded-full transition-colors duration-300 ${
                      done ? 'bg-brand-700' : 'bg-[#E6EBE8]'
                    }`}
                  />
                </div>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
