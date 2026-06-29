# FrustraDAR backend image — serves the existing FastAPI app (app.main:app) via uvicorn.
#
# Configuration is entirely via environment variables consumed by app.core.config.Settings
# (DATABASE_URL, SECRET_KEY, FIREBASE_CREDENTIALS_PATH, SENDGRID_API_KEY, SENDGRID_FROM_EMAIL,
# CORS_ORIGINS, DEBUG). NO secrets are baked into the image — supply them at run time.
#
# Migrations are intentionally NOT run in the container entrypoint; running `alembic upgrade head`
# is an orchestration concern (see docs/10 B-D4). alembic/ and alembic.ini are copied in so a
# migration can be invoked explicitly (e.g. `docker run ... alembic upgrade head`) when desired.

FROM python:3.11-slim

# Faster, cleaner Python in containers.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install runtime dependencies first for better layer caching.
# psycopg2-binary ships wheels, so no system build toolchain is required.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Application code + migration assets (no secrets; see .dockerignore).
COPY app/ ./app/
COPY alembic/ ./alembic/
COPY alembic.ini .

# Run as a non-root user.
RUN useradd --create-home --uid 1000 appuser
USER appuser

EXPOSE 8000

# Serve the existing FastAPI application. PORT is overridable at run time.
ENV PORT=8000
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
