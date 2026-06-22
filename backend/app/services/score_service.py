from sqlalchemy.orm import Session
from sqlalchemy import desc
from fastapi import HTTPException, status
from datetime import datetime, timedelta
from typing import List, Optional
from uuid import UUID
from app.models.score import FrustrationScore
from app.models.session import GameSession
from app.models.user import User
from app.schemas.score import ScoreBatchCreate


class ScoreService:
    def __init__(self, db: Session):
        self.db = db

    # ============================================
    # UPLOAD BATCH SCORES
    # Returns (score_objects, max_score, latest_score)
    # Pipeline is triggered by the route, not here
    # ============================================
    def upload_batch(
        self,
        user:  User,
        batch: ScoreBatchCreate
    ) -> dict:
        """
        Persist batch of frustration scores.
        Returns result dict for pipeline orchestration.
        Pipeline (alerts/WS/FCM) is handled by EventPipeline in the route.
        """

        # Verify session belongs to user
        session = self.db.query(GameSession).filter(
            GameSession.id      == batch.session_id,
            GameSession.user_id == user.id
        ).first()

        if not session:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Session not found"
            )

        # Build score objects
        score_objects = []
        for score_data in batch.scores:
            score = FrustrationScore(
                user_id=user.id,
                session_id=batch.session_id,
                timestamp=score_data.timestamp,
                facial_score=score_data.facial_score,
                audio_score=score_data.audio_score,
                motion_score=score_data.motion_score,
                behavior_score=score_data.behavior_score,
                fusion_score=score_data.fusion_score,
                signals_used=score_data.signals_used,
                window_duration_sec=score_data.window_duration_sec
            )
            score_objects.append(score)

        # Persist
        self.db.bulk_save_objects(score_objects)
        self.db.commit()

        # Re-query to get DB-assigned IDs (bulk_save doesn't refresh)
        latest_score = self.db.query(FrustrationScore).filter(
            FrustrationScore.user_id    == user.id,
            FrustrationScore.session_id == batch.session_id
        ).order_by(desc(FrustrationScore.timestamp)).first()

        max_score = max(s.fusion_score for s in batch.scores)

        return {
            "uploaded":     len(score_objects),
            "max_score":    max_score,
            "latest_score": latest_score,
            "session":      session
        }

    # ============================================
    # QUERIES
    # ============================================
    def get_latest_scores(
        self,
        user_id: UUID,
        limit:   int = 20
    ) -> List[FrustrationScore]:
        return self.db.query(FrustrationScore).filter(
            FrustrationScore.user_id == user_id
        ).order_by(
            desc(FrustrationScore.timestamp)
        ).limit(limit).all()

    def get_session_scores(
        self,
        session_id: UUID,
        user_id:    UUID
    ) -> List[FrustrationScore]:
        return self.db.query(FrustrationScore).filter(
            FrustrationScore.session_id == session_id,
            FrustrationScore.user_id    == user_id
        ).order_by(FrustrationScore.timestamp).all()

    def get_trend_data(
        self,
        user_id: UUID,
        days:    int = 7
    ) -> List[dict]:
        since = datetime.utcnow() - timedelta(days=days)

        scores = self.db.query(FrustrationScore).filter(
            FrustrationScore.user_id   == user_id,
            FrustrationScore.timestamp >= since
        ).order_by(FrustrationScore.timestamp).all()

        return [
            {
                "timestamp":    s.timestamp.isoformat(),
                "fusion_score": s.fusion_score,
                "game_name":    s.session.game_name if s.session else None
            }
            for s in scores
        ]

    def get_today_stats(self, user_id: UUID) -> dict:
        today_start = datetime.utcnow().replace(
            hour=0, minute=0, second=0, microsecond=0
        )

        scores = self.db.query(FrustrationScore).filter(
            FrustrationScore.user_id   == user_id,
            FrustrationScore.timestamp >= today_start
        ).all()

        if not scores:
            return {"avg_score": None, "max_score": None, "score_count": 0}

        fusion_scores = [s.fusion_score for s in scores]
        return {
            "avg_score":   round(sum(fusion_scores) / len(fusion_scores), 2),
            "max_score":   max(fusion_scores),
            "score_count": len(fusion_scores)
        }