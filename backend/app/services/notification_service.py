import logging
import firebase_admin
from firebase_admin import credentials, messaging
from app.core.config import settings
import os

logger = logging.getLogger(__name__)

_firebase_initialized = False


def init_firebase():
    global _firebase_initialized
    if _firebase_initialized:
        return

    try:
        if os.path.exists(settings.FIREBASE_CREDENTIALS_PATH):
            cred = credentials.Certificate(settings.FIREBASE_CREDENTIALS_PATH)
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            logger.info("Firebase initialized successfully")
        else:
            logger.warning(
                "Firebase credentials not found — "
                "push notifications disabled"
            )
    except Exception as e:
        logger.error(f"Firebase init failed: {e}")


class NotificationService:

    # ============================================
    # CORE SEND
    # ============================================
    def send_push(
        self,
        fcm_token: str,
        title:     str,
        body:      str,
        data:      dict = None
    ) -> bool:
        """
        Send FCM push notification.
        Used for IMPORTANT events only — not every score update.
        Returns True if sent successfully.
        """
        if not _firebase_initialized:
            logger.debug("Firebase not initialized — skipping push")
            return False

        if not fcm_token:
            return False

        # FCM data values must all be strings
        str_data = {k: str(v) for k, v in (data or {}).items()}

        try:
            message = messaging.Message(
                notification=messaging.Notification(
                    title=title,
                    body=body
                ),
                data=str_data,
                token=fcm_token,
                android=messaging.AndroidConfig(
                    priority="high"
                )
            )
            response = messaging.send(message)
            logger.info(f"FCM sent: {response}")
            return True

        except Exception as e:
            logger.error(f"FCM send failed: {e}")
            return False

    # ============================================
    # PARENT NOTIFICATIONS
    # Only called for HIGH / CRITICAL alerts
    # NOT called for every score update
    # ============================================
    def notify_high_frustration(
        self,
        parent_fcm_token: str,
        child_name:       str,
        score:            float,
        game_name:        str = None
    ):
        """Notify parent — HIGH or CRITICAL frustration alert."""
        severity = "CRITICAL" if score >= settings.FRUSTRATION_CRITICAL_THRESHOLD \
                   else "HIGH"
        title = f"⚠️ {severity}: {child_name} is frustrated"
        body  = (
            f"Frustration score: {score:.0f}/100"
            + (f" playing {game_name}" if game_name else "")
        )
        self.send_push(
            parent_fcm_token, title, body,
            data={
                "type":  "frustration_alert",
                "score": str(score)
            }
        )

    def notify_night_gaming(
        self,
        parent_fcm_token: str,
        child_name:       str,
        game_name:        str = None
    ):
        """Notify parent — night gaming detected."""
        title = f"🌙 {child_name} is gaming late at night"
        body  = (
            f"Gaming detected after "
            f"{settings.NIGHT_START_HOUR}:00"
            + (f" — {game_name}" if game_name else "")
        )
        self.send_push(
            parent_fcm_token, title, body,
            data={"type": "night_session"}
        )

    def notify_addiction_risk(
        self,
        parent_fcm_token: str,
        child_name:       str,
        hours:            float,
        risk_score:       float
    ):
        """Notify parent — excessive gaming."""
        title = f"📱 {child_name} has been gaming for {hours:.1f} hours"
        body  = f"Addiction risk score: {risk_score:.0f}/100"
        self.send_push(
            parent_fcm_token, title, body,
            data={
                "type":       "addiction_risk",
                "risk_score": str(risk_score)
            }
        )

    # ============================================
    # STUDENT NOTIFICATION
    # Secondary signal only — primary is ON-DEVICE
    # ============================================
    def notify_student_overlay(
        self,
        student_fcm_token: str,
        score:             float,
        severity:          str
    ):
        """
        Send secondary overlay trigger to student device via FCM.
        Primary student warning is ON-DEVICE (no round trip needed).
        This is a backup/confirmation signal only.
        Android DetectionService handles the actual vignette/sound.
        """
        self.send_push(
            student_fcm_token,
            title="frustradar_internal",
            body="overlay_trigger",
            data={
                "type":     "show_overlay",
                "score":    str(score),
                "severity": severity
            }
        )