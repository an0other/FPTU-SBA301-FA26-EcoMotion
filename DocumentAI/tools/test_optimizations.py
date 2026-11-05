"""One invocation, isolated benchmark processes, no automatic model selection."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import sys

VARIANTS = [
    ('baseline', 1, 'png', 'full', 'full'),
    ('batch-memory', 4, 'raw', 'full', 'full'),
    ('combined', 4, 'raw', 'fields', 'adaptive'),
]


def snapshot(response):
    return {
        'data': response.get('data', {}),
        'additionalData': response.get('additionalData', {}),
        'ocrCandidates': {k: (v.get('candidate') or {}).get('value')
                          for k,v in response.get('ocrData', {}).items()},
        'qrPayloads': sorted({d['rawPayload'] for d in response.get('qrData', {}).get('documents', [])}),
        'status': response.get('status'),
        'statusReasons': response.get('statusReasons', []),
    }


def differences(reference, actual):
    left, right = snapshot(reference), snapshot(actual)
    return {k: {'reference': left[k], 'actual': right[k]} for k in left if left[k] != right[k]}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for key in ('front', 'back', 'corner-weights', 'vietocr-python', 'output'):
        p.add_argument('--'+key, required=True)
    p.add_argument('--gpu-python')
    p.add_argument('--profile', default='configs/gplx-fields.pilot.json')
    p.add_argument('--reference-response')
    p.add_argument('--repeats', type=int, default=3)
    p.add_argument('--cpu-threads', type=int, default=4)
    args = p.parse_args()
    if not 2 <= args.repeats <= 20: p.error('repeats must be 2..20')
    if not 1 <= args.cpu_threads <= 32: p.error('cpu-threads must be 1..32')
    for path in [args.front, args.back, args.corner_weights, args.vietocr_python, args.profile,
                 *([args.reference_response] if args.reference_response else []),
                 *([args.gpu_python] if args.gpu_python else [])]:
        if not Path(path).is_file(): p.error('File not found: '+path)
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    baseline_path = Path(args.reference_response).resolve() if args.reference_response else None
    benchmark = Path(__file__).with_name('benchmark_reader_latency.py')
    devices = [('cpu', args.vietocr_python)]
    if args.gpu_python: devices.append(('cuda:0', args.gpu_python))
    rows = []
    for device, interpreter in devices:
        for name, batch, transport, scope, qr in VARIANTS:
            label = ('gpu' if device.startswith('cuda') else 'cpu')+'-'+name
            folder = output/label
            command = [sys.executable, '-u', str(benchmark), '--front', args.front, '--back', args.back,
                       '--corner-weights', args.corner_weights, '--vietocr-python', interpreter,
                       '--profile', args.profile, '--recognizer-device', device,
                       '--performance-mode', 'optimized', '--cpu-threads', str(args.cpu_threads),
                       '--repeats', str(args.repeats), '--batch-size', str(batch),
                       '--crop-transport', transport, '--recognition-scope', scope, '--qr-search', qr,
                       '--output', str(folder)]
            if baseline_path: command += ['--reference-response', str(baseline_path)]
            print('\n=== '+label+' ===', flush=True)
            with (output/(label+'.log')).open('w', encoding='utf-8') as log:
                with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                      text=True, encoding='utf-8', errors='replace', shell=False) as child:
                    for line in child.stdout:
                        print(line, end='', flush=True); log.write(line); log.flush()
                    code = child.wait()
            row = {'case': label, 'device': device, 'batchSize': batch, 'cropTransport': transport,
                   'recognitionScope': scope, 'qrSearch': qr, 'exitCode': code,
                   'firstRequestMs': None, 'warmMedianMs': None, 'processingErrors': None,
                   'changedRuns': None, 'comparisonAvailable': False, 'runComparisons': []}
            if (folder/'benchmark.json').exists():
                report = json.loads((folder/'benchmark.json').read_text(encoding='utf-8'))
                row.update({k: report['summary'].get(k) for k in
                            ('firstRequestMs','warmMedianMs','processingErrors')})
                if baseline_path is None and device == 'cpu' and name == 'baseline' and code == 0:
                    baseline_path = folder/'response-02.json'
                if baseline_path:
                    reference = json.loads(baseline_path.read_text(encoding='utf-8-sig'))
                    for item in report['runs']:
                        response = json.loads((folder/f"response-{item['iteration']:02}.json").read_text(encoding='utf-8'))
                        row['runComparisons'].append({'iteration': item['iteration'],
                            'differences': differences(reference, response),
                            'status': response['status'], 'warnings': response.get('warnings', []),
                            'qrSearchCoverage': response.get('qrData', {}).get('searchCoverage'),
                            'recognitionScopeReport': response.get('recognitionScopeReport', [])})
                    row['comparisonAvailable'] = True
                    row['changedRuns'] = sum(bool(v['differences']) for v in row['runComparisons'])
            rows.append(row)
            (output/'matrix.json').write_text(json.dumps({
                'schemaVersion': 'reader-optimization-matrix/0.1', 'cases': rows,
                'referenceResponse': str(baseline_path) if baseline_path else None,
                'note': 'Output equivalence, not independent accuracy. No automatic winner. Combined limits QR search and optional OCR coverage. Warm medians from only two requests at repeats=3.'
            }, ensure_ascii=False, indent=2), encoding='utf-8')
            with (output/'matrix.csv').open('w', encoding='utf-8-sig', newline='') as stream:
                keys = [k for k in row if k != 'runComparisons']
                writer = csv.DictWriter(stream, fieldnames=keys)
                writer.writeheader(); writer.writerows({k:r[k] for k in keys} for r in rows)
    print('\n'+str(output/'matrix.json'), flush=True)
    return 1 if any(row['exitCode'] or row['processingErrors'] for row in rows) else 0


if __name__ == '__main__': sys.exit(main())
