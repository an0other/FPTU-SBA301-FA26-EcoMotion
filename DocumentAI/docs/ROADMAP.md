# Roadmap

## Current demo

- Read one observed pilot layout each for GPLX and CCCD.
- Keep OCR/QR reading and image-signal analysis separate.
- Expose explicit evidence gaps and require manual approval.
- Provide extension points for additional document types and analyzers.

## Before claiming eKYC verification

1. Define per-document assurance requirements and threat models.
2. Obtain permitted, representative, independently labeled data.
3. Evaluate false-positive and false-negative rates across documents, people, cameras, and capture conditions.
4. Add trusted issuer/provenance and current-validity checks where authoritative sources exist.
5. Add holder binding and capture-integrity checks with consent and tested thresholds.
6. Begin in observe-only mode; document review and escalation policy before any automated business decision.

Photoshop/recapture detection, issuer verification, revocation lookup, holder ownership, and same-physical-card verification are not implemented as verified capabilities today.
