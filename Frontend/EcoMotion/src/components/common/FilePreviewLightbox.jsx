import { useEffect } from 'react';

/**
 * FilePreviewLightbox - Modal lồng / Lightbox xem trước tệp đa năng
 * Hỗ trợ hiển thị hình ảnh (JPG, PNG, WEBP) và nhúng tài liệu PDF trực tiếp.
 * Hoạt động lồng an toàn trên các modal khác nhờ z-index cao (z-[70]).
 *
 * @param {Object} props
 * @param {boolean} props.isOpen - Trạng thái mở modal
 * @param {Function} props.onClose - Hàm đóng modal
 * @param {string} props.fileUrl - Đường dẫn tệp ảnh hoặc PDF
 * @param {string} [props.fileName] - Tên tệp hiển thị
 * @param {string} [props.title] - Tiêu đề văn bản/ảnh
 */
export default function FilePreviewLightbox({
  isOpen,
  onClose,
  fileUrl,
  fileName,
  title
}) {
  // Lắng nghe phím ESC để đóng modal
  useEffect(() => {
    if (!isOpen) return;

    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        onClose?.();
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  // Khóa cuộn trang khi modal đang mở
  useEffect(() => {
    if (isOpen) {
      const originalStyle = window.getComputedStyle(document.body).overflow;
      document.body.style.overflow = 'hidden';
      return () => {
        document.body.style.overflow = originalStyle;
      };
    }
  }, [isOpen]);

  if (!isOpen || !fileUrl) return null;

  const cleanUrl = fileUrl.split('?')[0].toLowerCase();
  const isPdf = cleanUrl.endsWith('.pdf');
  const displayName = fileName || fileUrl.split('/').pop() || 'Tệp đính kèm';
  const displayTitle = title || (isPdf ? 'Tài liệu văn bản PDF' : 'Hình ảnh phóng to');

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={displayTitle}
      onClick={onClose}
      className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/85 backdrop-blur-xs p-2 sm:p-4 md:p-6 animate-in fade-in duration-200"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-5xl bg-slate-900 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[94vh] border border-slate-800"
      >
        {/* Header Bar */}
        <div className="flex items-center justify-between px-4 py-3 bg-slate-900 border-b border-slate-800 text-white shrink-0">
          <div className="flex items-center gap-2.5 min-w-0 pr-3">
            <span
              className={`material-symbols-outlined text-2xl shrink-0 ${
                isPdf ? 'text-rose-400' : 'text-emerald-400'
              }`}
            >
              {isPdf ? 'picture_as_pdf' : 'image'}
            </span>
            <div className="min-w-0">
              <h3 className="text-xs sm:text-sm font-bold text-white truncate">
                {displayTitle}
              </h3>
              <p className="text-[11px] text-slate-400 truncate font-mono">
                {displayName}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <a
              href={fileUrl}
              download={displayName}
              className="p-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer flex items-center justify-center"
              title="Tải tệp về máy tính"
            >
              <span className="material-symbols-outlined text-[19px]">download</span>
            </a>
            <button
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer flex items-center justify-center"
              title="Đóng xem trước (ESC)"
            >
              <span className="material-symbols-outlined text-xl">close</span>
            </button>
          </div>
        </div>

        {/* Content Viewer */}
        <div className="p-2 sm:p-4 flex-1 overflow-auto flex items-center justify-center bg-black/50 min-h-[300px]">
          {isPdf ? (
            <iframe
              src={fileUrl}
              className="w-full h-[75vh] md:h-[80vh] rounded-xl border border-slate-800 bg-white"
              title={displayName}
            />
          ) : (
            <img
              src={fileUrl}
              alt={displayName}
              className="max-h-[80vh] w-auto max-w-full object-contain rounded-lg shadow-lg"
              onError={(e) => {
                e.target.onerror = null;
                e.target.src = 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80';
              }}
            />
          )}
        </div>

        {/* Footer Bar */}
        <div className="px-4 py-2 bg-slate-900/90 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-400 shrink-0">
          <span>
            {isPdf
              ? 'Tài liệu PDF · Cuộn trang để đọc toàn bộ nội dung'
              : 'Hình ảnh thực tế · Nhấn nút [✕] hoặc bấm vùng ngoài để đóng'}
          </span>
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1 bg-slate-800 hover:bg-slate-700 text-white rounded-lg text-xs font-medium transition-colors cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

