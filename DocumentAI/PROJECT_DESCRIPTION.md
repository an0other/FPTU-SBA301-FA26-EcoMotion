# Project scope

DocumentAI v0.32 is a reusable, local-first document reading service. Its current pilot supports one observed layout each for Vietnamese driving licences and citizen ID cards.

## Current capabilities

- Decode, orient, localize, and assess image quality.
- Read document fields and QR data, retain their provenance, and compare available values.
- Serve synchronous reads and in-memory asynchronous jobs through FastAPI.
- Add image analyzers through a bounded adapter contract without coupling them to OCR or identity fields.
- Report reading status separately from evidence assessment and manual approval.

## Not an authenticity verifier

Readable OCR/QR is not proof of authenticity, issuer, validity, or holder ownership. No validated digital-tampering or screen/print-recapture model is configured. The service never automatically approves a document.

See [README.md](README.md) for entry points and [docs/EKYC_ASSURANCE.md](docs/EKYC_ASSURANCE.md) for the assurance model and extension boundaries.
