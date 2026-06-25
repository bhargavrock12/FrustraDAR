import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.db.init_db import init_db
from app.services.notification_service import init_firebase
from app.api.routes import (
    auth, scores, sessions,
    alerts, users, dashboard, reports
)
from app.websocket.ws_router import router as ws_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)


# ============================================
# APP
# ============================================
app = FastAPI(
    title="FrustraDAR API",
    description="Backend API for Gamer Frustration Detection",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# ============================================
# CORS
# ============================================
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============================================
# STARTUP
# ============================================
@app.on_event("startup")
async def startup():
    logger.info("FrustraDAR API starting...")
    init_db()
    init_firebase()
    logger.info("FrustraDAR API ready ✅")

# ============================================
# REST ROUTES
# ============================================
app.include_router(auth.router,      prefix="/api/v1/auth",      tags=["Auth"])
app.include_router(scores.router,    prefix="/api/v1/scores",    tags=["Scores"])
app.include_router(sessions.router,  prefix="/api/v1/sessions",  tags=["Sessions"])
app.include_router(alerts.router,    prefix="/api/v1/alerts",    tags=["Alerts"])
app.include_router(users.router,     prefix="/api/v1/users",     tags=["Users"])
app.include_router(dashboard.router, prefix="/api/v1/dashboard", tags=["Dashboard"])
app.include_router(reports.router,   prefix="/api/v1/reports",   tags=["Reports"])

# ============================================
# WEBSOCKET
# ============================================
app.include_router(ws_router, tags=["WebSocket"])

# ============================================
# HEALTH
# ============================================
@app.get("/")
def root():
    return {
        "message": "FrustraDAR API is running 🎮",
        "version": "1.0.0",
        "docs":    "/docs",
        "ws":      "ws://host/ws?token=<jwt>"
    }

@app.get("/health")
def health():
    return {"status": "healthy"}