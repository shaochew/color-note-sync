# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**color-note-sync** is a ColorNote-style checklist app with two components:
- **Android App** (`android/`) — Kotlin + XML Views, Room DB, Retrofit for sync
- **Backend** (`backend/`) — Python Flask + SQLAlchemy + SQLite, Jinja2 web UI

Notes are color-coded checklists. Items can be checked off (strikethrough), reordered via drag-and-drop, and synced between devices via a manual sync button.

## Architecture

```
Android App (Kotlin)          Flask Backend (Python)
├── Room SQLite (local)       ├── SQLAlchemy + SQLite
├── Retrofit HTTP client  ──► ├── REST API (/api/*)
├── Activities + XML Views    ├── Jinja2 Web UI (/notes)
└── Home screen widgets       └── Hosted on Render (free)
```

Sync is manual (user-initiated), last-write-wins based on `updated_at` timestamps.

## Build & Run

### Backend
```bash
cd backend
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python app.py                    # dev server on :5001
```
Production: `gunicorn app:app --bind 0.0.0.0:$PORT`

### Android
Requires Android Studio or Gradle with Android SDK.
```bash
cd android
./gradlew assembleDebug          # build debug APK → app/build/outputs/apk/debug/
./gradlew installDebug           # install on connected device/emulator
```
Note: Generate Gradle wrapper first if missing: `gradle wrapper` (requires Gradle 8.2+)

## API Endpoints

All under `/api/`:
- `GET|POST /notes` — list/create notes
- `GET|PUT|DELETE /notes/<id>` — single note CRUD
- `POST /notes/<id>/items` — add item
- `PUT|DELETE /notes/<id>/items/<item_id>` — update/delete item
- `PUT /notes/<id>/items/reorder` — batch reorder `[{id, sort_order}]`
- `POST /sync` — bulk sync (last-write-wins)
- `GET /export`, `POST /import` — full data backup/restore as JSON

## Key Design Decisions

- **Port 5001** for local dev (macOS port 5000 conflicts with AirPlay Receiver)
- **SQLite on both sides** — no external DB service needed, zero hosting cost
- **Android emulator** uses `http://10.0.2.2:5001` to reach host machine's backend
- **Widget** uses a single `AppWidgetProvider` with `resizeMode="horizontal|vertical"` (user resizes to 2x2, 2x4, or 4x4)
- **Render free tier**: web service sleeps after 15 min inactivity; SQLite may reset on redeploy — use export/import for backup

## Deployment

Backend deploys to Render via `backend/render.yaml`. Connect GitHub repo at https://dashboard.render.com, point to the `backend/` directory.
