import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { Footer } from './components/layout/Footer'
import { Header } from './components/layout/Header'
import { BookingDetailPage } from './pages/booking/BookingDetailPage'
import { BookingRequestPage } from './pages/booking/BookingRequestPage'
import { DepositPaymentPage } from './pages/booking/DepositPaymentPage'
import { MyBookingsPage } from './pages/booking/MyBookingsPage'
import { SubmittedBookingPage } from './pages/booking/SubmittedBookingPage'
import { PolicyPage } from './pages/PolicyPage'
import { ensureRenterSession } from './services/authService'
import { readErrorMessage } from './services/apiClient'

function Shell() {
  const navigate = useNavigate()
  const [ready, setReady] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    ensureRenterSession()
      .then(() => setReady(true))
      .catch((err) => setError(readErrorMessage(err)))
  }, [])

  return (
    <div className="min-h-screen flex flex-col bg-[#F7F8F6] text-[#14221A] font-sans antialiased">
      <Header />
      <main className="flex-1 w-full">
        {error ? <p className="max-w-3xl mx-auto px-6 py-10 text-sm text-[#B42318]">{error}</p> : null}
        {ready ? (
          <Routes>
            <Route path="/" element={<Navigate to="/renter/bookings/new" replace />} />
            <Route path="/renter/bookings/new" element={<BookingRequestPage />} />
            <Route path="/renter/bookings/:bookingCode/submitted" element={<SubmittedBookingPage />} />
            <Route path="/renter/bookings/:bookingCode/checkout" element={<DepositPaymentPage />} />
            <Route path="/renter/bookings/:bookingCode" element={<BookingDetailPage />} />
            <Route path="/renter/bookings" element={<MyBookingsPage />} />
            <Route path="/policy" element={<PolicyPage onBookNow={() => navigate('/renter/bookings/new')} />} />
          </Routes>
        ) : null}
      </main>
      <Footer />
    </div>
  )
}

export default function App() {
  return <Shell />
}
