import sendgrid
from sendgrid.helpers.mail import Mail
from app.core.config import settings


def send_email(
    to_email: str,
    subject: str,
    html_content: str
) -> bool:
    """
    Send email using SendGrid
    Returns True if successful
    """
    if not settings.SENDGRID_API_KEY:
        print("SendGrid API key not set — skipping email")
        return False

    try:
        sg = sendgrid.SendGridAPIClient(api_key=settings.SENDGRID_API_KEY)
        message = Mail(
            from_email=settings.FROM_EMAIL,
            to_emails=to_email,
            subject=subject,
            html_content=html_content
        )
        response = sg.send(message)
        print(f"Email sent to {to_email}: {response.status_code}")
        return True

    except Exception as e:
        print(f"Email failed: {e}")
        return False


def send_frustration_alert_email(
    parent_email: str,
    child_name: str,
    score: float,
    game_name: str = None
):
    """Send frustration alert email to parent"""
    subject = f"⚠️ FrustraDAR Alert — {child_name} is frustrated"
    html = f"""
    <div style="font-family: Arial; padding: 20px;">
        <h2 style="color: #e74c3c;">⚠️ Frustration Alert</h2>
        <p><strong>{child_name}</strong> is showing high frustration levels.</p>
        <div style="background: #f8f9fa; padding: 15px; border-radius: 8px;">
            <p><strong>Frustration Score:</strong> {score:.1f} / 100</p>
            {"<p><strong>Game:</strong> " + game_name + "</p>" if game_name else ""}
        </div>
        <p>Consider checking in with them or suggesting a break.</p>
        <p style="color: #95a5a6; font-size: 12px;">
            — FrustraDAR Monitoring System
        </p>
    </div>
    """
    send_email(parent_email, subject, html)


def send_weekly_report_email(
    parent_email: str,
    child_name: str,
    report: dict
):
    """Send weekly gaming report to parent"""

    current = report["current"]

    subject = f"📊 FrustraDAR Weekly Report — {child_name}"

    html = f"""
    <div style="font-family: Arial; padding: 20px;">
        <h2>📊 Weekly Gaming Report — {child_name}</h2>

        <p>Week: {report['week_start']} to {report['week_end']}</p>

        <div style="background: #f8f9fa; padding: 15px; border-radius: 8px;">
            <p>
                <strong>Total Play Time:</strong>
                {current['total_play_time_min'] // 60}h
                {current['total_play_time_min'] % 60}m
            </p>

            <p>
                <strong>Total Sessions:</strong>
                {current['total_sessions']}
            </p>

            <p>
                <strong>Avg Frustration:</strong>
                {current.get('avg_frustration_score', 'N/A')}
            </p>

            <p>
                <strong>Peak Frustration:</strong>
                {current.get('max_frustration_score', 'N/A')}
            </p>

            <p>
                <strong>Night Sessions:</strong>
                {current['night_session_count']}
            </p>
        </div>

        <p style="color: #95a5a6; font-size: 12px;">
            — FrustraDAR
        </p>
    </div>
    """

    send_email(parent_email, subject, html)