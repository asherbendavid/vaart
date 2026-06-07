# Vaart 🚗

A GPS-based speedometer and trip computer for Android, built in Kotlin.

**Vaart** (Afrikaans: *speed, momentum*) started as two parallel learning projects: replacing a useful car app whose ads became intolerable, and practising real-world Git and GitHub workflows on an Android project from day one.

---

## Current Features (v1.1.0)

- **Live speed display** — GPS-based, landscape locked, screen always on
- **GPS accuracy indicator** — Good / Fair / Weak with colour coding
- **Odometer** — persistent, zero-padded six-digit display with thousands separator
- **Trip A — this journey** — distance, moving time, average speed, max speed. Auto-clears after 30 minutes of inactivity. Manual reset available.
- **Trip B — since refuel** — same metrics, manual reset only, persists across app restarts
- Moving time pauses automatically after 3 minutes below 5 km/h threshold
- Snap-to-zero below 1 km/h to suppress GPS jitter

---

## Roadmap

### Phase 2 — In Progress
- [x] Trip counters (A and B)
- [x] Max speed per trip
- [x] Odometer
- [ ] HUD flip (mirror display for windscreen projection)

### Phase 3
- [ ] Overspeed alert (colour change + sound)
- [ ] Trip history log (Room database)

### Phase 4
- [ ] Route recording and map display
- [ ] Spotify integration (current track, play/pause, skip)
- [ ] Speed limit display (OpenStreetMap / HERE API)

---

## Technical Notes

- **Min SDK:** API 29 (Android 10)
- **Location:** FusedLocationProviderClient (Google Play Services)
- **Background tracking:** Foreground Service with persistent notification
- **Persistence:** SharedPreferences for odometer and trip counters
- **Package:** cvc.dashingdog.vaart

---

## Package

`cvc.dashingdog.vaart`

Part of the [dashingdog](https://github.com/asherbendavid) personal app family.
