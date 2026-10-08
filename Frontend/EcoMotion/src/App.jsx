import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ActivatePage from './pages/ActivatePage'
import MePage from './pages/MePage'
import ProtectedRoute from './components/ProtectedRoute'
import VehicleListPage from './pages/VehicleListPage'
import ProviderDashboard from './pages/ProviderDashboard'
import AdminDashboard from './pages/AdminDashboard'

export default function App() {
  return (
    <Routes>
      {/* The home page IS the car list, and it is public: a visitor browses the fleet before
          signing in. Everything that needs an identity lives behind <ProtectedRoute /> below. */}
      <Route path="/" element={<VehicleListPage />} />
      {/* Kept as an alias so existing links, bookmarks and roleHome targets keep working. */}
      <Route path="/renter" element={<Navigate to="/" replace />} />

      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/activate" element={<ActivatePage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/me" element={<MePage />} />
        <Route path="/provider" element={<ProviderDashboard />} />
        <Route path="/admin" element={<AdminDashboard />} />
      </Route>

      {/* With a public home there is no reason to push an unknown URL at the login form. */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
