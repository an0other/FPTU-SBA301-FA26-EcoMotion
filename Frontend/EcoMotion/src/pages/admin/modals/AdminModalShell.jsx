/**
 * Shared non-scrolling modal shell for Admin views:
 * pins header and action footer (`shrink-0`) while letting only the middle
 * body scroll if content exceeds a compact viewport height.
 */
export default function AdminModalShell({
  maxWidth = 'max-w-5xl',
  zIndex = 'z-50',
  role,
  ariaModal,
  ariaLabel,
  onBackdropClick,
  header,
  footer,
  bodyClassName = 'px-5 py-3.5',
  panelClassName = 'rounded-3xl border border-slate-200 bg-white shadow-2xl',
  children,
  extraOverlay,
}) {
  return (
    <div
      className={`fixed inset-0 ${zIndex} flex items-center justify-center bg-black/60 p-3 sm:p-4 backdrop-blur-xs`}
      onClick={onBackdropClick}
    >
      <div
        role={role}
        aria-modal={ariaModal}
        aria-label={ariaLabel}
        onClick={onBackdropClick ? (e) => e.stopPropagation() : undefined}
        className={`relative flex max-h-[92vh] w-full ${maxWidth} flex-col overflow-hidden ${panelClassName}`}
      >
        {header && (
          <div className="shrink-0 border-b border-slate-100 px-5 py-3.5">
            {header}
          </div>
        )}

        <div className={`min-h-0 flex-1 overflow-y-auto ${bodyClassName}`}>
          {children}
        </div>

        {footer && (
          <div className="shrink-0 border-t border-slate-100 bg-white px-5 py-3">
            {footer}
          </div>
        )}
      </div>

      {extraOverlay}
    </div>
  )
}

