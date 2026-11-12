import warnings
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Lock
from contextlib import asynccontextmanager
from identity_verification.jobs import VerificationJobs

from fastapi import Depends, FastAPI, HTTPException, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError

from identity_verification.config import get_settings
from identity_verification.face_detection import detect_faces
from identity_verification.security import require_api_key
from identity_verification.verification_pipeline import verify_video

settings = get_settings()
@asynccontextmanager
async def lifespan(app):
    yield
    jobs.close()

app = FastAPI(lifespan=lifespan,
    title="EcoMotion Identity Verification",
    version="0.1.0",
)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "identity-verification",
    }


MAX_IMAGE_BYTES = settings.max_image_mb * 1024 * 1024
MAX_IMAGE_PIXELS = settings.max_image_pixels
MAX_VIDEO_BYTES = settings.max_video_mb * 1024 * 1024
verification_lock = Lock()
jobs = VerificationJobs(verification_lock)


@app.get("/v1/constraints", dependencies=[Depends(require_api_key)])
def constraints():
    from identity_verification.check_challenge import EXPECTED, MIN_HOLD_SECONDS

    return {
        "maxImageBytes": MAX_IMAGE_BYTES,
        "maxImagePixels": MAX_IMAGE_PIXELS,
        "maxVideoBytes": MAX_VIDEO_BYTES,
        "maxVideoSeconds": settings.max_video_seconds,
        "maxVideoFps": settings.max_video_fps,
        "maxVideoFrames": settings.max_video_frames,
        "maxVideoPixels": settings.max_video_pixels,
        "imageFormats": ["JPEG", "PNG"],
        "videoContainers": ["MP4", "MOV", "WebM", "Matroska"],
        "challenge": list(EXPECTED),
        "minimumHoldSeconds": MIN_HOLD_SECONDS,
    }


def save_upload(upload: UploadFile, destination: Path, limit: int):
    total = 0

    with destination.open("wb") as target:
        while chunk := upload.file.read(1024 * 1024):
            total += len(chunk)

            if total > limit:
                raise HTTPException(
                    status_code=413,
                    detail="File vượt quá dung lượng cho phép",
                )

            target.write(chunk)

    if total == 0:
        raise HTTPException(status_code=400, detail="File rỗng")


def validate_reference_image(path: Path):
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)

            with Image.open(path) as image:
                if image.format not in {"JPEG", "PNG"}:
                    raise HTTPException(
                        status_code=415,
                        detail="Ảnh CCCD phải là JPEG hoặc PNG",
                    )

                if image.width * image.height > MAX_IMAGE_PIXELS:
                    raise HTTPException(
                        status_code=413,
                        detail=f"Ảnh vượt quá {settings.max_image_pixels} pixel",
                    )

                image.verify()

    except (Image.DecompressionBombWarning, Image.DecompressionBombError):
        raise HTTPException(
            status_code=413,
            detail="Độ phân giải ảnh quá lớn",
        )
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError):
        raise HTTPException(
            status_code=400,
            detail="Ảnh không hợp lệ hoặc bị hỏng",
        )


@app.post(
    "/v1/verifications",
    dependencies=[Depends(require_api_key)],
)
def create_verification(cccd: UploadFile, video: UploadFile):
    acquired = verification_lock.acquire(blocking=False)

    try:
        if not acquired:
            raise HTTPException(
                status_code=503,
                detail="Service đang xử lý một lượt xác minh, hãy thử lại",
            )

        with TemporaryDirectory(prefix="identity-verification-") as directory:
            workspace = Path(directory)
            cccd_path = workspace / "reference.image"
            video_path = workspace / "capture.video"

            save_upload(cccd, cccd_path, MAX_IMAGE_BYTES)
            save_upload(video, video_path, MAX_VIDEO_BYTES)

            validate_reference_image(cccd_path)

            try:
                result = verify_video(cccd_path, video_path)
            except ValueError as error:
                raise HTTPException(
                    status_code=422,
                    detail=str(error),
                ) from error

            return result

    finally:
        cccd.file.close()
        video.file.close()

        if acquired:
            verification_lock.release()


@app.post('/v1/verifications/jobs', status_code=202, dependencies=[Depends(require_api_key)])
def create_verification_job(cccd: UploadFile, video: UploadFile):
    directory = TemporaryDirectory(prefix='identity-verification-')
    try:
        workspace = Path(directory.name)
        reference = workspace / 'reference.image'
        capture = workspace / 'capture.video'
        save_upload(cccd, reference, MAX_IMAGE_BYTES)
        save_upload(video, capture, MAX_VIDEO_BYTES)
        validate_reference_image(reference)
        try:
            key = jobs.submit(lambda progress: verify_video(reference, capture, progress=progress), directory.cleanup)
        except RuntimeError as error:
            raise HTTPException(503, 'Verification busy; retry later') from error
        return {'jobId':key}
    except Exception:
        directory.cleanup()
        raise
    finally:
        cccd.file.close()
        video.file.close()


@app.get('/v1/verifications/jobs/{job_id}', dependencies=[Depends(require_api_key)])
def verification_job(job_id: str):
    result = jobs.get(job_id)
    if result is None:
        raise HTTPException(404, 'Job not found or expired')
    return result


@app.post("/v1/images/inspect")
async def inspect_image(file: UploadFile):
    try:
        content = await file.read(MAX_IMAGE_BYTES + 1)
    finally:
        await file.close()

    if not content:
        raise HTTPException(status_code=400, detail="File ảnh rỗng")

    if len(content) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail=f"Ảnh vượt quá {settings.max_image_mb} MB")

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)

            with Image.open(BytesIO(content)) as image:
                image_format = image.format

                if image_format not in {"JPEG", "PNG"}:
                    raise HTTPException(
                        status_code=415,
                        detail="Chỉ hỗ trợ ảnh JPEG hoặc PNG",
                    )

                if image.width * image.height > MAX_IMAGE_PIXELS:
                    raise HTTPException(
                        status_code=413,
                        detail=f"Ảnh vượt quá {settings.max_image_pixels} pixel",
                    )

                image.verify()

            with Image.open(BytesIO(content)) as image:
                normalized = ImageOps.exif_transpose(image)
                normalized.load()
                width, height = normalized.size
                rgb_image = normalized.convert("RGB")

    except (Image.DecompressionBombWarning, Image.DecompressionBombError):
        raise HTTPException(
            status_code=413,
            detail="Độ phân giải ảnh quá lớn",
        )
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError):
        raise HTTPException(
            status_code=400,
            detail="Không thể đọc ảnh hoặc file ảnh bị hỏng",
        )

    faces = detect_faces(rgb_image)
    return {
        "status": "ok",
        "format": image_format,
        "width": width,
        "height": height,
        "sizeBytes": len(content),
        "faceCount": len(faces),
        "faces": faces
    }
