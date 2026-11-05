# Trip end confirmation and VIETMAP setup

Driver-reported trip end opens a 12-hour renter response window. The server checks every 10 seconds, including overdue windows after restart. Only the end-confirmation step advances automatically. Billing uses the driver's reported end timestamp, not the waiting period. A system confirmation does not populate renter confirmation timestamps.

An objection creates a TRIP_NOT_CONFIRMED dispute and pauses the window. Admin must explicitly choose RESUME_TRIP or REOPEN_CONFIRMATION; closing a dispute alone leaves the pause intact. Reopening creates a fresh 12-hour attempt and preserves the verified end timestamp. Legacy bookings without an attempt/deadline are not automatically backfilled.

## Database

Apply `Backend/EcoMotion/src/main/resources/db/migrations/20261105_trip_end_and_vietmap.sql` after backing up the target database. It only adds nullable metadata/history structure and is repeatable. Existing booking timestamps, prices, payments and transactions are unchanged. No repair of historical end windows is performed.

## VIETMAP configuration

- Backend environment: `VIETMAP_SERVICES_KEY`. Never expose it in frontend variables.
- Frontend environment: `VITE_VIETMAP_TILEMAP_KEY`, restricted to permitted domains in the VIETMAP console.
- Rebuild frontend after changing Vite environment variables.
- Missing keys do not prevent application startup. Address lookup, road-route quotes and map tiles report unavailable; new pickup-fee quotes cannot be finalized without a valid route. Old bookings remain readable at their saved prices.
- Optional backend properties: `app.vietmap.timeout=5s`, `app.vietmap.base-url=https://maps.vietmap.vn/api`, `app.trip-end.check-ms=10000`.

Routes use car mode, lat/lon query points, meter distances, millisecond provider durations converted to seconds, and GeoJSON lon/lat geometry. Pickup fees retain the existing ceiling formula. Route cache is bounded to 512 entries for 60 seconds, concurrent identical requests coalesce, and at most four provider requests run concurrently. Tracking route refreshes are limited to once per 30 seconds. No straight-line or zero-distance fallback is used.

## Actual-map acceptance gate

Without keys, only mocked integration is verified. In development, inspect the actual default VIETMAP tm style on desktop and mobile at multiple zoom levels. Confirm the absence of the nine-dash line and the required Hoang Sa / Truong Sa labels and depiction. Also verify real one-way routes, bridge detours, unreachable car destinations and quota/key errors.

Only after that review set `VITE_VIETMAP_STYLE_VERIFIED=true` for the production build. The flag is a release gate, not evidence that a provider style has passed review. If the style fails, work with VIETMAP to choose an appropriate style; do not cover labels or draw invented boundaries. Keep provider attribution visible.
