"""Resident text detector subprocess. Standard output is JSON protocol only."""
import argparse,base64,contextlib,json,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))


def send(value):print(json.dumps(value,allow_nan=False),flush=True)


def main():
    p=argparse.ArgumentParser();p.add_argument('--device',default='cpu');p.add_argument('--model',default='PP-OCRv6_tiny_det')
    a=p.parse_args();started=time.perf_counter()
    try:
        with contextlib.redirect_stdout(sys.stderr):
            from document_ai.reader.text_regions import PaddleTextRegions
            from PIL import Image
            detector=PaddleTextRegions(device=a.device,model_name=a.model);detector.ensure_model()
        send({'type':'READY','startupMs':round((time.perf_counter()-started)*1000,2)})
    except Exception as error:
        traceback.print_exc(file=sys.stderr);send({'type':'STARTUP_ERROR','errorType':type(error).__name__});return 1
    for line in sys.stdin:
        request={}
        try:
            request=json.loads(line);key=request['id'];w,h=request['width'],request['height']
            if type(w) is not int or type(h) is not int or not 2<=w<=2560 or not 2<=h<=2560:raise ValueError('Invalid detector dimensions')
            pixels=base64.b64decode(request['rgb'],validate=True)
            if len(pixels)!=w*h*3:raise ValueError('Invalid RGB byte count')
            image=Image.frombytes('RGB',(w,h),pixels)
            def progress(stage):send({'type':'STAGE','id':key,'stage':stage})
            with contextlib.redirect_stdout(sys.stderr):
                # Progress explicitly writes to original stdout while model stdout stays in the log.
                def progress(stage):
                    stream=sys.__stdout__;stream.write(json.dumps({'type':'STAGE','id':key,'stage':stage})+'\n');stream.flush()
                regions=detector.detect(image,progress=progress)
            send({'type':'RESULT','id':key,'regions':regions,'timing':detector.last_timing})
        except Exception as error:
            traceback.print_exc(file=sys.stderr)
            send({'type':'ERROR','id':request.get('id'),'errorType':type(error).__name__})
    return 0


if __name__=='__main__':sys.exit(main())
