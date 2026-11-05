# DocumentAI v0.32

Local-first Python service for reading the currently supported pilot layouts of Vietnamese driving licences and citizen ID cards.

## Start here

- [Run and demo guide](docs/START_HERE_V032.md)
- [eKYC assurance and current limitations](docs/EKYC_ASSURANCE.md)
- [Reader pipeline and extension points](docs/PIPELINE_V032.md)
- [API contract](docs/CONTRACT.md)

The reader extracts and compares document data. It does **not** establish that a document is genuine, current, issued by a trusted authority, or owned by the person submitting it. Image-forensics and recapture models are not validated or configured; approval remains manual.

## Quick checks

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

For the API demo, follow the versioned setup and launch commands in `docs/START_HERE_V032.md`. Keep local model weights, training/evaluation data, and virtual environments outside source cleanup.
