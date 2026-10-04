import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { IconCar } from '../components/icons/Icons'
import './Dashboard.css'

/**
 * Shared placeholder dashboard shell — shows the current session and a logout
 * button. Real role features (vehicles, bookings, …) land here later.
 */
export default function Dashboard({ title, subtitle }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="dashboard">
      <header className="dashboard__header">
        <div className="dashboard__brand">
          <IconCar />
          EcoMotion <span>VN</span>
        </div>
        <button type="button" className="dashboard__logout" onClick={handleLogout}>
          Đăng xuất
        </button>
      </header>

      <main className="dashboard__main">
        <div className="dashboard__card">
          <h1 className="dashboard__title">{title}</h1>
          <p className="dashboard__subtitle">{subtitle}</p>

          <dl className="dashboard__info">
            <div className="dashboard__row">
              <dt>Email</dt>
              <dd>{user?.email}</dd>
            </div>
            <div className="dashboard__row">
              <dt>Vai trò</dt>
              <dd>
                <span className="dashboard__badge">{user?.role}</span>
              </dd>
            </div>
            <div className="dashboard__row">
              <dt>Trạng thái tài khoản</dt>
              <dd>{user?.status}</dd>
            </div>
          </dl>
        </div>
      </main>
    </div>
  )
}
