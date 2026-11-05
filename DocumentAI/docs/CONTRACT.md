# Reader API contract

The current API entry point is `document_ai.reader.api:create_app`.

| Method | Route | Purpose |
|---|---|---|
| GET | `/health` | Process and warm-up status |
| GET | `/ready` | Whether the reader is ready for requests |
| GET | `/v1/capabilities` | Supported document types and configured analyzer capabilities |
| POST | `/v1/documents/read` | Synchronous two-sided document read |
| POST | `/v1/documents/jobs` | Start an in-memory asynchronous read |
| GET | `/v1/documents/jobs/{job_id}` | Poll a read job |

Read and job creation accept multipart `front`, `back`, and `document_type` fields. `document_type` may be `AUTO`, `VN_DRIVING_LICENSE`, or `VN_CITIZEN_ID`.

The service requires `DOCUMENT_AI_API_KEY` (at least 32 ASCII characters) at
startup. The read, job-create, and job-poll routes require the matching
`X-API-Key` header. Keep this secret between trusted backend services; never
put it in frontend code. Health/readiness/capability routes expose operational
metadata only.

Development mode enables `DOCUMENT_AI_PIPELINE_TRACE=1` by default. It writes
request images and stage evidence under the ignored project `runs/requests/`
folder and appends ordered stage/check events to `runs/pipeline.log`. Set
`DOCUMENT_AI_PIPELINE_TRACE=0` to disable these writes. Production mode always
processes input in memory and does not persist uploaded images or local traces.

## Result interpretation

- `status` and `data` describe the reading result, not authenticity.
- `ocrData`, `qrData`, and field provenance describe observed sources and comparisons.
- `imageAnalysis` contains analyzer-level image signals. Every row states its scope and `decisionUsed: false`.
- `assessment.documentAuthenticity.status` remains `NOT_ESTABLISHED` until independent eKYC evidence is implemented and evaluated.
- `assessment.approval` remains manual; `automaticApproval` and `documentVerified` are false.
- `pipelineReport` separates stage status and workflow status from reading status.
- Development `pipeline.log` records trace/request IDs, ordered stage/check
  status, timing, and reason codes; it excludes image bytes and OCR/QR values.
  Stage folders may contain source images, OCR line crops, and other sensitive
  evidence. Keep them local and remove them after inspection.

The API and report shape are evolving with the v0.32 reader. See [START_HERE_V032.md](START_HERE_V032.md) and [PIPELINE_V032.md](PIPELINE_V032.md) for the supported deployment and detailed interpretation.
