import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconBuilding,
  IconCalendar,
  IconCar,
  IconChevronRight,
  IconClock,
  IconImage,
  IconInfo,
  IconMapPin,
  IconPercent,
  IconPhone,
  IconRefresh,
  IconRoute,
  IconSettings2,
  IconShield,
  IconSparkles,
  IconStar,
  IconTag,
  IconUsers,
  IconZap,
} from '../components/icons/Icons'
import { useVehicle } from '../hooks/useVehicle'
import { formatCurrency, FUEL_LABELS, PLAN_LABELS, TRANSMISSION_LABELS } from '../utils/formatCurrency'

function bookingRequestPath(vehicleId, planId) {
  const params = new URLSearchParams({ vehicleId: String(vehicleId) })
  if (planId != null && planId !== '') params.set('planId', String(planId))
  return `/renter/bookings/new?${params}`
}


function formatDuration(minutes) {
  const value = Number(minutes)
  if (!Number.isFinite(value) || value <= 0) return null
  if (value % 1440 === 0) return `${value / 1440} ngày`
  if (value % 60 === 0) return `${value / 60} giờ`
  return `${value} phút`
}

function formatRatioPercent(ratio) {
  if (ratio === null || ratio === undefined || ratio === '') return null
  const value = Number(ratio) * 100
  if (Number.isNaN(value)) return null
  return `${Number(value.toFixed(2))}%`
}

function policyRowsOf(plan) {
  if (!plan) return []
  const rows = []

  const overtime = formatDuration(plan.allowedOvertimeMinutes)
  if (overtime) rows.push({ label: 'Vượt giờ cho phép', value: overtime, Icon: IconClock })

  const overtimeRate = formatRatioPercent(plan.overtimeSurchargeRatio)
  if (overtimeRate) rows.push({ label: 'Phụ thu vượt giờ', value: overtimeRate, Icon: IconPercent })

  if (plan.freeRadiusKm !== null && plan.freeRadiusKm !== undefined) {
    rows.push({ label: 'Miễn phí trong bán kính', value: `${plan.freeRadiusKm} km`, Icon: IconRoute })
  }
  if (plan.outOfRadiusRate !== null && plan.outOfRadiusRate !== undefined) {
    rows.push({
      label: 'Phụ thu ngoài bán kính',
      value: `${formatCurrency(plan.outOfRadiusRate)}/km`,
      Icon: IconRoute,
    })
  }

  const waitingFree = formatDuration(plan.waitingFreeMinutes)
  if (waitingFree) rows.push({ label: 'Chờ miễn phí', value: waitingFree, Icon: IconClock })

  if (plan.waitingRate !== null && plan.waitingRate !== undefined) {
    rows.push({ label: 'Phụ thu thời gian chờ', value: formatCurrency(plan.waitingRate), Icon: IconClock })
  }

  const weekend = formatRatioPercent(plan.weekendSurchargeRatio)
  if (weekend) rows.push({ label: 'Phụ thu cuối tuần', value: weekend, Icon: IconPercent })

  const threshold = formatDuration(plan.hourlyToDailyThresholdMinutes)
  if (threshold) rows.push({ label: 'Ngưỡng quy đổi sang ngày', value: threshold, Icon: IconClock })

  return rows
}

function DetailSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <div className="space-y-5 lg:col-span-8">
        <div className="aspect-[16/10] animate-pulse rounded-xl bg-slate-200" />
        <div className="h-28 animate-pulse rounded-xl bg-slate-100" />
        <div className="h-40 animate-pulse rounded-xl bg-slate-100" />
      </div>
      <div className="lg:col-span-4">
        <div className="h-80 animate-pulse rounded-xl bg-slate-100" />
      </div>
    </div>
  )
}

function StateMessage({ icon, title, description, children }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-card">
      <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        {icon}
      </span>
      <h1 className="mt-4 text-xl font-bold text-slate-900">{title}</h1>
      <p className="mx-auto mt-2 max-w-md text-[13px] text-slate-500">{description}</p>
      <div className="mt-5 flex flex-wrap items-center justify-center gap-3">{children}</div>
    </div>
  )
}

function PolicyRow({ label, value, Icon }) {
  return (
    <div className="flex items-start justify-between gap-3 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
      <span className="flex min-w-0 items-center gap-1.5 text-[12px] font-medium text-slate-600">
        <Icon className="h-3.5 w-3.5 shrink-0 text-brand-600" />
        {label}
      </span>
      <span className="max-w-[55%] text-right text-[12px] font-bold text-slate-900">{value}</span>
    </div>
  )
}

export default function VehicleDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { vehicle, loading, error, notFound, reload } = useVehicle(id)
  const [activeImage, setActiveImage] = useState(0)
  const [selectedPlanType, setSelectedPlanType] = useState(null)

  const images = vehicle?.imageUrls ?? []
  const activeIndex = images.length ? Math.min(activeImage, images.length - 1) : 0
  const cover = images[activeIndex] ?? null

  const plans = vehicle?.plans ?? []
  const defaultPlan = plans.find((plan) => plan.planType === 'DAILY') ?? plans[0] ?? null
  const selectedPlan = plans.find((plan) => plan.planType === selectedPlanType) ?? defaultPlan
  const selectedPolicyRows = policyRowsOf(selectedPlan)


  const provider = vehicle?.provider ?? null
  const hotline = provider?.hotline ?? null
  const carName = vehicle
    ? `${vehicle.brand} ${vehicle.model}${vehicle.manufactureYear ? ` - ${vehicle.manufactureYear}` : ''}`
    : ''
  const ratingCount = Number(vehicle?.ratingCount ?? 0)
  const hasRating = ratingCount > 0
  const ratingAvg = Number(vehicle?.ratingAvg ?? 0)
  const fuelLabel = FUEL_LABELS[vehicle?.fuelType] ?? vehicle?.fuelType ?? '—'
  const transmissionLabel =
    TRANSMISSION_LABELS[vehicle?.transmission] ?? vehicle?.transmission ?? '—'

  const specs = vehicle
    ? [
        { label: 'Hãng xe', value: vehicle.brand, Icon: IconCar },
        { label: 'Dòng xe', value: vehicle.model, Icon: IconTag },
        { label: 'Đời xe', value: vehicle.manufactureYear, Icon: IconCalendar },
        { label: 'Số chỗ ngồi', value: vehicle.seatCount ? `${vehicle.seatCount} chỗ` : null, Icon: IconUsers },
        { label: 'Hộp số', value: transmissionLabel, Icon: IconSettings2 },
        { label: 'Nhiên liệu', value: fuelLabel, Icon: IconZap },
        { label: 'Màu sắc', value: vehicle.color, Icon: IconSparkles },
      ].filter((spec) => spec.value !== null && spec.value !== undefined && spec.value !== '')
    : []

  return (
    <div className="min-h-screen bg-surface">
      <AppHeader />

      <nav aria-label="Đường dẫn trang" className="border-b border-slate-200 bg-white py-3">
        <div className="mx-auto flex max-w-[1400px] items-center px-4 text-xs sm:px-6">
          <div className="flex min-w-0 flex-wrap items-center gap-2 text-slate-500">
            <Link to="/" className="transition-colors hover:text-brand-600">
              Trang chủ
            </Link>
            <IconChevronRight className="h-3.5 w-3.5 text-slate-400" />
            <span className="max-w-[220px] truncate font-semibold text-slate-800" aria-current="page">
              {vehicle ? carName : 'Chi tiết xe'}
            </span>
          </div>
        </div>
      </nav>

      <main className="mx-auto w-full max-w-[1280px] px-4 py-7 sm:px-6 lg:py-10">
        {loading && <DetailSkeleton />}

        {!loading && notFound && (
          <StateMessage
            icon={<IconCar className="h-7 w-7" />}
            title="Không tìm thấy xe"
            description="Xe này không tồn tại hoặc chưa được duyệt để cho thuê. Hãy quay lại danh sách để chọn chiếc xe khác."
          >
            <Link
              to="/"
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-[13px] font-semibold text-white transition-colors hover:bg-brand-700"
            >
              <IconArrowLeft className="h-4 w-4" />
              Về danh sách xe
            </Link>
          </StateMessage>
        )}

        {!loading && !notFound && error && (
          <StateMessage
            icon={<IconAlertTriangle className="h-7 w-7" />}
            title="Không tải được thông tin xe"
            description={error}
          >
            <button
              type="button"
              onClick={reload}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-[13px] font-semibold text-white transition-colors hover:bg-brand-700"
            >
              <IconRefresh className="h-4 w-4" />
              Thử lại
            </button>
            <Link
              to="/"
              className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-[13px] font-semibold text-slate-700 transition-colors hover:bg-slate-100"
            >
              <IconArrowLeft className="h-4 w-4" />
              Về danh sách xe
            </Link>
          </StateMessage>
        )}

        {!loading && vehicle && (
          <div className="grid grid-cols-1 items-start gap-7 lg:grid-cols-12 lg:gap-8">
            <div className="space-y-6 lg:col-span-8">
              <div className="space-y-3">
                <div className="aspect-[16/9] overflow-hidden rounded-2xl border border-slate-200 bg-slate-900 shadow-lg sm:aspect-[2/1]">
                  {cover ? (
                    <img src={cover} alt={carName} className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full flex-col items-center justify-center gap-2 text-sm text-slate-400">
                      <IconImage className="h-7 w-7" />
                      Chưa có ảnh
                    </div>
                  )}

                </div>

                {images.length > 1 && (
                  <div className="grid grid-cols-4 gap-2 sm:gap-3">
                    {images.map((url, index) => (
                      <button
                        key={url}
                        type="button"
                        onClick={() => setActiveImage(index)}
                        aria-label={`Xem ảnh ${index + 1} của ${carName}`}
                        aria-pressed={index === activeIndex}
                        className={`aspect-[16/10] overflow-hidden rounded-xl border-2 transition-all ${
                          index === activeIndex
                            ? 'border-brand-700 ring-2 ring-brand-500/30 shadow-card'
                            : 'border-slate-200 opacity-70 hover:border-slate-300 hover:opacity-100'
                        }`}
                      >
                        <img
                          src={url}
                          alt={`${carName} - ảnh ${index + 1}`}
                          className="h-full w-full object-cover"
                        />
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-card sm:p-7">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <h1 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">{carName}</h1>
                  <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-brand-50/50 px-4 py-3">
                    <IconStar className="h-5 w-5 text-amber-500" />
                    <span className="text-xl font-extrabold text-ink">{hasRating ? ratingAvg.toFixed(1) : 'Mới'}</span>
                    {hasRating && <span className="text-xs text-slate-500">({ratingCount} đánh giá)</span>}
                  </div>
                </div>
                {provider?.depotAddress && <p className="mt-3 flex items-start gap-2 text-sm text-slate-500"><IconMapPin className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />{provider.depotAddress}</p>}
              </div>

              <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-card sm:p-7">
                <h2 className="flex items-center gap-2.5 text-lg font-bold text-slate-900">
                  <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-brand-700"><IconSettings2 className="h-4 w-4" /></span>
                  Thông số kỹ thuật phương tiện
                </h2>
                <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {specs.map(({ label, value, Icon }) => (
                    <div key={label} className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                      <span className="flex items-center gap-2 text-xs font-medium text-slate-500">
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-brand-700"><Icon className="h-4 w-4" /></span>
                        {label}
                      </span>
                      <p className="mt-3 text-sm font-bold text-slate-900 sm:text-base">{value}</p>
                    </div>
                  ))}
                </div>

              </section>

              {provider && (
                <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-card">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-100 text-brand-700">
                        <IconBuilding className="h-6 w-6" />
                      </span>
                      <div className="min-w-0">
                        <p className="text-[15px] font-bold text-slate-900">{provider.displayName}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] text-slate-500">
                          {Number(provider.ratingCount ?? 0) > 0 ? (
                            <span className="flex items-center gap-1 font-semibold text-slate-700">
                              <IconStar className="h-3.5 w-3.5 text-amber-500" />
                              {Number(provider.ratingAvg ?? 0).toFixed(1)}
                              <span className="font-normal text-slate-500">
                                ({provider.ratingCount})
                              </span>
                            </span>
                          ) : (
                            <span>Chưa có đánh giá</span>
                          )}
                          {hotline && (
                            <span className="flex items-center gap-1">
                              <IconPhone className="h-3.5 w-3.5 text-brand-600" />
                              {hotline}
                            </span>
                          )}
                        </div>
                        {provider.depotAddress && (
                          <p className="mt-1.5 flex items-start gap-1.5 text-[12px] text-slate-500">
                            <IconMapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-brand-600" />
                            {provider.depotAddress}
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                </section>
              )}
            </div>

            <div className="lg:col-span-4">
              <div className="space-y-4 lg:sticky lg:top-24">
                <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg">
                  <div className="border-b border-slate-100 px-5 py-5 sm:px-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                      Giá thuê theo gói
                    </span>
                    <p className="mt-2 flex items-end gap-1">
                      <span className="text-3xl font-bold tracking-tight text-brand-700">
                        {selectedPlan ? formatCurrency(selectedPlan.slotPrice) : 'Chưa có giá'}
                      </span>
                      <span className="pb-1 text-[13px] font-medium text-slate-500">
                        / slot
                      </span>
                    </p>
                  </div>

                  <div className="p-5 sm:p-6">
                    {plans.length > 0 ? (
                      <div className="flex gap-1 rounded-lg bg-slate-100 p-1">
                        {plans.map((plan) => (
                          <button
                            key={plan.planType}
                            type="button"
                            onClick={() => setSelectedPlanType(plan.planType)}
                            className={`flex-1 rounded-md px-2 py-1.5 text-[12px] font-semibold transition-colors ${
                              selectedPlan?.planType === plan.planType
                                ? 'bg-white text-brand-700 shadow-xs'
                                : 'text-slate-500 hover:text-slate-700'
                            }`}
                          >
                            {PLAN_LABELS[plan.planType] ?? plan.planType}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p className="rounded-lg bg-slate-50 px-3 py-2 text-[12px] text-slate-500">
                        Xe chưa có gói thuê đang mở bán.
                      </p>
                    )}

                    {selectedPlan && (
                      <dl className="mt-4 space-y-2 border-t border-slate-100 pt-4 text-[13px]">
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-slate-500">Độ dài mỗi slot</dt>
                          <dd className="font-semibold text-slate-900">
                            {formatDuration(selectedPlan.slotLengthMinutes) ?? '—'}
                          </dd>
                        </div>
                        {selectedPlan.upfrontAmount !== null &&
                          selectedPlan.upfrontAmount !== undefined && (
                            <div className="flex items-center justify-between gap-3">
                              <dt className="text-slate-500">Trả trước</dt>
                              <dd className="font-semibold text-slate-900">
                                {formatCurrency(selectedPlan.upfrontAmount)}
                              </dd>
                            </div>
                          )}
                      </dl>
                    )}

                    <div className="mt-5 border-t border-slate-100 pt-5">
                      <div className="flex items-start gap-3">
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-100 text-brand-700">
                          <IconCalendar className="h-5 w-5" />
                        </span>
                        <div>
                          <h2 className="text-lg font-bold text-ink">Đặt xe</h2>
                          <p className="mt-1 text-sm text-slate-500">
                            Chọn gói bên trên rồi gửi yêu cầu. Bạn sẽ chọn lịch và điểm đón ở bước tiếp theo.
                            {!selectedPlan ? ' Xe này chưa có gói đang mở bán.' : ''}
                          </p>
                        </div>
                      </div>
                      <div className="mt-5">
                        <button
                          type="button"
                          disabled={!selectedPlan}
                          onClick={() => {
                            if (!vehicle || !selectedPlan) return
                            navigate(bookingRequestPath(vehicle.id, selectedPlan.id))
                          }}
                          className={`w-full rounded-xl px-5 py-3 text-sm font-bold transition-colors ${
                            selectedPlan
                              ? 'bg-brand-600 text-white hover:bg-brand-700'
                              : 'cursor-not-allowed bg-slate-200 text-slate-500'
                          }`}
                        >
                          Gửi yêu cầu đặt xe
                        </button>
                      </div>
                    </div>
                    <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-relaxed text-slate-500">
                      <IconInfo className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                      Chưa đăng nhập sẽ được chuyển sang trang đăng nhập, rồi quay lại wizard đặt xe.
                    </p>
                  </div>
                </div>

                <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card">
                  <h2 className="flex items-center gap-2 text-[15px] font-bold text-slate-900">
                    <IconShield className="h-4 w-4 text-brand-600" />
                    Chính sách gói đang chọn
                  </h2>
                  {selectedPolicyRows.length === 0 ? (
                    <p className="mt-3 text-[12px] text-slate-500">
                      Gói này chưa được nhà cung cấp cấu hình chính sách riêng.
                    </p>
                  ) : (
                    <div className="mt-3 space-y-1.5">
                      {selectedPolicyRows.map((row) => (
                        <PolicyRow key={row.label} {...row} />
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
