"""Explicit QR formats, conservative label extraction, source-aware fusion."""
import re,os
from datetime import datetime
from document_ai.name_comparison import unaccent_name
from document_ai.document_comparison import extract_pilot,parse_qr

TYPES=('VN_DRIVING_LICENSE','VN_CITIZEN_ID')
COMMON=('documentNumber','fullName','dateOfBirth','nationality','address','issueDate','expiryDate')
EXTRA={'VN_DRIVING_LICENSE':('licenseClass',), 'VN_CITIZEN_ID':('previousId','sex','placeOfOrigin')}


def date(text):
    for fmt in ('%d/%m/%Y','%d%m%Y','%Y-%m-%d'):
        try:return datetime.strptime(text.strip(),fmt).date().isoformat()
        except ValueError:pass
    return None


def qr_parse(payload):
    if '\n' in payload:
        old=parse_qr([payload])
        if old['status']!='PARSED':return None
        fields=dict(old['fields']);fields['documentNumber']=fields.pop('licenseNo')
        unmapped=fields.pop('regionRaw',None)
        return {'documentType':TYPES[0],'schema':'gplx-five-lines/v1','fields':fields,
                'unmappedFields':[{'position':5,'rawValue':unmapped,'semanticStatus':'UNCONFIRMED'}] if unmapped is not None else []}
    parts=payload.split('|')
    if len(parts)!=7:return None
    number,previous,name,birth,sex,address,issued=[p.strip() for p in parts]
    if not re.fullmatch('[0-9]{12}',number) or previous and not re.fullmatch('[0-9]{9,12}',previous):return None
    if not name or not date(birth) or not date(issued) or unaccent_name(sex) not in {'NAM','NU'}:return None
    return {'documentType':TYPES[1],'schema':'cccd-seven-pipe-fields/v1','fields':{
        'documentNumber':number,'previousId':previous or None,'fullName':name,'dateOfBirth':date(birth),
        'sex':'MALE' if unaccent_name(sex)=='NAM' else 'FEMALE','address':address or None,'issueDate':date(issued)}}


def title_type(lines):
    from .document_titles import title_evidence
    return title_evidence(lines)['documentType']

ALIASES={
 'documentNumber':('IDENTITY CARD NO','PERSONAL IDENTIFICATION','SO DINH DANH','SO/NO','SO / NO','SO:'),
 'fullName':('FULL NAME','HO VA TEN','HO TEN'),
 'dateOfBirth':('DATE OF BIRTH','NGAY SINH'),
 'sex':('SEX','GIOI TINH'), 'nationality':('NATIONALITY','QUOC TICH'),
 'address':('PLACE OF RESIDENCE','RESIDENCE','NOI THUONG TRU','NOI CU TRU','ADDRESS'),
 'placeOfOrigin':('PLACE OF ORIGIN','QUE QUAN'),
 'issueDate':('DATE OF ISSUE','NGAY CAP'),
 'expiryDate':('DATE OF EXPIRY','EXPIRES','CO GIA TRI DEN'),
 'licenseClass':('CLASS','HANG')}


def printed_labels(text):
    if os.getenv('DOCUMENT_AI_FIELD_LABEL_POLICY','owned')=='legacy':
        flat=unaccent_name(text)
        return {key for key,aliases in ALIASES.items() if any(alias in flat for alias in aliases)}
    from .label_ownership import label_keys
    return label_keys(text,ALIASES)


def normalize_field(key,text):
    text=text.strip(' :/.-')
    if not text:return None
    flat=unaccent_name(text)
    if key=='documentNumber':
        nums=re.findall(r'(?<![0-9])[0-9]{12}(?![0-9])',text);return nums[0] if len(nums)==1 else None
    if key in {'dateOfBirth','issueDate','expiryDate'}:
        if key=='expiryDate' and ('KHONG THOI HAN' in flat or 'UNLIMITED' in flat):return 'UNLIMITED'
        found=re.findall(r'\b\d{2}/\d{2}/\d{4}\b',text);return date(found[0]) if len(found)==1 else None
    if key=='sex':return {'NAM':'MALE','NU':'FEMALE','MALE':'MALE','FEMALE':'FEMALE'}.get(flat)
    if key=='licenseClass':return flat if re.fullmatch('[A-Z][0-9]?',flat) else None
    if key=='nationality':
        if any(c.isdigit() for c in text):return None
        if re.sub(r'\s+','',flat)=='VIETNAM':return 'Việt Nam'
    if key=='fullName' and (any(c.isdigit() for c in text) or any(x in text for x in ':/')):return None
    if printed_labels(text):return None
    return text


def field_neighbors(lines,index,key,overlap=0):
    """Right-of-label first; below only if no same-row value. Never span next label."""
    anchor=lines[index];p=anchor.get('polygon') or []
    if len(p)!=4:return []
    x1=min(q[0] for q in p);x2=max(q[0] for q in p)
    y1=min(q[1] for q in p);y2=max(q[1] for q in p);h=max(1,y2-y1);cy=(y1+y2)/2
    def bounds(line):
        p=line.get('polygon') or []
        if len(p)!=4:return None
        return min(q[0] for q in p),max(q[0] for q in p),min(q[1] for q in p),max(q[1] for q in p)
    following=[];right=[];below=[]
    for j,line in enumerate(lines):
        if j==index or line.get('side')!=anchor.get('side'):continue
        b=bounds(line)
        if b is None:continue
        ox,ex,oy,ey=b;flat=unaccent_name(line['text'])
        labeled=bool(printed_labels(line['text']))
        if labeled:
            if oy>cy:following.append(oy)
            continue
        if ox>=x2-h*.25 and abs((oy+ey)/2-cy)<=h*.55:right.append((j,line,b))
        elif -overlap*h<=oy-y2<=h*1.2 and abs(ox-x1)<=h*2:below.append((j,line,b))
    stop=min(following) if following else float('inf')
    chosen=right if right else [v for v in below if (v[2][2]+v[2][3])/2<stop]
    if not chosen:return []
    if key not in {'address','placeOfOrigin'}:
        chosen.sort(key=lambda v:(abs((v[2][2]+v[2][3])/2-cy),v[2][0]))
        return [(chosen[0][0],chosen[0][1])]
    # Preserve multi-line address pieces as one field, before the next label/date footer.
    bottom=max(v[2][3] for v in chosen)
    used={v[0] for v in chosen}
    for j,line in enumerate(lines):
        if j in used or j==index or line.get('side')!=anchor.get('side'):continue
        b=bounds(line)
        if b is None:continue
        flat=unaccent_name(line['text'])
        if printed_labels(line['text']) or re.search(r'\d{4}',flat) or 'TRUONG PHONG' in flat or 'GIAM DOC' in flat:continue
        ox,ex,oy,ey=b
        if oy>=bottom-h*.3 and oy-bottom<=h*1.25 and (oy+ey)/2<stop and ex>=x1:
            chosen.append((j,line,b))
    chosen.sort(key=lambda v:(round(((v[2][2]+v[2][3])/2-cy)/h),v[2][0]))
    return [(j,line) for j,line,b in chosen]


def extract(lines,kind):
    candidates={k:[] for k in COMMON+EXTRA[kind]}
    def add(key,value,line,index):
        if value is not None:candidates[key].append({'value':value,'rawText':line['text'],'confidence':line.get('confidence'),'lineIndex':index,'side':line.get('side'),'polygon':line.get('polygon'),'recognitionEngine':line.get('recognitionEngine')})
    for i,line in enumerate(lines):
        flat=unaccent_name(line['text'])
        for key,aliases in ALIASES.items():
            if key not in candidates or key not in printed_labels(line['text']):continue
            matched=next((alias for alias in aliases if alias in flat),None)
            if not matched:continue
            # Accent stripping preserves character positions for common Vietnamese text.
            # Prefer English label end and discard slash label remnants conservatively.
            end=flat.find(matched)+len(matched)
            tail=flat[end:].lstrip(' :/')
            raw=line['text'][end:].lstrip(' :/')
            value=normalize_field(key,raw)
            # Observed label "Place of origine" leaves its final e after alias removal.
            # Do not reject short real place names globally (e.g. Mỹ).
            if key=='placeOfOrigin' and raw.strip(' :/.').lower()=='e':value=None
            if value is not None:add(key,value,line,i);continue
            if key in {'nationality','address','placeOfOrigin'}:
                neighbors=field_neighbors(lines,i,key,overlap=.6 if kind==TYPES[1] else 0)
                if key in {'address','placeOfOrigin'} and len(neighbors)>1:
                    pieces=[(j,l,normalize_field(key,l['text'])) for j,l in neighbors]
                    pieces=[v for v in pieces if v[2] is not None]
                    if pieces:
                        merged={**pieces[0][1],'text':' '.join(v[1]['text'] for v in pieces),'confidence':min((v[1].get('confidence') or 0) for v in pieces),'polygon':None}
                        add(key,' '.join(v[2] for v in pieces),merged,pieces[0][0])
                else:
                    for j,other in neighbors:add(key,normalize_field(key,other['text']),other,j)
                continue
            pts=line.get('polygon') or []
            if len(pts)!=4:continue
            x1=min(p[0] for p in pts);x2=max(p[0] for p in pts);y1=min(p[1] for p in pts);y2=max(p[1] for p in pts);h=max(1,y2-y1)
            for j,other in enumerate(lines):
                if j==i or other.get('side')!=line.get('side'):continue
                poly=other.get('polygon') or []
                if len(poly)!=4:continue
                ox=min(p[0] for p in poly);oy=min(p[1] for p in poly);oy2=max(p[1] for p in poly)
                same=ox>=x2-h and abs((oy+oy2-y1-y2)/2)<h
                below=(-.6*h if kind==TYPES[1] else 0)<=oy-y2<=h*1.6 and abs(ox-x1)<=h*2
                if same or below:add(key,normalize_field(key,other['text']),other,j)
    if kind==TYPES[1]:
        # Supported CCCD has an unlabeled 12-digit holder number on FRONT.
        # Keep all candidates; multiple numbers stay ambiguous and QR is never consulted.
        for i,line in enumerate(lines):
            if line.get('side')=='FRONT' and re.fullmatch(r'[0-9]{12}',line['text'].strip()):
                add('documentNumber',line['text'].strip(),line,i)
            flat=unaccent_name(line['text'])
            if line.get('side')=='BACK' and ('DATE, MONTH' in flat or 'NGAY, THANG, NAM' in flat):
                add('issueDate',normalize_field('issueDate',line['text']),line,i)
    if kind==TYPES[0]:
        pilot=extract_pilot(lines)
        for old,key in [('licenseNo','documentNumber'),('fullName','fullName'),('dateOfBirth','dateOfBirth'),('licenseClass','licenseClass')]:
            if pilot[old]['candidate']:
                c=pilot[old]['candidate'];add(key,c['value'],{'text':c['raw_text'],'confidence':c['confidence'],'polygon':c['polygon'],'side':lines[c['line_index']].get('side'),'recognitionEngine':lines[c['line_index']].get('recognitionEngine')},c['line_index'])
    result={}
    for key,items in candidates.items():
        unique={c['value']:c for c in items}
        result[key]={'status':'EXTRACTED' if len(unique)==1 else 'MISSING' if not unique else 'AMBIGUOUS',
                     'candidate':next(iter(unique.values())) if len(unique)==1 else None,'candidates':list(unique.values())}
    return result


def fuse(ocr,qr,kind):
    result={};issues=[]
    for key in COMMON+EXTRA[kind]:
        entry=ocr.get(key,{})
        c=entry.get('candidate');ov=c['value'] if c else None;qv=qr.get(key)
        status='MISSING';value=None;source=None
        if ov is not None and qv is not None:
            exact=ov==qv
            loose=key in {'fullName','address','nationality','placeOfOrigin'} and unaccent_name(ov)==unaccent_name(qv)
            if exact or loose:value=qv;source='OCR_QR' if exact else 'QR';status='EXACT_MATCH' if exact else 'UNACCENTED_MATCH'
            elif key=='address':
                # OCR may cover one printed line while QR contains the full address.
                # No abbreviation expansion or semantic equivalence is invented.
                left=re.match(r'^\s*([0-9]+(?:/[0-9]+)*)',ov)
                right=re.match(r'^\s*([0-9]+(?:/[0-9]+)*)',qv)
                if left and right and left.group(1)!=right.group(1):status='MISMATCH';issues.append(key)
                else:value=qv;source='QR';status='ADDRESS_NOT_CONFIRMED'
            else:status='MISMATCH';issues.append(key)
        elif qv is not None:
            value=qv;source='QR';status='QR_ONLY' if entry.get('status')!='AMBIGUOUS' else 'OCR_AMBIGUOUS'
        elif ov is not None:
            preserve_name=key=='fullName' and str(c.get('recognitionEngine') or '').startswith('vietocr-')
            value=ov if preserve_name or key!='fullName' else unaccent_name(ov)
            source='OCR';status='OCR_ONLY_UNACCENTED' if key=='fullName' and not preserve_name else 'OCR_ONLY'
        if status=='MISSING' and entry.get('status')=='AMBIGUOUS':status='OCR_AMBIGUOUS'
        if kind==TYPES[0] and key=='address':
            # Optional GPLX address does not participate in core identity comparison.
            # An ambiguous primary candidate is visible as best-effort, never verified.
            if qv is not None:
                value=qv;source='QR'
                if status not in {'EXACT_MATCH','UNACCENTED_MATCH'}:status='QR_ONLY'
            elif ov is not None:
                value=ov;source='OCR';status='OCR_BEST_EFFORT'
            if key in issues:issues.remove(key)

        result[key]={'value':value,'source':source,'status':status,'ocr':c,'qrValue':qv,'ocrCandidates':entry.get('candidates',[]),
                     'confidence':c.get('confidence') if c and source=='OCR' else None,
                     'bestEffort':kind==TYPES[0] and key=='address' and source=='OCR',
                     'needsReview':status in {'MISSING','MISMATCH','OCR_AMBIGUOUS','OCR_BEST_EFFORT','QR_ONLY'} or bool(c and (c.get('confidence') or 0)<.9),
                     'ocrAmbiguous':entry.get('status')=='AMBIGUOUS'}
    return result,issues
