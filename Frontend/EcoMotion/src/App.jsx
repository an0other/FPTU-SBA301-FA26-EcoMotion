import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import AppHeader from './components/AppHeader'
import { BookingSubnav } from './components/booking/BookingSubnav'
import ProtectedRoute from './components/ProtectedRoute'
import ActivatePage from './pages/ActivatePage'
import VehicleListPage from './pages/VehicleListPage'
import VehicleDetailPage from './pages/VehicleDetailPage'
import ProviderDashboard from './pages/ProviderDashboard'
import ProviderBookingQueuePage from './pages/ProviderBookingQueuePage'
import ProviderBookingDetailPage from './pages/ProviderBookingDetailPage'
import AdminDashboard from './pages/AdminDashboard'
import LoginPage from './pages/LoginPage'
import MePage from './pages/MePage'
import RegisterPage from './pages/RegisterPage'
import { BookingDetailPage } from './pages/booking/BookingDetailPage'
import { BookingRequestPage } from './pages/booking/BookingRequestPage'
import { DepositPaymentPage } from './pages/booking/DepositPaymentPage'
import { MyBookingsPage } from './pages/booking/MyBookingsPage'
import { SubmittedBookingPage } from './pages/booking/SubmittedBookingPage'
import { PolicyPage } from './pages/PolicyPage'

function BookingShell({ children }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface font-sans text-slate-900 antialiased">
      <AppHeader />
      <BookingSubnav />
      <main className="w-full flex-1">{children}</main>
    </div>
  )
}


function WizardShell({ children }) {
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
    <Routes>
      <Route path="/" element={<VehicleListPage />} />
      <Route path="/vehicles/:id" element={<VehicleDetailPage />} />
      <Route path="/renter" element={<Navigate to="/" replace />} />

      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/activate" element={<ActivatePage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/me" element={<MePage />} />
        <Route path="/provider" element={<ProviderDashboard />} />
        <Route path="/provider/bookings" element={<ProviderBookingQueuePage />} />
        <Route path="/provider/bookings/:bookingId" element={<ProviderBookingDetailPage />} />
        <Route path="/admin" element={<AdminDashboard />} />
        <Route
          path="/renter/bookings/new"
          element={<BookingShell><BookingRequestPage /></BookingShell>}
        />
        <Route
          path="/renter/bookings/:bookingCode/submitted"
          element={<BookingShell><SubmittedBookingPage /></BookingShell>}
        />
        <Route
          path="/renter/bookings/:bookingCode/checkout"
          element={<BookingShell><DepositPaymentPage /></BookingShell>}
        />
        <Route
          path="/renter/bookings/:bookingCode"
          element={<BookingShell><BookingDetailPage /></BookingShell>}
        />
        <Route
          path="/renter/bookings"
          element={<BookingShell><MyBookingsPage /></BookingShell>}
        />
      </Route>

      <Route
        path="/policy"
        element={(
          <BookingShell>
            <PolicyPage onBookNow={() => navigate('/')} />
          </BookingShell>
        )}
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
