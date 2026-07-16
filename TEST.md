Phase 7 tests on change vehicle at session summary time:

7.2
Distance / moving time / average speed (avg is derived, so these are the real inputs):

1. Correction where old vehicle had prior Trip B data before this session — old vehicle's remaining distance/time should equal pre-session totals, unaffected by the moved session.
2. Correction where this session was the only data since old vehicle's last refuel reset — old vehicle's Trip B should return to exactly zero after correction.
3. New vehicle had zero Trip B data (fresh since refuel) — new vehicle's Trip B should end up equal to just this session's numbers.
4. New vehicle already had other Trip B data — new vehicle's totals should be the sum of its existing data plus this session.

Max speed (the one that needs true recompute, not arithmetic):
5. Session's max speed is higher than old vehicle's remaining (other) records' max — old vehicle's max should drop to the next-highest remaining record after correction.
6. Session's max speed is lower than old vehicle's other records' max — old vehicle's max should stay unchanged.
7. Session's max speed is higher than new vehicle's current max — new vehicle's max should rise to the session's value.
8. Session's max speed is lower than new vehicle's current max — new vehicle's max should stay unchanged.
9. Old vehicle has zero remaining records after correction (session was the only one) — old vehicle's max should reset to 0/null, not error or retain a stale value.

7.3
Worth a mental test case before you build: start a trip, drive, stop (creates a TYPE_TRIP record), let Trip A idle-expire or reset manually without starting a new session in between — should produce no trip_a_reset entry. Then: drive, stop, drive again without resetting, then reset — should produce a trip_a_reset entry, since Trip A's total now differs from the last single TYPE_TRIP record.