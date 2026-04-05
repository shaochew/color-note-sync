import json
import logging
import os
import uuid
from datetime import datetime, timezone

import requests as http_requests
from flask import Flask, jsonify, redirect, render_template, request, url_for
from flask_cors import CORS

from models import Note, NoteItem, db

app = Flask(__name__)
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///notes.db"
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False

db.init_app(app)
CORS(app)

with app.app_context():
    db.create_all()

GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
GIST_ID = os.environ.get("GIST_ID", "")

log = logging.getLogger(__name__)


def parse_iso_datetime(s):
    """Parse ISO 8601 string to naive UTC datetime."""
    if not s:
        return datetime.now(timezone.utc).replace(tzinfo=None)
    s = s.replace("Z", "+00:00")
    dt = datetime.fromisoformat(s)
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt


# ---------------------------------------------------------------------------
# Gist backup / restore
# ---------------------------------------------------------------------------

def _gist_headers():
    return {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json",
    }


def backup_to_gist():
    """Save current DB state to GitHub Gist."""
    if not GITHUB_TOKEN or not GIST_ID:
        return
    try:
        notes = Note.query.order_by(Note.updated_at.desc()).all()
        data = json.dumps({"notes": [n.to_dict() for n in notes]}, indent=2)
        resp = http_requests.patch(
            f"https://api.github.com/gists/{GIST_ID}",
            headers=_gist_headers(),
            json={"files": {"notes_backup.json": {"content": data}}},
            timeout=15,
        )
        if resp.ok:
            log.info("Gist backup successful")
        else:
            log.warning("Gist backup failed: %s %s", resp.status_code, resp.text[:200])
    except Exception as e:
        log.warning("Gist backup error: %s", e)


def restore_from_gist():
    """Restore DB from GitHub Gist if DB is empty."""
    if not GITHUB_TOKEN or not GIST_ID:
        return
    try:
        note_count = Note.query.count()
        if note_count > 0:
            log.info("DB has %d notes, skipping gist restore", note_count)
            return

        resp = http_requests.get(
            f"https://api.github.com/gists/{GIST_ID}",
            headers=_gist_headers(),
            timeout=15,
        )
        if not resp.ok:
            log.warning("Gist fetch failed: %s", resp.status_code)
            return

        gist_data = resp.json()
        file_content = gist_data.get("files", {}).get("notes_backup.json", {}).get("content", "")
        if not file_content:
            log.info("Gist is empty, nothing to restore")
            return

        data = json.loads(file_content)
        incoming_notes = data.get("notes", [])
        if not incoming_notes:
            return

        for cn in incoming_notes:
            note = Note(
                id=cn.get("id", str(uuid.uuid4())),
                title=cn.get("title", ""),
                color=cn.get("color", "#FFEB3B"),
                updated_at=parse_iso_datetime(cn.get("updated_at")),
                created_at=parse_iso_datetime(cn.get("created_at")),
            )
            db.session.add(note)
            for ci in cn.get("items", []):
                item = NoteItem(
                    id=ci.get("id", str(uuid.uuid4())),
                    note_id=note.id,
                    text=ci.get("text", ""),
                    is_done=ci.get("is_done", False),
                    sort_order=ci.get("sort_order", 0),
                    created_at=parse_iso_datetime(ci.get("created_at")),
                )
                db.session.add(item)

        db.session.commit()
        log.info("Restored %d notes from gist", len(incoming_notes))
    except Exception as e:
        log.warning("Gist restore error: %s", e)
        db.session.rollback()


with app.app_context():
    restore_from_gist()


# ---------------------------------------------------------------------------
# Web routes (placeholders)
# ---------------------------------------------------------------------------

@app.route("/")
def index():
    return redirect(url_for("notes_page"))


@app.route("/notes")
def notes_page():
    return render_template("notes.html")


@app.route("/notes/<note_id>")
def note_detail_page(note_id):
    return render_template("note_detail.html", note_id=note_id)


# ---------------------------------------------------------------------------
# API — Notes
# ---------------------------------------------------------------------------

@app.route("/api/notes", methods=["GET"])
def get_notes():
    notes = Note.query.order_by(Note.updated_at.desc()).all()
    return jsonify([n.to_dict() for n in notes]), 200


@app.route("/api/notes/<note_id>", methods=["GET"])
def get_note(note_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    return jsonify(note.to_dict()), 200


@app.route("/api/notes", methods=["POST"])
def create_note():
    data = request.get_json(silent=True) or {}
    note = Note(
        id=str(uuid.uuid4()),
        title=data.get("title", ""),
        color=data.get("color", "#FFEB3B"),
    )
    db.session.add(note)
    db.session.commit()
    return jsonify(note.to_dict()), 201


@app.route("/api/notes/<note_id>", methods=["PUT"])
def update_note(note_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    data = request.get_json(silent=True) or {}
    if "title" in data:
        note.title = data["title"]
    if "color" in data:
        note.color = data["color"]
    note.updated_at = datetime.now(timezone.utc)
    db.session.commit()
    return jsonify(note.to_dict()), 200


@app.route("/api/notes/<note_id>", methods=["DELETE"])
def delete_note(note_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    db.session.delete(note)
    db.session.commit()
    return jsonify({"message": "Note deleted"}), 200


# ---------------------------------------------------------------------------
# API — Note Items
# ---------------------------------------------------------------------------

@app.route("/api/notes/<note_id>/items", methods=["POST"])
def create_item(note_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    data = request.get_json(silent=True) or {}
    item = NoteItem(
        id=str(uuid.uuid4()),
        note_id=note_id,
        text=data.get("text", ""),
        sort_order=data.get("sort_order", 0),
    )
    db.session.add(item)
    note.updated_at = datetime.now(timezone.utc)
    db.session.commit()
    return jsonify(item.to_dict()), 201


@app.route("/api/notes/<note_id>/items/<item_id>", methods=["PUT"])
def update_item(note_id, item_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    item = db.session.get(NoteItem, item_id)
    if not item or item.note_id != note_id:
        return jsonify({"error": "Item not found"}), 404
    data = request.get_json(silent=True) or {}
    if "text" in data:
        item.text = data["text"]
    if "is_done" in data:
        item.is_done = data["is_done"]
    if "sort_order" in data:
        item.sort_order = data["sort_order"]
    note.updated_at = datetime.now(timezone.utc)
    db.session.commit()
    return jsonify(item.to_dict()), 200


@app.route("/api/notes/<note_id>/items/<item_id>", methods=["DELETE"])
def delete_item(note_id, item_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    item = db.session.get(NoteItem, item_id)
    if not item or item.note_id != note_id:
        return jsonify({"error": "Item not found"}), 404
    db.session.delete(item)
    note.updated_at = datetime.now(timezone.utc)
    db.session.commit()
    return jsonify({"message": "Item deleted"}), 200


@app.route("/api/notes/<note_id>/items/reorder", methods=["PUT"])
def reorder_items(note_id):
    note = db.session.get(Note, note_id)
    if not note:
        return jsonify({"error": "Note not found"}), 404
    data = request.get_json(silent=True) or []
    for entry in data:
        item = db.session.get(NoteItem, entry.get("id"))
        if item and item.note_id == note_id:
            item.sort_order = entry.get("sort_order", item.sort_order)
    note.updated_at = datetime.now(timezone.utc)
    db.session.commit()
    return jsonify(note.to_dict()), 200


# ---------------------------------------------------------------------------
# API — Sync
# ---------------------------------------------------------------------------

@app.route("/api/sync", methods=["POST"])
def sync():
    try:
        data = request.get_json(silent=True) or {}
        client_notes = data.get("notes", [])

        for cn in client_notes:
            client_updated = parse_iso_datetime(cn.get("updated_at"))
            client_created = parse_iso_datetime(cn.get("created_at"))
            server_note = db.session.get(Note, cn.get("id"))

            if server_note:
                server_updated = server_note.updated_at
                if server_updated.tzinfo is not None:
                    server_updated = server_updated.astimezone(timezone.utc).replace(tzinfo=None)
                if server_updated > client_updated:
                    # Server is newer — keep server version
                    continue

            # Client is newer or note doesn't exist — replace with client version
            if server_note:
                # Delete existing items
                NoteItem.query.filter_by(note_id=server_note.id).delete()
                server_note.title = cn.get("title", "")
                server_note.color = cn.get("color", "#FFEB3B")
                server_note.updated_at = client_updated
                server_note.created_at = client_created
            else:
                server_note = Note(
                    id=cn.get("id", str(uuid.uuid4())),
                    title=cn.get("title", ""),
                    color=cn.get("color", "#FFEB3B"),
                    updated_at=client_updated,
                    created_at=client_created,
                )
                db.session.add(server_note)

            # Add client items
            for ci in cn.get("items", []):
                item = NoteItem(
                    id=ci.get("id", str(uuid.uuid4())),
                    note_id=server_note.id,
                    text=ci.get("text", ""),
                    is_done=ci.get("is_done", False),
                    sort_order=ci.get("sort_order", 0),
                    created_at=parse_iso_datetime(ci.get("created_at")),
                )
                db.session.add(item)

        db.session.commit()
        backup_to_gist()

        # Return full server state
        notes = Note.query.order_by(Note.updated_at.desc()).all()
        return jsonify({"notes": [n.to_dict() for n in notes]}), 200
    except Exception as e:
        db.session.rollback()
        return jsonify({"error": str(e)}), 500


# ---------------------------------------------------------------------------
# API — Export / Import
# ---------------------------------------------------------------------------

@app.route("/api/export", methods=["GET"])
def export_data():
    notes = Note.query.order_by(Note.updated_at.desc()).all()
    return jsonify({"notes": [n.to_dict() for n in notes]}), 200


@app.route("/api/import", methods=["POST"])
def import_data():
    data = request.get_json(silent=True) or {}
    incoming_notes = data.get("notes", [])

    # Clear all existing data
    NoteItem.query.delete()
    Note.query.delete()

    for cn in incoming_notes:
        note = Note(
            id=cn.get("id", str(uuid.uuid4())),
            title=cn.get("title", ""),
            color=cn.get("color", "#FFEB3B"),
            updated_at=datetime.fromisoformat(cn["updated_at"].replace("Z", "")) if cn.get("updated_at") else datetime.now(timezone.utc),
            created_at=datetime.fromisoformat(cn["created_at"].replace("Z", "")) if cn.get("created_at") else datetime.now(timezone.utc),
        )
        db.session.add(note)
        for ci in cn.get("items", []):
            item = NoteItem(
                id=ci.get("id", str(uuid.uuid4())),
                note_id=note.id,
                text=ci.get("text", ""),
                is_done=ci.get("is_done", False),
                sort_order=ci.get("sort_order", 0),
                created_at=datetime.fromisoformat(ci["created_at"].replace("Z", "")) if ci.get("created_at") else datetime.now(timezone.utc),
            )
            db.session.add(item)

    db.session.commit()
    return jsonify({"message": "Import successful", "count": len(incoming_notes)}), 200


# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    app.run(debug=True, port=5001)
