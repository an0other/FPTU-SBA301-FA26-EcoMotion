"""Run complete reader variants in isolated processes, on identical original files."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--front',required=True);parser.add_argument('--back',required=True)
    parser.add_argument('--python',required=True);parser.add_argument('--vietocr-python',required=True)
    parser.add_argument('--corner-weights',required=True);parser.add_argument('--field-profile')
    parser.add_argument('--document-type',default='VN_DRIVING_LICENSE',choices=['VN_DRIVING_LICENSE','VN_CITIZEN_ID','AUTO'])
    parser.add_argument('--output',required=True);parser.add_argument('--with-qr',action='store_true')
    parser.add_argument('--localizer-device',default='cpu');parser.add_argument('--recognizer-device',default='cpu')
    args=parser.parse_args()
    out=Path(args.output).resolve();out.mkdir(parents=True,exist_ok=False)
    front=Path(args.front).resolve();back=Path(args.back).resolve()
    hashes={side:hashlib.sha256(path.read_bytes()).hexdigest() for side,path in [('FRONT',front),('BACK',back)]}
    rows=[]
    tool=Path(__file__).with_name('read_document.py')
    for engine in ['paddle','vietocr-seq2seq','vietocr-transformer']:
        print('Full reader: '+engine,flush=True)
        folder=out/engine
        command=[str(Path(args.python).resolve()),str(tool),'--front',str(front),'--back',str(back),
            '--document-type',args.document_type,'--output',str(folder),'--save-evidence',
            '--corner-weights',str(Path(args.corner_weights).resolve()),'--localizer-device',args.localizer_device,
            '--recognizer',engine,'--vietocr-python',str(Path(args.vietocr_python).resolve()),
            '--recognizer-device',args.recognizer_device]
        if args.field_profile:command+=['--field-profile',str(Path(args.field_profile).resolve())]
        if not args.with_qr:command+=['--no-qr']
        row={'engine':engine,'response':str(folder/'response.json')}
        try:
            with (out/(engine+'.log')).open('w',encoding='utf-8') as log:
                process=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,shell=False)
            row['exitCode']=process.returncode
            if (folder/'response.json').exists():
                data=json.loads((folder/'response.json').read_text(encoding='utf-8'))
                if {v['side']:v['sha256'] for v in data['images']}!=hashes:
                    raise ValueError('Input hash mismatch')
                row.update(status=data['status'],statusReasons=data.get('statusReasons',[]),data=data['data'],additionalData=data['additionalData'],assessment=data['assessment'],qrData=data['qrData'],qrMetadata=data['qrMetadata'],ocrData=data['ocrData'],
                           coreComparison=data['coreComparison'],warnings=data['warnings'],
                           elapsedMs=data['elapsedMs'],checks=data['checks'],readerConfig=data['readerConfig'])
            else:row.update(status='PROCESSING_ERROR',errorType='NO_RESPONSE_INSPECT_LOG')
        except (OSError,ValueError,KeyError) as exc:
            row.update(status='PROCESSING_ERROR',errorType=type(exc).__name__)
        rows.append(row)
        report={'schemaVersion':'full-reader-comparison/0.1','inputHashes':hashes,
                'qrEnabled':args.with_qr,'results':rows,
                'note':'Same input files, separate cold reader processes. No ground-truth accuracy computed. Model agreement is not correctness. elapsedMs includes localization, orientation, recognition and optional QR; first model calls may include weight downloads.'}
        (out/'comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(engine+': '+row['status'],flush=True)
    print(out/'comparison.json')
    return 1 if any(v['status']=='PROCESSING_ERROR' for v in rows) else 0


if __name__=='__main__':sys.exit(main())
