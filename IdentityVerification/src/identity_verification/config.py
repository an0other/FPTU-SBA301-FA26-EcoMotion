from functools import lru_cache

from pydantic import Field, SecretStr, field_validator
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
