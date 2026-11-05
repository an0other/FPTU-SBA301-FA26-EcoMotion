"""Private JSON-lines worker. Keep model/library stdout away from protocol."""
import argparse
import contextlib
import json
import os
import sys
import time
import traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--model',choices=['vgg_seq2seq','vgg_transformer'],required=True)
    parser.add_argument('--device',default='cpu')
    parser.add_argument('--cpu-threads',type=int,default=4)
    parser.add_argument('--batch-size',type=int,default=1)
    args=parser.parse_args()
    # Imports and lazy model construction happen inside redirected stdout.
    with contextlib.redirect_stdout(sys.stderr):
        import torch
        use_cuda=args.device.startswith('cuda')
        if use_cuda and not torch.cuda.is_available():raise RuntimeError('CUDA requested but unavailable; use a CUDA PyTorch build or choose cpu')
        if use_cuda:torch.cuda.set_device(torch.device(args.device))
        if args.device=='cpu':
            if not 1<=args.cpu_threads<=32:raise ValueError('cpu-threads must be 1..32')
            torch.set_num_threads(args.cpu_threads)
        print('VietOCR runtime: device='+args.device+', intraopThreads='+str(torch.get_num_threads()),file=sys.stderr)
        from document_ai.adapters.ocr import VietOcrRecognizer
        from document_ai.reader.crop_transport import decode_crops
        recognizer=VietOcrRecognizer(args.device,args.model)
    for line in sys.stdin:
        request={}
        try:
            request=json.loads(line);rows=[]
            with contextlib.redirect_stdout(sys.stderr):
                images=decode_crops(request['images'])
                initialize_started=time.perf_counter();cold=recognizer._predictor is None
                recognizer._ensure_predictor()
                if use_cuda:torch.cuda.synchronize()
                initialize_ms=(time.perf_counter()-initialize_started)*1000
                inference_started=time.perf_counter()
                if args.batch_size>1:rows=recognizer.recognize_batch(images,args.batch_size,tolerate_invalid=True,max_single_retries=int(request.get('recoveryLimit',4)))
                else:
                    for image in images:
                        start=time.perf_counter()
                        try:
                            text,confidence=recognizer.recognize(image)
                            rows.append({'text':text,'confidence':confidence,'elapsedMs':round((time.perf_counter()-start)*1000,3)})
                        except ValueError as error:
                            if str(error)!='Invalid recognizer confidence':raise
                            rows.append({'text':'','confidence':None,'status':'ERROR','errorType':'INVALID_RECOGNIZER_CONFIDENCE'})
                if use_cuda:torch.cuda.synchronize()
                inference_ms=(time.perf_counter()-inference_started)*1000
            runtime={'coldModel':cold,'initializationMs':round(initialize_ms,2),'inferenceMs':round(inference_ms,2),
                     'torchVersion':torch.__version__,'cudaBuild':torch.version.cuda,'device':args.device,
                     'intraopThreads':torch.get_num_threads(),'cudaAvailable':torch.cuda.is_available(),'batchSize':args.batch_size,
                     'singleRetryCount':getattr(recognizer,'last_single_retry_count',0),
                     'singleCropRecoveryBackend':'REFERENCE_PREDICTOR_FP32_SAME_DEVICE',
                     'failedCropIndexes':[i for i,row in enumerate(rows) if row.get('status')=='ERROR'],
                     'transport':request.get('transport','png'),'batchGroups':getattr(recognizer,'last_batch_groups',[]),
                     'experimentSettings':{key:os.getenv(key,default) for key,default in [('DOCUMENT_AI_BATCH_POLICY','legacy'),('DOCUMENT_AI_DECODE_BACKEND','legacy'),('DOCUMENT_AI_PRECISION','fp32'),('DOCUMENT_AI_PREPROCESSOR','numpy')]}}
            if use_cuda:
                torch.cuda.synchronize()
                runtime.update(gpuName=torch.cuda.get_device_name(),
                    allocatedMb=round(torch.cuda.memory_allocated()/1048576,1),
                    reservedMb=round(torch.cuda.memory_reserved()/1048576,1))
            for i,row in enumerate(rows):
                if row.get('status')=='ERROR':
                    print(json.dumps({'workerRequestId':request['id'],'cropIndex':i,'errorType':row.get('errorType'),'confidenceValid':False}),file=sys.stderr)
            reply={'id':request['id'],'status':'OK','results':rows,'runtime':runtime}
        except Exception as exc:
            traceback.print_exc(file=sys.stderr)
            reply={'id':request.get('id'),'status':'ERROR','errorType':type(exc).__name__}
        print(json.dumps(reply,ensure_ascii=True),flush=True)


if __name__=='__main__':main()
