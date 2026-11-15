"""Migration configuration; secrets are never put in alembic.ini."""

from alembic import context
from sqlalchemy import create_engine, pool

from app.config import DatabaseSettings
from app.models.tables import metadata
from app.persistence import database_url

url = context.config.attributes.get("database_url") or database_url(DatabaseSettings())
if context.is_offline_mode():
    context.configure(url=url, target_metadata=metadata, literal_binds=True)
    with context.begin_transaction():
        context.run_migrations()
else:
    engine = create_engine(url, poolclass=pool.NullPool, hide_parameters=True)
    with engine.connect() as connection:
        context.configure(connection=connection, target_metadata=metadata)
        with context.begin_transaction():
            context.run_migrations()
