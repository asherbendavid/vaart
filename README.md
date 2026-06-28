# Vaart 🚗

A GPS-based speedometer and trip computer for Android, built in Kotlin.

**Vaart** (Afrikaans: *speed, momentum*) started as two parallel learning projects: replacing a useful car app whose ads became intolerable, and practising real-world Git and GitHub workflows on an Android project from day one.

---

## Current Features (v2.2.0+, Phase 5b complete)

- **Live speed display** — GPS-based, landscape locked, screen always on
- **GPS accuracy indicator** — Good / Fair / Weak with colour coding
- **Battery indicator** — segmented fuel-gauge style display next to the GPS indicator; green/orange/red tiers by charge level, blue while charging, red pulsing animation below 25% when not charging; percentage text show/hide togglable in Settings
- **On-screen clock**
- **Odometer** — persistent, zero-padded six-digit display with thousands separator, per-vehicle
- **Trip A — this journey** — distance, moving time, average speed, max speed. Auto-clears after 30 minutes of inactivity. Manual reset available. Tracked independently from the finalised trip record, so mid-trip resets or carry-over never corrupt history.
- **Trip B — since refuel** — same metrics, manual reset only, persists across app restarts, per-vehicle, immediately written to the database on reset
- **HUD mirror mode** — flips the display horizontally for windscreen (or daytime perspex reflector) projection, disables reset/menu buttons while active
- **Independent 180° display rotation** — separate settings for normal mode and HUD mode, useful for matching the phone's charge-port orientation to how it's mounted. Implemented via real device orientation (`requestedOrientation`/`SCREEN_ORIENTATION_REVERSE_LANDSCAPE`), not a view transform — this matters because dialogs, popup menus, and the system status bar all rotate consistently along with the app content. *(An earlier attempt using `View.rotation` looked correct on the main screen but left dialogs/popups upside-down, since that approach only transforms the app's own view hierarchy, not system-drawn overlay windows — reverted and rebuilt on a branch.)*
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
- **Speed limit alerts — independently configurable indicators**
  - Three separate toggles, each defaulting on: **audible alert**, **underline indicator** (coloured line under the breached sign), **sign opacity indicator** (sign jumps to fully opaque on breach)
  - **Sign base opacity** is a separate slider (0–100%), independent of whether the opacity *indicator* is enabled — so a user can keep signs permanently dimmed even with dynamic opacity switched off
  - **Overspeed grace margin** — a signed numeric setting (stored unit-agnostic, converted to km/h at the comparison point via `SpeedUnitFormatter.unitConversionFactor()`) shifting when the alert/visual indicators trigger relative to the posted limit. Positive values add tolerance before triggering; **negative values trigger early, as a pre-emptive warning** before the limit is actually reached
  - **Selectable alert audio channel** — Alarm / Notification / Ringtone / Media, applied via `AudioAttributes` usage on the `SoundPool`, rebuildable at runtime when the setting changes
  - Fully session-gated — no alerts fire unless a trip is actively being recorded
- **Speed sign colour schemes** — data-driven via a `SignColorScheme` structure (background/border/text colour for both max and min signs, plus shape and optional fixed-text fields for future non-circular variants). Currently includes **International Standard** and **South African Standard (1974–1993)** — historic round blue-background/red-border/white-text signs used in South Africa during that period. Structure is intentionally extensible for future schemes (e.g. pre-decimalisation British black/yellow signs) without code changes beyond adding a new registry entry
- **Live speed limit signs (OpenStreetMap Overpass API)**
  - Maximum speed sign populated from real OSM road data
  - Minimum speed sign shown only where OSM minimum-speed data exists for that road
  - Local tile-based caching (~2 km tiles, 30-day expiry) — most driving happens from cache, not a live network call
  - Automatic pre-fetch of neighbouring tiles when near a cached-area boundary, so coverage extends ahead of you before you need it
  - Shows "--" rather than a guessed value when no speed-limit data exists for the current road — no alert fires on unknown roads
  - Confirmed via real-world driving; one known limitation found so far — nearest-road matching can occasionally "adopt" a side road's speed limit near junctions (tracked for fixing, see Phase 6 below)
- **Battery-aware service lifecycle** — the GPS foreground service stops entirely when the app loses focus and no trip is active, instead of running indefinitely in the background; it resumes seamlessly when reopened, and stays alive throughout backgrounding only while a session is genuinely in progress (e.g. while using Waze or Maps mid-trip)
- **Forced-dark theme, app-wide** — replaced the previous `DayNight` theme (which caused a white-flash/unreadable-Settings bug on light-mode devices) with a single forced-dark `Theme.Vaart`. All secondary activities (Settings, Trip History, Trip Map) now use the same in-layout Toolbar pattern for their action bar, rather than relying on theme-provided chrome
- **Hide status bar** — togglable, with swipe-to-reveal behaviour; correctly re-applies after popup menus/dialogs close (`onWindowFocusChanged`)
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

### Phase 5b — Settings Population ✅ Complete
- [x] Speed unit setting — Follow region / km/h / mph, fully applied across the main screen, session summary, trip history, and trip map (shared conversion logic in `SpeedUnitFormatter`); "Follow region" currently falls back to device locale, automatic GPS-based detection is planned for Phase 6
- [x] Battery percentage text show/hide toggle
- [x] Hide system status bar option
- [x] Selectable alert audio channel (alarm/notification/ringtone/media)
- [x] Overspeed alert grace margin (signed value, supports early-warning negative values)
- [x] Independent 180° display rotation for normal and HUD modes (real device orientation, not a view transform)
- [x] Speed sign opacity indicator (independently toggleable, with separate base-opacity slider) and underline indicator (independently toggleable)
- [x] Speed sign colour scheme setting (International / South African 1974–1993), built on an extensible `SignColorScheme` data structure
- [x] (Unplanned but completed along the way) Forced-dark app theme, consistent Toolbar pattern across all secondary activities — fixed a white-flash/readability bug surfaced while building the status-bar toggle

### Phase 6 — Speed Limit Hardening (Planned, next)
- [ ] Nearest-segment matching instead of nearest-point (more accurate distance calculation against a road's actual geometry)
- [ ] Bearing/heading comparison to avoid matching against crossing or adjacent roads
- [ ] Hysteresis — require a new road match to persist across more than one GPS fix before switching the displayed limit, to filter out momentary junction mismatches
- [ ] Road classification weighting as a tiebreaker (prefer matches of the same road class as the currently-matched way)
- [ ] Verification pass on South African `maxspeed` OSM tag formats, hardening the parser if non-numeric or unit-suffixed tags are found in practice
- [ ] Automatic region detection for the speed unit setting, using a small bundled GeoJSON dataset of mph-using countries with a point-in-polygon check (replacing the Phase 5b locale-based fallback) — reusing the existing ~2 km Overpass tile infrastructure by tagging tiles as "near mph-country border: yes/no", rather than a separate point-to-polygon-edge distance system
- [x] ⚠️ Resolved/no longer open: the previously-flagged "📍 pin" question from an earlier session was a small mph-rounding fix (round-up via `ceil()`), already shipped in Phase 5b — no outstanding open question carried into Phase 6

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
- [ ] Help screen — see **Help Screen Content Notes** below for material already drafted during development
- [ ] About screen

### Future — Optimisation
- [ ] Further GPS battery tuning — lower-frequency location updates when idle outside of a session

---

## Help Screen Content Notes

Material worth carrying into the eventual help screen, captured while fresh during Phase 5b development rather than left to be reconstructed later:

- **Audio channel choice has real safety implications, not just a sound preference.** `Alarm` ignores Do Not Disturb and most silent-mode settings in most configurations — the most likely channel to be heard even if the phone is silenced, which is why it's the default for a driving-safety alert. `Ringtone` follows the physical ringer/silent switch. `Notification` follows notification volume and most DND settings. `Media` follows media volume and will mix/duck with music — relevant if alerts are routed through a car stereo.
- **The overspeed grace margin accepts negative values as an early-warning feature**, not just a tolerance buffer — e.g. "-5" triggers all indicators (audible, line, opacity) 5 km/h (or mph) *before* the posted limit is reached, for drivers who want advance notice rather than a tolerance margin.
- **Known cosmetic limitation:** none currently outstanding for rotation/status-bar (the original `View.rotation` notification-shade quirk was resolved by switching to real device orientation) — revisit this note if a future change reintroduces transform-based UI tricks.
- **Sign opacity and the opacity *indicator* toggle are independent settings.** The base opacity slider always applies; the toggle only controls whether opacity *additionally* jumps to 100% on a breach.

---

## Technical Notes

- **Min SDK:** API 29 (Android 10)
- **Location:** FusedLocationProviderClient (Google Play Services), via a Foreground Service
- **Background tracking:** Foreground Service with persistent notification; lifecycle-tied to focus + active session state rather than running indefinitely
- **Mapping:** OSMDroid (no API key required) for trip route display
- **Speed limit data:** OpenStreetMap, queried via the Overpass API, cached locally in Room with a tile-based system to minimise live network calls
- **Settings:** `androidx.preference`, backed by the same `vaart_prefs` SharedPreferences file used elsewhere in the app. Settings that affect live on-screen state (battery %, status bar, audio channel, HUD rotation/mirror) are re-applied in `onResume()`/`onWindowFocusChanged()`, not just at cold start, so changes take effect immediately on returning from Settings rather than waiting for an unrelated future event
- **Speed/distance unit conversion:** centralised in `SpeedUnitFormatter`, a single shared object read by every screen that displays speed, distance, or odometer values — avoids duplicating unit-conversion logic per activity. mph/mile conversions round up rather than truncate. `unitConversionFactor()` exposes the raw conversion multiplier for settings (like the grace margin) that need to convert a unit-agnostic stored value rather than format a display string
- **Sign colour schemes:** data-driven via `SignColorScheme`/`SignColorSchemes`, rendered as runtime-built `GradientDrawable`s rather than static XML drawables, specifically so new schemes can be added as data (a new registry entry) without new rendering code in the common case. Triangular sign shapes are represented in the data model but currently render as ovals — true triangle rendering would need a custom `Path`-based drawable, deferred until actually needed
- **Theme:** forced-dark (`Theme.Material3.Dark.NoActionBar`) app-wide, no `DayNight`/light variant. Full light-theme support deliberately deferred to Final Polish, by choice — see Phase 5b history for the bug that prompted dropping `DayNight` rather than patching around it
- **Persistence:**
  - SharedPreferences (`vaart_prefs`) — the live source of truth during an active session: anonymous vehicle data, active Trip A/B, active vehicle reference, app settings
  - Room DB — named vehicle profiles, trip records and recorded routes, cached speed-limit road data; vehicle/trip data is a periodic snapshot synced at specific moments (switch, reset, edit, create), not continuously
- **Package:** cvc.dashingdog.vaart

---

## Package

`cvc.dashingdog.vaart`

Part of the [dashingdog](https://github.com/asherbendavid) personal app family.
