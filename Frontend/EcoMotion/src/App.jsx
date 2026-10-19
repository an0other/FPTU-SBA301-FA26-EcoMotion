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

import ProviderFinancePage from './pages/ProviderFinancePage'
import DriverSchedulePage from './pages/DriverSchedulePage'
import DriverTripDetailPage from './pages/DriverTripDetailPage'
import CustomerPaymentPage from './pages/CustomerPaymentPage'

function BookingShell({ children }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface font-sans text-slate-900 antialiased">
      <AppHeader />
      <BookingSubnav />
      <main className="w-full flex-1">{children}</main>
    </div>
  )
}

import { Navigate, Route, Routes } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import ActivatePage from "./pages/ActivatePage";
import MePage from "./pages/MePage";
import ProtectedRoute from "./components/ProtectedRoute";
import VehicleListPage from "./pages/VehicleListPage";
import VehicleDetailPage from "./pages/VehicleDetailPage";
import ProviderDashboard from "./pages/ProviderDashboard";
import AdminDashboard from "./pages/AdminDashboard";
import PaymentHistoryPage from "./pages/PaymentHistoryPage";
import BookingReviewsPage from "./pages/BookingReviewsPage";
import YearlyRentalPage from "./pages/YearlyRentalPage";

export default function App() {
  const navigate = useNavigate()

  return (
    <Routes>
      <Route path="/" element={<VehicleListPage />} />
      <Route path="/vehicles/:id" element={<VehicleDetailPage />} />
      <Route path="/bookings/:bookingId/payment" element={<CustomerPaymentPage />} />
      <Route path="/renter/bookings/:bookingId/payment" element={<CustomerPaymentPage />} />
      <Route path="/renter" element={<Navigate to="/" replace />} />

      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/activate" element={<ActivatePage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/me" element={<MePage />} />
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
      <Route element={<ProtectedRoute allowedRoles={['PROVIDER']} />}>
        <Route path="/provider" element={<ProviderDashboard />} />
        <Route path="/provider/bookings" element={<ProviderBookingQueuePage />} />
        <Route path="/provider/bookings/:bookingCode" element={<ProviderBookingDetailPage />} />
        <Route path="/provider/finance" element={<ProviderFinancePage />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['DRIVER']} />}>
        <Route path="/driver" element={<Navigate to="/driver/schedule" replace />} />
        <Route path="/driver/schedule" element={<DriverSchedulePage />} />
        <Route path="/driver/trips" element={<Navigate to="/driver/schedule" replace />} />
        <Route path="/driver/trips/:bookingCode" element={<DriverTripDetailPage />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={["RENTER"]} />}>
        <Route path="/renter/payments" element={<PaymentHistoryPage />} />
        <Route
          path="/renter/bookings/:bookingCode/reviews"
          element={<BookingReviewsPage />}
        />
        <Route path="/renter/yearly-rental" element={<YearlyRentalPage />} />

        <Route
          path="/renter/bookings/:bookingCode/yearly-rental"
          element={<YearlyRentalPage />}
        />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
