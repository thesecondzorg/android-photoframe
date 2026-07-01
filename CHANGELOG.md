# Changelog

## v1.1 (versionCode 2) — 2026-07-01

### New Features
- **Night/Dim Schedule** — set a time window and brightness level from the admin page; the photo frame dims automatically (supports overnight ranges, e.g. 22:00 → 07:00)
- **Configurable slide duration** — choose how many seconds each photo is displayed (3–120 s) from the admin page
- **OTA Updates** — future app updates can be downloaded and installed directly from the admin page without a USB cable

### Bug Fixes
- Fixed slideshow permanently stalling when a photo file fails to load (broken image now skipped after 2 s)
- Fixed Ken Burns animation clipping — animation duration now matches the slide interval exactly
- Fixed screen locking after extended idle periods — foreground service + keyguard bypass keeps the display on indefinitely

### Performance
- App startup is significantly faster — thumbnail cache is no longer wiped on every boot; thumbnails are only regenerated when the source photo changes
- Metadata cache saves happen on a background thread, removing a blocking I/O stall from photo list responses
- Photo list polling interval increased from 3 s to 15 s (reduces unnecessary network load)
- Clock widget updated every 10 s instead of every 1 s

### Security & Reliability
- 50 MB file size limit on uploads (prevents storage exhaustion)
- Settings fields are validated and sanitised before being written to disk
- All API JSON responses now use `JSONObject`/`JSONArray` (eliminates potential injection from filenames with special characters)
- Modern `WindowInsetsController` API used on Android 11+ for fullscreen mode

---

## v1.0 (versionCode 1) — 2026-06-24

- Initial release
