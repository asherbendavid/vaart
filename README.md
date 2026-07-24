# Vaart 🚗

A GPS-based speedometer and trip computer for Android, built in Kotlin.

**Vaart** (Afrikaans: *speed, momentum*) started as two parallel learning projects: replacing a useful car app whose ads became intolerable, and practising real-world Git and GitHub workflows on an Android project from day one.

---

## Current Features (Phase 9 complete)

- **Live speed display** — GPS-based, landscape locked, screen always on
- **GPS accuracy indicator** — Good / Fair / Weak with colour coding
- **Battery indicator** — segmented fuel-gauge style display next to the GPS indicator; green/orange/red tiers by charge level, blue while charging, red pulsing animation below 25% when not charging; percentage text show/hide togglable in Settings
- **On-screen clock**
- **Odometer** — persistent, zero-padded six-digit display with thousands separator, per-vehicle
- **Trip A — this journey** — distance, moving time, average speed, max speed. Auto-clears after 30 minutes of inactivity. Manual reset available. Tracked independently from the finalised trip record, so mid-trip resets or carry-over never corrupt history.
  - **Pin Trip A** — bypasses the 30-minute idle auto-clear for long road trips, toggled from the main menu (checkmark shown via a forced-icon workaround, since native `PopupMenu` checkmarks render inconsistently across OEMs). A passive pin icon appears beneath Reset A only while pinned. A confirmation prompt guards any reset attempt while pinned. "Unpin on reset" is separately configurable in Settings (default on)
- **Trip B — since refuel** — same metrics, manual reset only, persists across app restarts, per-vehicle, immediately written to the database on reset. Reset carries an unconditional confirmation prompt (Trip B has no pin state — it's treated as always "pinned" by design)
  - **Unreliable flag** — set automatically when a vehicle reassignment leaves Trip B numbers un-reconciled (see Vehicle correction below); shown as a 🚫 next to Trip B on the main screen, clears itself the next time Trip B is genuinely reset
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
  - **Vehicle correction from the session summary** — "Change vehicle" reassigns a just-completed session (and any Trip A/B resets that occurred during it) to a different vehicle after the fact. Odometer is transferred exactly (subtracted from the old vehicle, added to the new one). Trip B is deliberately *not* arithmetically reconciled between vehicles — an earlier subtract/recompute design was rejected as too fragile against future record deletion (Phase 7.4) — instead, a Settings toggle (default on) either resets Trip B outright on both vehicles, or leaves both untouched and flags both unreliable (including the source vehicle, since its own Trip B can't be guaranteed unaffected — e.g. a Trip B reset that happened mid-trip, before the reassignment). A `vehicle_reassigned` audit entry is logged in trip history either way
- **Trip history**
  - Every session is logged as a `TripRecord` with full route (`TripPoint` list) once stopped
  - Dedicated portrait history screen, dark themed, listing all recorded trips with vehicle, date, distance, duration, average/max speed
  - Tapping a trip opens a route map (OSMDroid) with the recorded path drawn as a polyline, auto-zoomed to fit
  - Gracefully handles trips with no stored route points (e.g. interrupted sessions) with a clear explanatory message instead of a blank/broken map
  - **Trip A/B resets logged as their own history entries** — a `TripRecord.type` field (`trip` / `trip_a_reset` / `trip_b_reset` / `vehicle_reassigned`) distinguishes a normal drive from a reset or reassignment event. Each type renders a distinct colour-coded badge (matching Trip A/B's on-screen colours) in the history list. A Trip A reset that exactly matches the immediately preceding trip is deliberately *not* double-logged — a decluttering decision, not a bug (see Help Screen Content Notes)
  - **Swipe-to-delete** individual records, either direction, with a confirmation prompt before the delete actually commits; cancelling restores the swiped item
  - **GPX export** — "Share GPX" on the trip map screen exports the viewed trip's route as a GPX 1.1 file (vehicle, device, distance/time/speed in the description) and hands it to the system share sheet; general-purpose, not tied to a specific destination app
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
- **GPX Route Player** — a Windows VB.NET/WinForms desktop tool that plays back a GPX file in real time on an OSM/Leaflet map (WebView2), with a moving marker, scrub slider, and filename display in the status bar. Used alongside the GPX Location Spoofer to visually follow the simulated route on a PC screen instead of running a second phone with a navigation app

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

### Phase 6 — Speed Limit Hardening ✅ Complete
- [x] Nearest-segment matching instead of nearest-point (true perpendicular distance to each segment, clamped to endpoints via `coerceIn`)
- [x] Bearing/heading comparison — speed-scaled penalty (0 below 20 km/h, full weight above 60 km/h) rejects crossing/adjacent roads without overriding a genuinely closer match
- [x] Hysteresis — challenger way must win 3 consecutive updates (~1.5s at modulo-2 update rate) before lock switches; empty-candidate runs count against the lock, fixing sticky-match on untagged roads
- [x] Road classification weighting — `HIGHWAY_RANK` map (motorway=8 to service=1) adds a small score bonus to higher-ranked roads, breaking ties without overriding distance
- [x] Overpass query widened to fetch all significant highway types regardless of `maxspeed` tag — way name and classification now display on untagged roads
- [x] Global `maxspeed`/`minspeed` OSM tag parser hardened — handles plain numeric, `km/h`/`mph` suffixes, `none`/`unlimited`, `walk`, `living_street`, and `country:context` format (e.g. `ZA:urban`) resolved via defaults table. Verified on London GPX ("20 mph" parsed and converted correctly)
- [x] Default speed limits for untagged roads — bundled `highway_speed_defaults.json` asset (country + highway type → km/h), loaded once at startup via `DefaultSpeedLimits`, applied when matched way has no explicit `maxspeed` tag. Includes published date for periodic update reference
- [x] Nominatim-based country detection — `CountryDetector` reverse-geocodes GPS fix to ISO 3166-1 country code, cached 30 days in SharedPreferences, invalidated on ~50 km position change, device locale fallback on network failure
- [x] Automatic mph/km/h unit switching — `detectedUseMph` derived from country code via `MPH_COUNTRY_CODES`, takes priority over locale fallback in `SpeedUnitFormatter`
- [x] Debug panel infrastructure — `DebugPanel.kt` with `DebugInfo` data class, `DebugField` enum, `ACTIVE_DEBUG_FIELDS` compile-time selector, and `DebugPanelRenderer`; full-width 20% strip reserved in `activity_main.xml` as foundation for Phase 11 swipeable secondary widget
- [x] ⚠️ Resolved/no longer open: the previously-flagged "📍 pin" question from an earlier session was a small mph-rounding fix (round-up via `ceil()`), already shipped in Phase 5b — no outstanding open question carried into Phase 6

### Phase 7 — Trip Data Integrity ✅ Complete
- [x] **7.1 — Pin Trip A** — toggle bypasses the 30-minute idle auto-clear; passive pin icon indicator; confirmation guard on any reset attempt while pinned; menu checkmark rendering required a `forceShowIcon` workaround since native `PopupMenu` checkmarks are unreliable across OEMs; "Unpin on reset" separately configurable
- [x] **7.2 — Vehicle correction from session summary** — "Change vehicle" reassigns a just-completed session, plus any Trip A/B resets that occurred during it, to a different vehicle. Odometer transferred exactly. Trip B intentionally not reconciled via arithmetic (rejected as fragile against future record deletion) — a Settings toggle either resets Trip B outright on both vehicles, or leaves both untouched and flags both unreliable (🚫 shown until next genuine reset). A `vehicle_reassigned` audit entry is logged either way
- [x] **7.3 — Trip counter resets logged as history entries** — `TripRecord.type` field distinguishes normal trips from Trip A/B resets and reassignments; each renders a distinct colour-coded badge in trip history; single-session Trip A resets are deliberately not double-logged
- [x] **7.4 — Delete individual trip records** — swipe-to-delete (either direction) with confirmation before commit; cancelling restores the item
- [x] (Unplanned but completed along the way) Fixed a `LocationService.onCreate()` initialization-order bug where `repository`/`speedLimitManager` were assigned after `loadPersistedState()`, silently breaking the auto-clear path's history logging
- [x] (Unplanned but completed along the way) Diagnosed and fixed an Overpass API retry-storm bug — failed tile fetches were retried on every GPS fix with no backoff, capable of exhausting the public rate limit within minutes and cascading into total data loss even in well-mapped areas. Fixed via a per-tile failure cooldown plus a global inter-call throttle respecting Overpass's 2-slot concurrency limit. A related narrow-candidate-radius bug (causing speed-limit/way-name flicker at highway speeds) was found and fixed in the same investigation

### Phase 8 — "Forgot to Press Start" Nudge ✅ Complete
- [x] While the app is in the foreground and no session is active, detect sustained speed above a configurable threshold and prompt the user with a sound and a pulsing Start button. "Sustained" defined as 3 consecutive location fixes above threshold (~3s at the current 1s fix interval)
- [x] Threshold configurable in Settings, default 10 km/h, stored unit-agnostic and converted at the comparison/display point (same pattern as the overspeed grace margin) — so a manual unit change or a "Follow region" cross-border trip never leaves the setting silently out of sync with the unit actually shown
- [x] Separate Settings toggle to disable the nudge entirely
- [x] Nudge fires once per stopped period, not once per app session — resets on both trip start and trip stop, so stopping for fuel/a rest and setting off again correctly re-arms it
- [x] Two-phase Start button pulse — ~4s of an attention-grabbing bright green, settling into the existing GPS-good green indefinitely until a session starts, so it doesn't keep demanding attention if driving untracked is deliberate
- [x] (Unplanned but completed along the way) Fixed a pre-existing bug where the overspeed grace margin's Settings summary didn't refresh on a manual unit change until the Settings screen was reopened

### Phase 9 — GPX Export ✅ Complete
- [x] "Share GPX" button on the trip map screen exports the currently-viewed trip's recorded points as a GPX 1.1 file and hands it to the system share sheet (WhatsApp, LocalSend, Drive, etc.) — general-purpose by design, not tied to any one destination app
- [x] `TripPointsToGpxWriter` — pure Kotlin, no Android framework dependency — builds `<metadata>`/`<trk><name>` (vehicle, date/time) and `<desc>` (vehicle, device id, distance, moving time, max speed), plus a `<trkseg>` of `<trkpt lat lon>` with an ISO 8601 UTC `<time>` per point. No `<ele>` — not captured by `TripPoint`, and optional per the GPX 1.1 schema
- [x] `DeviceInfo` — shared `Build.MANUFACTURER + Build.MODEL` utility, feeding the GPX description; reusable elsewhere later (e.g. debug panel)
- [x] `GpxExportHelper` — builds a filesystem-safe filename (`Vehicle yyyy-MM-dd HH-mm.gpx`, dashes instead of slashes/colons — `/` breaks path creation, `:` is rejected by Windows), writes to `cacheDir/gpx_exports/`, and sweeps exports older than 1 hour once per app launch (`MainActivity.onCreate()`) rather than via a background job
- [x] `FileProvider` added (manifest + `res/xml/file_paths.xml`), scoped only to the `gpx_exports` cache subfolder — deliberately narrow rather than exposing the whole cache path
- [x] Verified round-trip: exported a real trip, played it back cleanly in the companion GPX Route Player, confirmed metadata renders correctly; also verified via a LocalSend share to a PC
- [x] (Unplanned but completed along the way) Fixed a `LocationService.loadPersistedState()` bug in the Trip A 30-minute auto-reset path: the duplicate-check and inserted record both used `currentVehicleId`, which is still at its default (-1, Anonymous) at that point in a freshly-started service instance — nothing has restored the real vehicle id yet. This caused auto-reset entries to be misattributed to "ANONYMOUS" and to always fail their duplicate check, producing a redundant history entry alongside the real session's entry. Fixed by having `stopTrip()` persist the active vehicle id (`KEY_ACTIVE_VEHICLE_ID`, previously declared but unused) at the moment it's known correct, and having `loadPersistedState()` read it back instead of relying on `currentVehicleId`. Existing stale duplicate entries from before the fix were left in place rather than retroactively cleaned up

### Phase 10 — Picture-in-Picture (Planned)
- [ ] PiP mode while a session is active, for use alongside Waze/Google Maps (contents and Back-button behaviour still to be finalised)

### Phase 11 — Secondary Display Widget (Planned)
- [ ] Swipeable secondary display area — music (Spotify/generic MediaSession), weather, or other widgets (placement and content still to be finalised)

### Final Polish (Planned, deliberately last)
- [ ] Help screen — see **Help Screen Content Notes** below for material already drafted during development
- [ ] About screen

### Future — Trip History Polish
- [ ] Swipe-to-delete visual feedback — coloured reveal background (red) with a trash icon during the drag, via `ItemTouchHelper.onChildDraw()`. Deliberately deferred from Phase 7.4 to keep single-delete shippable on its own
- [ ] Multi-select delete — long-press to enter selection mode, tap to add/remove records, "Select all" and a contextual delete action. Needs per-item selection state in `TripHistoryAdapter` and either a custom `ActionMode` or a swappable toolbar state

### Future — Display & Layout
- [ ] **User-configurable visible elements** — independently toggleable: trip counters, media/weather bar, speed limit indicators (disabling also automatically disables the alerts tied to them). Prerequisite groundwork for the map idea below, since it turns the display from a fixed layout into reclaimable space
- [ ] **Local road map on the main display** — shown in place of the trip counter frame when selected/activated for a trip. OSMDroid is already a project dependency (used for trip route maps), so no new library needed, but a live-updating map is a meaningfully heavier widget than anything currently on the main screen — real screen-real-estate and performance tradeoffs to work through, hence gated behind the configurable-elements work above

### Future — Optimisation
- [ ] Further GPS battery tuning — lower-frequency location updates when idle outside of a session

---

## Help Screen Content Notes

Material worth carrying into the eventual help screen, captured while fresh during Phase 5b development rather than left to be reconstructed later:

- **Audio channel choice has real safety implications, not just a sound preference.** `Alarm` ignores Do Not Disturb and most silent-mode settings in most configurations — the most likely channel to be heard even if the phone is silenced, which is why it's the default for a driving-safety alert. `Ringtone` follows the physical ringer/silent switch. `Notification` follows notification volume and most DND settings. `Media` follows media volume and will mix/duck with music — relevant if alerts are routed through a car stereo.
- **The overspeed grace margin accepts negative values as an early-warning feature**, not just a tolerance buffer — e.g. "-5" triggers all indicators (audible, line, opacity) 5 km/h (or mph) *before* the posted limit is reached, for drivers who want advance notice rather than a tolerance margin.
- **Known cosmetic limitation:** none currently outstanding for rotation/status-bar (the original `View.rotation` notification-shade quirk was resolved by switching to real device orientation) — revisit this note if a future change reintroduces transform-based UI tricks.
- **Sign opacity and the opacity *indicator* toggle are independent settings.** The base opacity slider always applies; the toggle only controls whether opacity *additionally* jumps to 100% on a breach.
- **A Trip A reset that exactly matches the most recent trip isn't logged as a separate history entry.** If Trip A was reset after only one uninterrupted session (nothing to distinguish it from the trip record already saved), logging a second identical entry would just be clutter — this is a deliberate decluttering decision, not a missed reset. A reset *during* an active session (before the trip stops and its own record exists) is always logged, since there's nothing yet to be a duplicate of.
- **A vehicle correction's Trip B outcome depends on a Settings toggle, not automatic reconciliation.** With "Reset Trip B for both vehicles on reassignment" on (default), both vehicles get a clean Trip B after a correction. With it off, both vehicles keep their existing Trip B numbers unchanged but are flagged 🚫 unreliable — including the vehicle the session is *moved from*, since its Trip B can't be guaranteed unaffected either (e.g. if Trip B was reset mid-trip before the correction happened). The flag clears itself automatically the next time Trip B is genuinely reset on that vehicle.
- **The nudge threshold's unit (km/h vs mph) can, in one narrow edge case, momentarily fall out of sync with what's on screen.** If "Follow region" is selected and the Settings screen happens to be open while crossing a border that changes the expected unit, the displayed threshold summary won't live-update — unlike a manual unit change in Settings, which does. This is an accepted, deliberately unhandled gap: anyone actively looking at Settings is either about to set the unit manually themselves (no problem), or would find a spontaneous mid-scroll unit change more confusing than reassuring. The underlying value is never wrong, only its Settings-screen display can lag until the screen is reopened.
- **The anonymous vehicle exists precisely for untracked driving** — running outside of a session (and dismissing or ignoring the "forgot to start" nudge) isn't a mistake to be corrected, it's a legitimate, supported way to use the app. The nudge's second pulse phase is deliberately calmer for this reason: a reminder, not a nag.

---

## Technical Notes

- **Min SDK:** API 29 (Android 10)
- **Location:** FusedLocationProviderClient (Google Play Services), via a Foreground Service
- **Background tracking:** Foreground Service with persistent notification; lifecycle-tied to focus + active session state rather than running indefinitely
- **Mapping:** OSMDroid (no API key required) for trip route display
- **Speed limit data:** OpenStreetMap, queried via the Overpass API, cached locally in Room with a tile-based system to minimise live network calls. Matching pipeline: nearest-segment distance → bearing-weighted scoring → hysteresis lock → road classification tiebreaker. Untagged roads resolved via bundled `highway_speed_defaults.json` (country + highway type → km/h). Country detected via Nominatim reverse geocoding (`CountryDetector`), cached 30 days, device locale fallback
- **Room DB version:** 9 (current). Schema includes `speed_limit_ways` (osmWayId, name, roadClassification, geometry, speed tags), `queried_tiles`, `vehicles` (now including `tripBUnreliable`), `trip_records` (now including a `type` discriminator: `trip` / `trip_a_reset` / `trip_b_reset` / `vehicle_reassigned`), `trip_points`. `fallbackToDestructiveMigration()` is in place — the speed-limit cache is re-fetchable and safe to wipe on schema changes; real vehicle/trip data has been carried through each Phase 7 bump without loss so far, but this remains a known risk to revisit if a future schema change needs to preserve data more deliberately
- **Debug panel:** `DebugPanel.kt` provides a `DebugField` enum, `ACTIVE_DEBUG_FIELDS` set (edit before each test build to select which fields render), and `DebugPanelRenderer`. Panel occupies the bottom 20% of the display in a `FrameLayout` container sized for Phase 11's future swipeable widget
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
