from sqlalchemy import create_engine      # Creates the SQLAlchemy Engine (connection manager)
from sqlalchemy.ext.declarative import declarative_base   # Creates the Base class for all database models
from sqlalchemy.orm import sessionmaker   # Creates Session objects (used for CRUD operations)
from app.core.config import settings      # Imports configuration from config.py (.env values)

# ------------------------------------------------------------------
# ENGINE
# ------------------------------------------------------------------
# Engine is the gateway between FastAPI and PostgreSQL.
# It uses settings.DATABASE_URL and internally uses psycopg2
# to establish database connections.
engine = create_engine(
    settings.DATABASE_URL,    # Connection string loaded from .env

    # Before giving a connection from the pool,
    # SQLAlchemy checks if it is still alive.
    # Prevents "stale connection" errors.
    pool_pre_ping=True,

    # Keep up to 10 database connections ready.
    # Reusing connections is much faster than creating a new one every request.
    pool_size=10,

    # If all 10 are busy, SQLAlchemy can temporarily create
    # up to 20 additional connections.
    max_overflow=20
)

# ------------------------------------------------------------------
# SESSION FACTORY
# ------------------------------------------------------------------
# SessionLocal is NOT a database session.
# It is a factory that creates NEW sessions whenever we call SessionLocal().
SessionLocal = sessionmaker(

    # SQLAlchemy will not automatically commit changes.
    # We decide when to commit.
    autocommit=False,

    # SQLAlchemy will not automatically send pending changes
    # before every query.
    # We explicitly commit/flush when needed.
    autoflush=False,

    # Every session created by SessionLocal uses this engine.
    bind=engine
)

# ------------------------------------------------------------------
# BASE MODEL
# ------------------------------------------------------------------
# Every database model (User, Session, Score, etc.)
# will inherit from Base.
#
# Example:
# class User(Base):
#     __tablename__ = "users"
#
Base = declarative_base()

# ------------------------------------------------------------------
# DATABASE DEPENDENCY
# ------------------------------------------------------------------
def get_db():
    """
    Creates one database session for each API request.

    Flow:
        Request Starts
              ↓
        SessionLocal() creates a Session
              ↓
        Route performs CRUD operations
              ↓
        Response sent
              ↓
        Session automatically closes

    This prevents database connection leaks.
    """

    # Create a new database session
    db = SessionLocal()

    try:
        # Give the session to the API route
        yield db

    finally:
        # Always close the session,
        # even if an exception occurs.
        db.close()