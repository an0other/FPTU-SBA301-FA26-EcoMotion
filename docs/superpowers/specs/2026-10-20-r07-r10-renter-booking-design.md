# R07–R10 Renter Booking Completion Design

**Status:** Approved for implementation on 2026-10-20
**Scope owner:** Task Member 2
**Date:** 2026-10-20

## 1. Intent

Complete the renter-owned booking journey described by R07–R10 so it can be demonstrated without fake map data, missing booking states, hidden state transitions, or manual database edits.

The target journey is:

`Choose vehicle and plan → choose an available time → select an exact pickup point → submit PENDING request → provider accepts → renter pays deposit by VietQR → confirmed trip appears in My Bookings → renter follows the driver/trip states → renter confirms the end or opens a valid dispute/cancellation flow.`

The existing VietQR settlement and booking state machine remain authoritative. This design fills renter-side gaps without absorbing Task Member 3's yearly-contract work.

## 2. Goals

### R07 — Create booking request

- Use a real Leaflet/OpenStreetMap pickup selector instead of a decorative image.
- Let the renter search an address, click the map, drag the marker, or use browser geolocation.
- Persist the selected address and exact latitude/longitude as part of the booking snapshot.
- Accept an optional one-line note for the driver.
- Check the selected time range against the existing availability endpoint before moving past step 1; the backend remains the final authority at submission and deposit confirmation.
- Keep the booking `PENDING` after submission and do not lock the schedule or collect money yet.
- Snapshot price and policy values exactly as the current backend already does.

### R08 — VietQR deposit/payment

- Keep VietQR as the only renter payment route.
- Show the payment request that matches the booking and current stage (`DEPOSIT` or `FINAL_BALANCE`).
- Replace the proof-image URL field with a local image picker backed by the existing file-storage abstraction; retain the stored URL internally.
- Show QR, transfer amount, transfer content, expiry, submitted evidence and provider-review status clearly.
- Preserve the atomic deposit confirmation behavior: lock vehicle, recheck buffered availability, record payment, create schedule and move `ACCEPTED → CONFIRMED` in one transaction.

### R09 — My Bookings

- Represent every renter-visible state with a Vietnamese label and badge:
  `PENDING`, `ACCEPTED`, `CONFIRMED`, `DRIVER_ASSIGNED`, `DRIVER_CONFIRMED`, `DRIVER_ARRIVED`, `IN_PROGRESS`, `AWAITING_EXTRA_CHARGE_CONFIRMATION`, `AWAITING_FINAL_PAYMENT`, `COMPLETED`, `CANCELLED`, and `REJECTED`.
- Group filters by useful user intent rather than exposing an excessively wide row of raw states:
  `Tất cả`, `Chờ xử lý`, `Sắp tới`, `Đang thực hiện`, `Chờ quyết toán`, `Hoàn thành`, and `Đã kết thúc`.
- Keep the public `bookingCode` as the only identifier shown or placed in renter URLs.
- Show the primary next action on each card only when valid, such as deposit payment or final-balance payment.

### R10 — Booking detail

- Display every booking state and timeline step consistently with R09.
- Show the exact pickup point on a small read-only Leaflet map with a link to open navigation.
- Show the driver note, driver/contact information when assigned, the charge formula, payments, refund preview and valid disputes.
- Keep cancel, payment, end-confirmation and dispute actions driven by backend capabilities/status, with backend enforcement retained.
- Allow the renter to change pickup time, pickup point, passenger count and driver note only while the booking is `PENDING` or `ACCEPTED` and before any accepted payment evidence exists.
- A schedule/time edit must recheck renter overlap and buffered vehicle availability. It updates booking fields only; no schedule exists before confirmed deposit.

## 3. Non-goals

- The yearly contract, yearly document workflow and yearly payment rules (`R14`).
- Provider cancellation, driver CRUD, trip extension and review creation (`R16`).
- Destination, route, waypoints, multi-stop bookings or fare calculation by route distance.
- Replacing OpenStreetMap/Nominatim with a paid map provider.
- Editing a booking after deposit confirmation.

## 4. Anti-goals

- Do not treat typed address text as an exact pickup point without coordinates.
- Do not geocode the address again on every provider/driver request when stored coordinates are available.
- Do not let the frontend invent action availability independently from backend state rules.
- Do not permit yearly plans through the generic R07–R10 wizard and imply they are fully supported.
- Do not introduce a second instant-payment path alongside VietQR.

## 5. Data model and API

### Booking fields

Add nullable database columns for backward compatibility:

- `pickup_latitude numeric(9,6)`
- `pickup_longitude numeric(9,6)`
- `driver_note varchar(160)`

Add a database check constraint requiring latitude and longitude to be both null or both present, plus geographic range checks. Existing seed and historical bookings may remain null. New non-yearly renter bookings require both coordinates at service validation time.

Expose these fields in create, summary/detail, provider detail and driver trip DTOs. Provider/driver tracking uses stored coordinates first and falls back to address geocoding only for legacy rows.

### Create booking

Extend `CreateBookingRequest` with latitude, longitude and optional driver note. Validate:

- coordinates are a complete pair and within range;
- address is non-blank and at most 500 characters;
- note is at most 160 characters and normalized to one line;
- plan type is not `YEARLY` in this generic endpoint;
- pickup is in the future;
- slot count, passengers, renter overlap and buffered availability follow the existing rules.

### Update pending booking

Add a renter endpoint such as:

`PATCH /api/renter/bookings/{bookingCode}/request-details`

The request contains pickup time, address, coordinates, passenger count and driver note. The service locks the booking row, verifies ownership, requires `PENDING` or `ACCEPTED`, rejects the edit if payment evidence is already submitted/accepted, recalculates the projected end from the snapshotted slot length, and reuses the same availability/overlap policy as create.

The endpoint does not change price, plan, vehicle or slot count.

More precisely, an edit is blocked when the booking's `DEPOSIT` payment request contains an evidence row in `PENDING` or `ACCEPTED`. A `REJECTED` evidence does not block correction of request details. Selecting a local file in the browser without submitting it creates no server record and therefore does not block editing.

### Payment evidence upload

Add an authenticated multipart submission endpoint bound to the payment request:

`POST /api/payment-requests/{paymentRequestId}/proofs/multipart`

It accepts the image and the existing proof metadata together. The service loads the payment request, verifies that the authenticated renter owns its booking and that the request is the currently payable `DEPOSIT` or `FINAL_BALANCE`, stores JPG/PNG/WebP through the existing `FileStorageService`, then creates the `PaymentEvidence`. This avoids unauthorised generic uploads and avoids normal client-side orphan files. The existing JSON proof endpoint remains for backward compatibility, while the renter UI uses multipart.

## 6. Renter UI behavior

### Booking wizard

Step 1 fetches occupied ranges from the start of the selected calendar day in `Asia/Ho_Chi_Minh` through the later of the next calendar-day boundary or the projected booking end. The availability API already expands the query and returned occupied ranges by the vehicle maintenance buffer, so the frontend compares its unbuffered requested interval to those returned ranges and must not apply the buffer again. It explains that availability is checked again when payment is confirmed.

The availability response includes only actual schedule-blocking rows (`BOOKED`, `MAINTENANCE`, `BLOCKED`). `PENDING` and `ACCEPTED` requests do not lock or advertise the vehicle as occupied; concurrent requests are resolved by the atomic availability check at deposit confirmation. This keeps UI availability, booking creation and the agreed lock point consistent.

Step 2 contains:

- an address search input with debounced suggestions from `/vehicles/map/search`;
- a Leaflet map centered on the selected result;
- one draggable marker;
- click-to-place and “use my location” controls;
- reverse geocoding through `/vehicles/map/reverse` after map selection;
- passenger count and optional driver note.

The next button is disabled until an address and valid coordinate pair exist. Step 3 shows the selected point, exact coordinates only as secondary information, note, time, estimated total and deposit.

If a yearly plan reaches the generic wizard by URL, show an explanatory yearly-contract notice and route to the dedicated yearly page instead of allowing submission.

### My Bookings and detail

Use one shared status metadata module for labels, colors, group filters and next-action text. This removes divergent switch statements between the list and detail pages. Every exact status maps to one group:

- `Chờ xử lý`: `PENDING`, `ACCEPTED`
- `Sắp tới`: `CONFIRMED`, `DRIVER_ASSIGNED`, `DRIVER_CONFIRMED`
- `Đang thực hiện`: `DRIVER_ARRIVED`, `IN_PROGRESS`
- `Chờ quyết toán`: `AWAITING_EXTRA_CHARGE_CONFIRMATION`, `AWAITING_FINAL_PAYMENT`
- `Hoàn thành`: `COMPLETED`
- `Đã kết thúc`: `CANCELLED`, `REJECTED`

`Tất cả` is the unfiltered view and therefore is not an exclusive state group.

Booking detail receives explicit capability flags from the backend where the rule is more complex than a single status: `canCancel`, `canEditRequest`, `canConfirmEnd`, `canPay`, and allowed dispute categories. Buttons are hidden when false and direct API calls remain protected.

## 7. Error handling

- Map/geocoding failure does not silently accept fake coordinates. Keep the last valid point and show a retryable inline error.
- Browser location denial leaves search and click selection available.
- A `409` availability conflict returns the renter to the time step with a clear message.
- Payment proof upload and proof submission are separate recoverable operations; an uploaded image URL can be retried without creating duplicate payment evidence.
- Request-detail updates are idempotent for the same values.

## 8. Migration and compatibility

- Schema changes are additive and nullable for existing rows.
- Seeders may populate coordinates for demonstration bookings, but legacy fallback must remain tested.
- Existing provider and driver APIs gain additive JSON fields only.
- The generic booking endpoint rejects `YEARLY`; the dedicated yearly flow remains untouched.

## 9. Verification

### Backend

- Create booking persists coordinates/note and rejects invalid pairs, past times and yearly plans.
- Create and edit share buffered availability and renter-overlap rules.
- Editing is allowed only in `PENDING`/eligible `ACCEPTED`, and rejected after proof/confirmation.
- Stored coordinates flow through renter, provider and driver responses; legacy address fallback still works.
- Evidence upload validates ownership, type and size.
- Existing VietQR atomic-confirmation and concurrency tests remain green.

### Frontend

- Map search, click, drag, geolocation fallback and reverse-geocode behavior have component tests.
- Wizard cannot continue without a real selected point and sends coordinates/note.
- Every booking status has a badge and belongs to exactly one useful filter group.
- Detail actions appear only for valid backend capabilities.
- Payment page uploads a local proof image and submits its returned URL.
- Yearly plan cannot be submitted from the generic wizard.

### End-to-end demonstration

Run a renter booking through `PENDING → ACCEPTED → VietQR deposit → CONFIRMED → driver states → IN_PROGRESS → renter confirmation → settlement/final payment → COMPLETED`, confirming the map point and note remain identical for renter, provider and driver throughout.

## 10. Builder calls

These are implementation choices made by the design, not user-supplied decisions:

- Reuse the project's existing Leaflet, OpenStreetMap, Nominatim proxy and storage abstraction.
- Store coordinates on `booking`, not in a separate location table, because R07 has exactly one pickup point.
- Keep new coordinate columns nullable only for historical compatibility; require them for newly created non-yearly requests.
- Use grouped renter filters while preserving exact status labels on each booking card.
- Limit editable request fields so price/plan/vehicle snapshots cannot drift.

## 11. Assignment traceability

The following matrix is the acceptance boundary for Task Member 2. It maps the concise assignment in `docs/PHAN_CONG_6_THANH_VIEN.md` to the fuller rules in `docs/SBA301_FALL26.md` and to concrete proof required from this implementation.

| Assignment | Authoritative behavior | Required implementation evidence |
|---|---|---|
| R07 — choose plan + pickup → `PENDING` + snapshot, no payment | N contiguous slots; one pickup point; passenger count; one-line driver note; price/policy frozen at request time | Create API and integration tests prove status is `PENDING`, no schedule/payment is created, all snapshot fields are saved, exact pickup coordinates/note persist, and unavailable/past/overlapping requests are rejected consistently |
| R08 — pay after provider accepts → lock schedule | VietQR deposit for hourly/daily/monthly; payment confirmation is the lock point | Payment page selects the correct `DEPOSIT` request, multipart proof works, provider confirmation atomically rechecks availability and creates exactly one schedule/payment transaction, and a collision rolls back fully with `409` |
| R09 — My Bookings by status using `booking_code` | Renter can understand and find the booking at every lifecycle state | API never exposes an internal booking ID as the renter route key; all twelve states have labels/badges, one group mapping and valid primary actions; component tests enumerate the status set |
| R10 — timeline, cancellation, disputes, renter end confirmation | Driver details/arrival state; both sides confirm end; only applicable actions are available | Detail response/UI shows the full timeline, stored pickup point/note, driver/contact, cost breakdown and refund preview; backend capability flags control edit/cancel/pay/confirm/dispute actions; transition and authorization tests cover each action |

### Cross-owner boundaries

- R07 consumes the approved-vehicle/plan data and vehicle availability supplied by TV1/provider features, but does not change vehicle catalogue ownership.
- R08 consumes provider accept and provider payment confirmation, but owns the renter checkout experience and its proof submission.
- R10 consumes driver state/location updates and settlement results, but does not implement driver operations, provider charge entry, reviews or payment-history pages.
- R14 yearly booking remains owned by TV3. R07 must route it away rather than partially reimplement it.

### Existing gaps confirmed in the current code

- `PickupStep.jsx` renders a static decorative map and the create request contains no coordinates or driver note.
- The availability API currently advertises `PENDING`/`ACCEPTED` requests as occupied even though the agreed workflow locks only at confirmed deposit; this must be aligned.
- `MyBookingsList.jsx` has no complete filter/badge treatment for the driver-assignment/arrival states.
- `BookingDetail.jsx` repeats its own incomplete state switch and does not show the exact stored pickup point.
- `CustomerPaymentPage.jsx` asks renters to paste an external receipt URL instead of uploading a proof image.
- There is no renter API for correcting request details before the deposit lock point.

These gaps are implementation targets, not merely documentation notes; the work is incomplete until their corresponding tests pass.
