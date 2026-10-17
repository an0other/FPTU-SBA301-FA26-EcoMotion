import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ActivatePage from './pages/ActivatePage'
import MePage from './pages/MePage'
import ProtectedRoute from './components/ProtectedRoute'
import VehicleListPage from './pages/VehicleListPage'
import VehicleDetailPage from './pages/VehicleDetailPage'
import ProviderHomePage from './pages/provider/ProviderHomePage'
import ProviderVehicleListPage from './pages/provider/ProviderVehicleListPage'
import AdminDashboard from './pages/AdminDashboard'

export default function App() {
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
        <Route path="/provider" element={<ProviderHomePage />} />
        <Route path="/provider/vehicles" element={<ProviderVehicleListPage />} />
        <Route path="/admin" element={<AdminDashboard />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
