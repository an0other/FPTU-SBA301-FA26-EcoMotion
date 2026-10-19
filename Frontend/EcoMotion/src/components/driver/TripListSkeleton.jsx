export default function TripListSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3].map((idx) => (
        <div
          key={idx}
          className="animate-pulse rounded-2xl border border-slate-200/80 bg-white p-4 sm:p-5"
        >
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div className="h-4 w-28 rounded bg-slate-200" />
            <div className="h-5 w-24 rounded-full bg-slate-200" />
          </div>
          <div className="my-3 space-y-3">
            <div className="h-4 w-3/4 rounded bg-slate-200" />
            <div className="h-4 w-1/2 rounded bg-slate-200" />
            <div className="h-12 rounded-xl bg-slate-100" />
          </div>
          <div className="flex items-center justify-between border-t border-slate-100 pt-3">
            <div className="h-3 w-20 rounded bg-slate-200" />
            <div className="h-7 w-24 rounded-xl bg-slate-200" />
          </div>
        </div>
      ))}
    </div>
  )
}
