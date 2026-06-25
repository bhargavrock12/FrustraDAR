from app.core.config import settings


def score_to_level(score: float) -> str:
    """
    Convert fusion score to frustration level string.
    Uses configured thresholds — not hardcoded values.

    Levels:
        calm     → below mild threshold
        mild     → score > 0 but below high threshold / 2
        moderate → score >= high_threshold / 2
        high     → score >= FRUSTRATION_HIGH_THRESHOLD
        critical → score >= FRUSTRATION_CRITICAL_THRESHOLD
    """
    if score >= settings.FRUSTRATION_CRITICAL_THRESHOLD:
        return "critical"

    if score >= settings.FRUSTRATION_HIGH_THRESHOLD:
        return "high"

    # Moderate = halfway to high threshold
    moderate_threshold = settings.FRUSTRATION_HIGH_THRESHOLD / 2

    if score >= moderate_threshold:
        return "moderate"

    if score > 0:
        return "mild"

    return "calm"