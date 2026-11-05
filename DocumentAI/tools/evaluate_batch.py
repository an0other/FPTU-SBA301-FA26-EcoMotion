"""Run using manifest-relative paths; inference never receives expected answers."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
import argparse
import csv
import json
from document_ai.batch_evaluation import FIELDS, evaluate_report, summarize
from document_ai.core import InputImage, load_profile, dumps
from document_ai.pipeline.runner import Pipeline


def run(manifest_path, output):
    manifest_path = Path(manifest_path).resolve()
    base = manifest_path.parent
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    cases = manifest.get('cases')
    if not isinstance(cases, list) or not cases:
        raise ValueError('Manifest must contain a nonempty cases list')
    ids = [c.get('id') for c in cases]
    if any(not isinstance(i, str) or not i.strip() for i in ids) or len(set(ids)) != len(ids):
        raise ValueError('Case IDs must be unique nonempty strings')
    for case in cases:
        expected = case.get('expected', {})
        if not isinstance(expected, dict) or any(k not in FIELDS or not isinstance(v, str) or not v for k, v in expected.items()):
            raise ValueError('Expected answers must be nonempty strings for supported fields')
        if bool(case.get('report')) == bool(case.get('front')):
            raise ValueError('Each case needs either report OR front/back input')
        if case.get('front') and not case.get('back'):
            raise ValueError('Image cases require front and back')
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    results = []
    pipelines = {}
    for index, case in enumerate(cases):
        print(f'Case {index + 1}/{len(cases)}', flush=True)
        try:
            if case.get('report'):
                report = json.loads((base / case['report']).read_text(encoding='utf-8-sig'))
                mode = 'existing_report'
            else:
                profile = str((base / manifest['profile']).resolve())
                if profile not in pipelines:
                    pipelines[profile] = Pipeline(load_profile(profile), output / 'reader-runs')
                pipeline = pipelines[profile]
                limit = pipeline.profile.get('limits', {}).get('max_bytes', 8388608) if hasattr(pipeline, 'profile') else 8388608
                inputs = []
                for side in ('FRONT', 'BACK'):
                    with (base / case[side.lower()]).open('rb') as f:
                        inputs.append(InputImage(side, f.read(limit + 1), side.lower()))
                corners = json.loads((base / case['corners']).read_text(encoding='utf-8-sig')) if case.get('corners') else None
                report = pipeline.run(inputs, diagnostic=True, save_ocr_evidence=False, corners=corners)
                mode = 'inference'
            result = evaluate_report(report, case.get('expected', {}))
            result.update(id=case['id'], status='DONE', mode=mode)
        except Exception as error:
            result = {'id': case['id'], 'status': 'ERROR', 'exception_type': type(error).__name__}
        results.append(result)
    payload = {'schemaVersion': 'reader-evaluation/0.1', 'summary': summarize(results), 'cases': results,
               'timingNote': 'First inference includes initialization; later cases reuse model. Existing-report timings are historical, not rerun measurements.'}
    (output / 'evaluation.json').write_text(dumps(payload), encoding='utf-8')
    columns = ['case_id', 'case_status', 'field', 'labeled', 'expected', 'ocr_value', 'extraction_status', 'exact_match', 'unaccented_match', 'confidence', 'qr_state', 'qr_parsed', 'elapsedMs', 'ocr_ms', 'qr_ms', 'exception_type']
    with (output / 'evaluation.csv').open('w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=columns)
        writer.writeheader()
        for case in results:
            for row in case.get('fields', [{}]):
                writer.writerow({**{k:v for k,v in row.items() if k in columns}, 'case_id': case['id'], 'case_status': case['status'], 'qr_state': case.get('qr_state'), 'qr_parsed': case.get('qr_parsed'), 'elapsedMs': case.get('elapsedMs'), 'ocr_ms': sum(t.get('duration_ms') or 0 for t in case.get('timings', []) if t['check'] == 'OCR') if case.get('timings') else None, 'qr_ms': sum(t.get('duration_ms') or 0 for t in case.get('timings', []) if t['check'] == 'QR') if case.get('timings') else None, 'exception_type': case.get('exception_type')})
    print(output / 'evaluation.json')
    return 1 if any(c['status'] == 'ERROR' for c in results) else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--output', required=True, help='New output directory; never overwrite previous evaluation')
    args = parser.parse_args()
    sys.exit(run(args.manifest, args.output))
