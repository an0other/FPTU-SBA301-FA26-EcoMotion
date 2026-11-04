# VNPay deposit synchronization: investigation and regression

## Evidence and scope

Work is on `task-member-5`, based on `945718e`. No push or merge is part of this fix. No application database row was updated to simulate a payment.

The reported booking is `EM-2026-000040`, rental 3,200,000 VND, deposit 960,000 VND. A read-only PostgreSQL session against the datasource configured in local `application.properties` (`localhost:5434/ecomotion`, public schema) found no matching booking, PaymentTransaction or PaymentRequest. The user subsequently confirmed the original data may have been lost. Matches for this booking code in the available `merge-verify` and `vnpay-task` files are integration-test logs, not evidence of the reported real Sandbox checkout. There is no original `vnp_TxnRef`, signed IPN payload, gateway response or runtime trace with which to establish whether that historical transfer succeeded.

Consequently this change does **not** claim that the original VNPay payment was successful, that its IPN was absent, or that its checksum/amount failed. QueryDR cannot identify a lost attempt without its reference and original transaction date. The original bank-to-application incident remains historically unverified.

The local application configuration did contain `spring.jpa.hibernate.ddl-auto=create-drop`. This is a confirmed configuration that permits schema/data destruction across application lifecycle events; no available runtime trace establishes which restart deleted this booking. The local ignored configuration and committed example now default to `${DB_DDL_AUTO:update}`. Test resources continue using isolated H2 create-drop. The ignored local file contains existing credentials and must not be committed.

## Defects reproduced from code

1. `BookingWorkflowServiceImpl` previously selected VERIFYING whenever any VNPay PENDING row existed, before considering applied payments. `ProviderBookingServiceImpl.resolvePaymentStatus` independently used the same precedence. Thus an obsolete attempt could obscure a verified deposit. There were two inconsistent projections of the same ledger.
2. Provider Detail lacked a Vietnamese label for VERIFYING and displayed unknown raw enums. The progress stepper checked AWAITING_DEPOSIT before VERIFYING, and could infer a completed deposit from any positive received amount even without a confirmed booking.
3. `useRealtimeSync` discarded synchronization requests received while a previous refetch was in progress. A PAYMENT_VERIFIED event during an old in-flight response could leave the page showing the earlier state until fallback polling. The new browser regression deliberately pauses that response, emits the event and checks both participants update without reload.
4. The VNPay application path needed consistent eligibility checks before applying money: expired/changed requests, disputes, vehicle validity, pickup time, snapshot amount and both end confirmations for final payment. Throwing during a verified payment could roll back the evidence of received money instead of preserving it for reconciliation. The hardened path records verified funds as PAID with appliedAmount zero when ineligible.
5. Reconciliation logs previously omitted booking/reference and reduced unexpected failures to a class name. New logs distinguish IPN reception, rejection, committed outcome, QueryDR request, rejected verification and business reconciliation. They do not deliberately log credentials or checksums.

## Implementation

`BookingPaymentStateService` is the shared service for workflow and Provider Detail. Its status enum is a response projection, not a persisted entity or database state. Only verified incoming PAID allocations contribute to the paid contract balance; mocked/unverified and reconciliation-required funds do not. A completed deposit suppresses obsolete pending deposit attempts even if their amounts differ. Only a currently due final-payment attempt can put the final obligation into VERIFYING. Deposit success does not imply FULLY_PAID/COMPLETED.

Verified IPN and signed QueryDR use the same writer under the existing booking-first transaction locks. A valid 960,000 VND deposit updates PaymentTransaction PAID/appliedAmount/verification/paidAt, PaymentRequest CONFIRMED/paidAmount/confirmedAt, hold ACTIVE and Booking CONFIRMED atomically. Duplicate IPN returns the existing outcome without adding money. Browser Return remains verify/read only. Invalid signatures, amounts or transaction identifiers never create PAID. Valid received funds that cannot be applied are preserved as RECONCILIATION_REQUIRED and do not confirm the booking.

The existing PAYMENT_VERIFIED event is delivered after commit to Renter and Provider. The shared frontend hook now queues a refetch requested during another refetch, reconnects when the access token changes, uses the configured API base URL and avoids queued work after cleanup. Provider Detail, Renter Detail and Provider Finance retain their canonical API refetches. The pending receipt page also refetches read-only backend receipts automatically. VNPay Return/QueryDR polling remains server-authoritative.

No entity, database migration or client-generated payment result was added. Provider acceptance, driver assignment mode and trip confirmation rules remain in their owning services.

## The 3,240,000 VND balance

The contract formula is:

```text
remaining = max(0, rental total
                   + sum(unwaived charges excluding BASE_RENTAL and DEPOSIT)
                   - applied verified incoming payments
                   + recorded refunds)
```

A 3,200,000 VND rental with 40,000 VND unwaived extras and no applied deposit produces 3,240,000 VND. After applying a 960,000 VND deposit it produces 2,280,000 VND; without those extras the balance is 2,240,000 VND. A regression fixture checks that arithmetic using a 40,000 VND waiting fee and identifies the fee explicitly as test data. The missing historical rows prevent identifying whether the reported 40,000 VND was waiting/overtime/another actual charge. No fee, amount or booking status is hardcoded into production to make the displayed numbers match.

Before the final-payment stage the UI now calls the balance “Còn lại theo chi phí hiện tại”; it uses “Cần quyết toán thêm” only during settlement. The verified deposit advances the deposit step to completed and the driver step to current, while the final-settlement step stays pending.

## Verification

`VnpayDepositSynchronizationIntegrationTest` reproduces the 3,200,000/960,000 scenario on isolated data and checks successful IPN, duplicate/concurrent IPN, Return before IPN, signed HTTP QueryDR through the reconciliation service, stale pending attempts, both participant projections, after-commit notification and rollback, expired hold, dispute, cancellation, incorrect amount/signature, unapplied funds and fee arithmetic.

`e2e/vnpay-deposit-sync.spec.js` opens Provider and Renter pages, injects PAYMENT_VERIFIED during a paused old refetch, checks both pages advance without reload, verifies the 960,000 paid and 2,240,000 remaining amounts and prevents a false final-payment label. It also checks Provider Finance history refresh. Browser APIs/SSE are controlled test doubles; these are not a real VNPay merchant/IPN acceptance test.

An initial test run shared `target/classes` with IDE compilation and reported missing classes/resources. Verification therefore uses an ignored temporary POM pointing to `target/p0-verification`, preserving the actual POM and test configuration. The separate run avoids attributing build interference to payment logic. Existing fixtures were corrected to include a numeric gateway transaction number, applied amounts and required end confirmations, keeping business/security assertions intact.

Final verification results:

| Check | Result |
| --- | --- |
| Full backend run on isolated H2 | 208 tests: 189 passed, 19 conditional skips, zero failures/errors |
| Final affected-suite rerun after allocation-null guard | 29/29 passed: 14 VNPay synchronization and 15 end-user workflow cases |
| PostgreSQL 18, isolated cluster at port 55439 | 32/32 passed: 13 synchronization and 19 payment-integrity cases; cluster stopped afterward |
| Frontend unit | 31/31 passed |
| Frontend production build | Passed; existing chunk-size warning remains |
| Full Playwright run | 50/50 passed; controlled API and SSE doubles |
| ESLint on affected/reviewed frontend files | Zero errors; two existing ref-cleanup warnings |
| Original VNPay Sandbox transfer | Unverified: historical record/reference and runtime evidence unavailable |

The full backend run passed before the final defensive handling of legacy PAID rows with a null allocation. The final affected-suite rerun checks that those rows require review and cannot be counted as paid contract money. No application record was patched to satisfy a test. PostgreSQL and H2 tests use isolated databases.

Artifacts are ignored local files: backend `p0-all-final.log`, `p0-regression-final.log`, `p0-postgres.log` and `target/p0-verification/surefire-reports`; frontend `p0-unit.log`, `p0-build.log`, `p0-e2e-all.log`, `p0-lint.log`. Screenshots regenerated by the full E2E suite were restored to their original versions.

Real Sandbox acceptance requires a new identifiable transaction, a reachable registered IPN URL and retained runtime evidence. Merely reaching a browser success URL is insufficient. The local `app.vnpay.ipn-url` points at localhost, which is not an externally reachable address; that property alone does not prove what IPN URL is registered in the merchant portal, because the adapter does not send it as a payment parameter. Check the merchant registration before conducting the new test. Retain the vnp_TxnRef and original vnp_CreateDate, verify the signed gateway response and correlate the new IPN/QueryDR logs with database rows.

Changes remain uncommitted locally on `task-member-5`; no push or merge was performed. The schema-default adjustment applies on the next application start and cannot recover records already deleted by earlier create-drop lifecycle events.

## Official gateway contracts

- [VNPay payment/IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [QueryDR request and response signature](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html)
- [Return URL versus IPN](https://sandbox.vnpayment.vn/apis/docs/faqs/)

## Changed files

22 repository files differ from task-member-5 HEAD. The ignored local src/main/resources/application.properties also has the targeted ddl-auto default change; it is deliberately excluded from Git.

```text
AGENT_CONTEXT.md
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/enums/BookingPaymentStatus.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingPaymentStateService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingPaymentStateServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingWorkflowServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/ProviderBookingServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/gateway/implement/VnpayPaymentGatewayStrategy.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentReconciliationServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VnpayServiceImpl.java
Backend/EcoMotion/src/main/resources/application.properties.example
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/service/Tv5PostgresConcurrencyTest.java
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/EndUserWorkflowIntegrationTest.java
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/Tv5FinancialSafetyIntegrationTest.java
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/VnpayDepositSynchronizationIntegrationTest.java
Frontend/EcoMotion/e2e/vnpay-deposit-sync.spec.js
Frontend/EcoMotion/src/hooks/useRealtimeSync.js
Frontend/EcoMotion/src/pages/PaymentSuccessPage.jsx
Frontend/EcoMotion/src/pages/ProviderBookingDetailPage.jsx
Frontend/EcoMotion/src/utils/bookingWorkflow.js
Frontend/EcoMotion/src/utils/bookingWorkflowSteps.js
Frontend/EcoMotion/test/bookingWorkflowSteps.test.js
docs/VNPAY_DEPOSIT_SYNC_P0.md
```
