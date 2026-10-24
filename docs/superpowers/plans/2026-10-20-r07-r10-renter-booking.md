# R07–R10 Renter Booking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the renter booking request, VietQR checkout, booking list and booking detail flow with exact pickup coordinates, correct lifecycle actions and full state coverage.

**Architecture:** Extend `Booking` as the single snapshot for one pickup point and driver note, expose capability-driven renter DTOs, and reuse the existing availability, Leaflet/Nominatim and file-storage infrastructure. Keep VietQR as the only payment path and use shared frontend metadata/utilities so list and detail cannot disagree about status or action visibility.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL/H2, React 19, React Router 7, Leaflet 1.9, Axios, Node test runner, Vite 8.

**Spec:** `docs/superpowers/specs/2026-10-20-r07-r10-renter-booking-design.md`

## Global Constraints

- Work directly on the current `task-member-2` branch and preserve unrelated user changes.
- R07–R10 cover hourly, daily and monthly plans; reject or route `YEARLY` to the dedicated R14 flow.
- A booking has one pickup point only; do not add destinations, routes, stops or return points.
- `PENDING` and `ACCEPTED` do not lock availability; confirmed VietQR deposit is the atomic schedule-lock point.
- Use public `bookingCode` in renter-facing routes and APIs; never expose internal booking IDs as navigation identifiers.
- Reuse Leaflet/OpenStreetMap, the backend Nominatim proxy and the existing `FileStorageService`; add no new dependency.
- Preserve the legacy address-geocode fallback for historical booking rows without coordinates.
- Keep the legacy instant checkout disabled.

## Review Focus

- Partial or out-of-range coordinate pairs must be rejected without persisting a booking; Task 1 integration tests pin this behavior.
- Two deposit confirmations for overlapping requests must still produce one schedule and one clean `409`; Task 3 reruns the concurrency integration test.
- A rejected proof may be retried and must not permanently prevent request-detail editing; Task 2 and Task 4 integration tests cover it.
- Tile/geolocation/geocoding failure must preserve the last valid selected point and allow retry or manual search; Task 5 pure utility tests and component behavior cover it.
- Every backend booking status must receive exactly one renter filter group and a non-empty label; Task 6 exhaustive frontend tests pin the enum set.

---

### Task 1: Persist the exact pickup snapshot

**Files:**
- Modify: `Backend/EcoMotion/src/main/resources/db/schema.sql`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/entity/Booking.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/request/CreateBookingRequest.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/BookingSummaryResponse.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/BookingDetailResponse.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/mapper/BookingMapper.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingServiceImpl.java`
- Modify: provider/driver booking response records and their service mappers that currently expose pickup address
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingApiIntegrationTest.java`
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingDetailApiIntegrationTest.java`
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/DriverTripApiIntegrationTest.java`

**Interfaces:**
- Produces: `Booking.pickupLatitude: BigDecimal`, `pickupLongitude: BigDecimal`, `driverNote: String` and matching additive JSON properties.
- Consumes: existing `MapGeocodingService` only as fallback when both stored coordinates are null.

- [ ] **Step 1: Write failing API tests for a valid coordinate pair and normalized one-line note**

Assert create returns `201`, detail returns the same six-decimal coordinates, and a note containing CR/LF is normalized to spaces. Assert a normalized note longer than 160 characters is rejected rather than silently truncated.

- [ ] **Step 2: Write failing validation tests**

Assert partial pairs, latitude outside `[-90,90]`, longitude outside `[-180,180]`, past pickup time and `YEARLY` plan return `400`/`409` without booking, charge, snapshot, schedule or payment rows.

- [ ] **Step 3: Run the focused tests and verify failure**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=RenterBookingApiIntegrationTest,RenterBookingDetailApiIntegrationTest test`

- [ ] **Step 4: Add schema/entity fields and constraints**

Add nullable `pickup_latitude numeric(9,6)`, `pickup_longitude numeric(9,6)` and `driver_note varchar(160)` with pair and range checks; map them as `BigDecimal`/`String` on `Booking`.

- [ ] **Step 5: Extend create DTO/service and renter responses**

Require valid coordinates for new non-yearly requests, normalize `driverNote` with whitespace collapsed to one line, reject past pickup time and generic yearly submission, then map fields to summary/detail.

- [ ] **Step 6: Propagate stored pickup fields to provider and driver responses**

Use stored coordinates first in `DriverTripServiceImpl` and `ProviderBookingServiceImpl`; invoke `MapGeocodingService.geocodeAddress(String)` only when both are null.

- [ ] **Step 7: Run focused backend tests**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=RenterBookingApiIntegrationTest,RenterBookingDetailApiIntegrationTest,DriverTripApiIntegrationTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

`git commit -m "feat(booking): persist exact pickup snapshot"`

### Task 2: Add safe request-detail editing before deposit lock

**Files:**
- Create: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/request/UpdateBookingRequestDetails.java`
- Create: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/BookingRequestDetailsResponse.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/controller/RenterBookingController.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingService.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingServiceImpl.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/repository/BookingRepository.java`
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingDetailApiIntegrationTest.java`

**Interfaces:**
- Produces: `BookingService.updateRequestDetails(Long userId, String bookingCode, UpdateBookingRequestDetails request)` and `PATCH /api/renter/bookings/{bookingCode}/request-details`.
- Consumes: Task 1 pickup fields; existing snapshotted slot length, base charge quantity, `AvailabilityPolicy`, `ScheduleService`, `PaymentRequestRepository` and evidence statuses.

- [ ] **Step 1: Write failing integration tests for allowed edits**

Assert an owner can update time/address/coordinates/passenger count/note in `PENDING` and in `ACCEPTED` with no `PENDING`/`ACCEPTED` deposit evidence; assert a rejected proof permits a retry edit.

- [ ] **Step 2: Write failing authorization/state/conflict tests**

Assert non-owner, `CONFIRMED`, active deposit evidence, renter overlap and buffered schedule overlap are rejected and original values remain unchanged.

- [ ] **Step 3: Run the focused test and verify failure**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=RenterBookingDetailApiIntegrationTest test`

- [ ] **Step 4: Implement the locked update transaction**

Lock the owned booking row, validate the editable states/evidence rule, compute end from snapshot plus base-charge quantity, reuse coordinate/passenger/time validation and availability policy, then update only request-detail fields.

- [ ] **Step 5: Return explicit renter capability flags**

Extend `BookingDetailResponse` with `canCancel`, `canEditRequest`, `canPay` while retaining `canConfirmEnd`; calculate them in `BookingServiceImpl` from state and payment-request/evidence state.

- [ ] **Step 6: Run focused tests**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=RenterBookingDetailApiIntegrationTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

`git commit -m "feat(booking): allow safe renter request edits"`

### Task 3: Align availability with the deposit lock point

**Files:**
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/implement/VehicleServiceImpl.java`
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingApiIntegrationTest.java`
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/PaymentRequestLifecycleIntegrationTest.java`

**Interfaces:**
- Produces: availability `occupiedSlots` containing only buffered active schedule rows.
- Consumes: existing `AvailabilityPolicy` semantics and atomic deposit-confirmation transaction.

- [ ] **Step 1: Write failing availability consistency tests**

Assert `PENDING` and `ACCEPTED` requests are absent from occupied slots, while `BOOKED`, `MAINTENANCE` and `BLOCKED` schedules are returned once with the vehicle's buffer applied.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=RenterBookingApiIntegrationTest test`

- [ ] **Step 3: Remove unscheduled request rows from availability output**

Keep the expanded schedule query and buffered returned intervals; remove the booking-repository merge that currently marks `PENDING`/`ACCEPTED` as occupied.

- [ ] **Step 4: Run availability and concurrent deposit tests**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=RenterBookingApiIntegrationTest,PaymentRequestLifecycleIntegrationTest test`
Expected: PASS, including one-winner overlapping deposit confirmation.

- [ ] **Step 5: Commit**

`git commit -m "fix(availability): lock slots only after deposit"`

### Task 4: Upload VietQR proof images directly

**Files:**
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/controller/PaymentRequestController.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/PaymentRequestService.java`
- Modify: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentRequestServiceImpl.java`
- Test: `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/PaymentRequestLifecycleIntegrationTest.java`
- Modify: `Frontend/EcoMotion/src/pages/CustomerPaymentPage.jsx`
- Modify: the payment-request frontend service used by `CustomerPaymentPage`

**Interfaces:**
- Produces: `POST /api/payment-requests/{id}/proofs/multipart` consuming `multipart/form-data` fields `file`, `transferredAmount`, optional `transactionReference`, `transferredAt`, `note`.
- Consumes: existing `FileStorageService.store("booking", userId, filename, bytes, contentType)` and existing proof-state validation.

- [ ] **Step 1: Write failing multipart integration tests**

Assert the owning renter can upload JPG/PNG/WebP to the current payable request; non-owner, wrong stage, empty file, PDF and oversized file are rejected without an evidence row.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=PaymentRequestLifecycleIntegrationTest test`

- [ ] **Step 3: Implement multipart submission**

Verify ownership/stage before reading/storing the image, store through `FileStorageService`, and delegate evidence creation to the same domain method used by JSON submission so duplicate/status rules remain identical.

- [ ] **Step 4: Replace URL input with file input**

Submit `FormData` from `CustomerPaymentPage`, show local filename/preview, upload progress/error and existing evidence review status; retain amount/reference/note inputs.

- [ ] **Step 5: Run focused backend test and frontend build**

Run: `cd Backend/EcoMotion; .\mvnw.cmd -Dtest=PaymentRequestLifecycleIntegrationTest test`
Run: `cd Frontend/EcoMotion; npm run build`
Expected: both PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(payment): upload VietQR proof images"`

### Task 5: Build the real pickup-map booking wizard

**Files:**
- Create: `Frontend/EcoMotion/src/components/booking/PickupMap.jsx`
- Create: `Frontend/EcoMotion/src/utils/bookingAvailability.js`
- Modify: `Frontend/EcoMotion/src/components/booking/PickupStep.jsx`
- Modify: `Frontend/EcoMotion/src/components/booking/ConfirmRequestStep.jsx`
- Modify: `Frontend/EcoMotion/src/components/booking/ChoosePlanStep.jsx`
- Modify: `Frontend/EcoMotion/src/pages/booking/BookingRequestPage.jsx`
- Modify: `Frontend/EcoMotion/src/services/bookingService.js`
- Reuse: `Frontend/EcoMotion/src/services/vehicleService.js`
- Create: `Frontend/EcoMotion/test/bookingRequest.test.js`

**Interfaces:**
- Produces: `PickupMap({ value, onChange, onError })`, where `value` is `{ address, latitude, longitude }`; `availabilityWindow(start, end)` and `overlapsOccupied(start, end, slots)` pure helpers.
- Consumes: `/vehicles/map/search`, `/vehicles/map/reverse`, `/vehicles/{id}/availability`, and Task 1 create fields.

- [ ] **Step 1: Write failing pure frontend tests**

Test availability-window timezone boundaries, exact-edge non-overlap, real overlap, coordinate validation and the create payload containing coordinates/note.

- [ ] **Step 2: Run frontend tests and verify failure**

Run: `cd Frontend/EcoMotion; npm test`

- [ ] **Step 3: Implement availability helpers and step-1 validation**

Fetch from the selected Ho Chi Minh calendar-day start through `max(nextDayStart, projectedEnd)`, compare the unbuffered requested interval with already-buffered returned ranges, and display a retryable inline error/disabled continue state.

- [ ] **Step 4: Implement `PickupMap`**

Reuse Leaflet tile fallback behavior, add a draggable marker, click selection, debounced address suggestions, browser geolocation and reverse geocoding; preserve the last valid point when a request fails.

- [ ] **Step 5: Integrate pickup state and note through steps 2–3**

Remove the hard-coded default address/static image, require a valid selected point, add the 160-character one-line note, show the map selection in confirmation and send all Task 1 fields.

- [ ] **Step 6: Guard yearly plans**

When `planType === "YEARLY"`, render a dedicated-flow explanation and link to the existing yearly page; never call generic create.

- [ ] **Step 7: Run frontend tests and build**

Run: `cd Frontend/EcoMotion; npm test`
Run: `cd Frontend/EcoMotion; npm run build`
Expected: PASS.

- [ ] **Step 8: Commit**

`git commit -m "feat(booking): select pickup on a real map"`

### Task 6: Complete My Bookings state coverage

**Files:**
- Create: `Frontend/EcoMotion/src/utils/bookingStatus.js`
- Modify: `Frontend/EcoMotion/src/components/booking/MyBookingsList.jsx`
- Modify: `Frontend/EcoMotion/src/pages/booking/MyBookingsPage.jsx`
- Modify: `Frontend/EcoMotion/src/utils/paymentDisplay.js`
- Create: `Frontend/EcoMotion/test/bookingStatus.test.js`

**Interfaces:**
- Produces: `BOOKING_STATUS_META`, `BOOKING_FILTERS`, `bookingStatusMeta(status)`, and `bookingMatchesFilter(status, filterId)`.
- Consumes: the exact twelve-state mapping in the design document and existing booking summary DTO.

- [ ] **Step 1: Write an exhaustive failing status test**

Assert all twelve statuses have non-empty Vietnamese labels and badge styles, each belongs to exactly one exclusive group, and payment CTAs exist only for `ACCEPTED` and `AWAITING_FINAL_PAYMENT`.

- [ ] **Step 2: Run frontend tests and verify failure**

Run: `cd Frontend/EcoMotion; npm test`

- [ ] **Step 3: Implement shared metadata and grouped filters**

Use the mapping from the design verbatim and make unknown statuses render a safe visible fallback rather than no badge.

- [ ] **Step 4: Refactor My Bookings to consume metadata**

Replace the component switch and raw-state filter chips, keep `bookingCode` routes, and expose only a valid primary action.

- [ ] **Step 5: Run tests and build**

Run: `cd Frontend/EcoMotion; npm test`
Run: `cd Frontend/EcoMotion; npm run build`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(booking): cover every renter booking state"`

### Task 7: Complete booking detail, edit UI and pickup display

**Files:**
- Create: `Frontend/EcoMotion/src/components/booking/PickupLocationCard.jsx`
- Create: `Frontend/EcoMotion/src/components/booking/EditBookingRequestModal.jsx`
- Modify: `Frontend/EcoMotion/src/components/booking/BookingDetail.jsx`
- Modify: `Frontend/EcoMotion/src/pages/booking/BookingDetailPage.jsx`
- Modify: `Frontend/EcoMotion/src/services/bookingService.js`
- Modify: `Frontend/EcoMotion/test/bookingRequest.test.js`

**Interfaces:**
- Produces: renter edit call `updateBookingRequestDetails(bookingCode, body)` and read-only pickup map/navigation card.
- Consumes: Task 2 capability flags/update endpoint, Task 5 `PickupMap`, and Task 6 status metadata.

- [ ] **Step 1: Add failing mapping/action tests**

Assert detail mapping retains coordinates/note/capabilities and action visibility is derived from `canCancel`, `canEditRequest`, `canPay`, `canConfirmEnd` rather than duplicated raw-state guesses.

- [ ] **Step 2: Run frontend tests and verify failure**

Run: `cd Frontend/EcoMotion; npm test`

- [ ] **Step 3: Add pickup and note presentation**

Render the exact point on a non-editable Leaflet map, address, note and a Google Maps navigation link; legacy rows without coordinates show address-only fallback.

- [ ] **Step 4: Add request-detail edit flow**

Reuse `PickupMap`, time/passenger/note validation and submit the Task 2 patch; on `409`, keep entered values, surface the conflict and refresh availability.

- [ ] **Step 5: Replace detail status/action switches**

Consume Task 6 metadata and backend capabilities for cancel/edit/pay/confirm/dispute controls while preserving error handling and timeline/charge/refund sections.

- [ ] **Step 6: Run frontend tests and build**

Run: `cd Frontend/EcoMotion; npm test`
Run: `cd Frontend/EcoMotion; npm run build`
Expected: PASS.

- [ ] **Step 7: Commit**

`git commit -m "feat(booking): complete renter booking detail actions"`

### Task 8: Full regression and completion audit

**Files:**
- Modify as needed: seeders and fixtures that construct booking rows or create request JSON
- Update: `docs/superpowers/plans/2026-10-20-r07-r10-renter-booking.md` checkboxes

**Interfaces:**
- Consumes: all prior tasks.
- Produces: verified R07–R10 deliverable with no known failing test/build/lint gate.

- [ ] **Step 1: Update all fixtures and seeders for additive pickup fields**

Populate realistic coordinates/notes for demo bookings while retaining at least one null-coordinate legacy fixture to prove fallback behavior.

- [ ] **Step 2: Run the entire backend suite**

Run: `cd Backend/EcoMotion; .\mvnw.cmd test`
Expected: all tests PASS with zero failures/errors.

- [ ] **Step 3: Run the entire frontend suite and build**

Run: `cd Frontend/EcoMotion; npm test`
Run: `cd Frontend/EcoMotion; npm run lint`
Run: `cd Frontend/EcoMotion; npm run build`
Expected: all commands PASS.

- [ ] **Step 4: Verify the full browser workflow**

Create a non-yearly booking using map search/click, accept it, upload/confirm VietQR deposit, observe every driver status in My Bookings/detail, confirm end, settle and complete; verify exact pickup point/note remains consistent across renter/provider/driver views.

- [ ] **Step 5: Audit every design requirement against authoritative evidence**

Record the test, build or browser evidence for every row in the design traceability table; do not claim completion for unverified behavior.

- [ ] **Step 6: Final commit**

`git commit -m "test(booking): verify R07-R10 renter workflow"`
