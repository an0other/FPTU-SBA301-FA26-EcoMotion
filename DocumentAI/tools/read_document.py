import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse,json
from document_ai.reader.service import Reader
from document_ai.reader.data import TYPES
p=argparse.ArgumentParser()
p.add_argument('--front',required=True);p.add_argument('--back',required=True)
p.add_argument('--document-type',choices=['AUTO',*TYPES],default='AUTO')
p.add_argument('--output',required=True);p.add_argument('--save-evidence',action='store_true')
p.add_argument('--recognizer',choices=['paddle','vietocr-seq2seq','vietocr-transformer'],default='vietocr-transformer');p.add_argument('--vietocr-python',default=str(Path(__file__).resolve().parents[1]/'.venv-vietocr'/('Scripts/python.exe' if sys.platform=='win32' else 'bin/python')));p.add_argument('--recognizer-device',default='cpu');p.add_argument('--no-qr',action='store_true');p.add_argument('--performance-mode',choices=['standard','balanced','optimized'],default='balanced');p.add_argument('--vietocr-cpu-threads',type=int,default=4);
p.add_argument('--batch-size',type=int,default=1);p.add_argument('--crop-transport',choices=['png','raw'],default='png');p.add_argument('--recognition-scope',choices=['full','fields'],default='full');p.add_argument('--qr-search',choices=['full','adaptive'],default='full');
p.add_argument('--field-profile',default=str(Path(__file__).resolve().parents[1]/'configs/gplx-fields.pilot.json'));p.add_argument('--corner-weights');p.add_argument('--localizer-device',default='cpu');p.add_argument('--allow-legacy-pose',action='store_true');a=p.parse_args();out=Path(a.output);out.mkdir(parents=True,exist_ok=False)
def read(path):
    with Path(path).open('rb') as f:return f.read(8388609)
kwargs={'field_profile':a.field_profile,'qr_enabled':not a.no_qr,'performance_mode':a.performance_mode,'recognition_scope':a.recognition_scope,'qr_search':a.qr_search}
engine=None
import os
if os.getenv('DOCUMENT_AI_PIPELINE_MODE','staged')=='staged':
    from document_ai.reader.factory import configured_reader
    os.environ.update(DOCUMENT_AI_RECOGNIZER=a.recognizer,DOCUMENT_AI_RECOGNIZER_DEVICE=a.recognizer_device,
        DOCUMENT_AI_LOCALIZER_DEVICE=a.localizer_device,DOCUMENT_AI_VIETOCR_PYTHON=a.vietocr_python,
        DOCUMENT_AI_FIELD_PROFILE=a.field_profile,DOCUMENT_AI_PERFORMANCE_MODE=a.performance_mode,
        DOCUMENT_AI_BATCH_SIZE=str(a.batch_size),DOCUMENT_AI_CROP_TRANSPORT=a.crop_transport,
        DOCUMENT_AI_RECOGNITION_SCOPE=a.recognition_scope,DOCUMENT_AI_QR_SEARCH=a.qr_search,
        DOCUMENT_AI_QR_ENABLED='0' if a.no_qr else '1',DOCUMENT_AI_VIETOCR_CPU_THREADS=str(a.vietocr_cpu_threads),
        DOCUMENT_AI_VIETOCR_LOG=str(out/'vietocr-worker.log'),DOCUMENT_AI_ALLOW_LEGACY_POSE='1' if a.allow_legacy_pose else '0')
    if a.corner_weights:os.environ['DOCUMENT_AI_CORNER_WEIGHTS']=a.corner_weights
    service=configured_reader();engine=service.ocr
elif a.recognizer!='paddle':
    if not a.vietocr_python:p.error('--vietocr-python is required for VietOCR')
    from document_ai.adapters.ocr import create_ocr
    from document_ai.reader.service import DEFAULT_OCR
    from document_ai.reader.vietocr_pipeline import VietOcrPipeline,VietOcrProcess
    engine=VietOcrPipeline(create_ocr(DEFAULT_OCR),VietOcrProcess(
        a.vietocr_python,'vgg_seq2seq' if a.recognizer=='vietocr-seq2seq' else 'vgg_transformer',
        a.recognizer_device,log=out/'vietocr-worker.log',cpu_threads=a.vietocr_cpu_threads,batch_size=a.batch_size,transport=a.crop_transport))
    if a.performance_mode=='optimized':
        from document_ai.reader.vietocr_pipeline import DetectorOnlyVietOcrPipeline
        from document_ai.reader.text_regions import PaddleTextRegions
        engine=DetectorOnlyVietOcrPipeline(engine.primary,engine.recognizer,PaddleTextRegions())
    kwargs['ocr']=engine
if os.getenv('DOCUMENT_AI_PIPELINE_MODE','staged')=='legacy' and a.corner_weights:
    from document_ai.reader.localizers import load_locator
    kwargs['locator']=load_locator(a.corner_weights,device=a.localizer_device,allow_legacy_pose=a.allow_legacy_pose)
try:
    r=(service if os.getenv('DOCUMENT_AI_PIPELINE_MODE','staged')=='staged' else Reader(**kwargs)).read(read(a.front),read(a.back),a.document_type,out/'evidence' if a.save_evidence else None)
finally:
    close=getattr(engine,'close',None)
    if close:close()
(out/'response.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':r['status'],'statusReasons':r.get('statusReasons',[]),'approvalStatus':r.get('assessment',{}).get('approval',{}).get('status'),'documentType':r['documentType'],'response':str(out/'response.json')},ensure_ascii=False))
sys.exit(1 if r['status']=='PROCESSING_ERROR' else 0)
