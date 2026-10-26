import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'

import AppHeader from './components/AppHeader'
import { BookingSubnav } from './components/booking/BookingSubnav'
import ProtectedRoute from './components/ProtectedRoute'
import ErrorBoundary from './components/ErrorBoundary'

import ActivatePage from './pages/ActivatePage'
import AdminDashboard from './pages/AdminDashboard'
import BookingReviewsPage from './pages/BookingReviewsPage'
import CustomerPaymentPage from './pages/CustomerPaymentPage'
import DriverSchedulePage from './pages/DriverSchedulePage'
import DriverTripDetailPage from './pages/DriverTripDetailPage'
import LoginPage from './pages/LoginPage'
import MePage from './pages/MePage'
import PaymentHistoryPage from './pages/PaymentHistoryPage'
import { PolicyPage } from './pages/PolicyPage'
import ProviderBookingDetailPage from './pages/ProviderBookingDetailPage'
import ProviderBookingQueuePage from './pages/ProviderBookingQueuePage'
import ProviderFinancePage from './pages/ProviderFinancePage'
import RegisterPage from './pages/RegisterPage'
import VehicleDetailPage from './pages/VehicleDetailPage'
import VehicleListPage from './pages/VehicleListPage'
import VnpayCallbackPage from './pages/VnpayCallbackPage'
import YearlyRentalPage from './pages/YearlyRentalPage'

import ProviderHomePage from './pages/provider/ProviderHomePage'
import ProviderVehicleListPage from './pages/provider/ProviderVehicleListPage'
import ProviderVehicleCreatePage from './pages/provider/ProviderVehicleCreatePage'
import ProviderVehicleHubPage from './pages/provider/ProviderVehicleHubPage'
import ProviderSchedulePage from './pages/provider/ProviderSchedulePage'
import ProviderDriverPage from './pages/provider/ProviderDriverPage'

import { BookingDetailPage } from './pages/booking/BookingDetailPage'
import { BookingRequestPage } from './pages/booking/BookingRequestPage'
import { MyBookingsPage } from './pages/booking/MyBookingsPage'
import { SubmittedBookingPage } from './pages/booking/SubmittedBookingPage'

function BookingShell({ children }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface font-sans text-slate-900 antialiased">
      <AppHeader />
      <BookingSubnav />
      <main className="w-full flex-1">{children}</main>
    </div>
  )
}

function BookingWizardShell({ children }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface font-sans text-slate-900 antialiased">
      <AppHeader />
      <main className="w-full flex-1">{children}</main>
    </div>
  )
}

export default function App() {
  const navigate = useNavigate()

  return (
    <ErrorBoundary>
      <Routes>
      <Route path="/" element={<VehicleListPage />} />
      <Route path="/vehicles/:id" element={<VehicleDetailPage />} />
      <Route path="/renter" element={<Navigate to="/" replace />} />

      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/activate" element={<ActivatePage />} />
      <Route
        path="/payment/vnpay/callback"
        element={
          <BookingShell>
            <VnpayCallbackPage />
          </BookingShell>
        }
      />

      <Route
        path="/policy"
        element={
          <BookingShell>
            <PolicyPage onBookNow={() => navigate('/')} />
          </BookingShell>
        }
      />

      <Route element={<ProtectedRoute />}>
        <Route path="/me" element={<MePage />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route path="/admin" element={<AdminDashboard />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['RENTER']} />}>
        <Route
          path="/renter/bookings/:bookingCode/payment"
          element={
            <BookingShell>
              <CustomerPaymentPage />
            </BookingShell>
          }
        />

        <Route
          path="/renter/bookings/new"
          element={
            <BookingWizardShell>
              <BookingRequestPage />
            </BookingWizardShell>
          }
        />

        <Route
          path="/renter/bookings/:bookingCode/submitted"
          element={
            <BookingShell>
              <SubmittedBookingPage />
            </BookingShell>
          }
        />

        <Route
          path="/renter/bookings/:bookingCode"
          element={
            <BookingShell>
              <BookingDetailPage />
            </BookingShell>
          }
        />

        <Route
          path="/renter/bookings"
          element={
            <BookingShell>
              <MyBookingsPage />
            </BookingShell>
          }
        />

        <Route
          path="/renter/payments"
          element={
            <BookingShell>
              <PaymentHistoryPage />
            </BookingShell>
          }
        />

        <Route
          path="/renter/bookings/:bookingCode/reviews"
          element={<BookingReviewsPage />}
        />

        <Route
          path="/renter/yearly-rental"
          element={
            <BookingShell>
              <YearlyRentalPage />
            </BookingShell>
          }
        />

        <Route
          path="/renter/bookings/:bookingCode/yearly-rental"
          element={
            <BookingShell>
              <YearlyRentalPage />
            </BookingShell>
          }
        />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['PROVIDER']} />}>
        <Route path="/provider" element={<ProviderHomePage />} />
        <Route path="/provider/vehicles" element={<ProviderVehicleListPage />} />
        <Route path="/provider/vehicles/new" element={<ProviderVehicleCreatePage />} />
        <Route path="/provider/vehicles/:id" element={<ProviderVehicleHubPage />} />
        <Route path="/provider/vehicles/:id/edit" element={<ProviderVehicleHubPage />} />
        <Route
          path="/provider/bookings"
          element={<ProviderBookingQueuePage />}
        />
        <Route
          path="/provider/bookings/:bookingCode"
          element={<ProviderBookingDetailPage />}
        />
        <Route
          path="/provider/finance"
          element={<ProviderFinancePage />}
        />
        <Route
          path="/provider/schedules"
          element={<ProviderSchedulePage />}
        />
        <Route
          path="/provider/drivers"
          element={<ProviderDriverPage />}
        />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['DRIVER']} />}>
        <Route
          path="/driver"
          element={<Navigate to="/driver/schedule" replace />}
        />
        <Route
          path="/driver/schedule"
          element={<DriverSchedulePage />}
        />
        <Route
          path="/driver/trips"
          element={<Navigate to="/driver/schedule" replace />}
        />
        <Route
          path="/driver/trips/:bookingCode"
          element={<DriverTripDetailPage />}
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </ErrorBoundary>
  )
}
