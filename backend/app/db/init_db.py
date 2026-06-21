from app.db.database import engine, Base

# Import all models so Base knows about them
from app.models.user import User
from app.models.session import GameSession
from app.models.score import FrustrationScore
from app.models.alert import Alert
from app.models.daily_summary import DailySummary


def init_db():
    """
    Create all tables in database
    Run this once when starting the app
    """
    print("Creating database tables...")
    Base.metadata.create_all(bind=engine)
    print("✅ Database tables created successfully!")


def drop_db():
    """
    Drop all tables — USE ONLY IN DEVELOPMENT
    """
    print("Dropping all tables...")
    Base.metadata.drop_all(bind=engine)
    print("✅ All tables dropped!")


if __name__ == "__main__":
    init_db()