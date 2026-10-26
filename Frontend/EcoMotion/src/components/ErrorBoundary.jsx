import React from 'react'

export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error }
  }

  componentDidCatch(error, errorInfo) {
    console.error('ErrorBoundary caught an unhandled error:', error, errorInfo)
  }

  handleReload = () => {
    window.location.reload()
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }

      return (
        <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4 font-sans text-slate-800">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl border border-slate-200 text-center space-y-4">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
              <span className="material-symbols-rounded text-3xl">error</span>
            </div>

            <div>
              <h2 className="text-lg font-bold text-slate-900">Đã xảy ra lỗi hiển thị</h2>
              <p className="mt-1 text-xs text-slate-500">
                Giao diện tạm thời gặp sự cố khi xử lý dữ liệu. Vui lòng tải lại trang hoặc thử lại sau.
              </p>
            </div>

            {this.state.error && (
              <div className="rounded-lg bg-slate-50 border border-slate-200 p-3 text-left font-mono text-[11px] text-rose-700 max-h-32 overflow-y-auto">
                {this.state.error.message || String(this.state.error)}
              </div>
            )}

            <div className="flex items-center justify-center gap-2 pt-2">
              <button
                type="button"
                onClick={this.handleReset}
                className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
              >
                Thử lại
              </button>
              <button
                type="button"
                onClick={this.handleReload}
                className="rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition"
              >
                Tải lại trang
              </button>
              <a
                href="/"
                className="rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200 transition"
              >
                Về trang chủ
              </a>
            </div>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}

export default ErrorBoundary
