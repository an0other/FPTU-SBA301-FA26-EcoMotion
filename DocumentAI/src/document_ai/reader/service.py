import os
"""Single public reader service for supported GPLX and CCCD formats."""
import hashlib,time,uuid,json
from pathlib import Path
from document_ai.imaging import decode,quality
from document_ai.adapters.ocr import create_ocr
from document_ai.adapters.qr import scan_qr
from document_ai.diagnostics import compare_qr_sources
from .geometry import locate
from .orientation import resolve_orientation
from .data import TYPES,title_type,qr_parse,extract,fuse
from .policy import gplx_core_comparison

DEFAULT_OCR={'engine':'paddle','version':'PP-OCRv6','lang':'vi','device':'cpu','detection_model':'PP-OCRv6_tiny_det','recognition_model':'PP-OCRv6_tiny_rec'}
DEFAULT_QUALITY={'min_short_side':400,'min_laplacian_variance':60,'max_dark_fraction':.65,'max_bright_fraction':.8,'calibrated':False}


class Reader:
    def __init__(self,ocr=None,locator=locate,scanner=scan_qr,quality_config=None,require_localization=False,field_profile=None,qr_enabled=True,performance_mode="standard",recognition_scope="full",qr_search="full"):
        if performance_mode not in {"standard","balanced","optimized"}:raise ValueError("Invalid performance mode")
        if recognition_scope not in {"full","fields"}:raise ValueError("Invalid recognition scope")
        if qr_search not in {"full","adaptive"}:raise ValueError("Invalid QR search")
        self.recognition_scope=recognition_scope;self.qr_search=qr_search
        self.performance_mode=performance_mode;self._request_count=0
        self.ocr=ocr or create_ocr(DEFAULT_OCR);self.locator=locator;self.scanner=scanner
        self.quality_config=quality_config or DEFAULT_QUALITY
        self.require_localization=require_localization;self.field_profile=field_profile;self.qr_enabled=qr_enabled

    def read(self,front,back,expected_type='AUTO',evidence_dir=None):
        if expected_type not in ('AUTO',)+TYPES:raise ValueError('Unsupported document type')
        self._request_count+=1
        start=time.perf_counter();checks=[];images=[];all_lines=[];qr_results=[];titles=[];failed_input=False;technical=False;uncropped_sides=[];unresolved_orientation=[];orientations=[];front_source=None;front_located=False;scope_records=[];qr_jobs=[];limited_qr=False
        def check(side,name,status,reasons=None,metrics=None,duration=0):
            checks.append({'side':side,'step':name,'status':status,'reasons':reasons or [],'metrics':metrics or {},'durationMs':round(duration,2)})
        for side,raw in [('FRONT',front),('BACK',back)]:
            before=time.perf_counter();hashvalue=hashlib.sha256(raw).hexdigest();decoded=decode(raw,{})
            images.append({'side':side,'sha256':hashvalue})
            check(side,'FILE',decoded.status,decoded.reasons,decoded.metrics,(time.perf_counter()-before)*1000)
            if decoded.status!='PASS':failed_input=True;continue
            original=decoded.payload['image']
            before=time.perf_counter()
            try:g=self.locator(original,hashvalue)
            except Exception as error:
                technical=True;check(side,'LOCALIZE','ERROR',['LOCALIZATION_ERROR'],{'exceptionType':type(error).__name__});continue
            check(side,'LOCALIZE',g['status'],[g['reason']] if g.get('reason') else [],{'method':g.get('method','opencv-contour/v1'),'corners':g.get('corners'),'candidates':g.get('candidates',[]),'semanticTypeVerified':False,'maskQuadIoU':g.get('maskQuadIoU'),'cropContextMarginFraction':g.get('cropContextMarginFraction',0)},(time.perf_counter()-before)*1000)
            if g['status']!='LOCATED':
                if self.require_localization:failed_input=True;continue
                uncropped_sides.append(side)
                aligned=original
                check(side,'LOCALIZATION_FALLBACK','REVIEW_REQUIRED',['ORIGINAL_IMAGE_USED_NO_CARD_CROP'])
            else:aligned=g['image']
            before=time.perf_counter();q=quality(aligned,self.quality_config)
            check(side,'QUALITY',q.status,q.reasons,q.metrics,(time.perf_counter()-before)*1000)
            folder=Path(evidence_dir)/side.lower() if evidence_dir else None
            if folder:
                folder.mkdir(parents=True,exist_ok=True);aligned.save(folder/'card.png')
                original.save(folder/'original.png')
                (folder/'quality.json').write_text(json.dumps({'status':q.status,'reasons':q.reasons,
                    'metrics':q.metrics,'stage':'BEFORE_ORIENTATION','decisionUnchanged':True},indent=2),encoding='utf-8')
                from PIL import ImageDraw
                overlay=original.copy();painter=ImageDraw.Draw(overlay)
                points=g.get('corners') or []
                if points:
                    xy=[tuple(p) for p in points];painter.line(xy+[xy[0]],fill='red',width=4)
                    for n,(x,y) in enumerate(xy):
                        painter.ellipse((x-7,y-7,x+7,y+7),fill='red');painter.text((x+10,y+10),str(n),fill='red')
                context=g.get('cropContextCorners') or []
                if context:
                    xy=[tuple(p) for p in context];painter.line(xy+[xy[0]],fill='blue',width=3)
                overlay.save(folder/'localization-overlay.jpg')
                (folder/'localization.json').write_text(json.dumps({'status':g['status'],'method':g.get('method'),'corners':points,'maskQuadIoU':g.get('maskQuadIoU'),'cropContextMarginFraction':g.get('cropContextMarginFraction',0)},indent=2),encoding='utf-8')
                regional_start=time.perf_counter()
                try:
                    from .quality_regions import measure_regions,save_overlay
                    regional=measure_regions(aligned)
                    (folder/'quality-regions.json').write_text(json.dumps(regional,indent=2),encoding='utf-8')
                    save_overlay(aligned,regional,folder/'quality-regions-overlay.png')
                    check(side,'QUALITY_REGIONS','DIAGNOSTIC_ONLY',metrics=regional,
                          duration=(time.perf_counter()-regional_start)*1000)
                except Exception as error:
                    check(side,'QUALITY_REGIONS','INCONCLUSIVE',['REGIONAL_DIAGNOSTIC_ERROR'],
                          {'exceptionType':type(error).__name__,'decisionUsed':False},
                          (time.perf_counter()-regional_start)*1000)
            if q.status!='PASS':failed_input=True;continue
            before=time.perf_counter()
            try:
                orientation_options={'card_rectified':g['status']=='LOCATED'}
                if self.performance_mode in {'balanced','optimized'}:orientation_options['fast']=True
                if self.performance_mode=='optimized' and hasattr(self.ocr,'read_oriented'):orientation_options['header_probe']=True
                orientation=resolve_orientation(aligned,getattr(self.ocr,'orientation_engine',self.ocr),**orientation_options)
                orientations.append({'side':side,'status':orientation['status'],'rotationApplied':orientation.get('rotationApplied'),'rotationConvention':'counterclockwise','readyForOcr':orientation['status']=='RESOLVED','attempts':orientation['attempts']})
                check(side,'ORIENTATION',orientation['status'],[orientation['reason']] if orientation.get('reason') else [],metrics={'rotationApplied':orientation.get('rotationApplied'),'selectionBasis':orientation.get('selectionBasis'),'attempts':orientation['attempts'],'strategy':orientation.get('strategy','FULL_IMAGE'),'headerProbeAttempts':orientation.get('headerProbeAttempts',[]),'headerObservations':orientation.get('headerObservations',[])},duration=(time.perf_counter()-before)*1000)
                if orientation['status']=='ERROR':raise RuntimeError('Orientation probes failed')
                if orientation['status']!='RESOLVED':unresolved_orientation.append(side)
                aligned=orientation['image'];lines=orientation['lines']
                recognition_start=time.perf_counter()
                if orientation['status']=='RESOLVED' and hasattr(self.ocr,'recognize_detected'):
                    if hasattr(self.ocr,'read_oriented'):
                        opts={'side':side,'expected_type':expected_type,'field_profile':self.field_profile} if self.recognition_scope=='fields' and g['status']=='LOCATED' else {}
                        lines=self.ocr.read_oriented(aligned,lines,**opts)
                        scope=dict(getattr(self.ocr,'last_scope',{}));scope['side']=side;scope_records.append(scope)
                        if side=='FRONT' and scope.get('status')=='APPLIED' and title_type(lines) is None:
                            lines=self.ocr.read_oriented(aligned)
                            scope['fallback']='FULL_TITLE_NOT_READ'
                    else:lines=self.ocr.recognize_detected(aligned,lines)
                recognition_ms=(time.perf_counter()-recognition_start)*1000
                kind=title_type(lines)
                if folder:aligned.save(folder/'ocr-source.png')
                for line in lines:line['side']=side
                all_lines+=lines
                if side=='FRONT':front_source=aligned;front_located=g['status']=='LOCATED'
                if folder:(folder/'ocr-lines.json').write_text(json.dumps(lines,ensure_ascii=False,indent=2),encoding='utf-8')
                if kind:titles.append(kind)
                check(side,'OCR','PASS' if orientation['status']=='RESOLVED' else 'SKIPPED',reasons=[] if orientation['status']=='RESOLVED' else ['ORIENTATION_NOT_RESOLVED'],metrics={'lineCount':len(lines),'titleType':kind,'inferenceDurationIncludedInOrientation':not hasattr(self.ocr,'recognize_detected'),'recognizer':getattr(self.ocr,'implementation','configured-ocr')},duration=recognition_ms)
            except Exception as error:
                technical=True;check(side,'OCR','ERROR',['OCR_EXECUTION_ERROR'],{'exceptionType':type(error).__name__})
            before=time.perf_counter()
            def configured_scanner(image,config,**kwargs):
                return self.scanner(image,{**config,'max_attempts':2,'time_budget_seconds':.4},**kwargs)
            scanner=configured_scanner if self.qr_search=='adaptive' else self.scanner
            qr=(compare_qr_sources(original,aligned,scanner,folder/'qr' if folder else None)
                if self.qr_enabled else {'status':'SKIPPED','payloads':[],'sources':{},
                'successful_sources':[],'payload_conflict':False,'reason':'QR_DISABLED_FOR_OCR_EVALUATION'})
            qr_results.append({'side':side,**qr})
            if self.qr_enabled and self.qr_search=='adaptive':qr_jobs.append((side,original,aligned,folder))
            check(side,'QR',qr['status'],['QR_DISABLED_FOR_OCR_EVALUATION'] if not self.qr_enabled else ['QR_PAYLOAD_CONFLICT'] if qr['payload_conflict'] else [],{'successfulSources':qr['successful_sources'],'decodedCount':len(qr['payloads'])},(time.perf_counter()-before)*1000)
            if qr['status']=='ERROR' and self.qr_search=='full':technical=True
        if qr_jobs:
            payloads={p for item in qr_results for p in item['payloads']}
            parsed_quick=[qr_parse(p) for p in payloads]
            usable=all(v['status']!='ERROR' for v in qr_results) and len(payloads)==1 and all(parsed_quick) and (expected_type=='AUTO' or parsed_quick[0]['documentType']==expected_type)
            if usable:limited_qr=True
            else:
                for index,(side,original,aligned,folder) in enumerate(qr_jobs):
                    before=time.perf_counter();quick=qr_results[index]
                    deep=compare_qr_sources(original,aligned,self.scanner,folder/'qr-deep' if folder else None)
                    combined=sorted(set(deep['payloads'])|set(quick['payloads']))
                    deep['payloads']=combined;deep['payload_conflict']=len(combined)>1
                    if deep['status']!='ERROR':deep['status']='INCONCLUSIVE' if len(combined)>1 else 'PASS' if combined else deep['status']
                    qr_results[index]={'side':side,**deep,'quickPass':quick}
                    check(side,'QR_FALLBACK',deep['status'],metrics={'strategy':'FULL_AFTER_QUICK'},duration=(time.perf_counter()-before)*1000)
                    if deep['status']=='ERROR':technical=True
        return self._finish({'front':front,'back':back,'expected_type':expected_type,'evidence_dir':evidence_dir,'start':start,'checks':checks,'images':images,'all_lines':all_lines,'qr_results':qr_results,'titles':titles,'failed_input':failed_input,'technical':technical,'uncropped_sides':uncropped_sides,'unresolved_orientation':unresolved_orientation,'orientations':orientations,'front_source':front_source,'front_located':front_located,'scope_records':scope_records,'limited_qr':limited_qr})

    def _finish(self, context):
        front=context['front']
        back=context['back']
        expected_type=context['expected_type']
        evidence_dir=context['evidence_dir']
        start=context['start']
        checks=context['checks']
        images=context['images']
        all_lines=context['all_lines']
        qr_results=context['qr_results']
        titles=context['titles']
        failed_input=context['failed_input']
        technical=context['technical']
        uncropped_sides=context['uncropped_sides']
        unresolved_orientation=context['unresolved_orientation']
        orientations=context['orientations']
        front_source=context['front_source']
        front_located=context['front_located']
        scope_records=context['scope_records']
        limited_qr=context['limited_qr']
        def check(side,name,status,reasons=None,metrics=None,duration=0):
            checks.append({"side":side,"step":name,"status":status,"reasons":reasons or [],"metrics":metrics or {},"durationMs":round(duration,2)})
        parsed=[];qr_documents=[];unsupported=0
        for result in qr_results:
            for payload in result['payloads']:
                value=qr_parse(payload)
                if value:
                    parsed.append(value)
                    qr_documents.append({'side':result['side'],'rawPayload':payload,**value})
                else:unsupported+=1
        kinds=set(titles)|{p['documentType'] for p in parsed}
        kind=next(iter(kinds)) if len(kinds)==1 else None
        mismatch=len(kinds)>1 or kind is not None and expected_type!='AUTO' and kind!=expected_type
        reasons=[];warnings=[]
        duplicate_input=hashlib.sha256(front).digest()==hashlib.sha256(back).digest()
        if duplicate_input:warnings.append('DUPLICATE_INPUT_IMAGES')
        if uncropped_sides:warnings.append('UNCROPPED_IMAGE_FALLBACK')
        if unresolved_orientation:warnings.append('ORIENTATION_NOT_RESOLVED')
        if failed_input:reasons.append('INPUT_OR_CARD_QUALITY_UNSUITABLE')
        if technical:reasons.append('STEP_EXECUTION_ERROR')
        if mismatch:reasons.append('DOCUMENT_TYPE_CONFLICT')
        if not kind and not mismatch:reasons.append('DOCUMENT_TYPE_NOT_IDENTIFIED')
        if unsupported:warnings.append('UNSUPPORTED_QR_PAYLOAD_PRESENT')
        if limited_qr:warnings.append('QR_SEARCH_LIMITED_TO_QUICK_PASSES')
        data={};fields={};field_mismatches=[];missing=[];uncertain=[];optional_missing=[];optional_review=[];core_comparison=None;ocr_fields={}
        qr_fields={};qr_conflict=any(r['payload_conflict'] for r in qr_results)
        if os.getenv('DOCUMENT_AI_FIELD_SCOPE','full')=='core' and kind==TYPES[0] and front_located and self.field_profile:warnings.append('CORE_SCOPE_OPTIONAL_FIELDS_NOT_REQUESTED')
        if kind and not context.get('pair_rejection'):
            for value in parsed:
                if value['documentType']!=kind:continue
                for k,v in value['fields'].items():
                    if k in qr_fields and qr_fields[k]!=v:qr_conflict=True
                    qr_fields[k]=v
            if qr_conflict:qr_fields={};reasons.append('QR_PAYLOAD_CONFLICT')
            # Side ownership is explicit: never fill holder name from the back.
            # QR may be found on either side; printed holder fields come from FRONT.
            extraction_started=time.perf_counter()
            front_lines=[line for line in all_lines if line.get('side')=='FRONT']
            back_lines=[line for line in all_lines if line.get('side')=='BACK']
            ocr=extract(front_lines,kind)
            if self.field_profile:
                if kind==TYPES[0] and front_located and front_source is not None and 'FRONT' not in unresolved_orientation:
                    from .field_regions import region_extract
                    roi=region_extract(front_lines,kind,front_source,self.field_profile,self.ocr,Path(evidence_dir)/'fields' if evidence_dir else None,retry_policy='direct_first' if self.performance_mode=='optimized' else 'selective' if self.performance_mode=='balanced' else 'always',field_keys={'documentNumber','fullName','dateOfBirth','licenseClass'} if os.getenv('DOCUMENT_AI_FIELD_SCOPE','full')=='core' else None)
                    for key,value in roi.items():ocr[key]=value
                    if any(v.get('directReading',{}).get('status')=='ERROR' for v in roi.values()):warnings.append('DIRECT_FIELD_READING_ERROR')
                    warnings.append('PILOT_FIELD_TEMPLATE_UNVALIDATED')
                else:warnings.append('FIELD_PROFILE_NOT_APPLIED')
            if kind==TYPES[1]:
                back_fields=extract(back_lines,kind)
                # Issue date is the only current back-owned field for supported CCCD.
                ocr['issueDate']=back_fields['issueDate']
            focused_front=next((v for v in scope_records if v.get('side')=='FRONT' and v.get('status')=='APPLIED' and not v.get('fallback')),None)
            if kind==TYPES[0] and focused_front and any(ocr.get(k,{}).get('candidate') is None for k in ('documentNumber','fullName','dateOfBirth','licenseClass')):
                rescue_started=time.perf_counter()
                try:
                    full_lines=self.ocr.read_oriented(front_source)
                    rescue_kind=title_type(full_lines)
                    if rescue_kind and rescue_kind!=kind:mismatch=True;reasons.append('DOCUMENT_TYPE_CONFLICT')
                    for line in full_lines:line['side']='FRONT'
                    all_lines=[l for l in all_lines if l.get('side')!='FRONT']+full_lines
                    ocr=extract(full_lines,kind)
                    if self.field_profile:
                        ocr.update(region_extract(full_lines,kind,front_source,self.field_profile,self.ocr,
                            Path(evidence_dir)/'fields-full-fallback' if evidence_dir else None,
                            retry_policy='direct_first' if self.performance_mode=='optimized' else 'selective',field_keys={'documentNumber','fullName','dateOfBirth','licenseClass'} if os.getenv('DOCUMENT_AI_FIELD_SCOPE','full')=='core' else None))
                    focused_front['fallback']='FULL_CORE_FIELD_NOT_READ'
                    if evidence_dir:
                        (Path(evidence_dir)/'front/ocr-lines-full-fallback.json').write_text(json.dumps(full_lines,ensure_ascii=False,indent=2),encoding='utf-8')
                    check('FRONT','FIELD_SCOPE_FALLBACK','COMPLETED',duration=(time.perf_counter()-rescue_started)*1000)
                except Exception as error:
                    technical=True
                    if 'STEP_EXECUTION_ERROR' not in reasons:reasons.append('STEP_EXECUTION_ERROR')
                    focused_front['fallbackError']=type(error).__name__
                    check('FRONT','FIELD_SCOPE_FALLBACK','ERROR',['OCR_EXECUTION_ERROR'],{'exceptionType':type(error).__name__})
            # A later read cannot erase an ambiguity already observed in the same request.
            for scope in scope_records:
                for key,old in scope.get('firstPassFields',{}).items():
                    old_value=(old.get('candidate') or {}).get('value')
                    current_value=(ocr.get(key,{}).get('candidate') or {}).get('value')
                    conflict=old.get('status')=='AMBIGUOUS' or old_value is not None and current_value is not None and old_value!=current_value
                    if not conflict:continue
                    candidates=old.get('candidates',[])+ocr.get(key,{}).get('candidates',[])
                    merged={c['value']:c for c in candidates if c.get('value') is not None}
                    ocr[key]={**ocr.get(key,{}),'status':'AMBIGUOUS','candidate':None,
                              'candidates':list(merged.values()),'reason':'EARLIER_VALID_OCR_CONFLICT_RETAINED'}
            ocr_fields=ocr
            fields,field_mismatches=fuse(ocr,qr_fields,kind)
            data={key:value['value'] for key,value in fields.items()}
            required=['documentNumber','fullName','dateOfBirth']+(['licenseClass'] if kind==TYPES[0] else ['sex'])
            missing=[key for key in required if data.get(key) is None]
            uncertain=[key for key in required if fields[key]['status'] in {'QR_ONLY','OCR_AMBIGUOUS'} or fields[key].get('ocr') and (fields[key]['ocr'].get('confidence') or 0)<.9]
            if kind==TYPES[0]:
                core_comparison=gplx_core_comparison(fields,qr_conflict)
                optional_keys=['nationality','address','issueDate','expiryDate']
                optional_missing=[key for key in optional_keys if data.get(key) is None]
                optional_review=[key for key in optional_keys if fields[key].get('needsReview') or fields[key].get('ocrAmbiguous') or fields[key].get('confidence') is not None and fields[key]['confidence']<.9]
                if optional_missing or optional_review:warnings.append('OPTIONAL_FIELDS_INCOMPLETE_OR_UNCERTAIN')
            if field_mismatches:reasons.append('OCR_QR_MISMATCH')
            if uncertain:warnings.append('REQUIRED_FIELDS_NEED_REVIEW')
            if 'fullName' in fields and fields['fullName']['status']=='OCR_ONLY_UNACCENTED':warnings.append('NAME_RETURNED_WITHOUT_ACCENTS')
            if not titles:warnings.append('TYPE_FROM_QR_ONLY')
            if any(field['status']=='ADDRESS_NOT_CONFIRMED' for field in fields.values()):warnings.append('ADDRESS_OCR_QR_NOT_CONFIRMED')
            if any(title_type([line])==kind for line in back_lines):warnings.append('POSSIBLE_SIDE_ORDER_ERROR')
            check('PAIR','FIELD_EXTRACTION','COMPLETED',metrics={'retryPolicy':'direct_first' if self.performance_mode=='optimized' else 'selective' if self.performance_mode=='balanced' else 'always'},duration=(time.perf_counter()-extraction_started)*1000)
        if any(line.get('recognition_status')=='ERROR' for line in all_lines):warnings.append('OCR_REGIONS_INCOMPLETE')
        from .read_status import resolve_read_status
        status,status_reasons=resolve_read_status(technical=technical,failed_input=failed_input,
            type_conflict=mismatch,kind=kind,qr_conflict=qr_conflict,mismatches=field_mismatches,
            missing=missing,uncertain=uncertain,titles=titles,duplicate_input=duplicate_input,
            uncropped_sides=uncropped_sides,unresolved_orientation=unresolved_orientation,warnings=warnings)
        if context.get('pair_rejection'):
            status='RETAKE_REQUIRED';status_reasons=context['pair_rejection']
            data={};fields={};ocr_fields={}
        for side in ('FRONT','BACK'):
            for step in ('FILE','LOCALIZE','QUALITY','ORIENTATION','OCR','QR'):
                if not any(c['side']==side and c['step']==step for c in checks):
                    check(side,step,'SKIPPED',['PREREQUISITE_NOT_PASSED'])
        from .document_titles import title_evidence
        printed_titles={side:title_evidence([line for line in all_lines if line.get('side')==side]) for side in ('FRONT','BACK')}
        check('PAIR','DOCUMENT_TYPE','CONFLICT' if mismatch else 'IDENTIFIED' if kind else 'INCONCLUSIVE',reasons=['DOCUMENT_TYPE_CONFLICT'] if mismatch else [],metrics={'titleTypes':titles,'qrTypes':sorted({p['documentType'] for p in parsed}),'printedTitleEvidence':printed_titles})
        check('PAIR','CONSISTENCY','MISMATCH' if field_mismatches or qr_conflict else 'PARTIAL' if missing or not parsed or any(fields[key]['status'] not in {'EXACT_MATCH','UNACCENTED_MATCH'} for key in fields if key in {'documentNumber','fullName','dateOfBirth','licenseClass','sex','address'} and (fields[key]['value'] is not None or key in {'documentNumber','fullName','dateOfBirth'})) else 'MATCH',metrics={'missingFields':missing,'mismatches':field_mismatches})
        if core_comparison is not None:
            pair_check=checks[-1]
            pair_check['status']=core_comparison['status']
            pair_check['metrics'].update({'scope':'GPLX_CORE_FIELDS_ONLY','fields':core_comparison['fields']})
        if mismatch or context.get('pair_rejection') or not fields or not all_lines:
            checks[-1]['status']='SKIPPED'
            checks[-1]['reasons']=context.get('pair_rejection') or ['DOCUMENT_TYPE_CONFLICT' if mismatch else 'NO_OCR_FIELDS_TO_COMPARE' if not all_lines else 'NO_FIELDS_TO_COMPARE']
        qr_data={'enabled':self.qr_enabled,'status':'DISABLED' if not self.qr_enabled else 'CONFLICT' if qr_conflict or mismatch else 'PARSED' if parsed else 'UNSUPPORTED_FORMAT' if unsupported else 'NOT_READ',
                 'fields':qr_fields,'documents':qr_documents,'searchCoverage':'DISABLED' if not self.qr_enabled else 'QUICK_ONLY' if limited_qr else 'FULL_STRATEGY'}
        qr_metadata={'unmappedFields':[{'side':doc['side'],'schema':doc['schema'],**item} for doc in qr_documents for item in doc.get('unmappedFields',[])]}
        additional={};assessment={}
        if kind==TYPES[0]:
            from .assessment import assess_gplx,GPLX_MAIN_FIELDS
            additional={k:v for k,v in data.items() if k not in GPLX_MAIN_FIELDS}
            data={k:data.get(k) for k in GPLX_MAIN_FIELDS}
            assessment=assess_gplx(data,fields,ocr_fields,status)
            assessment['approval']['reviewReasons']=['MANUAL_APPROVAL_POLICY']+(['PILOT_FIELD_TEMPLATE_UNVALIDATED'] if 'PILOT_FIELD_TEMPLATE_UNVALIDATED' in warnings else [])
        return {'schemaVersion':'document-reader/2.0','requestId':uuid.uuid4().hex,'code':'PROCESSED' if not technical else 'PROCESSING_ERROR',
            'status':status,'statusReasons':status_reasons,'documentType':kind,'expectedDocumentType':expected_type,'data':data,'additionalData':additional,'fields':fields,'assessment':assessment,
            'ocrLines':all_lines,'qrData':qr_data,'qrMetadata':qr_metadata,'printedTitleEvidence':printed_titles,
            'ocrData':ocr_fields,'recognitionScopeReport':scope_records,'readerConfig':{'recognitionScope':self.recognition_scope,'qrSearch':self.qr_search,'batchSize':getattr(getattr(self.ocr,'recognizer',None),'batch_size',1),'cropTransport':getattr(getattr(self.ocr,'recognizer',None),'transport','png'),'scope':'READING_AND_CONSISTENCY_ONLY','performanceMode':self.performance_mode,'readerRequestOrdinal':self._request_count,'vietocrCpuThreads':getattr(getattr(self.ocr,'recognizer',None),'cpu_threads',None),'recognizerRuntime':getattr(getattr(self.ocr,'recognizer',None),'runtime',{}),'qrEnabled':self.qr_enabled,'ocrEngine':getattr(self.ocr,'implementation','configured-ocr'),'orientationEngine':getattr(getattr(self.ocr,'orientation_engine',self.ocr),'implementation','configured-ocr')},
            'checks':checks,'reasons':reasons,'warnings':warnings,'missingFields':missing,'inconsistentFields':field_mismatches,
            'optionalMissingFields':optional_missing,'optionalReviewFields':optional_review,'coreComparison':core_comparison,
            'orientations':orientations,'images':images,'uncroppedSides':uncropped_sides,'qr':{'enabled':self.qr_enabled,'results':qr_results,'parsedSchemas':sorted({p['schema'] for p in parsed}),'conflict':qr_conflict},
            'verification':{'authenticity':'NOT_VERIFIED','ownership':'NOT_VERIFIED','issuer':'NOT_CHECKED','legalEligibility':'NOT_CHECKED'},
            'limitations':['Contour localization and label extraction are pilot heuristics','Quality and confidence thresholds are uncalibrated','QR payload equality does not prove authenticity'],
            'elapsedMs':round((time.perf_counter()-start)*1000,2)}
