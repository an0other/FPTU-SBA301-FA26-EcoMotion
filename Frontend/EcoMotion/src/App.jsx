import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ActivatePage from './pages/ActivatePage'
import MePage from './pages/MePage'
import ProtectedRoute from './components/ProtectedRoute'
import VehicleListPage from './pages/VehicleListPage'
import VehicleDetailPage from './pages/VehicleDetailPage'
import ProviderDashboard from './pages/ProviderDashboard'
import AdminDashboard from './pages/AdminDashboard'
import ProviderBookingQueuePage from './pages/ProviderBookingQueuePage'
import ProviderBookingDetailPage from './pages/ProviderBookingDetailPage'
import ProviderFinancePage from './pages/ProviderFinancePage'
import DriverSchedulePage from './pages/DriverSchedulePage'
import DriverTripDetailPage from './pages/DriverTripDetailPage'
import CustomerPaymentPage from './pages/CustomerPaymentPage'

export default function App() {
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
        <Route path="/provider" element={<ProviderDashboard />} />
        <Route path="/admin" element={<AdminDashboard />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['PROVIDER']} />}>
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

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
