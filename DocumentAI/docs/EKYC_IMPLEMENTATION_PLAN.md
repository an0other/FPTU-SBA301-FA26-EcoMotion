# eKYC implementation plan

## Goal and scope

Build the first end-to-end yearly-rental driving-license flow between the
DocumentAI service and the Spring backend. The frontend upload and review UI
comes after the backend-to-AI flow and its API contract are stable.

The renter may upload GPLX images from the device gallery. The initial business
scope requires an automobile driving-license class and an unexpired license.
Checking suspension, revocation, or other changing legal rules is explicitly
out of scope for this first version and can be added later in the backend
business service.

This project has no government verification API. OCR, QR decoding, and image
analysis are evidence signals; they do not prove that the document was issued
by an authority or is genuine.

## Intended delivery order

### Phase 1 — secure and stabilize the DocumentAI API (current work)

- Keep the existing reader API as the reusable document-reading service for
  GPLX and CCCD.
- Define the backend-facing request and response contract, including document
  type, both image sides, field provenance, read status, and a request ID.
- Keep technical reading status separate from eKYC approval and rental
  eligibility. An OCR/QR match is a consistency result, not issuer
  authentication.
- Add service-to-service authentication for inference endpoints before exposing
  the service to Spring.
- Add an auditable, privacy-minimized pipeline trace for demo and diagnosis.
- In `development`, save uploaded images and per-stage evidence under the
  DocumentAI project's ignored `runs/` directory so the demo pipeline can be
  inspected. `DOCUMENT_AI_PIPELINE_TRACE=0` disables this; treat all images
  and OCR artifacts as sensitive and do not commit them.
- In `production`, process images without persisting the uploaded files or a
  local pipeline log.
- The Spring project already has local document storage configuration under
  its own `storage/` directory. Decide separately whether and how the business
  backend retains accepted GPLX evidence; do not silently make the AI debug
  copy the permanent business record.

### Phase 2 — connect Spring backend to DocumentAI

- Add a Spring adapter/provider that calls the internal DocumentAI API. Do not
  put the service credential in the browser or frontend configuration.
- Authenticate the renter and enforce upload count, content type, size, and
  supported document type in Spring before calling DocumentAI.
- Map transport errors, invalid uploads, unreadable documents, partial reads,
  inconsistent fields, and successful reads to explicit backend outcomes.
  Service unavailability must never produce a success-shaped verification
  result.
- Keep the existing CCCD eKYC provider flow intact while adding the GPLX reader
  as a separate capability.

### Phase 3 — implement GPLX business state and yearly-booking eligibility

- The intended demo policy is automatic acceptance only after the configured
  image-screening checks pass and OCR/QR consistency checks pass. This policy
  is not implemented by the current reader: its image analyzers are
  experimental/unconfigured, and `READ_SUCCESS` alone must never trigger
  acceptance.
- Label that outcome as accepted by the demo policy, not proof of government
  issuance or legal authenticity. A detector can miss attacks; this limitation
  must remain visible in API/UI wording.
- Require the needed automobile-license class and an unexpired date before
  allowing a yearly self-drive booking. For the initial policy, validity must
  cover the full requested rental period, including the planned end date.
- Recheck eligibility in the backend when creating or confirming the yearly
  booking. Frontend validation alone is not authoritative.
- Do not check whether a license is suspended or revoked in this version.
- Do not use manual review as the normal happy path for this demo.
- In development, retain images locally in `runs/` for pipeline inspection.
  In production, do not persist input images. Any later exception to this
  policy requires a defined retention/deletion period and restricted access.

### Phase 4 — plan and add frontend upload/status UI

- Add front/back GPLX uploads to the yearly-rental flow.
- Show extracted fields, explicit processing/acceptance states, and actionable
  errors.
- Do not describe image screening or OCR/QR agreement as government
  authentication or proof of authenticity.

### Phase 5 — research-led image screening improvements

This phase starts after the core Spring ↔ DocumentAI ↔ frontend flow works.
Gallery uploads remain supported.

Evaluate these as separate, replaceable signals before OCR:

1. Localized image manipulation, splicing, or edited text. TruFor and
   document-focused approaches such as DocTamper are research candidates, not
   assumed solutions.
2. Fully AI-generated document images. This is distinct from a localized
   AI-inpainted field and requires its own detector and test set.

Do not conflate these tasks with OCR, QR matching, or screen/print recapture.
Recapture detection is a separate future scope unless research or product
requirements reprioritize it.

Before any detector gates OCR, evaluate it on permitted Vietnamese GPLX/CCCD
data, including benign compression and resizing. Split by source document or
person, template, and capture session; keep derived and edited copies of one
source in the same split. Report false-positive and false-negative behavior at
predeclared thresholds. A suspected edit or AI-generation signal should result in rejection of that
submission or a request to upload a clearer/new image under the demo policy,
not a claim that the person committed fraud.

Research references:

- [SVTRv2 — scene text recognition, ICCV 2025](https://openaccess.thecvf.com/content/ICCV2025/html/Du_SVTRv2_CTC_Beats_Encoder-Decoder_Models_in_Scene_Text_Recognition_ICCV_2025_paper.html)
- [TruFor — image manipulation localization, CVPR 2023](https://arxiv.org/abs/2212.10957)
- [DocTamper — document tampered-text detection, CVPR 2023](https://doi.org/10.1109/CVPR52729.2023.00575)
- [FantasyID — identity-document-like image forgery benchmark, IJCB 2025](https://arxiv.org/abs/2507.20808)
- [UnivFD — general fake-image detection, CVPR 2023](https://openaccess.thecvf.com/content/CVPR2023/html/Ojha_Towards_Universal_Fake_Image_Detectors_That_Generalize_Across_Generative_Models_CVPR_2023_paper.html)
- [AIForge-Doc — AI-inpainted document images, 2026 preprint](https://arxiv.org/abs/2602.20569)
- [AIGDoc — AI-generated document images, 2026 preprint](https://arxiv.org/abs/2609.14352)

These papers do not establish detector performance on Vietnamese identity
documents. Preprints and benchmark results are research leads, not deployment
evidence.

## Service-to-service API key for local demo

A shared API key is a reasonable **temporary authentication layer** for a
local or private-network demonstration, provided it is implemented on both
services:

- Generate a high-entropy secret and inject it through environment variables
  or a local secret manager. Never commit it, return it from an endpoint, or
  expose it to the frontend.
- Require the key on inference endpoints and compare credentials safely.
- Bind DocumentAI to localhost or a private network; do not publish its port
  directly to the internet. Use TLS if the call crosses an untrusted network.
- Apply upload size/type limits, bounded concurrency, and timeouts. Avoid
  logging authorization headers, image bytes, OCR/QR values, or document
  identifiers.
- Treat a leaked key as compromised: rotate it and review access logs. The key
  limits who can invoke the service; it does not make OCR or image signals
  proof of document authenticity.

This is suitable for the demo threat model, not a complete production security
design. Production deployment needs a reviewed network boundary, secret
rotation, monitoring, retention policy, and potentially stronger workload
identity or signed requests.

## Status vocabulary

Use separate outcomes for:

- `READ_SUCCESS` / `PARTIALLY_READ` / `RETAKE_REQUIRED` / `INCONSISTENT_DATA` /
  `PROCESSING_ERROR`: document-reading and technical outcomes.
- `PENDING_REVIEW` / `APPROVED` / `REJECTED`: authorized business review.
- `ELIGIBLE` / `INELIGIBLE`: yearly-rental business policy result.

Never infer business approval or eligibility merely from a reader success
status. Do not report a document as issuer-authenticated without an
issuer-authenticated source.

## Confirmed decisions and remaining implementation details

- Validity must cover the full requested rental period.
- Suspension/revocation status is out of scope for the first policy.
- Local development saves input images and pipeline traces under ignored
  `runs/`; production does not persist uploaded images.
- After future image screening and OCR/QR consistency checks pass, the demo
  may auto-accept according to its declared policy; that is not issuer
  authentication.
- Still define the allowed automobile-license classes and how the yearly
  booking end date is calculated before implementing the Spring eligibility
  gate.
