# TV5 merge verification — 2026-11-01

Merged `origin/main` at `9241b8aa17953d753205bad66e15bc48461c1930` into
`task-member-5`, starting from `7dcaab09c7efec70443d3fe2aefb3afdaf07ca32`.

## Resolution

- Combined DTO fields from both branches: mixed rental pricing and official planned
  end time, assignment expiry, rejection details, payment status and assignment permissions.
- Preserved payment verification, receipt authorization, reconciliation, current assignment
  validation, driver eligibility and renter consent requirements from TV5.
- Kept main's mixed pricing, booking edits, monthly dashboard and updated seed data.
- Availability now uses the purchased official end time in both batch candidate lists
  and individual conflict checks, retaining the existing extension for late trip starts.
  Added three regression tests covering these combinations.
- Replacing the proposal value before saving a response ensures rejection and withdrawal
  persist after pessimistic refresh. Existing HTTP/refetch and concurrent response tests pass.
  Acceptance rules, ownership checks and idempotency remain in place.
- Updated the concurrent assignment fixture to move both pickup and planned end together;
  updated the dashboard expectation for main's seven seeded August completions.
- Honored main's deliberate deletion of legacy tests, documented in
  `MIXED_RENTAL_PRICING.md`. Retained the new mixed pricing and TV5 regression suites.
- Updated UI audit selectors to select the visible desktop/mobile booking row and open
  advanced filters before interacting with them. Preserved layout and interaction assertions.

## Validation

- Backend: Maven 3.9.12, JDK 25.0.4 compiling for Java 21, offline `clean verify`:
  **BUILD SUCCESS**, 195 tests discovered, 176 passed, 19 skipped, no failures/errors.
- The 19 skipped cases are `Tv5PostgresConcurrencyTest`, gated by `tv5.postgres=true`
  and a dedicated local `tv5_test` database. They were not executed in this run.
- Frontend: `npm test`: **28 passed**; `npm run build`: **passed**.
- Playwright UI audit: **4 passed**, including desktop, tablet, mobile and filter/export interactions.
- Full Playwright suite: **48 passed** (45.1 seconds), no failures or skips.
- Vite reports an existing bundle size warning; production output is generated successfully.

Tests use the project's H2 test configuration; no migration was run against the developer database.
