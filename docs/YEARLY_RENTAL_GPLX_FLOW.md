> Chính sách demo đã mở rộng: B, B1, B2 được hỗ trợ, bỏ mốc ngày cấp 2025. Chính sách theo commit `11ef8eb`; điều kiện tuổi, hạn GPLX và số chỗ vẫn được kiểm tra.

> **Bộ demo hiện hành:** 3 renter + 1 provider, ghép các bước trên cùng booking; xem [danh sách đăng nhập và workflow](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md). Các mô tả seed lịch sử bên dưới không dùng cho demo mới.

> **Cập nhật 07/11/2026:** Phần seed/flow thuê năm trong tài liệu cũ này đã được thay bằng [hướng dẫn demo gói năm](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md). Dùng ba renter theo giai đoạn và booking chính EM-Y2-MONTHLY/EM-Y2-HAPPY-END, không dùng các mã EM-SEED-YEARLY-* để demo flow mới.

# GPLX and yearly self-drive rental

## Legal references and limits

The demo policy is based on the **Law on Road Traffic Order and Safety, No. 36/2024/QH15**:

- Article 57 defines current driving-license classes. Class B covers cars carrying up to eight passengers, not including the driver; the statute also includes vehicle-weight conditions.
- Article 88 sets the general effective date to 1 January 2025.
- Article 89 contains transition rules for licenses issued under the previous law. Older licenses remain usable according to their printed term, subject to the applicable transition and conversion rules.
- Official Government legal-document portal: [vanban.chinhphu.vn](https://vanban.chinhphu.vn/?pageid=27160) (search for `36/2024/QH15`).
- Readable reference copy: [Law No. 36/2024/QH15](https://thuvienphapluat.vn/van-ban/Giao-thong-Van-tai/Luat-Trat-tu-an-toan-giao-thong-duong-bo-2024-so-36-2024-QH15-615463.aspx).

This is a course-project policy, not legal advice or a complete legal eligibility determination. The vehicle model has a seat count but no gross vehicle weight or full vehicle-category fields, so the system cannot enforce all statutory conditions. The demo automatically supports valid B/B1/B2 licenses under commit `11ef8eb`, without a 2025 issue-date cutoff (issue date does not determine eligibility). A finite expiry must cover today in Vietnam and the entire rental. The renter must be verified by OCR/QR and at least 18 at pickup; vehicles support at most nine seats including the driver. This is a demo product rule, not a complete legal determination for every B1/B2 license.

## Business rules implemented

### License intake

1. The renter uploads the front and back of a JPG/PNG license (up to 8 MB per image). The UI allows selecting from the device library; camera capture is not required.
2. Spring calls the separately hosted DocumentAI service over a configured base URL, with `DOCUMENT_AI_API_KEY` supplied in server environment configuration. The API key is never sent to the browser.
3. Spring accepts a read only when the document type and read status are correct, all main fields were read, and the OCR/QR core comparison matches. It stores both images using private document storage and only exposes authenticated renter image endpoints.
4. The response autofills the license number, name, date of birth, class, issue date, and expiry. The license number uses the existing encrypted-string persistence pattern.
5. `licenseVerified` means this OCR/QR read passed the above checks. It does **not** mean government verification, proof of authenticity, proof that an image was not manipulated or AI-generated, proof of ownership, revocation checking, or matching against CCCD. Those checks are intentionally not implemented.

### Yearly booking

- One active yearly plan and exactly one 365-day slot are accepted. Start time must be at least three hours in the future.
- The renter must be at least 18 on pickup; the license must be current class B under the demo policy and remain valid through the full rental end date.
- The current demo supports cars with at most nine seats including the driver. Because gross vehicle weight is not stored, seat count alone is not a complete legal test.
- The renter is the driver; no provider driver assignment or availability check is performed. Passenger count is limited to seats other than the driver.
- The provider has 24 hours to accept or reject the request. An accepted yearly request holds the vehicle for up to 24 hours while the renter uploads the signed PDF contract.
- Contract upload rechecks license eligibility. Uploading the accepted contract creates the upfront-payment request; payment is not available before the contract is stored. License eligibility is checked again for payment.
- Successful upfront payment confirms the vehicle schedule. The payment-history UI labels the transaction `UPFRONT_PAY`; the initial payment request uses the existing `DEPOSIT` request category.
- The current demo's yearly cancellation/refund policy is intentionally simple and does not model statutory, contract-specific, or negotiated refund cases. Do not treat it as a production policy.

## End-to-end demo

### Prerequisites

1. Start DocumentAI on a private/reachable host and configure the Spring backend with `DOCUMENT_AI_BASE_URL` and `DOCUMENT_AI_API_KEY`. There is no built-in localhost endpoint fallback; if either value is missing, license reads fail explicitly. Keep the key out of frontend variables and source control.
2. Start the Spring backend and the frontend. Sign in with a renter account that has an associated renter profile.
3. Ensure the provider has an approved vehicle with an active YEARLY plan, a 365-day slot, a positive upfront amount no greater than the plan price, and payment receiving details.

### Happy path

1. Open a vehicle with a yearly plan and choose **Thuê theo năm**.
2. On **GPLX cho gói năm tự lái**, select both license sides and submit. Confirm that OCR fields and eligibility reason render, and that both private previews can be loaded after refresh.
3. Pick a start time at least three hours ahead. Confirm that an unsupported/old class, expired license, or expiry before the 365-day rental end disables progression.
4. Set a pickup point on the map and passenger count within the remaining seats; review the yearly price, pickup fee, and upfront amount.
5. Submit the request. It should be `PENDING`, create no payment request, and create no driver assignment.
6. As the provider, accept the request. As the renter, upload a signed PDF within the contract hold window.
7. Confirm the booking advances to `AWAITING_DEPOSIT`, displays **Thanh toán khoản trả trước**, and only then exposes the payment page. Complete payment through the configured demo payment provider.
8. Open **Lịch sử thanh toán** and inspect the transaction label and charge breakdown. Existing renter review pages allow a vehicle review (and a driver review only when the booking has a driver) after a booking reaches `COMPLETED`.

### Demo an installment for an active yearly booking

1. Sign in as `renter.yearly.monthly@ecomotion.vn` with the dev password `EcoMotion@123`.
2. Open **My bookings** → `EM-SEED-YEARLY-ACTIVE`, or navigate to `/renter/bookings/EM-SEED-YEARLY-ACTIVE`.
3. In **Thanh toán và lịch sử kỳ thuê**, review the three recorded rent transactions and the pending 4th-of-12 installment.
4. Choose **Thanh toán kỳ này** → **VNPAY Sandbox** and complete the sandbox payment. VietQR can also be used when demonstrating provider-confirmed bank transfer.
5. Return to booking detail: the new transaction appears in the history and the booking remains `IN_PROGRESS`. Seed dates are relative to the run date: booking creation precedes the past rental start, while installment 4 is due soon in the future.

### Negative checks

- Upload only one side, a non-JPG/PNG file, a file over the size limit, an unreadable card, or mismatched OCR/QR data: the API should reject the read and not mark the license usable.
- Try a license whose expiry is earlier than the rental end, a non-supported class, or a vehicle with more than nine seats: yearly booking must be blocked by both UI feedback and backend validation.
- Try to create an annual payment before provider acceptance and contract upload: the backend must reject it.
- Let the provider-response or contract-upload hold expire: the request should be cancelled and its vehicle hold released.
- Try to read a private license image while unauthenticated or as another renter: access must not be granted.

## Known scope boundaries

- The AI pipeline's authenticity/tamper detection phases are not used to approve the license. A successful OCR/QR comparison is not an authenticity claim.
- Government license status, revocation, and CCCD ownership matching are not checked.
- Older license classes need a verified conversion matrix before automatic approval.
- Vehicle gross weight and other statutory vehicle attributes must be added before claiming complete statutory eligibility.
- This phase delivers request, provider acceptance, contract upload, and upfront payment. Review eligibility remains governed by the existing `COMPLETED` booking state; the yearly self-drive trip start/return lifecycle needs a separate business decision before it can be represented as completed in the current driver-oriented lifecycle.

## Yearly demo accounts and seed data (`dev` profile only)

All three accounts use the default password `EcoMotion@123`:

| Email | Scenario |
|---|---|
| `renter.yearly.new@ecomotion.vn` | Has a seeded class A1 license and is ineligible under the demo's class B policy. |
| `renter.yearly.monthly@ecomotion.vn` | Has active booking `EM-SEED-YEARLY-ACTIVE`, with three paid monthly-rent transactions and a pending fourth installment request. The account also has completed booking `EM-SEED-YEARLY-HISTORY` with twelve monthly transactions. |
| `renter.yearly.happy-end@ecomotion.vn` | Has a seeded eligible class B license and no yearly booking, ready to submit a new request. |

The license images are generated placeholders stamped “DEMO ONLY”. The seeder sets `licenseVerified` to create the UI scenario; these are not DocumentAI results and do not represent real license verification. Never use these accounts or data outside the dev environment.

The three monthly transactions and pending fourth-period request are seeded records. The current fourth-period request supports VNPAY Sandbox and provider-confirmed bank transfer, and booking detail shows its transaction breakdown. Automatic creation of later monthly requests, recurring collection, and a complete general-purpose installment schedule are not implemented. Deposit and deposit-refund rules are intentionally excluded pending a separate business decision.
