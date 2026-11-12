from functools import lru_cache

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="IDENTITY_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    api_key: SecretStr
    max_image_mb: int = Field(default=8, ge=1, le=20)
    max_image_pixels: int = Field(default=20_000_000, ge=1, le=40_000_000)

    max_video_mb: int = Field(default=20, ge=1, le=100)
    max_video_seconds: float = Field(default=15, ge=1, le=30)
    max_video_fps: float = Field(default=120, ge=1, le=120)
    max_video_frames: int = Field(default=1800, ge=1, le=3600)
    max_video_pixels: int = Field(default=1920 * 1080, ge=1, le=3840 * 2160)

    face_match_low_threshold: float | None = Field(default=None, ge=-1, le=1)
    face_match_high_threshold: float | None = Field(default=None, ge=-1, le=1)
    face_match_min_frames: int = Field(default=3, ge=1, le=60)
    face_match_calibration_id: str | None = Field(default=None, pattern=r"^[A-Za-z0-9._-]{1,100}$")
    face_match_model_sha256: str | None = Field(default=None, pattern=r"^[a-f0-9]{64}$")

    @model_validator(mode='after')
    def validate_face_match_policy(self):
        low, high = self.face_match_low_threshold, self.face_match_high_threshold
        if (low is None) != (high is None):
            raise ValueError('Configure both face-match thresholds or neither')
        if low is not None and (low >= high or not self.face_match_calibration_id or not self.face_match_model_sha256):
            raise ValueError('Experimental face-match thresholds require low < high, calibration ID and pinned model SHA256')
        return self

    @field_validator("api_key")
    @classmethod
    def validate_api_key(cls, value):
        key = value.get_secret_value()

        if (
            len(key) < 32
            or not key.isascii()
            or any(character.isspace() for character in key)
        ):
            raise ValueError(
                "IDENTITY_API_KEY must contain at least 32 ASCII "
                "characters without whitespace"
            )

        return value


@lru_cache(maxsize=1)
def get_settings():
    return Settings()
