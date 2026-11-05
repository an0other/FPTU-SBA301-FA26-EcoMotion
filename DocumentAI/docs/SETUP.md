# Cài đặt và chạy thử DocumentAI v0.32 trên Windows

Hướng dẫn này dành cho người mới nhận source code. Làm lần lượt từ trên xuống:

1. Kiểm tra máy và lấy source.
2. Cài Python và các thư viện.
3. Nhận checkpoint định vị thẻ riêng.
4. Thêm ảnh đầu vào riêng.
5. Tạo API key, chạy service, đợi warm-up.
6. Gọi thử API.

> **Lưu ý:** cài được project không có nghĩa là đã có đủ model để đọc giấy tờ.
> Checkpoint định vị thẻ của dự án không nằm trong Git và không có URL tải tự
> động. Cần nhận đúng checkpoint và file metadata từ người quản lý model.

## 1. Kiểm tra máy trước khi cài

Luồng hướng dẫn dưới đây chạy trên Windows 10/11, Python 3.11 64-bit và CPU.
Không bắt buộc phải có GPU. Python 3.11 là lựa chọn đã dùng để kiểm thử dự án;
`pyproject.toml` cho phép Python từ 3.11 đến trước 3.13.

Mở PowerShell và kiểm tra Python launcher:

```powershell
py -0p
py -3.11 --version
```

Kết quả thứ hai phải hiện Python 3.11.x. Nếu không có lệnh `py` hoặc không có
Python 3.11, cài **Python 3.11 x64** từ trang tải chính thức:
[python.org/downloads/windows](https://www.python.org/downloads/windows/).
Trong màn hình cài đặt, chọn Python Launcher. Sau đó đóng/mở lại PowerShell và
chạy hai lệnh kiểm tra trên lần nữa.

Không cần cài CUDA để làm theo các bước CPU. Cấu hình GPU là tùy chọn ở cuối
hướng dẫn.

## 2. Lấy source code

Clone repository nội bộ mà nhóm cung cấp, hoặc mở thư mục đã được clone:

```powershell
git clone <URL-repository-DocumentAI>
Set-Location .\DocumentAI
```

Thay `<URL-repository-DocumentAI>` bằng URL Git thật của dự án. Nếu đã clone
trước đó, vào thư mục repo và cập nhật nhánh theo quy trình của nhóm, không
clone lồng thêm một bản mới.

Xác nhận đang ở thư mục gốc DocumentAI:

```powershell
Test-Path .\pyproject.toml
Test-Path .\scripts\setup.ps1
```

Cả hai kết quả cần là `True`. Các lệnh còn lại trong hướng dẫn phải chạy tại
thư mục này.

## 3. Cài môi trường cho API và PaddleOCR

Script setup tạo `.venv` bằng Python 3.11 nếu môi trường chưa có; cài project,
dependency dev/QR và chạy test. `-WithOcr` cài thêm PaddleOCR cho bước phát
hiện vùng chữ/hướng ảnh.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1 -WithOcr
```

Sau khi script kết thúc, kiểm tra import và phiên bản:

```powershell
.\.venv\Scripts\python.exe --version
.\.venv\Scripts\python.exe -c "import paddleocr, paddle; print('PaddleOCR', paddleocr.__version__); print('Paddle', paddle.__version__)"
```

Project gọi Ultralytics YOLO để chạy checkpoint segmentation của thẻ. Gói này
không nằm trong `pyproject.toml` và không được cài bởi `setup.ps1`, nên cài
riêng vào đúng `.venv`:

```powershell
.\.venv\Scripts\python.exe -m pip install ultralytics==8.4.189
.\.venv\Scripts\python.exe -c "import ultralytics; print('Ultralytics', ultralytics.__version__)"
```

Không dùng `pip` trần hoặc một Python khác cho các lệnh này; dùng đường dẫn
`.venv\Scripts\python.exe` để package được cài đúng môi trường API.

### Vì sao không chỉ chạy `pip install -e . --no-deps`?

Lệnh này chỉ đăng ký source dưới dạng editable install. `--no-deps` có nghĩa là
pip **bỏ qua việc cài dependencies**. Lệnh đó không tự cài PaddleOCR,
Ultralytics, VietOCR, PyTorch hoặc checkpoint của dự án. Nếu đang dùng một
môi trường được nhóm chuẩn bị sẵn, có thể cần lệnh này để cập nhật source;
nhưng trên máy mới hãy làm các bước đầy đủ trong tài liệu này.

## 4. Cài môi trường riêng cho VietOCR

Reader dùng hai môi trường Python:

- `.venv` chạy API, PaddleOCR và Ultralytics.
- `.venv-vietocr` chạy VietOCR và PyTorch trong worker riêng.

Tạo môi trường VietOCR CPU và cài các phiên bản tương ứng với cấu hình hiện
tại của dự án:

```powershell
py -3.11 -m venv .venv-vietocr
.\.venv-vietocr\Scripts\python.exe -m pip install --upgrade pip
.\.venv-vietocr\Scripts\python.exe -m pip install torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu
.\.venv-vietocr\Scripts\python.exe -m pip install vietocr==0.3.13
```

Kiểm tra interpreter và thư viện:

```powershell
.\.venv-vietocr\Scripts\python.exe .\tools\check_vietocr_runtime.py --device cpu
```

Kết quả cuối cần có `"status": "RUNTIME_AVAILABLE"`. Nếu cài VietOCR/PyTorch
thất bại, hãy giữ nguyên toàn bộ lỗi PowerShell để kiểm tra; đừng cài thêm
`torch` vào `.venv` thay cho môi trường VietOCR.

VietOCR/PaddleOCR có thể cần tải model upstream vào cache ở lần warm-up đầu
tiên. Hãy đảm bảo máy có Internet/proxy phù hợp trong lần chạy đầu. Các file
cache đó không thay thế checkpoint localizer ở bước tiếp theo.

## 5. Nhận checkpoint localizer của dự án

Launcher mặc định yêu cầu **đúng hai file**:

```text
models\localizer\document-card-seg-v1.pt
models\localizer\document-card-seg-v1.card.json
```

Các file này bị Git ignore vì đây là model đã train, không phải source code.
Repo hiện không có URL tải công khai hay lệnh tự tải model. Hãy yêu cầu người
quản lý dự án/model gửi đúng cặp file qua kênh chia sẻ artifact riêng mà nhóm
cho phép (ví dụ kho artifact nội bộ). Không thay bằng `yolo11n-seg.pt`, model
YOLO tải từ internet, hoặc checkpoint của thành viên khác: đó không phải model
đã train cho task `document_card`.

Tạo folder đích rồi chép file được cung cấp vào đó:

```powershell
New-Item -ItemType Directory -Force .\models\localizer | Out-Null
# Chép hai file được cấp vào .\models\localizer\
```

Kiểm tra file và hash checkpoint. File metadata đi kèm lưu SHA-256; lệnh này
phải in `Checkpoint hash matches metadata.`:

```powershell
$weights = Get-Content .\models\localizer\document-card-seg-v1.card.json -Raw | ConvertFrom-Json
$actual = (Get-FileHash .\models\localizer\document-card-seg-v1.pt -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $weights.sha256) { throw "Checkpoint hash does not match the supplied metadata." }
Write-Output "Checkpoint hash matches metadata."
```

Nếu thiếu một file hoặc hash không khớp, dừng tại đây và xin lại đúng cặp
artifact. Không sửa `sha256` trong metadata để làm cho file khác được chấp
nhận.

## 6. Thêm ảnh warm-up và ảnh test

Folder `inputs/` không được push lên Git. Mỗi người chạy tự tạo folder và thêm
ảnh được phép dùng để kiểm thử. Dùng ảnh chụp **đủ cả thẻ, rõ, không cắt mất
góc**, đúng thứ tự mặt trước/mặt sau.

```powershell
New-Item -ItemType Directory -Force .\inputs | Out-Null
```

Để launcher khởi động và warm-up, cần hai ảnh GPLX với tên mặc định:

```text
inputs\gplx-test-front.JPEG
inputs\gplx-test-back.JPEG
```

Có thể copy ảnh của bạn vào đúng tên, ví dụ:

```powershell
Copy-Item "D:\duong-dan-den-anh\gplx-mat-truoc.jpg" .\inputs\gplx-test-front.JPEG
Copy-Item "D:\duong-dan-den-anh\gplx-mat-sau.jpg" .\inputs\gplx-test-back.JPEG
```

Kiểm tra trước khi chạy:

```powershell
Test-Path .\inputs\gplx-test-front.JPEG
Test-Path .\inputs\gplx-test-back.JPEG
```

Cả hai phải là `True`. Có thể truyền đường dẫn ảnh khác vào các tham số
`-WarmupFront` và `-WarmupBack` của launcher, nhưng API vẫn cần hai ảnh warm-up
hợp lệ để báo `READY`.

Để chạy smoke test đầy đủ bằng `tools\test_api_v32.py`, cần thêm cặp CCCD:

```text
inputs\cccd-front.jpeg
inputs\cccd-back.jpeg
```

Copy ảnh CCCD được phép dùng vào đúng tên tương tự GPLX ở trên. Smoke test này
thử cả GPLX, CCCD, job bất đồng bộ và một số trường hợp ảnh sai. Nếu chỉ có bộ
ảnh GPLX, bỏ qua smoke test đầy đủ và dùng Swagger để gọi riêng GPLX ở bước 10.

Không commit ảnh giấy tờ, kể cả ảnh đã đổi tên. Folder `inputs/` bị ignore vì
ảnh có dữ liệu nhạy cảm; chỉ dùng dữ liệu được phép chia sẻ và giữ chúng local.

## 7. Tạo API key local

API yêu cầu `DOCUMENT_AI_API_KEY` dài ít nhất 32 ký tự ASCII. Tạo một key ngẫu
nhiên trong PowerShell:

```powershell
$apiKey = & .\.venv\Scripts\python.exe -c "import secrets; print(secrets.token_urlsafe(32))"
Set-Content -Path .\.env -Value "DOCUMENT_AI_API_KEY=$apiKey" -Encoding ascii
Remove-Variable apiKey
```

`.env` phải nằm ngay cạnh `pyproject.toml`. File này đã được Git ignore; không
commit, gửi vào chat chung hoặc đưa key vào frontend.

Kiểm tra chỉ tên biến và độ dài, không in giá trị key:

```powershell
$keyLine = Get-Content .\.env | Where-Object { $_ -match '^DOCUMENT_AI_API_KEY=' } | Select-Object -First 1
if (-not $keyLine) { throw "Missing DOCUMENT_AI_API_KEY in .env" }
$keyValue = $keyLine.Substring("DOCUMENT_AI_API_KEY=".Length)
if ($keyValue.Length -lt 32) { throw "API key must be at least 32 characters" }
Write-Output "API key is present and long enough."
Remove-Variable keyLine, keyValue
```

## 8. Chạy API

Mở **Terminal 1** tại thư mục gốc repo. Chạy CPU mode để bắt đầu; không cần
CUDA:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\serve-v32.ps1 `
  -Python .\.venv\Scripts\python.exe `
  -VietocrPython .\.venv-vietocr\Scripts\python.exe `
  -RecognizerDevice cpu `
  -ImageAnalysis off `
  -Environment development `
  -Port 8001
```

Giữ terminal này mở. Lần đầu có thể chờ lâu vì PaddleOCR/VietOCR khởi tạo và
tải model upstream vào cache. Launcher cũng đọc hai ảnh GPLX để warm-up. Chỉ
chuyển sang bước kế tiếp khi log cho thấy startup hoàn tất và warm-up không
báo lỗi.

`-ImageAnalysis off` tắt diagnostics ELA/FFT thử nghiệm để demo đọc giấy tờ
gọn hơn. Nó **không** ảnh hưởng OCR/QR và không phải công tắc xác minh thật
giả.

## 9. Kiểm tra service đã sẵn sàng

Mở **Terminal 2**, cũng tại thư mục gốc repo:

```powershell
Invoke-RestMethod http://127.0.0.1:8001/health
Invoke-RestMethod http://127.0.0.1:8001/ready
Invoke-RestMethod http://127.0.0.1:8001/v1/capabilities
```

`/ready` phải trả `status` là `READY`, `modelInferenceChecked` là `True`.
`/v1/capabilities` phải báo phiên bản `0.32.0`. Nếu `/ready` trả HTTP 503,
quay lại Terminal 1 và xử lý lỗi warm-up/model trước; không tiếp tục gửi ảnh
cho API khi reader chưa sẵn sàng.

## 10. Gọi thử API

### Cách A — Swagger UI

Mở `http://127.0.0.1:8001/docs`.

1. Mở `.env` bằng Notepad, sao chép phần giá trị đứng sau
   `DOCUMENT_AI_API_KEY=`. Bấm **Authorize** và nhập riêng giá trị đó (không
   nhập tên biến hay `=`).
2. Mở `POST /v1/documents/read`, bấm **Try it out**.
3. Chọn file GPLX mặt trước và mặt sau.
4. Chọn `VN_DRIVING_LICENSE` cho `document_type`.
5. Bấm **Execute**.

API trả JSON có `status`, `data`, `checks`, `pipelineReport` và các trường kết
quả. `REVIEW_REQUIRED`, `PARTIALLY_READ` hoặc `RETAKE_REQUIRED` có thể xảy ra
nếu ảnh mờ, sai mặt, thiếu trường hoặc không khớp. Đây là kết quả đọc/đối
chiếu, **không phải kết luận giấy phép thật hay giả**.

### Cách B — smoke test đầy đủ có sẵn

Chỉ chạy khi đã thêm đủ hai ảnh GPLX và hai ảnh CCCD ở bước 6. Chọn tên output
mới chưa tồn tại:

```powershell
.\.venv\Scripts\python.exe .\tools\test_api_v32.py `
  --base-url http://127.0.0.1:8001 `
  --output runs\api-check-first-run `
  --gplx-front inputs\gplx-test-front.JPEG `
  --gplx-back inputs\gplx-test-back.JPEG `
  --cccd-front inputs\cccd-front.jpeg `
  --cccd-back inputs\cccd-back.jpeg
```

Tool lấy API key từ `.env`, kiểm tra `/ready` và `/v1/capabilities`, rồi lưu
kết quả vào `runs\api-check-first-run\`. Nếu folder output đã tồn tại, đổi
sang tên khác. Các JSON có thể chứa thông tin OCR; giữ local và không gửi lên
kênh công khai. Đọc `api-test.json` để xem từng case `passed`, HTTP status và
reading status.

### Cách C — gọi riêng GPLX bằng `curl.exe`

Nếu chỉ có ảnh GPLX, có thể gọi endpoint trực tiếp. Thay `PASTE_LOCAL_KEY`
bằng giá trị trong `.env`; tránh chạy lệnh này trong terminal dùng chung vì
key có thể lưu trong command history:

```powershell
$apiKey = "PASTE_LOCAL_KEY"
curl.exe -sS -X POST "http://127.0.0.1:8001/v1/documents/read" `
  -H "X-API-Key: $apiKey" `
  -F "front=@.\inputs\gplx-test-front.JPEG" `
  -F "back=@.\inputs\gplx-test-back.JPEG" `
  -F "document_type=VN_DRIVING_LICENSE"
Remove-Variable apiKey
```

Kết quả HTTP `401` thường là thiếu/sai key; `422` là thiếu hoặc sai field form;
`429` là reader đang xử lý request khác. Với HTTP `200`, vẫn đọc trường
`status` và `checks` trong JSON để biết ảnh có được đọc đầy đủ hay cần chụp
lại.

## 11. GPU (tùy chọn)

CPU là cách khởi đầu đơn giản nhất. Nếu máy có GPU NVIDIA và muốn chạy
recognizer VietOCR bằng CUDA, trước tiên cài `.venv-vietocr` CPU ở bước 4.
Sau đó dùng script có sẵn để tạo một môi trường GPU riêng:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-vietocr-gpu.ps1 `
  -CpuPython .\.venv-vietocr\Scripts\python.exe
```

Script dùng PyTorch 2.6.0/torchvision 0.21.0 với wheel CUDA 12.4, kiểm tra
`cuda:0`, và tạo `.venv-vietocr-gpu`. Nếu driver/GPU không tương thích, giữ
CPU; không chọn `cuda:0` khi chưa qua kiểm tra. Khi GPU check thành công, đổi
tham số launcher:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\serve-v32.ps1 `
  -Python .\.venv\Scripts\python.exe `
  -VietocrPython .\.venv-vietocr-gpu\Scripts\python.exe `
  -RecognizerDevice cuda:0 `
  -ImageAnalysis off `
  -Environment development `
  -Port 8001
```

Trong cấu hình hiện tại, GPU chỉ dùng cho recognizer VietOCR; localizer vẫn
chạy CPU. Không cần cài CUDA nếu chỉ chạy CPU.

## Các lỗi thường gặp

| Hiện tượng | Cần kiểm tra |
|---|---|
| `py -3.11` không chạy | Cài Python 3.11 x64 và mở lại PowerShell. |
| Thiếu `paddleocr`, `paddle`, `ultralytics` | Cài lại đúng bước 3 bằng `.venv\Scripts\python.exe`. |
| `RUNTIME_AVAILABLE` không xuất hiện | Kiểm tra `.venv-vietocr`, Python 3.11, PyTorch, torchvision và VietOCR ở bước 4. |
| Không tìm thấy `.pt` hoặc lỗi hash | Đảm bảo nhận đủ checkpoint cùng `.card.json`, đúng tên/thư mục và hash ở bước 5. |
| Lỗi tải model khi warm-up | Kiểm tra Internet/proxy, quyền ghi cache và log ở Terminal 1; upstream PaddleOCR/VietOCR có thể chưa có model trong cache. |
| `DOCUMENT_AI_API_KEY` thiếu/ngắn | Đặt key trong `.env` cạnh `pyproject.toml`, dài ít nhất 32 ký tự ASCII. |
| `/ready` trả 503 | Warm-up chưa thành công. Xem Terminal 1; kiểm tra ảnh GPLX, checkpoint, đường dẫn Python và model runtime. |
| `401 Missing/Invalid X-API-Key` | Swagger/curl chưa gửi key hoặc key không giống `.env` mà server đã nạp. |
| `RETAKE_REQUIRED` hoặc thiếu trường | Kiểm tra đúng mặt trước/sau, ảnh đủ thẻ, rõ nét, không bị lóa/cắt góc. |

## Những gì không cần push lên Git

`.venv*`, `.env`, `models/`, `inputs/`, `training/`, `runs/`, `storage/` và
local evaluation/config files được ignore. Cần chia sẻ checkpoint, ảnh demo
hoặc dataset thì dùng kênh artifact riêng và chỉ chia sẻ khi được phép; không
đưa dữ liệu giấy tờ cá nhân lên Git.

Ignore rule không gỡ các file đã được commit từ trước. Nếu model/ảnh/secret đã
được track, cần kiểm tra và xử lý riêng trong repo Git thật trước khi push.

Để xem luồng API/trace và giới hạn của kết quả, đọc thêm
[START_HERE_V032.md](START_HERE_V032.md), [CONTRACT.md](CONTRACT.md) và
[EKYC_ASSURANCE.md](EKYC_ASSURANCE.md).
