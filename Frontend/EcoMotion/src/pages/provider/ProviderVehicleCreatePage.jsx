import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { providerService } from "../../services/providerService";
import ProviderHeader from "../../components/provider/ProviderHeader";
import Toast from "../../components/Toast";

const DEFAULT_COVER_IMAGE =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuBbaQp-zgmEeeOXgITBoZMG_mcjykw9QLCVVMpxpETzMKWOKamELEhCdDXqwPVtL9zm5vOF2SlweeWygVXyZ2tA4s1juN6evzQAEWXkCkB-3K6P9MO7bJcwoh5d5bQLraMTZHHwFbqsBRK0fvMsCFq9tdvSzMkam9bV_EbMWD3qlBjHLp2u0T1nqp7jBbTKlsC-OK844n_DbPKYGhFWBhfKb6BMCy-DRHd4iSz2nmy1jHs274eOxg4y";

const BRAND_OPTIONS = [
  "VinFast",
  "Hyundai",
  "Toyota",
  "Tesla",
  "BMW",
  "Mercedes-Benz",
  "Porsche",
  "Kia",
  "Ford",
];

const VIETNAM_PLATE_REGEX =
  /^[0-9]{2}[A-Z]{1,2}-([0-9]{3}\.[0-9]{2}|[0-9]{4,5})$/;
const VIN_REGEX = /^[A-Z0-9-]{6,50}$/;
const ENGINE_REGEX = /^[A-Z0-9-]{4,50}$/;

export default function ProviderVehicleCreatePage() {

  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [toast, setToast] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [showTierModal, setShowTierModal] = useState(false);
  const [errors, setErrors] = useState({});

  const [form, setForm] = useState({
    chassisNo: "",
    engineNo: "",
    licensePlate: "",
    brand: "VinFast",
    model: "VF 8 Plus",
    manufactureYear: 2024,
    seatCount: 5,
    transmission: "AUTOMATIC",
    fuelType: "EV",
    color: "Xanh Lục Bảo (Deep Ocean)",
    maintenanceBufferMinutes: 60,
    coverImageUrl: DEFAULT_COVER_IMAGE,
  });

  useEffect(() => {
    let ignore = false;
    async function fetchSummary() {
      try {
        const data = await providerService.getDashboardSummary();
        if (!ignore && data) {
          setSummary(data);
        }
      } catch (err) {
        console.error("Failed to load provider summary:", err);
      }
    }
    fetchSummary();
    return () => {
      ignore = true;
    };
  }, []);

  function handleChange(field, value) {
    setForm((prev) => ({
      ...prev,
      [field]: value,
    }));
    if (errors[field]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  }


  function adjustBuffer(delta) {
    setForm((prev) => {
      const next = Math.max(
        30,
        Math.min(180, (prev.maintenanceBufferMinutes || 60) + delta),
      );
      return { ...prev, maintenanceBufferMinutes: next };
    });
  }

  function handleImageFileSelected(e) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      setToast({
        message: "Vui lòng chọn tệp định dạng hình ảnh.",
        type: "error",
      });
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      setToast({ message: "Dung lượng ảnh tối đa là 10MB.", type: "error" });
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      if (event.target?.result) {
        handleChange("coverImageUrl", event.target.result);
        setToast({ message: `Đã chọn ảnh: ${file.name}`, type: "success" });
      }
    };
    reader.readAsDataURL(file);
  }

  async function handleSubmit(e) {
    e.preventDefault();

    const cleanPlate = form.licensePlate.trim().toUpperCase();
    const cleanChassis = form.chassisNo.trim().toUpperCase();
    const cleanEngine = form.engineNo.trim().toUpperCase();
    const cleanBrand = form.brand.trim();
    const cleanModel = form.model.trim();
    const cleanColor = form.color.trim();
    const yr = Number(form.manufactureYear);

    const newErrors = {};

    if (!cleanChassis) {
      newErrors.chassisNo = "Vui lòng nhập Số khung (VIN).";
    } else if (!VIN_REGEX.test(cleanChassis)) {
      newErrors.chassisNo =
        "Số khung (VIN) không hợp lệ (tối thiểu 6 ký tự chữ, số hoặc gạch nối).";
    }

    if (!cleanEngine) {
      newErrors.engineNo = "Vui lòng nhập Số máy / Mã mô tơ điện.";
    } else if (!ENGINE_REGEX.test(cleanEngine)) {
      newErrors.engineNo =
        "Số máy không hợp lệ (tối thiểu 4 ký tự chữ, số hoặc gạch nối).";
    }

    if (!cleanPlate) {
      newErrors.licensePlate = "Vui lòng nhập Biển số xe kiểm soát.";
    } else if (!VIETNAM_PLATE_REGEX.test(cleanPlate)) {
      newErrors.licensePlate =
        "Biển số xe không đúng định dạng quy chuẩn Việt Nam (VD: 51K-334.12 hoặc 51A-11111).";
    }

    if (!cleanBrand) {
      newErrors.brand = "Vui lòng chọn Hãng xe.";
    }

    if (!cleanModel) {
      newErrors.model = "Vui lòng nhập Dòng xe / Model.";
    }

    if (!yr || yr < 2015 || yr > 2026) {
      newErrors.manufactureYear = "Năm sản xuất hợp lệ từ 2015 đến 2026.";
    }

    if (!cleanColor) {
      newErrors.color = "Vui lòng nhập Màu sắc ngoại thất.";
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      const firstField = Object.keys(newErrors)[0];
      const elementIdMap = {
        chassisNo: "chassis_no",
        engineNo: "engine_no",
        licensePlate: "license_plate",
        brand: "brand",
        model: "model",
        manufactureYear: "manufacture_year",
        color: "color",
      };
      const targetId = elementIdMap[firstField];
      if (targetId) {
        const el = document.getElementById(targetId);
        if (el) {
          el.scrollIntoView({ behavior: "smooth", block: "center" });
          el.focus();
        }
      }
      setToast({
        message:
          Object.values(newErrors)[0] ||
          "Vui lòng kiểm tra lại các trường được đánh dấu đỏ.",
        type: "error",
      });
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        chassisNo: cleanChassis,
        engineNo: cleanEngine,
        licensePlate: cleanPlate,
        brand: cleanBrand,
        model: cleanModel,
        manufactureYear: yr,
        seatCount: Number(form.seatCount),
        transmission: form.transmission,
        fuelType: form.fuelType,
        color: cleanColor,
        maintenanceBufferMinutes: Number(form.maintenanceBufferMinutes),
        coverImageUrl: form.coverImageUrl || DEFAULT_COVER_IMAGE,
      };

      const created = await providerService.createVehicle(payload);
      setToast({
        message: `Đăng ký xe ${created.licensePlate || cleanPlate} thành công! Đang chuyển hướng...`,
        type: "success",
      });

      setTimeout(() => {
        navigate("/provider/vehicles");
      }, 1500);
    } catch (err) {
      console.error("Create vehicle failed:", err);
      const serverMsg =
        err.original?.response?.data?.message ||
        err.response?.data?.message ||
        err.message ||
        "Không thể đăng ký xe. Vui lòng kiểm tra lại thông tin.";

      const serverErrors = {};
      const lower = serverMsg.toLowerCase();
      if (
        lower.includes("biển số") ||
        lower.includes("licenseplate") ||
        lower.includes("license")
      ) {
        serverErrors.licensePlate = serverMsg;
        document
          .getElementById("license_plate")
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
        document.getElementById("license_plate")?.focus();
      } else if (
        lower.includes("khung") ||
        lower.includes("vin") ||
        lower.includes("chassis")
      ) {
        serverErrors.chassisNo = serverMsg;
        document
          .getElementById("chassis_no")
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
        document.getElementById("chassis_no")?.focus();
      } else if (
        lower.includes("máy") ||
        lower.includes("mô tơ") ||
        lower.includes("engine")
      ) {
        serverErrors.engineNo = serverMsg;
        document
          .getElementById("engine_no")
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
        document.getElementById("engine_no")?.focus();
      } else if (lower.includes("hãng") || lower.includes("brand")) {
        serverErrors.brand = serverMsg;
        document
          .getElementById("brand")
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
      } else if (lower.includes("dòng") || lower.includes("model")) {
        serverErrors.model = serverMsg;
        document
          .getElementById("model")
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
      } else if (lower.includes("màu") || lower.includes("color")) {
        serverErrors.color = serverMsg;
        document
          .getElementById("color")
          ?.scrollIntoView({ behavior: "smooth", block: "center" });
      }

      if (Object.keys(serverErrors).length > 0) {
        setErrors((prev) => ({ ...prev, ...serverErrors }));
      }
      setToast({ message: serverMsg, type: "error" });
    } finally {
      setSubmitting(false);
    }
  }

  const providerName = summary?.providerName || "";
  const depotAddress = summary?.depotAddress || "";
  const cleanPlate = form.licensePlate.trim().toUpperCase();
  const displayPlate = cleanPlate || "51K-334.12";
  const isPlateValid = cleanPlate ? VIETNAM_PLATE_REGEX.test(cleanPlate) : null;

  return (
    <div className="bg-[#f8f9ff] text-[#334155] min-h-screen flex flex-col font-['Be_Vietnam_Pro',sans-serif]">
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type || "info"}
          onClose={() => setToast(null)}
        />
      )}

      {/* Header dùng chung của dự án */}
      <ProviderHeader
        activeTab="vehicles"
        providerName={providerName}
        depotAddress={depotAddress}
      />

      <main className="flex-1 w-full bg-[#f8f9ff]">
        <div className="flex flex-col w-full">
          <form className="w-full pb-36" onSubmit={handleSubmit}>
            <div className="max-w-[1440px] mx-auto px-6 lg:px-12 py-6">
              {/* Breadcrumb */}
              <nav
                aria-label="Breadcrumb"
                className="flex items-center gap-2 text-xs mb-3 text-slate-500"
              >
                <Link
                  to="/provider/vehicles"
                  className="hover:text-emerald-700 transition-colors flex items-center gap-1 font-medium"
                >
                  Đội xe của tôi
                </Link>
                <span className="material-symbols-outlined text-[14px] text-slate-400">
                  chevron_right
                </span>
                <span className="text-[#0b1c30] font-semibold">
                  Đăng ký xe mới
                </span>
              </nav>

              {/* Main Header with Status Badge */}
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-6">
                <div className="flex flex-col gap-1.5">
                  <div className="flex flex-wrap items-center gap-3">
                    <h1 className="text-2xl sm:text-3xl font-extrabold text-[#0b1c30] tracking-tight">
                      Đăng ký xe mới vào Đội xe
                    </h1>
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 text-amber-700 font-semibold text-xs border border-amber-200 shadow-xs">
                      <span className="w-2 h-2 rounded-full bg-amber-600 animate-pulse"></span>
                      ĐĂNG KÝ MỚI (CHỜ DUYỆT / PENDING)
                    </span>
                  </div>
                  <p className="text-sm text-slate-500">
                    Nhập đầy đủ thông tin phương tiện tuân thủ kiến trúc kiểm
                    duyệt 3 Tầng —{" "}
                    <span className="text-emerald-700 font-semibold">
                      Quy chuẩn kiến trúc
                    </span>
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => setShowTierModal(true)}
                  className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white text-emerald-700 font-semibold text-sm shadow-sm hover:bg-emerald-50 transition-all border border-slate-200 shrink-0 self-start lg:self-auto cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[20px] text-emerald-600">
                    verified_user
                  </span>
                  <span>Quy chuẩn kiến trúc</span>
                </button>
              </div>

              {/* Architecture Overview Banner */}
              <div className="rounded-2xl p-5 bg-gradient-to-r from-emerald-50 via-slate-50 to-emerald-50 border border-emerald-100 shadow-xs mb-8">
                <div className="flex items-start gap-4">
                  <div className="w-10 h-10 rounded-xl bg-emerald-700 flex items-center justify-center text-white shrink-0 shadow-sm mt-0.5">
                    <span className="material-symbols-outlined text-[22px]">
                      policy
                    </span>
                  </div>
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-base font-bold text-[#0b1c30]">
                        Kiến trúc Cập nhật Hồ sơ 3 Tầng (3-Tier Legal Rules) —
                        Quy chuẩn kiến trúc
                      </span>
                      <span className="px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 font-semibold text-[11px]">
                        Bảo vệ quyền khai thác
                      </span>
                    </div>
                    <p className="text-xs sm:text-sm text-slate-600 leading-relaxed">
                      Phân tách rõ rủi ro pháp lý giữa{" "}
                      <strong className="text-[#0b1c30]">
                        Tầng 1 (Định danh vật lý bất biến)
                      </strong>
                      ,{" "}
                      <strong className="text-[#0b1c30]">
                        Tầng 2 (Định danh pháp lý)
                      </strong>{" "}
                      và{" "}
                      <strong className="text-[#0b1c30]">
                        Tầng 3 (Mô tả &amp; Vận hành tự do)
                      </strong>{" "}
                      nhằm bảo vệ quyền khai thác kinh doanh cho chủ xe.
                    </p>
                  </div>
                </div>
              </div>

              {/* Form Content: 3 Tier Cards */}
              <div className="flex flex-col space-y-8">
                {/* ================= TẦNG 1: ĐỊNH DANH VẬT LÝ ================= */}
                <section className="bg-white rounded-2xl p-6 lg:p-8 shadow-sm flex flex-col gap-6 border border-slate-200">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2 border-b border-slate-100">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-slate-100 border border-slate-300 flex items-center justify-center text-[#0b1c30] font-bold text-sm">
                        01
                      </div>
                      <div>
                        <h2 className="text-lg font-bold text-[#0b1c30] tracking-tight">
                          01 | TẦNG 1: Định danh vật lý
                        </h2>
                        <p className="text-xs text-slate-500">
                          Gắn liền với thân vỏ kim loại và cụm mô tơ truyền động
                          gốc.
                        </p>
                      </div>
                    </div>
                    <span className="self-start sm:self-center inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 border border-slate-300 text-slate-700 font-semibold text-xs">
                      <span className="material-symbols-outlined text-[14px]">
                        lock
                      </span>
                      BẤT BIẾN SAU KHI TẠO
                    </span>
                  </div>

                  <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-200 text-amber-900 flex items-start gap-3">
                    <span className="material-symbols-outlined text-[20px] text-amber-700 shrink-0 mt-0.5">
                      info
                    </span>
                    <span className="text-xs sm:text-sm leading-relaxed">
                      <strong className="font-bold text-amber-950">
                        Quy định bất biến:
                      </strong>{" "}
                      Số khung và Số máy sẽ bị khóa cứng vĩnh viễn trên CSDL sau
                      khi tạo thành công. Sai sót kỹ thuật yêu cầu xóa hồ sơ tạo
                      lại để tránh gian lận tráo xe.
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {/* Field 1: chassis_no */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="chassis_no"
                      >
                        <span>Số khung (VIN / Chassis Number)</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative">
                        <input
                          id="chassis_no"
                          type="text"
                          required
                          maxLength={50}
                          value={form.chassisNo}
                          onChange={(e) =>
                            handleChange(
                              "chassisNo",
                              e.target.value.toUpperCase(),
                            )
                          }
                          placeholder="Nhập 17 ký tự chuẩn VIN (VD: VF8E24XXXXXXXXX)..."
                          className={`w-full h-11 px-3.5 pr-10 bg-white font-mono text-sm text-[#0b1c30] rounded-xl border ${
                            errors.chassisNo
                              ? "border-red-500 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                              : "border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                          } shadow-xs uppercase tracking-wider transition-colors`}
                        />
                        <span className="absolute right-3.5 top-3 material-symbols-outlined text-[20px] text-slate-400 pointer-events-none">
                          fingerprint
                        </span>
                      </div>
                      {errors.chassisNo ? (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          {errors.chassisNo}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-500">
                          Mã định danh khung xe chuẩn quốc tế do nhà sản xuất dập
                          chìm trên thân xe.
                        </span>
                      )}
                    </div>

                    {/* Field 2: engine_no */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="engine_no"
                      >
                        <span>Số máy / Mã mô tơ điện (Engine / Motor No.)</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative">
                        <input
                          id="engine_no"
                          type="text"
                          required
                          maxLength={50}
                          value={form.engineNo}
                          onChange={(e) =>
                            handleChange(
                              "engineNo",
                              e.target.value.toUpperCase(),
                            )
                          }
                          placeholder="VD: EM57-8941029K hoặc số seri động cơ..."
                          className={`w-full h-11 px-3.5 pr-10 bg-white font-mono text-sm text-[#0b1c30] rounded-xl border ${
                            errors.engineNo
                              ? "border-red-500 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                              : "border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                          } shadow-xs uppercase tracking-wider transition-colors`}
                        />
                        <span className="absolute right-3.5 top-3 material-symbols-outlined text-[20px] text-slate-400 pointer-events-none">
                          electric_bolt
                        </span>
                      </div>
                      {errors.engineNo ? (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          {errors.engineNo}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-500">
                          Mã định danh cụm động cơ hoặc mô tơ điện dẫn động in
                          trên giấy kiểm định.
                        </span>
                      )}
                    </div>
                  </div>
                </section>

                {/* ================= TẦNG 2: ĐỊNH DANH PHÁP LÝ ================= */}
                <section className="bg-white rounded-2xl p-6 lg:p-8 shadow-sm flex flex-col gap-6 border border-slate-200">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2 border-b border-slate-100">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-amber-100 border border-amber-300 flex items-center justify-center text-amber-800 font-bold text-sm">
                        02
                      </div>
                      <div>
                        <h2 className="text-lg font-bold text-[#0b1c30] tracking-tight">
                          02 | TẦNG 2: Định danh pháp lý
                        </h2>
                        <p className="text-xs text-slate-500">
                          Biển số đăng ký lưu hành giao thông đường bộ.
                        </p>
                      </div>
                    </div>
                    <span className="self-start sm:self-center inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold text-xs shadow-xs">
                      <span className="material-symbols-outlined text-[14px]">
                        assignment
                      </span>
                      DUYỆT LẠI KHI THAY ĐỔI
                    </span>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-slate-700 flex items-start gap-3">
                    <span className="material-symbols-outlined text-[20px] text-emerald-600 shrink-0 mt-0.5">
                      verified
                    </span>
                    <span className="text-xs sm:text-sm leading-relaxed">
                      <strong className="font-bold text-[#0b1c30]">
                        Quy chuẩn xét duyệt:
                      </strong>{" "}
                      Phương tiện đăng ký mới sẽ tự động ở trạng thái{" "}
                      <strong className="text-amber-700 font-semibold">
                        CHỜ DUYỆT (PENDING)
                      </strong>{" "}
                      để Chuyên viên kiểm duyệt hồ sơ đăng ký xe trước khi đưa
                      vào vận hành nhận khách thực tế.
                    </span>
                  </div>

                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-center pt-2">
                    {/* Left Column: License Plate Input */}
                    <div className="flex flex-col gap-2">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="license_plate"
                      >
                        <span>Biển số xe kiểm soát (License Plate)</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative flex items-center">
                        <div className="absolute left-3.5 flex items-center pointer-events-none text-slate-400">
                          <span className="material-symbols-outlined text-[20px]">
                            badge
                          </span>
                        </div>
                        <input
                          id="license_plate"
                          type="text"
                          required
                          maxLength={20}
                          value={form.licensePlate}
                          onChange={(e) =>
                            handleChange(
                              "licensePlate",
                              e.target.value.toUpperCase(),
                            )
                          }
                          placeholder="51K-334.12"
                          className={`w-full h-12 pl-10 pr-4 bg-white font-mono font-bold text-lg text-[#0b1c30] rounded-xl border focus:outline-none shadow-xs tracking-widest uppercase transition-colors ${
                            isPlateValid === false
                              ? "border-red-400 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                              : isPlateValid === true
                              ? "border-emerald-500 focus:ring-2 focus:ring-emerald-600 bg-emerald-50/10"
                              : "border-slate-200 focus:ring-2 focus:ring-emerald-600"
                          }`}
                        />
                      </div>
                      {isPlateValid === false ? (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          Biển số chưa đúng quy chuẩn (VD: 51K-334.12 hoặc 51A-11111)
                        </span>
                      ) : isPlateValid === true ? (
                        <span className="text-xs text-emerald-600 font-medium flex items-center gap-1">
                          <span className="material-symbols-outlined text-[15px]">
                            check_circle
                          </span>
                          Biển số xe hợp lệ theo quy chuẩn Việt Nam
                        </span>
                      ) : (
                        <span className="text-xs text-slate-500">
                          Định dạng chuẩn biển số ô tô Việt Nam: 51K-334.12 hoặc 30H-998.24
                        </span>
                      )}
                    </div>

                    {/* Right Column: Live Plate Preview Container */}
                    <div className="flex flex-col gap-2">
                      <label className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1">
                        <span>Mô phỏng biển số thực tế (Live Preview):</span>
                      </label>
                      <div className="flex flex-col p-4 bg-slate-50 rounded-xl border border-slate-200 shadow-xs">
                        <div className="flex flex-col items-center justify-center py-2">
                          <div
                            className={`relative bg-white border-2 ${
                              isPlateValid === false
                                ? "border-red-500 shadow-red-100"
                                : isPlateValid === true
                                ? "border-emerald-700 shadow-emerald-100"
                                : "border-slate-800"
                            } rounded-xl px-7 pt-2.5 pb-2 shadow-md flex flex-col items-center justify-center min-w-[240px] max-w-xs transition-all transform hover:scale-[1.02] overflow-hidden`}
                          >
                            <div className="absolute top-2 left-3 w-1.5 h-1.5 rounded-full bg-slate-300 border border-slate-400"></div>
                            <div className="absolute top-2 right-3 w-1.5 h-1.5 rounded-full bg-slate-300 border border-slate-400"></div>
                            <div className="flex items-center gap-1.5 pb-0.5 opacity-90">
                              <div className="w-3.5 h-2.5 bg-red-600 rounded-xs flex items-center justify-center relative shadow-xs">
                                <span className="w-1 h-1 bg-yellow-300 rounded-full"></span>
                              </div>
                              <span className="text-[10px] font-bold text-[#0b1c30] tracking-widest">
                                VIETNAM
                              </span>
                            </div>
                            <span className="font-mono font-bold text-[24px] tracking-widest text-[#0b1c30] leading-tight select-none my-0.5">
                              {displayPlate}
                            </span>
                            <div
                              className={`w-full h-1 ${
                                isPlateValid === false
                                  ? "bg-red-500"
                                  : "bg-emerald-600"
                              } rounded-full mt-1.5`}
                            ></div>
                          </div>
                          {isPlateValid === false ? (
                            <span className="inline-flex items-center gap-1.5 mt-3 text-red-600 text-xs font-semibold">
                              <span className="material-symbols-outlined text-[15px]">
                                warning
                              </span>
                              Biển số không hợp lệ theo quy chuẩn VN
                            </span>
                          ) : isPlateValid === true ? (
                            <span className="inline-flex items-center gap-1.5 mt-3 text-emerald-700 text-xs font-medium">
                              <span className="material-symbols-outlined text-[15px]">
                                verified
                              </span>
                              Biển số hợp lệ theo quy chuẩn VN
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 mt-3 text-slate-500 text-xs font-medium">
                              <span className="material-symbols-outlined text-[15px]">
                                info
                              </span>
                              Đang kết nối live preview theo ô nhập
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </section>

                {/* ================= TẦNG 3: THÔNG TIN MÔ TẢ & VẬN HÀNH ================= */}
                <section className="bg-white rounded-2xl p-6 lg:p-8 shadow-sm flex flex-col gap-6 border border-slate-200">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2 border-b border-slate-100">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-emerald-100 border border-emerald-300 flex items-center justify-center text-emerald-800 font-bold text-sm">
                        03
                      </div>
                      <div>
                        <h2 className="text-lg font-bold text-[#0b1c30] tracking-tight">
                          03 | TẦNG 3: Thông tin mô tả &amp; Vận hành
                        </h2>
                        <p className="text-xs text-slate-500">
                          Cấu hình trải nghiệm người dùng, hình ảnh thương mại
                          &amp; thời gian đệm trạm depot.
                        </p>
                      </div>
                    </div>
                    <span className="self-start sm:self-center inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-semibold text-xs shadow-xs">
                      <span className="material-symbols-outlined text-[14px]">
                        bolt
                      </span>
                      TỰ DO CẬP NHẬT (KHÔNG CẦN DUYỆT LẠI)
                    </span>
                  </div>

                  <div className="p-4 rounded-xl bg-emerald-50/60 border border-emerald-200 text-emerald-950 flex items-start gap-3">
                    <span className="material-symbols-outlined text-[20px] text-emerald-700 shrink-0 mt-0.5">
                      auto_awesome
                    </span>
                    <span className="text-xs sm:text-sm leading-relaxed">
                      <strong className="text-emerald-900 font-bold">
                        Đặc quyền Tầng 3:
                      </strong>{" "}
                      Các thông số dưới đây (hãng, dòng xe, màu sơn, ảnh bìa,
                      buffer bảo trì) có thể cập nhật linh hoạt sau này mà không
                      làm gián đoạn trạng thái hoạt động của xe.
                    </span>
                  </div>

                  {/* Grid 1: brand, model, manufacture_year, seat_count */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
                    {/* brand */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="brand"
                      >
                        <span>Hãng xe (Brand)</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative">
                        <select
                          id="brand"
                          required
                          value={form.brand}
                          onChange={(e) =>
                            handleChange("brand", e.target.value)
                          }
                          className={`w-full h-11 px-3.5 pr-10 bg-white text-sm text-[#0b1c30] rounded-xl border ${
                            errors.brand
                              ? "border-red-500 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                              : "border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                          } shadow-xs appearance-none cursor-pointer transition-colors`}
                        >
                          {BRAND_OPTIONS.map((b) => (
                            <option key={b} value={b}>
                              {b}
                            </option>
                          ))}
                        </select>
                        <span className="absolute right-3.5 top-3 material-symbols-outlined text-[20px] text-slate-400 pointer-events-none">
                          expand_more
                        </span>
                      </div>
                      {errors.brand && (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1 mt-0.5">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          {errors.brand}
                        </span>
                      )}
                    </div>

                    {/* model */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="model"
                      >
                        <span>Dòng xe (Model)</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <input
                        id="model"
                        type="text"
                        required
                        maxLength={50}
                        value={form.model}
                        onChange={(e) => handleChange("model", e.target.value)}
                        placeholder="VD: VF 8, Ioniq 5, Camry, VF 3..."
                        className={`w-full h-11 px-3.5 bg-white text-sm text-[#0b1c30] rounded-xl border ${
                          errors.model
                            ? "border-red-500 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                            : "border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                        } shadow-xs transition-colors`}
                      />
                      {errors.model && (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1 mt-0.5">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          {errors.model}
                        </span>
                      )}
                    </div>

                    {/* manufacture_year */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="manufacture_year"
                      >
                        <span>Năm sản xuất</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <input
                        id="manufacture_year"
                        type="number"
                        required
                        min={2015}
                        max={2026}
                        value={form.manufactureYear}
                        onChange={(e) =>
                          handleChange("manufactureYear", e.target.value)
                        }
                        className={`w-full h-11 px-3.5 bg-white text-sm text-[#0b1c30] rounded-xl border ${
                          errors.manufactureYear
                            ? "border-red-500 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                            : "border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                        } shadow-xs transition-colors`}
                      />
                      {errors.manufactureYear && (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1 mt-0.5">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          {errors.manufactureYear}
                        </span>
                      )}
                    </div>

                    {/* seat_count */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="seat_count"
                      >
                        <span>Số chỗ ngồi</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative">
                        <select
                          id="seat_count"
                          required
                          value={form.seatCount}
                          onChange={(e) =>
                            handleChange("seatCount", Number(e.target.value))
                          }
                          className="w-full h-11 px-3.5 pr-10 bg-white text-sm text-[#0b1c30] rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600 shadow-xs appearance-none cursor-pointer"
                        >
                          <option value={4}>4 chỗ (Compact)</option>
                          <option value={5}>5 chỗ (Sedan / Crossover)</option>
                          <option value={7}>7 chỗ (MPV / SUV)</option>
                          <option value={8}>8 chỗ</option>
                        </select>
                        <span className="absolute right-3.5 top-3 material-symbols-outlined text-[20px] text-slate-400 pointer-events-none">
                          airline_seat_recline_normal
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Grid 2: transmission, fuel_type, color */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 pt-2">
                    {/* transmission */}
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1">
                        <span>Hộp số truyền động</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="grid grid-cols-2 gap-2 h-11 p-1 bg-slate-100 rounded-xl border border-slate-200">
                        <button
                          type="button"
                          onClick={() =>
                            handleChange("transmission", "AUTOMATIC")
                          }
                          className={`flex items-center justify-center gap-1 rounded-lg text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                            form.transmission === "AUTOMATIC"
                              ? "bg-emerald-50 text-emerald-800 border border-emerald-300 shadow-xs"
                              : "text-slate-500 hover:text-[#0b1c30]"
                          }`}
                        >
                          <span className="material-symbols-outlined text-[18px]">
                            sync_saved_locally
                          </span>
                          <span>Tự động (AT)</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => handleChange("transmission", "MANUAL")}
                          className={`flex items-center justify-center gap-1 rounded-lg text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                            form.transmission === "MANUAL"
                              ? "bg-emerald-50 text-emerald-800 border border-emerald-300 shadow-xs"
                              : "text-slate-500 hover:text-[#0b1c30]"
                          }`}
                        >
                          <span className="material-symbols-outlined text-[18px]">
                            tune
                          </span>
                          <span>Số sàn (MT)</span>
                        </button>
                      </div>
                    </div>

                    {/* fuel_type */}
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1">
                        <span>Loại nhiên liệu</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
                        {[
                          { id: "EV", label: "Điện EV", icon: "electric_bolt" },
                          { id: "GASOLINE", label: "Xăng", icon: "local_gas_station" },
                          { id: "DIESEL", label: "Dầu", icon: "oil_barrel" },
                          { id: "HYBRID", label: "Hybrid", icon: "battery_charging_full" },
                        ].map((fuel) => (
                          <button
                            key={fuel.id}
                            type="button"
                            onClick={() => handleChange("fuelType", fuel.id)}
                            className={`h-11 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 text-center p-1 transition-all cursor-pointer ${
                              form.fuelType === fuel.id
                                ? "bg-emerald-50 text-emerald-800 border border-emerald-300 shadow-xs"
                                : "bg-slate-100 text-slate-600 hover:text-[#0b1c30] border border-transparent"
                            }`}
                          >
                            <span className="material-symbols-outlined text-[16px] leading-none">
                              {fuel.icon}
                            </span>
                            <span>{fuel.label}</span>
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* color */}
                    <div className="flex flex-col gap-1.5">
                      <label
                        className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1"
                        htmlFor="color"
                      >
                        <span>Màu sắc ngoại thất</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <div className="relative">
                        <div className="absolute left-3.5 top-3.5 w-4 h-4 rounded-full bg-emerald-700 ring-2 ring-emerald-200 shadow-xs"></div>
                        <input
                          id="color"
                          type="text"
                          required
                          maxLength={40}
                          value={form.color}
                          onChange={(e) =>
                            handleChange("color", e.target.value)
                          }
                          placeholder="VD: Trắng ngọc trai, Xám titan..."
                          className={`w-full h-11 pl-10 pr-3.5 bg-white text-sm text-[#0b1c30] rounded-xl border ${
                            errors.color
                              ? "border-red-500 focus:ring-2 focus:ring-red-500 bg-red-50/20"
                              : "border-slate-200 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                          } shadow-xs transition-colors`}
                        />
                      </div>
                      {errors.color && (
                        <span className="text-xs text-red-600 font-medium flex items-center gap-1 mt-0.5">
                          <span className="material-symbols-outlined text-[15px]">
                            error
                          </span>
                          {errors.color}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Maintenance Buffer Field: maintenance_buffer_minutes */}
                  <div className="p-5 rounded-2xl bg-emerald-50/60 border border-emerald-200 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xs">
                    <div className="flex items-start sm:items-center gap-4 max-w-2xl">
                      <div className="w-11 h-11 rounded-xl bg-white border border-emerald-200 shadow-xs flex items-center justify-center shrink-0 text-emerald-700">
                        <span className="material-symbols-outlined text-[24px]">
                          timelapse
                        </span>
                      </div>
                      <div className="flex flex-col gap-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-sm font-bold text-[#0b1c30]">
                            Khoảng đệm bảo trì giữa các chuyến (Maintenance
                            Buffer)
                          </span>
                          <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[11px] font-semibold border border-emerald-200">
                            Đặc quyền EcoMotion
                          </span>
                        </div>
                        <span className="text-xs text-slate-600 leading-relaxed">
                          Khoảng thời gian hệ thống tự động khóa lịch giữa 2
                          lượt khách đặt liên tiếp để nhà xe vệ sinh nội thất,
                          sạc pin tối thiểu 80% và kiểm tra áp suất lốp tại kho
                          xe.
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2.5 shrink-0 self-start sm:self-center">
                      <div className="bg-white border border-emerald-200 rounded-xl px-5 py-2 flex items-baseline gap-1.5 shadow-xs min-w-[110px] justify-center">
                        <span className="text-2xl font-bold text-emerald-800 tracking-tight">
                          {form.maintenanceBufferMinutes}
                        </span>
                        <span className="text-xs font-semibold text-slate-500">
                          phút
                        </span>
                      </div>
                      <div className="flex flex-col bg-white border border-emerald-200 rounded-xl overflow-hidden shadow-xs">
                        <button
                          type="button"
                          aria-label="Tăng 15 phút"
                          onClick={() => adjustBuffer(15)}
                          className="w-8 h-6 flex items-center justify-center text-emerald-700 hover:bg-emerald-50 transition-colors border-b border-emerald-100 cursor-pointer"
                        >
                          <span className="material-symbols-outlined text-[18px]">
                            keyboard_arrow_up
                          </span>
                        </button>
                        <button
                          type="button"
                          aria-label="Giảm 15 phút"
                          onClick={() => adjustBuffer(-15)}
                          className="w-8 h-6 flex items-center justify-center text-emerald-700 hover:bg-emerald-50 transition-colors cursor-pointer"
                        >
                          <span className="material-symbols-outlined text-[18px]">
                            keyboard_arrow_down
                          </span>
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Vehicle Cover Image: vehicle_image table (display_order = 0) */}
                  <div className="flex flex-col gap-3">
                    <div className="flex items-center justify-between">
                      <label className="text-xs sm:text-sm font-semibold text-[#0b1c30] flex items-center gap-1">
                        <span>Ảnh bìa xe chính thức (Hero Display Image)</span>
                        <span className="text-red-500 font-bold">*</span>
                      </label>
                      <span className="text-xs text-slate-500 font-mono">
                        Bảng vehicle_image (display_order = 0)
                      </span>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
                      {/* Left: Image Preview (Ratio 16:9) */}
                      <div className="lg:col-span-5 relative rounded-2xl overflow-hidden shadow-sm aspect-video bg-slate-100 group border border-slate-200">
                        <img
                          src={form.coverImageUrl}
                          alt="Cover Preview"
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                        />
                        <div className="absolute inset-0 bg-gradient-to-t from-[#0b1c30]/80 via-transparent to-transparent"></div>
                        <div className="absolute top-3 left-3 px-2.5 py-1 rounded-md bg-emerald-700/90 backdrop-blur-md text-white text-xs font-semibold flex items-center gap-1 shadow-sm">
                          <span className="material-symbols-outlined text-[14px]">
                            photo_camera
                          </span>
                          <span>ẢNH BÌA CHÍNH THỨC · 16:9</span>
                        </div>
                        <div className="absolute bottom-3 left-3 right-3 text-white">
                          <div className="font-bold text-sm leading-tight text-white drop-shadow-sm">
                            {form.brand} {form.model} ({form.manufactureYear})
                          </div>
                          <div className="text-[11px] text-white/80">
                            Hiển thị làm ảnh đại diện trên ứng dụng thuê xe cho
                            khách
                          </div>
                        </div>
                      </div>

                      {/* Right: Drag & Drop Upload Zone */}
                      <div
                        onClick={() =>
                          document.getElementById("coverImageInput")?.click()
                        }
                        className="lg:col-span-7 rounded-2xl bg-slate-50 p-6 flex flex-col items-center justify-center text-center cursor-pointer hover:bg-emerald-50/50 transition-colors shadow-xs border-2 border-dashed border-slate-300 hover:border-emerald-500"
                      >
                        <input
                          id="coverImageInput"
                          type="file"
                          accept="image/png, image/jpeg, image/webp"
                          className="hidden"
                          onChange={handleImageFileSelected}
                        />
                        <div className="w-12 h-12 rounded-full bg-emerald-100 text-emerald-700 border border-emerald-200 flex items-center justify-center mb-3 shadow-xs">
                          <span className="material-symbols-outlined text-[26px]">
                            cloud_upload
                          </span>
                        </div>
                        <div className="text-sm font-bold text-[#0b1c30] mb-1">
                          Kéo thả ảnh mới hoặc{" "}
                          <span className="text-emerald-700 underline font-semibold">
                            Duyệt tệp từ máy
                          </span>
                        </div>
                        <p className="text-xs text-slate-500 max-w-sm leading-relaxed">
                          Hỗ trợ định dạng WEBP, PNG, JPG. Dung lượng tối đa
                          10MB. Khuyến nghị ảnh chụp thực tế góc 3/4 mặt trước
                          xe với tỷ lệ 16:9.
                        </p>
                        <div className="mt-4 inline-flex items-center gap-1 px-3 py-1 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-semibold shadow-xs">
                          <span className="material-symbols-outlined text-[14px]">
                            check_circle
                          </span>
                          <span>Đã kiểm tra tỷ lệ khung hình chuẩn</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            </div>

            {/* Sticky Bottom Action Bar */}
            <aside
              aria-label="Thanh tác vụ đăng ký xe"
              className="fixed bottom-0 inset-x-0 z-40 bg-white/95 backdrop-blur-md border-t border-slate-200 shadow-[0_-4px_16px_rgba(0,0,0,0.06)] py-4 px-6 lg:px-12"
            >
              <div className="max-w-[1440px] mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
                <div className="flex items-center gap-2 text-slate-600 text-xs sm:text-sm">
                  <span className="material-symbols-outlined text-[20px] text-emerald-700 shrink-0">
                    info
                  </span>
                  <span>
                    <strong>Trạng thái sau khi bấm gửi:</strong> Phương tiện sẽ
                    chuyển vào hàng đợi{" "}
                    <strong className="text-amber-700 font-bold">
                      CHỜ DUYỆT (PENDING)
                    </strong>{" "}
                    để Chuyên viên kiểm duyệt hồ sơ.
                  </span>
                </div>

                <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
                  <Link
                    to="/provider/vehicles"
                    className="px-5 py-2.5 rounded-xl text-slate-700 font-semibold text-sm hover:bg-slate-100 transition-colors text-center border border-slate-200 shadow-xs"
                  >
                    Hủy bỏ
                  </Link>

                  <button
                    type="submit"
                    disabled={submitting}
                    className="px-6 py-2.5 rounded-xl bg-emerald-700 hover:bg-emerald-800 disabled:opacity-60 text-white font-semibold text-sm flex items-center justify-center gap-2 shadow-sm transition-all focus:outline-none active:scale-[0.98] cursor-pointer"
                  >
                    {submitting ? (
                      <>
                        <span className="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                        <span>Đang xử lý...</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[20px]">
                          send
                        </span>
                        <span>Tạo xe &amp; Gửi duyệt</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            </aside>
          </form>
        </div>
      </main>

      {/* MODAL: Chi tiết Quy chuẩn kiến trúc 3 Tầng v10.1 */}
      {showTierModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0b1c30]/60 backdrop-blur-xs p-4">
          <div className="bg-white w-full max-w-2xl rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh] border border-slate-200">
            {/* Modal Header */}
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-emerald-700 flex items-center justify-center text-white">
                  <span className="material-symbols-outlined text-[18px]">
                    verified_user
                  </span>
                </div>
                <div>
                  <h3 className="text-base font-bold text-[#0b1c30]">
                    Kiến trúc Quản lý Phương tiện 3 Tầng — v10.1
                  </h3>
                  <p className="text-xs text-slate-500">
                    Tiêu chuẩn phân tách rủi ro pháp lý xe EcoMotion
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowTierModal(false)}
                className="w-8 h-8 rounded-lg hover:bg-slate-200 flex items-center justify-center text-slate-500 hover:text-[#0b1c30] cursor-pointer"
              >
                <span className="material-symbols-outlined text-[20px]">
                  close
                </span>
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto flex flex-col gap-4 text-xs sm:text-sm">
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-[#0b1c30]">
                    Tầng 1: Định danh vật lý (Physical Identity)
                  </span>
                  <span className="font-semibold text-xs px-2 py-0.5 rounded bg-slate-200 text-[#0b1c30]">
                    BẤT BIẾN
                  </span>
                </div>
                <p className="text-slate-600 leading-relaxed">
                  Bao gồm <strong>Số khung</strong> và <strong>Số máy</strong>.
                  Đây là định danh vật lý duy nhất của tài sản. Sau khi ghi nhận
                  vào hệ thống, thông tin này sẽ bị khóa cứng vĩnh viễn trên cơ
                  sở dữ liệu để ngăn ngừa gian lận tráo xe.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-200 flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-amber-950">
                    Tầng 2: Định danh pháp lý (Legal Identity)
                  </span>
                  <span className="font-semibold text-xs px-2 py-0.5 rounded bg-amber-200 text-amber-900">
                    DUYỆT LẠI
                  </span>
                </div>
                <p className="text-amber-900/90 leading-relaxed">
                  Bao gồm <strong>Biển số kiểm soát</strong>. Nếu chủ xe thực
                  hiện sang tên đổi biển, cập nhật biển số mới sẽ tự động chuyển
                  về trạng thái <strong>CHỜ DUYỆT</strong> để Chuyên viên kiểm
                  duyệt lại cà vẹt trước khi tiếp tục nhận khách.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-emerald-950">
                    Tầng 3: Mô tả &amp; Vận hành (Operational &amp; Marketing)
                  </span>
                  <span className="font-semibold text-xs px-2 py-0.5 rounded bg-emerald-200 text-emerald-900">
                    TỰ DO
                  </span>
                </div>
                <p className="text-emerald-900/90 leading-relaxed">
                  Bao gồm: Hãng, dòng xe, năm sản xuất, số chỗ, hộp số, nhiên
                  liệu, màu sơn, ảnh đại diện và thời gian đệm bảo trì. Chủ xe
                  được quyền cập nhật linh hoạt 24/7 để tối ưu kinh doanh mà
                  không làm gián đoạn trạng thái hoạt động của xe.
                </p>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex justify-end">
              <button
                type="button"
                onClick={() => setShowTierModal(false)}
                className="px-5 py-2 rounded-xl bg-emerald-700 text-white font-semibold text-sm hover:bg-emerald-800 transition-colors shadow-xs cursor-pointer"
              >
                Đã hiểu quy chuẩn
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
