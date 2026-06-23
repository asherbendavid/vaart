# Vaart 🚗

A GPS-based speedometer and trip computer for Android, built in Kotlin.

**Vaart** (Afrikaans: *speed, momentum*) started as two parallel learning projects: replacing a useful car app whose ads became intolerable, and practising real-world Git and GitHub workflows on an Android project from day one.

---

## Current Features (v2.2.0, Phase 5a complete)

- **Live speed display** — GPS-based, landscape locked, screen always on
- **GPS accuracy indicator** — Good / Fair / Weak with colour coding
- **Battery indicator** — segmented fuel-gauge style display next to the GPS indicator; green/orange/red tiers by charge level, blue while charging, red pulsing animation below 25% when not charging
- **On-screen clock**
- **Odometer** — persistent, zero-padded six-digit display with thousands separator, per-vehicle
- **Trip A — this journey** — distance, moving time, average speed, max speed. Auto-clears after 30 minutes of inactivity. Manual reset available. Tracked independently from the finalised trip record, so mid-trip resets or carry-over never corrupt history.
- **Trip B — since refuel** — same metrics, manual reset only, persists across app restarts, per-vehicle, immediately written to the database on reset
- **HUD mirror mode** — flips entire display horizontally for windscreen (or daytime perspex reflector) projection, disables reset/menu buttons while active
- Moving time pauses automatically after 3 minutes below threshold speed
- Snap-to-zero below 1 km/h to suppress GPS jitter; moving threshold tuned to 2.5 km/h to correctly register walking pace
- **Multi-vehicle support**
  - Vehicle profiles stored in Room DB (name, optional registration, optional notes, manual odometer correction)
  - Each vehicle has its own odometer and Trip B
  - Anonymous mode (default on first launch) behaves identically, data lives only in SharedPreferences
  - Vehicle selector menu with sort-by-most-recently-used
  - Odometer confirmation prompt on vehicle switch, with inline correction
  - Anonymous data can be carried over when creating a new vehicle
  - Vehicle switching disabled during an active session
  - Manage vehicles screen: edit details (name/registration/notes/odometer) or delete, with optional plain-text export before removal
  - Standalone odometer correction via long-press, outside of an active session
  - Session-end summary dialog showing the just-completed trip's stats
- **Trip history**
  - Every session is logged as a `TripRecord` with full route (`TripPoint` list) once stopped
  - Dedicated portrait history screen, dark themed, listing all recorded trips with vehicle, date, distance, duration, average/max speed
  - Tapping a trip opens a route map (OSMDroid) with the recorded path drawn as a polyline, auto-zoomed to fit
  - Gracefully handles trips with no stored route points (e.g. interrupted sessions) with a clear explanatory message instead of a blank/broken map
- **Overspeed alert**
  - Speed display turns red and a coloured line appears under the speed-limit sign when the live limit is exceeded
  - Audible 3-bong burst (alarm-channel volume, independent of media volume), rate-limited to once per 60 seconds regardless of how often the threshold is crossed
  - Fully session-gated — no alerts fire unless a trip is actively being recorded
- **Live speed limit signs (OpenStreetMap Overpass API)**
  - Maximum speed sign (white/red, right of the speed display) populated from real OSM road data
  - Minimum speed sign (blue/white, left of the speed display) shown only where OSM minimum-speed data exists for that road
  - Local tile-based caching (~2 km tiles, 30-day expiry) — most driving happens from cache, not a live network call
  - Automatic pre-fetch of neighbouring tiles when near a cached-area boundary, so coverage extends ahead of you before you need it
  - Single amber chime (not a repeating alarm) and amber speed text when travelling below a posted minimum — deliberately non-repeating, since dropping below a freeway minimum in heavy traffic is common and shouldn't nag
  - Shows "--" rather than a guessed value when no speed-limit data exists for the current road — no alert fires on unknown roads
  - Confirmed via real-world driving; one known limitation found so far — nearest-road matching can occasionally "adopt" a side road's speed limit near junctions (tracked for fixing, see Phase 6 below)
- **Battery-aware service lifecycle** — the GPS foreground service now stops entirely when the app loses focus and no trip is active, instead of running indefinitely in the background; it resumes seamlessly when reopened, and stays alive throughout backgrounding only while a session is genuinely in progress (e.g. while using Waze or Maps mid-trip)
- **Settings screen** — accessible from the main menu, built on `androidx.preference`, sharing the same `vaart_prefs` SharedPreferences file as the rest of the app

---

## Companion Tools

- **GPX Location Spoofer** — a separate, minimal testing app (`cvc.dashingdog.locationspoofer`) that plays back a GPX route as mock GPS input, used to test Vaart's speed-limit matching and alert behaviour against repeatable routes/speeds without needing to physically drive them (including deliberately-too-slow stretches that can't safely be tested in real traffic)

---

## Roadmap

### Phase 1–3 — Core speedometer, trip counters, multi-vehicle support ✅ Complete

### Phase 4 — Alerts and History ✅ Complete
- [x] Trip history (Room database, list + map screens, per-trip route recording)
- [x] Overspeed alert (colour change + sound, session-gated)
- [x] Live speed limit data via OpenStreetMap Overpass API, with local tile caching
- [x] Minimum speed sign and underspeed alert

### Phase 5a — Main Screen UI Additions ✅ Complete
- [x] On-screen clock
- [x] Battery level indicator (segmented, tiered colour, charging state, low-battery pulse)
- [x] Settings entry point added to the existing main menu, alongside Trip History

### Phase 5b — Settings Population (In Progress)
- [x] Speed unit setting — Follow region / km/h / mph (manual 3-way choice; "Follow region" currently falls back to device locale, automatic GPS-based region detection is planned for Phase 6)
- [ ] Battery percentage text show/hide toggle
- [ ] Hide system status bar option
- [ ] Selectable alert audio channel (alarm/notification/ringtone/media)
- [ ] Overspeed alert grace margin (configurable buffer before the alert fires)
- [ ] Reverse landscape orientation option
- [ ] Speed sign opacity setting (dimmed when within limits, full opacity when breached) and a historic 1990s-style colour scheme option

### Phase 6 — Speed Limit Hardening (Planned)
- [ ] Nearest-segment matching instead of nearest-point (more accurate distance calculation against a road's actual geometry)
- [ ] Bearing/heading comparison to avoid matching against crossing or adjacent roads
- [ ] Hysteresis — require a new road match to persist across more than one GPS fix before switching the displayed limit, to filter out momentary junction mismatches
- [ ] Road classification weighting as a tiebreaker (prefer matches of the same road class as the currently-matched way)
- [ ] Verification pass on South African `maxspeed` OSM tag formats, hardening the parser if non-numeric or unit-suffixed tags are found in practice
- [ ] Automatic region detection for the speed unit setting, using a small bundled GeoJSON dataset of mph-using countries with a point-in-polygon check (replacing the Phase 5b locale-based fallback)

### Phase 7 — Trip Data Integrity (Planned)
- [ ] "Pin Trip A" toggle for long road trips — bypasses the 30-minute idle expiry, with a passive on-screen indicator and a confirmation step before any reset while pinned
- [ ] Vehicle correction from the session summary screen — lets a session started under the wrong vehicle be reassigned after the fact (selection menu, confirmation prompt, and verification that data is committed to the correct record)
- [ ] Trip counter resets (Trip A/B) logged as their own history entries, giving a complete refuel-to-refuel distance log
- [ ] Delete individual trip records from the history screen

### Phase 8 — "Forgot to Press Start" Nudge (Planned)
- [ ] While the app is in the foreground and no session is active, detect sustained speed above a configurable threshold and prompt the user with a sound and a pulsing Start button

### Phase 9 — GPX Export (Planned)
- [ ] GPX export from the trip map screen

### Phase 10 — Picture-in-Picture (Planned)
- [ ] PiP mode while a session is active, for use alongside Waze/Google Maps (contents and Back-button behaviour still to be finalised)

### Phase 11 — Secondary Display Widget (Planned)
- [ ] Swipeable secondary display area — music (Spotify/generic MediaSession), weather, or other widgets (placement and content still to be finalised)

### Final Polish (Planned, deliberately last)
- [ ] Help screen
- [ ] About screen

### Future — Optimisation
- [ ] Further GPS battery tuning — lower-frequency location updates when idle outside of a session

---

## Technical Notes

- **Min SDK:** API 29 (Android 10)
- **Location:** FusedLocationProviderClient (Google Play Services), via a Foreground Service
- **Background tracking:** Foreground Service with persistent notification; now lifecycle-tied to focus + active session state rather than running indefinitely
- **Mapping:** OSMDroid (no API key required) for trip route display
- **Speed limit data:** OpenStreetMap, queried via the Overpass API, cached locally in Room with a tile-based system to minimise live network calls
- **Settings:** `androidx.preference`, backed by the same `vaart_prefs` SharedPreferences file used elsewhere in the app
- **Persistence:**
  - SharedPreferences (`vaart_prefs`) — the live source of truth during an active session: anonymous vehicle data, active Trip A/B, active vehicle reference, app settings
  - Room DB — named vehicle profiles, trip records and recorded routes, cached speed-limit road data; vehicle/trip data is a periodic snapshot synced at specific moments (switch, reset, edit, create), not continuously
- **Package:** cvc.dashingdog.vaart

---

## Package

`cvc.dashingdog.vaart`

Part of the [dashingdog](https://github.com/asherbendavid) personal app family.
