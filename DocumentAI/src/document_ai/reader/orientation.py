"""Bounded four-way OCR orientation probe. Text clues, not line-count voting."""
import math
from document_ai.name_comparison import unaccent_name

CLUES=(
 ('gplx_title',("DRIVER'S LICENSE",'DRIVING LICEN','GIAY PHEP LAI XE'),4),
 ('cccd_title',('CAN CUOC CONG DAN','CITIZEN IDENTITY','THE CAN CUOC'),4),
 ('gplx_back',('CLASSIFICATION OF MOTOR VEHICLES','CAC LOAI XE','BEGINNING DATE','NGAY TRUNG TUYEN','XE MOTO'),4),
 ('cccd_back',('DAC DIEM NHAN DANG','PERSONAL IDENTIFICATION'),3),
 ('full_name',('FULL NAME','HO VA TEN','HO TEN'),1),
 ('birth',('DATE OF BIRTH','NGAY SINH'),1),
 ('class',('HANG/CLASS','HANG / CLASS'),1),
 ('nationality',('NATIONALITY','QUOC TICH'),1),
 ('expiry',('EXPIRES','DATE OF EXPIRY','CO GIA TRI DEN'),1),
 ('residence',('PLACE OF RESIDENCE','NOI THUONG TRU'),1),
 ('issue',('DATE OF ISSUE','NGAY CAP'),1))


def horizontal_support(lines):
    """Use text-line quadrilaterals to distinguish OCR's vertical-text capability.
    Missing geometry supplies no evidence. Confidence is not authenticity.
    """
    weights=[]
    for line in lines:
        p=line.get('polygon')
        if not p or len(p)!=4:continue
        try:
            x=float(p[1][0])-float(p[0][0]); y=float(p[1][1])-float(p[0][1])
            if not math.isfinite(x+y) or x*x+y*y<1:continue
            horizontal=abs(x)/math.hypot(x,y)
            confidence=max(0.,min(1.,float(line.get('confidence',0.))))
            weights.append((horizontal,confidence))
        except (TypeError,ValueError,IndexError):continue
    total=sum(w for _,w in weights)
    return sum(h*w for h,w in weights)/total if total else None


def resolve_orientation(image,ocr,card_rectified=False,fast=False,header_probe=False):
    if header_probe and card_rectified:
        class HeaderProbe:
            def __init__(self):self.observations=[]
            def extract(self,work):
                crop=work.crop((0,0,work.width,max(2,round(work.height*.40))))
                lines=ocr.extract(crop)
                self.observations.append({'imageSize':list(work.size),'cropSize':list(crop.size),'lines':lines})
                return lines
        probe=HeaderProbe()
        header=resolve_orientation(image,probe,card_rectified=True,fast=fast)
        header['headerObservations']=probe.observations
        if header['status']=='RESOLVED':
            header['strategy']='HEADER_40_PERCENT'
            return header
        full=resolve_orientation(image,ocr,card_rectified=card_rectified,fast=fast)
        full['strategy']='FULL_IMAGE_FALLBACK'
        full['headerProbeAttempts']=header['attempts']
        full['headerObservations']=probe.observations
        return full
    attempts=[];runs=[];expected_runs=0
    for angle in (0,90,180,270):
        work=image if angle==0 else image.rotate(angle,expand=True)
        if fast and card_rectified and not work.width>work.height*1.15:
            attempts.append({'rotationCounterClockwise':angle,'status':'SKIPPED',
                             'reason':'RECTIFIED_CARD_LANDSCAPE_REQUIRED'})
            continue
        expected_runs+=1
        try:
            lines=ocr.extract(work)
            text=' '.join(unaccent_name(line['text']) for line in lines)
            hits=[(name,weight) for name,aliases,weight in CLUES if any(alias in text for alias in aliases)]
            from .document_titles import title_evidence
            title=title_evidence(lines)
            if title['status']=='IDENTIFIED':
                name='gplx_title' if title['documentType']=='VN_DRIVING_LICENSE' else 'cccd_title'
                if not any(n==name for n,_ in hits):hits.append((name,4))
            score=sum(weight for _,weight in hits)
            attempts.append({'rotationCounterClockwise':angle,'status':'PASS','clues':[name for name,_ in hits],'evidenceWeight':score,'horizontalSupport':horizontal_support(lines)})
            runs.append((score,angle,work,lines))
        except Exception as error:attempts.append({'rotationCounterClockwise':angle,'status':'ERROR','exceptionType':type(error).__name__})
    if expected_runs==0:return {'status':'INCONCLUSIVE','attempts':attempts,'reason':'NO_PLAUSIBLE_CARD_ORIENTATION','image':image,'lines':[],'rotationApplied':None}
    if not runs:return {'status':'ERROR','attempts':attempts,'reason':'ALL_ORIENTATION_PROBES_FAILED'}
    eligible=runs
    # Supported physical card profiles are landscape after rectification, independent
    # of the FULL photograph's aspect ratio. Do not apply to uncropped fallbacks.
    if card_rectified:
        eligible=[run for run in runs if run[2].width>run[2].height*1.15]
    if not eligible:
        return {'status':'INCONCLUSIVE','attempts':attempts,'reason':'NO_PLAUSIBLE_CARD_ORIENTATION','image':image,'lines':[],'rotationApplied':None}
    ranked=sorted(eligible,key=lambda run:run[0],reverse=True);best=ranked[0]
    # Every eligible probe must complete. Only already-ineligible portrait candidates may be skipped.
    unique=len(runs)==expected_runs and best[0]>=2 and (len(ranked)==1 or best[0]>ranked[1][0])
    if not unique and len(runs)==expected_runs and best[0]>=2:
        tied=[run for run in ranked if run[0]==best[0]]
        evidence=[(horizontal_support(run[3]),run) for run in tied]
        # Geometry tie-break only when every tied candidate has measured geometry.
        if len(tied)>1 and all(h is not None for h,_ in evidence):
            evidence.sort(key=lambda item:item[0],reverse=True)
            if evidence[0][0]>=.85 and evidence[0][0]-evidence[1][0]>=.20:
                best=evidence[0][1];unique=True
    if not unique:return {'status':'INCONCLUSIVE','attempts':attempts,'reason':'ORIENTATION_NOT_UNIQUE_OR_INSUFFICIENT','image':image,'lines':[],'rotationApplied':None}
    return {'status':'RESOLVED','rotationApplied':best[1],'image':best[2],'lines':best[3],'attempts':attempts,'readyForOcr':True,'selectionBasis':'RECTIFIED_CARD_LANDSCAPE_AND_TEXT' if card_rectified else 'TEXT_AND_LINE_GEOMETRY'}
