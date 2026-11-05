# Architecture

## Request path

`FastAPI reader API -> StagedReader -> image preparation and pair checks -> image analyzers -> detailed OCR/QR -> extraction and consistency -> report`

- `reader.api` owns HTTP routes, upload handling, readiness, and single-inference coordination.
- `reader.staged` coordinates the supported reading flow and early exits.
- `reader.service` contains the reader's image, OCR, QR, and extraction behavior.
- `reader.image_analysis` runs isolated image-only analyzers through a small contract.
- `reader.document_authenticity` summarizes eKYC evidence dimensions; it does not make a genuine/fake decision.
- `reader.pipeline_report` keeps reading results, image signals, and approval state distinct.

The configured reader is assembled in `reader.factory`. Analyzer registration is server-side; uploaded requests cannot select or load executable plugins.

## Data boundaries

Image analyzers receive an EXIF-oriented source image, its side, a content hash, and source format. They do not receive OCR text, QR payloads, names, or document numbers. Each analyzer gets an image copy so it cannot mutate the image used by reading.

API jobs are held in memory and are not durable across process restarts. The service is a local/demo integration component, not a production eKYC deployment with retention, issuer connectivity, or a full approval workflow.

Inference and job-result routes require a shared service API key supplied
through environment configuration. Health/readiness/capability routes expose
operational metadata only. Development mode may retain uploaded images and
per-stage evidence under ignored project `runs/requests/`, with ordered events
in `runs/pipeline.log`. `DOCUMENT_AI_PIPELINE_TRACE=0` disables this tracing;
production mode never persists input images or local pipeline logs.

See [PIPELINE_V032.md](PIPELINE_V032.md) for stage behavior and [EKYC_ASSURANCE.md](EKYC_ASSURANCE.md) for the boundaries of authenticity evidence.
