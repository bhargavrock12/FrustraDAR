from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):

    # ── Database ──────────────────────────────────────────────────────────
    DATABASE_URL: str = (
        "postgresql://postgres:password@localhost:5432/frustradar"
    )

    # ── JWT ───────────────────────────────────────────────────────────────
    SECRET_KEY: str                  = "change-this-secret-key"
    ALGORITHM: str                   = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440

    # ── Firebase ──────────────────────────────────────────────────────────
    FIREBASE_CREDENTIALS_PATH: str = "./firebase-credentials.json"

    # ── SendGrid ──────────────────────────────────────────────────────────
    SENDGRID_API_KEY: str = ""
    FROM_EMAIL: str       = "alerts@frustradar.com"

    # ── App ───────────────────────────────────────────────────────────────
    DEBUG: bool            = True
    CORS_ORIGINS: List[str] = [
        "http://localhost:3000",
        "http://localhost:5173"
    ]

    # ── Frustration Alert Thresholds ──────────────────────────────────────
    FRUSTRATION_HIGH_THRESHOLD:     float = 70.0
    FRUSTRATION_CRITICAL_THRESHOLD: float = 85.0

    # ── Alert Cooldown ────────────────────────────────────────────────────
    # Minimum minutes between identical alert types per user
    ALERT_COOLDOWN_MINUTES: int = 5

    # ── Night Gaming ──────────────────────────────────────────────────────
    NIGHT_START_HOUR: int = 23   # 11 PM
    NIGHT_END_HOUR:   int = 5    # 5 AM

    # ── Behavioral Analytics Thresholds ───────────────────────────────────
    # Used to generate behavioral indicators — not an addiction score
    DAILY_PLAYTIME_THRESHOLD_HOURS: float  = 3.0   # → HIGH_DAILY_PLAYTIME
    LONG_SESSION_THRESHOLD_MIN:     int    = 60     # → LONG_SESSION
    FREQUENT_SESSIONS_THRESHOLD:    int    = 4      # → FREQUENT_SESSIONS

    # ── Rapid Reopen ──────────────────────────────────────────────────────
    # Detected by Android — consumed and stored by backend
    RAPID_REOPEN_INTERVAL_SEC: int = 30
    RAPID_REOPEN_COUNT:        int = 3

    # ── WebSocket ─────────────────────────────────────────────────────────
    WS_HEARTBEAT_INTERVAL: int = 30   # seconds between ping frames

    class Config:
        env_file = ".env"

settings = Settings()