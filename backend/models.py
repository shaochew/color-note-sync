import uuid
from datetime import datetime, timezone

from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()


class Note(db.Model):
    __tablename__ = "notes"

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title = db.Column(db.String(255), nullable=False, default="")
    color = db.Column(db.String(7), nullable=False, default="#FFEB3B")
    updated_at = db.Column(db.DateTime, nullable=False, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None), onupdate=lambda: datetime.now(timezone.utc).replace(tzinfo=None))
    created_at = db.Column(db.DateTime, nullable=False, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    items = db.relationship(
        "NoteItem",
        backref="note",
        lazy=True,
        order_by="NoteItem.sort_order",
        cascade="all, delete-orphan",
    )

    def to_dict(self):
        return {
            "id": self.id,
            "title": self.title,
            "color": self.color,
            "updated_at": self.updated_at.isoformat() + "Z",
            "created_at": self.created_at.isoformat() + "Z",
            "items": [item.to_dict() for item in self.items],
        }


class NoteItem(db.Model):
    __tablename__ = "note_items"

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    note_id = db.Column(db.String(36), db.ForeignKey("notes.id"), nullable=False)
    text = db.Column(db.String(1000), nullable=False, default="")
    is_done = db.Column(db.Boolean, nullable=False, default=False)
    sort_order = db.Column(db.Integer, nullable=False, default=0)
    created_at = db.Column(db.DateTime, nullable=False, default=lambda: datetime.now(timezone.utc).replace(tzinfo=None))

    def to_dict(self):
        return {
            "id": self.id,
            "note_id": self.note_id,
            "text": self.text,
            "is_done": self.is_done,
            "sort_order": self.sort_order,
            "created_at": self.created_at.isoformat() + "Z",
        }
