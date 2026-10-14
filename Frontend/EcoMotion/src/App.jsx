import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import { Footer } from './components/layout/Footer'
import { Header } from './components/layout/Header'
import ActivatePage from './pages/ActivatePage'
import AdminDashboard from './pages/AdminDashboard'
import LoginPage from './pages/LoginPage'
import MePage from './pages/MePage'
import ProviderDashboard from './pages/ProviderDashboard'
import RegisterPage from './pages/RegisterPage'
import VehicleDetailPage from './pages/VehicleDetailPage'
import VehicleListPage from './pages/VehicleListPage'
import { BookingDetailPage } from './pages/booking/BookingDetailPage'
import { BookingRequestPage } from './pages/booking/BookingRequestPage'
import { DepositPaymentPage } from './pages/booking/DepositPaymentPage'
import { MyBookingsPage } from './pages/booking/MyBookingsPage'
import { SubmittedBookingPage } from './pages/booking/SubmittedBookingPage'
import { PolicyPage } from './pages/PolicyPage'

function BookingShell({ children }) {
  return (
    <div className="min-h-screen flex flex-col bg-[#F7F8F6] text-[#14221A] font-sans antialiased">
      <Header />
      <main className="flex-1 w-full">{children}</main>
      <Footer />
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
            <PolicyPage onBookNow={() => navigate('/renter/bookings/new')} />
          </BookingShell>
        )}
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
