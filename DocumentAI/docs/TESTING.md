# Testing

Run the unit and contract tests with the project's Python environment:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Focused eKYC/report tests:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_v32_pipeline_modules.py -q
```

The HTTP smoke test requires the v0.32 API to be running and uses the command in [START_HERE_V032.md](START_HERE_V032.md). Synthetic/injected-reader tests verify software contracts; they do not establish OCR accuracy or document authenticity. Real model evaluation requires separately labeled, permitted data and the evaluation tools.
