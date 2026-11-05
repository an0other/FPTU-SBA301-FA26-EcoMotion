"""Small real HTTP test against an already warm local API; no response caching."""
import argparse,json,os,time,urllib.request,urllib.error,uuid
from pathlib import Path


def load_api_key():
    env_file=Path(__file__).resolve().parents[1]/'.env'
    if env_file.is_file():
        for line in env_file.read_text(encoding='utf-8-sig').splitlines():
            entry=line.strip()
            if not entry or entry.startswith('#') or '=' not in entry:
                continue
            name,value=entry.split('=',1)
            if name.strip()!='DOCUMENT_AI_API_KEY':
                continue
            value=value.strip()
            if len(value)>=2 and value[0]==value[-1] and value[0] in {"'",'"'}:
                value=value[1:-1]
            if value:
                os.environ['DOCUMENT_AI_API_KEY']=value
                return value
    return os.getenv('DOCUMENT_AI_API_KEY','')


def call(base,path,method='GET',payload=None,timeout=60):
    api_key=load_api_key()
    if len(api_key)<32:raise RuntimeError('Set DOCUMENT_AI_API_KEY in the local .env file or process environment.')
    headers={'X-API-Key':api_key};body=None
    if payload is not None:
        boundary='DocumentAI'+uuid.uuid4().hex;parts=[]
        for name,value in payload.items():
            parts.append(('--'+boundary+'\r\n').encode())
            if isinstance(value,bytes):
                parts.append((f'Content-Disposition: form-data; name="{name}"; filename="{name}.jpg"\r\nContent-Type: application/octet-stream\r\n\r\n').encode());parts.append(value)
            else:parts.append((f'Content-Disposition: form-data; name="{name}"\r\n\r\n'+value).encode())
            parts.append(b'\r\n')
        parts.append(('--'+boundary+'--\r\n').encode());body=b''.join(parts)
        headers['Content-Type']='multipart/form-data; boundary='+boundary
    request=urllib.request.Request(base.rstrip('/')+path,data=body,headers=headers,method=method)
    began=time.perf_counter()
    try:
        with urllib.request.urlopen(request,timeout=timeout) as response:return response.status,json.load(response),(time.perf_counter()-began)*1000
    except urllib.error.HTTPError as error:
        return error.code,json.loads(error.read()),(time.perf_counter()-began)*1000


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base-url',default='http://127.0.0.1:8001');p.add_argument('--output',required=True)
    for key,default in [('gplx-front','inputs/gplx-test-front.JPEG'),('gplx-back','inputs/gplx-test-back.JPEG'),('cccd-front','inputs/cccd-front.jpeg'),('cccd-back','inputs/cccd-back.jpeg')]:p.add_argument('--'+key,default=default)
    a=p.parse_args()
    if len(load_api_key())<32:
        p.error('Set DOCUMENT_AI_API_KEY in the local .env file or process environment.')
    output=Path(a.output);output.mkdir(parents=True,exist_ok=False)
    code,ready,_=call(a.base_url,'/ready')
    if code!=200 or ready.get('status')!='READY':raise RuntimeError('API not ready. Start serve-v32 and wait for warm-up.')
    code,capabilities,_=call(a.base_url,'/v1/capabilities')
    if code!=200 or capabilities.get('version')!='0.32.0':raise RuntimeError('Expected DocumentAI 0.32.0. Restart server after installing source.')
    inputs={k:Path(getattr(a,k)).read_bytes() for k in ('gplx_front','gplx_back','cccd_front','cccd_back')}
    cases=[]
    def save():
        (output/'api-test.json').write_text(json.dumps({'schemaVersion':'api-pipeline-test/0.1','capabilities':capabilities,'cases':cases,
            'note':'HTTP 202 is submission only. Job wall time includes polling. Equality is stability, not ground truth. First CCCD request is separately visible, not a warm latency guarantee.'},ensure_ascii=False,indent=2),encoding='utf-8')
    for name,kind in [('gplx','VN_DRIVING_LICENSE'),('cccd','VN_CITIZEN_ID')]:
        payload={'front':inputs[name+'_front'],'back':inputs[name+'_back'],'document_type':kind}
        code,result,ms=call(a.base_url,'/v1/documents/read','POST',payload)
        (output/(name+'-read.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        ok=code==200 and result.get('status') in {'READ_SUCCESS','REVIEW_REQUIRED'} and bool(result.get('pipelineReport'))
        cases.append({'case':name+'-read','passed':ok,'httpStatus':code,'wallMs':round(ms,2),'readingStatus':result.get('status'),'imageAnalysis':result.get('imageAnalysis',{}).get('status')});save()
        started=time.perf_counter();code,job,submitted=call(a.base_url,'/v1/documents/jobs','POST',payload)
        if code!=202:cases.append({'case':name+'-jobs','passed':False,'httpStatus':code});save();continue
        deadline=time.monotonic()+180;status={}
        while time.monotonic()<deadline:
            _,status,_=call(a.base_url,job['pollUrl'])
            if status.get('state') in {'COMPLETED','FAILED'}:break
            time.sleep(.5)
        completed=status.get('result') or {}
        (output/(name+'-job.json')).write_text(json.dumps(status,ensure_ascii=False,indent=2),encoding='utf-8')
        values=lambda r:{k:(v.get('candidate') or {}).get('value') for k,v in r.get('ocrData',{}).items()}
        same=values(completed)==values(result)
        cases.append({'case':name+'-jobs','passed':status.get('state')=='COMPLETED' and same and bool(completed.get('pipelineReport')),
            'state':status.get('state'),'submissionMs':round(submitted,2),'wallUntilPollCompletionMs':round((time.perf_counter()-started)*1000,2),
            'sameOcrValuesAsRead':same,'readingStatus':completed.get('status')});save()
    for name,front,back in [('mixed',inputs['cccd_front'],inputs['gplx_back']),('duplicate',inputs['cccd_front'],inputs['cccd_front']),('empty-file',b'',inputs['gplx_back'])]:
        code,result,ms=call(a.base_url,'/v1/documents/read','POST',{'front':front,'back':back,'document_type':'AUTO'})
        (output/(name+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        cases.append({'case':name,'passed':code==200 and result.get('status')=='RETAKE_REQUIRED','httpStatus':code,'readingStatus':result.get('status'),'wallMs':round(ms,2)});save()
    print(output/'api-test.json')
    return int(any(not c['passed'] for c in cases))


if __name__=='__main__':raise SystemExit(main())
