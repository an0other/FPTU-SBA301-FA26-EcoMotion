"""Pair checks before detailed OCR. Models persist, intermediates stay request-local."""
import hashlib
import json
import os
import time
from pathlib import Path
from document_ai.imaging import decode,quality
from document_ai.diagnostics import compare_qr_sources
from .service import Reader
from .geometry import locate as contour_locate
from .data import TYPES,title_type,qr_parse
from .fast_orientation import orient,clues
from .request_ocr import RequestOcr
from document_ai.name_comparison import unaccent_name


def add_crop_context(original,geometry):
    if geometry.get('cropContextMarginFraction') or not geometry.get('homography'):return geometry
    import cv2
    import numpy as np
    from PIL import Image
    w,h=geometry['image'].size;mx,my=max(1,round(w*.06)),max(1,round(h*.06))
    matrix=np.array([[1.,0.,mx],[0.,1.,my],[0.,0.,1.]])@np.asarray(geometry['homography'])
    image=Image.fromarray(cv2.warpPerspective(np.asarray(original.convert('RGB')),matrix,(w+2*mx,h+2*my)))
    return {**geometry,'image':image,'homography':matrix.tolist(),'cropContextMarginFraction':.06,
            'cropContextCorners':cv2.perspectiveTransform(np.float32([[[0,0],[image.width-1,0],[image.width-1,image.height-1],[0,image.height-1]]]),np.linalg.inv(matrix))[0].tolist()}


def side_hint(lines):
    kind=title_type(lines);hits=clues(lines)
    if kind:return {'documentType':kind,'face':'FRONT','source':'PRINTED_TITLE'}
    text=''.join(''.join(unaccent_name(l['text']).split()) for l in lines if l.get('text'))
    back=[]
    if 'CLASSIFICATIONOFMOTORVEHICLES' in text or 'CACLOAIXECOGIOI' in text:back.append(TYPES[0])
    if 'DACDIEMNHANDANG' in text or 'LEFTINDEXFINGER' in text and 'RIGHTINDEXFINGER' in text:back.append(TYPES[1])
    return {'documentType':back[0] if len(back)==1 else None,
            'face':'BACK' if len(back)==1 else 'UNKNOWN','source':'BACK_TEXT_CLUES'}


def reject_pair(records,expected_type):
    kinds=set();numbers={}
    for r in records:
        hint=r.get('hint',{})
        if hint.get('documentType'):kinds.add(hint['documentType'])
        for value in r.get('qr',{}).get('payloads',[]):
            parsed=qr_parse(value)
            if parsed:
                kinds.add(parsed['documentType'])
                numbers.setdefault(parsed['documentType'],set()).add(parsed['fields'].get('documentNumber'))
    if len(kinds)>1 or kinds and expected_type!='AUTO' and kinds!={expected_type}:
        return ['DOCUMENT_TYPE_CONFLICT']
    if any(len(values)>1 for values in numbers.values()):return ['DOCUMENT_NUMBER_CONFLICT']
    if any(r.get('hint',{}).get('face') in {'FRONT','BACK'} and r['hint']['face']!=r['side'] for r in records):
        return ['DOCUMENT_SIDE_ORDER_CONFLICT']
    return []


def save_evidence(record,root):
    from PIL import Image,ImageDraw
    from .quality_regions import measure_regions,save_overlay
    folder=Path(root)/'stages'/'02_localization_quality'/record['side'].lower()
    folder.mkdir(parents=True,exist_ok=True)
    record['original'].save(folder/'original.png');record['aligned'].save(folder/'card.png')
    q=record['quality'];g=record['geometry']
    (folder/'quality.json').write_text(json.dumps({'status':q.status,'reasons':q.reasons,'metrics':q.metrics},indent=2),encoding='utf-8')
    (folder/'localization.json').write_text(json.dumps({k:v for k,v in g.items() if k!='image'},indent=2),encoding='utf-8')
    canvas=record['original'].copy();draw=ImageDraw.Draw(canvas);points=g.get('corners')
    if points:
        draw.line([tuple(p) for p in points]+[tuple(points[0])],fill='red',width=4)
        mask=Image.new('L',record['original'].size,0)
        ImageDraw.Draw(mask).polygon([tuple(p) for p in points],fill=255)
        mask.save(folder/'card-mask.png')
    canvas.save(folder/'localization-overlay.jpg')
    try:
        report=measure_regions(record['aligned'])
        (folder/'quality-regions.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        save_overlay(record['aligned'],report,folder/'quality-regions-overlay.png')
    except Exception as error:
        (folder/'quality-regions.json').write_text(json.dumps({'status':'INCONCLUSIVE','exceptionType':type(error).__name__,'decisionUsed':False}),encoding='utf-8')


class StagedReader(Reader):
    supports_progress=True
    def __init__(self,*args,orientation_model=None,preview_side=960,max_probes=4,max_retries=6,image_analysis=None,**kwargs):
        super().__init__(*args,**kwargs)
        if not 256<=preview_side<=1600 or not 1<=max_probes<=4 or not 0<=max_retries<=16:
            raise ValueError('Invalid staged reader limits')
        from .image_analysis import configured_analysis
        self.image_analysis=image_analysis if image_analysis is not None else configured_analysis()
        self.orientation_model=orientation_model;self.preview_side=preview_side
        self.max_probes=max_probes;self.max_retries=max_retries

    def read(self,front,back,expected_type='AUTO',evidence_dir=None,progress=None):
        try:return self._read(front,back,expected_type,evidence_dir,progress)
        finally:
            end=getattr(self.ocr,'end_request',None)
            if end:end()

    def _read(self,front,back,expected_type='AUTO',evidence_dir=None,progress=None):
        if expected_type not in ('AUTO',)+TYPES:raise ValueError('Unsupported document type')
        self._request_count+=1;start=time.perf_counter();events=[]
        context=dict(front=front,back=back,expected_type=expected_type,evidence_dir=evidence_dir,start=start,
            checks=[],images=[],all_lines=[],image_analysis_rows=[],qr_results=[],titles=[],failed_input=False,technical=False,
            uncropped_sides=[],unresolved_orientation=[],orientations=[],front_source=None,
            front_located=False,scope_records=[],limited_qr=False,pair_rejection=[])
        request_ocr=RequestOcr(self.ocr,self.max_retries)
        finisher=Reader(ocr=request_ocr,locator=self.locator,scanner=self.scanner,quality_config=self.quality_config,
            require_localization=self.require_localization,field_profile=self.field_profile,
            qr_enabled=self.qr_enabled,performance_mode=self.performance_mode,
            recognition_scope=self.recognition_scope,qr_search=self.qr_search)
        finisher._request_count=self._request_count
        def emit(stage,side='PAIR'):
            event={'stage':stage,'side':side,'elapsedMs':round((time.perf_counter()-start)*1000,2)}
            events.append(event)
            if progress:progress(dict(event))
        def check(side,step,status,reasons=None,metrics=None,began=None):
            context['checks'].append({'side':side,'step':step,'status':status,'reasons':reasons or [],
                'metrics':metrics or {},'durationMs':round((time.perf_counter()-began)*1000,2) if began else 0})
        def finish(stage):
            emit('FINALIZING')
            result=finisher._finish(context)
            if 'DOCUMENT_TYPE_CONFLICT' in result['statusReasons']:
                result.update(status='RETAKE_REQUIRED',data={},fields={})
                if stage=='COMPLETE':stage='FIELD_VALIDATION_PAIR_CHECK'
            emit('COMPLETED')
            result['executionReport']={'pipelineMode':'staged','exitStage':stage,
                'earlyExit':stage!='COMPLETE','events':events,'ocrBudget':request_ocr.stats(),
                'orientationPreviewMaxSide':self.preview_side,'orientationMaxProbes':self.max_probes,
                'componentTimings':list(getattr(self.ocr,'request_timings',[])),
                'scope':'READING_ONLY_NOT_AUTHENTICITY',
                'optimizationProfile':getattr(self,'optimization_profile','baseline')}
            components=result['executionReport']['componentTimings']
            transports=[t.get('recognition',{}).get('transport',{}) for t in components]
            result['executionReport']['optimizationCounters']={
                'componentDetectorCalls':sum(not t.get('reusedDetection',False) for t in components),
                'componentNewCrops':sum(t.get('recognition',{}).get('newRegions',0) for t in components),
                'componentReusedCrops':sum(t.get('recognition',{}).get('reusedRegions',0) for t in components),
                'componentExactPixelReuseHits':sum(t.get('recognition',{}).get('exactPixelReuseHits',0) for t in components),
                'componentEncoderForwards':sum(g.get('actualForwardCount') or 0 for t in transports for g in t.get('batchGroups',[])),
                'orientationAnglesTried':sum(sum(a.get('status')=='PASS' for a in r.get('orientation',{}).get('attempts',[])) for r in records),
                'fullGeometryRescues':sum(c['step']=='GEOMETRY_REUSE_FALLBACK' for c in context['checks']),
                'targetedFieldAttempts':sum(len(s.get('targetedRescue',{}).get('attempts',[])) for s in context['scope_records']),
                'coverage':'COMPONENT_CALLS_ONLY_FOOTER_SINGLE_CROP_READS_MAY_BE_EXCLUDED'}
            if any(s.get('scope')=='CCCD_BACK_FIELDS_EXCLUDING_LONG_BOTTOM_LINES' for s in context['scope_records']):
                result['warnings'].append('CCCD_LONG_BOTTOM_LINES_NOT_READ_MRZ_NOT_VERIFIED')
            if any(r.get('orientation',{}).get('classifierError') for r in records):
                result['warnings'].append('ORIENTATION_MODEL_ERROR_USED_BOUNDED_FALLBACK')
            failure=context.get('detector_failure') or getattr(getattr(self.ocr,'detector',None),'last_failure',None)
            if failure:
                context['detector_failure']=failure
                result.update(status='PROCESSING_ERROR',code='PROCESSING_ERROR')
                result['executionReport']['detectorFailure']=failure
                result['statusReasons']=list(dict.fromkeys(result['statusReasons']+[context['detector_failure']['reason']]))
            from .pipeline_report import enrich
            result=enrich(result,context['image_analysis_rows'],self.image_analysis.mode,self.image_analysis.capabilities())
            result['elapsedMs']=round((time.perf_counter()-start)*1000,2)
            return result
        records=[];emit('VALIDATING_FILES')
        for side,raw in [('FRONT',front),('BACK',back)]:
            began=time.perf_counter();decoded=decode(raw,{})
            digest=hashlib.sha256(raw).hexdigest();context['images'].append({'side':side,'sha256':digest})
            check(side,'FILE',decoded.status,decoded.reasons,decoded.metrics,began)
            if decoded.status!='PASS':context['failed_input']=True
            else:records.append({'side':side,'original':decoded.payload['image'],'sha256':digest,'source_format':decoded.metrics.get('format','UNKNOWN')})
        if context['failed_input']:return finish('FILE_CHECK')
        if records[0]['sha256']==records[1]['sha256']:
            context['pair_rejection']=['DUPLICATE_INPUT_IMAGES'];return finish('PAIR_CHECK')
        emit('LOCALIZING_AND_CHECKING_QUALITY')
        for r in records:
            began=time.perf_counter()
            try:
                g=self.locator(r['original'],r['sha256'])
                primary={k:v for k,v in g.items() if k!='image'}
                if g['status']=='NOT_FOUND':
                    alternative=contour_locate(r['original'],r['sha256'])
                    if alternative['status']=='LOCATED':g={**alternative,'primaryFailure':primary,'method':'contour-fallback-after-model-not-found/v1'}
                if g['status']=='LOCATED':g=add_crop_context(r['original'],g)
                r['geometry']=g
            except Exception as error:
                context['technical']=True;check(r['side'],'LOCALIZE','ERROR',['LOCALIZATION_ERROR'],{'exceptionType':type(error).__name__},began);continue
            check(r['side'],'LOCALIZE',g['status'],[g['reason']] if g.get('reason') else [],{k:v for k,v in g.items() if k!='image'},began)
            if g['status']!='LOCATED':
                if self.require_localization:context['failed_input']=True
                context['uncropped_sides'].append(r['side'])
            r['aligned']=g['image'] if g['status']=='LOCATED' else r['original']
            began=time.perf_counter();q=quality(r['aligned'],self.quality_config);r['quality']=q
            check(r['side'],'QUALITY',q.status,q.reasons,q.metrics,began)
            if evidence_dir:save_evidence(r,evidence_dir)
            if q.status!='PASS':context['failed_input']=True
        if context['technical'] or context['failed_input']:return finish('QUALITY_CHECK')
        if self.qr_enabled:
            emit('QUICK_QR_PAIR_CHECK')
            context['limited_qr']=True  # Early exits have not exercised the full search strategy.
            def quick(image,config,**kwargs):
                return self.scanner(image,{**config,'max_attempts':2,'time_budget_seconds':.4},**kwargs)
            for r in records:
                folder=Path(evidence_dir)/'stages'/'03_qr_pair_precheck'/r['side'].lower() if evidence_dir else None
                if folder:folder.mkdir(parents=True,exist_ok=True)
                began=time.perf_counter();r['qr']=compare_qr_sources(
                    r['original'],r['aligned'],quick,
                    evidence_dir=folder/'scan-evidence' if folder else None)
                context['qr_results'].append({'side':r['side'],**r['qr']})
                check(r['side'],'QR_PRECHECK',r['qr']['status'],metrics={'decodedCount':len(r['qr']['payloads'])},began=began)
                if folder:
                    (folder/'result.json').write_text(json.dumps({
                        'status':r['qr']['status'],
                        'decodedCount':len(r['qr']['payloads']),
                        'sourceCount':len(r['qr'].get('sources',[])),
                        'payloadsOmitted':True,
                    },indent=2),encoding='utf-8')
            context['pair_rejection']=reject_pair(records,expected_type)
            if context['pair_rejection']:
                check('PAIR','PAIR_PRECHECK','REJECTED_INPUT',context['pair_rejection']);return finish('PAIR_CHECK')
        emit('CHECKING_ORIENTATION_AND_TYPE')
        for r in records:
            began=time.perf_counter()
            try:
                o=orient(r['aligned'],getattr(self.ocr,'orientation_engine',self.ocr),self.orientation_model,
                         self.preview_side,self.max_probes,card_rectified=r['geometry']['status']=='LOCATED')
            except Exception as error:
                if not getattr(error,'fatal_for_request',False):raise
                context['technical']=True
                context['detector_failure']={'reason':error.reason,**error.metadata}
                check(r['side'],'ORIENTATION','ERROR',[error.reason],error.metadata,began)
                return finish('DETECTOR_FAILURE')
            r['orientation']=o;r['hint']=side_hint(o['lines'])
            if evidence_dir:
                folder=Path(evidence_dir)/'stages'/'04_orientation_document_type'/r['side'].lower()
                folder.mkdir(parents=True,exist_ok=True)
                o['image'].save(folder/'oriented.png')
                (folder/'orientation.json').write_text(json.dumps({
                    'status':o['status'],
                    'rotationApplied':o.get('rotationApplied'),
                    'rotationConvention':'counterclockwise',
                    'strategy':o.get('strategy'),
                    'attemptCount':len(o.get('attempts',[])),
                    'documentTypeHint':r['hint'].get('documentType'),
                    'faceHint':r['hint'].get('face'),
                    'source':r['hint'].get('source'),
                    'ocrTextOmitted':True,
                },indent=2),encoding='utf-8')
            preview_kind=title_type(o['lines'])
            if preview_kind:context['titles'].append(preview_kind)
            context['orientations'].append({'side':r['side'],'status':o['status'],'rotationApplied':o['rotationApplied'],
                'rotationConvention':'counterclockwise','readyForOcr':o['status']=='RESOLVED','attempts':o['attempts']})
            check(r['side'],'ORIENTATION',o['status'],[o['reason']] if o.get('reason') else [],
                  {k:v for k,v in o.items() if k not in ('image','lines')},began)
            if o['status']!='RESOLVED':context['unresolved_orientation'].append(r['side'])
        context['pair_rejection']=reject_pair(records,expected_type)
        check('PAIR','PAIR_PRECHECK','REJECTED_INPUT' if context['pair_rejection'] else 'NO_CONFLICT_OBSERVED',
              context['pair_rejection'],{'sideEvidence':[{ 'side':r['side'],**r['hint']} for r in records],
                                        'samePhysicalCardVerified':False})
        if context['pair_rejection']:return finish('PAIR_CHECK')
        if context['unresolved_orientation']:return finish('ORIENTATION_CHECK')
        emit('ANALYZING_IMAGE_SIGNALS')
        from .image_analysis import AnalysisInput
        from .pipeline_report import aggregate
        for r in records:
            began=time.perf_counter()
            image_evidence_dir=Path(evidence_dir)/'stages'/'05_image_analysis' if evidence_dir else None
            rows=self.image_analysis.run(AnalysisInput(r['original'],r['side'],r['sha256'],r.get('source_format','UNKNOWN')),image_evidence_dir)
            context['image_analysis_rows']+=rows
            check(r['side'],'IMAGE_ANALYSIS',aggregate(rows),metrics={'mode':self.image_analysis.mode,'checks':rows,'decisionUsed':False},began=began)
        emit('READING_FIELDS')
        for r in records:
            image=r['orientation']['image'];began=time.perf_counter()
            try:
                options={'side':r['side'],'expected_type':r['hint'].get('documentType') or expected_type,
                         'field_profile':self.field_profile} if self.recognition_scope=='fields' and r['geometry']['status']=='LOCATED' else {}
                lines=request_ocr.read_oriented(image,r['orientation']['lines'],**options)
                scope=dict(getattr(request_ocr,'last_scope',{}));scope['side']=r['side']
                # A reused preview must not create a fast success with missing core OCR.
                # QR is deliberately absent from the decision and rescue extraction.
                if scope.get('orientationGeometry',{}).get('status')=='REUSED':
                    from .data import extract
                    observed=[{**line,'side':r['side']} for line in lines]
                    kind=r['hint'].get('documentType') or expected_type
                    parsed=extract(observed,kind) if kind in TYPES else {}
                    fallback_policy=os.getenv('DOCUMENT_AI_FALLBACK_POLICY','profile')
                    if fallback_policy not in {'profile','v29'}:raise ValueError('Invalid fallback policy')
                    if fallback_policy=='profile' and kind==TYPES[0] and r['side']=='FRONT' and self.field_profile and r['geometry']['status']=='LOCATED':
                        from .field_regions import observe_profile
                        parsed.update(observe_profile(observed,kind,image,self.field_profile))
                        scope['fallbackParser']='FINAL_PROFILE_RULES_NO_INFERENCE'
                    keys=(['documentNumber','fullName','dateOfBirth']+(['licenseClass'] if kind==TYPES[0] else ['sex'])) if r['side']=='FRONT' else (['issueDate'] if kind==TYPES[1] else [])
                    missing=[k for k in keys if not parsed.get(k,{}).get('candidate')]
                    original_fields={k:v for k,v in parsed.items() if v.get('candidate') or v.get('status')=='AMBIGUOUS'}
                    rescue_policy=os.getenv('DOCUMENT_AI_RESCUE_POLICY','full')
                    if rescue_policy not in {'full','targeted'}:raise ValueError('Invalid rescue policy')
                    if missing and rescue_policy=='targeted' and kind==TYPES[0] and r['side']=='FRONT' and self.field_profile and r['geometry']['status']=='LOCATED' and title_type(lines)==kind:
                        from .targeted_rescue import rescue_missing
                        rescue=time.perf_counter();initial=lines
                        lines,parsed,targeted=rescue_missing(image,lines,parsed,self.field_profile,request_ocr)
                        scope['targetedRescue']=targeted
                        if targeted['attempts']:
                            scope['fallback']='TARGETED_FIELD_ROI'
                            scope['firstPassLines']=initial;scope['firstPassFields']=original_fields
                        check(r['side'],'TARGETED_FIELD_RESCUE',targeted['status'],metrics=targeted,began=rescue)
                        missing=[k for k in keys if not parsed.get(k,{}).get('candidate')]
                    # Re-reading cannot erase valid ambiguity, but missing data still gets a bounded full rescue.
                    needs_full=bool(missing) if rescue_policy=='full' else any(parsed.get(k,{}).get('status')!='AMBIGUOUS' for k in missing)
                    if needs_full or r['side']=='FRONT' and title_type(lines) is None:
                        first_pass=lines
                        rescue=time.perf_counter()
                        lines=request_ocr.read_oriented(image,r['orientation']['lines'],force_full_detection=True)
                        scope['fallback']='FRESH_DETECTION_CORE_FIELD_OR_TITLE_NOT_READ'
                        scope['fallbackFields']=missing
                        scope['firstPassLines']=first_pass
                        scope['firstPassFields']={**{k:v for k,v in parsed.items() if v.get('candidate') or v.get('status')=='AMBIGUOUS'},**original_fields}
                        check(r['side'],'GEOMETRY_REUSE_FALLBACK','COMPLETED',metrics={'missingOcrFields':missing,'qrUsed':False},began=rescue)
                context['scope_records'].append(scope)
                for line in lines:line['side']=r['side']
                context['all_lines']+=lines
                kind=title_type(lines)
                if kind:context['titles'].append(kind)
                check(r['side'],'OCR','PASS',metrics={'lineCount':sum(l.get('recognition_status')!='ERROR' for l in lines),'failedLineCount':sum(l.get('recognition_status')=='ERROR' for l in lines),'titleType':kind,'componentTimings':getattr(self.ocr,'last_timing',{})},began=began)
                if r['side']=='FRONT':context['front_source']=image;context['front_located']=r['geometry']['status']=='LOCATED'
                if evidence_dir:
                    folder=Path(evidence_dir)/'stages'/'06_ocr'/r['side'].lower()
                    folder.mkdir(parents=True,exist_ok=True)
                    image.save(folder/'ocr-source.png')
                    (folder/'ocr-lines.json').write_text(json.dumps(lines,ensure_ascii=False,indent=2),encoding='utf-8')
                    from document_ai.ocr_evidence import evidence_for_lines
                    for name,content in evidence_for_lines(image,lines).items():
                        (folder/name).write_bytes(content)
            except Exception as error:
                context['technical']=True
                if getattr(error,'fatal_for_request',False):context['detector_failure']={'reason':error.reason,**error.metadata}
                check(r['side'],'OCR','ERROR',[getattr(error,'reason','OCR_EXECUTION_ERROR')],{'exceptionType':type(error).__name__,**getattr(error,'metadata',{})},began)
                return finish('OCR')
        # Body text can contradict a precheck; never hide new evidence.
        for r in records:
            r['hint']=side_hint([l for l in context['all_lines'] if l.get('side')==r['side']])
        context['pair_rejection']=reject_pair(records,expected_type)
        if context['pair_rejection']:return finish('PAIR_CHECK_AFTER_OCR')
        if self.qr_enabled:
            emit('COMPARING_OCR_AND_QR')
            payloads={p for r in records for p in r['qr']['payloads']}
            usable=len(payloads)==1 and qr_parse(next(iter(payloads))) is not None and all(r['qr']['status']!='ERROR' for r in records)
            context['limited_qr']=usable and self.qr_search=='adaptive'
            if not context['limited_qr']:
                for index,r in enumerate(records):
                    folder=Path(evidence_dir)/'stages'/'07_qr_comparison'/r['side'].lower() if evidence_dir else None
                    if folder:folder.mkdir(parents=True,exist_ok=True)
                    began=time.perf_counter();full=compare_qr_sources(
                        r['original'],r['orientation']['image'],self.scanner,
                        evidence_dir=folder/'scan-evidence' if folder else None)
                    full['payloads']=sorted(set(full['payloads'])|set(r['qr']['payloads']))
                    full['payload_conflict']=len(full['payloads'])>1
                    context['qr_results'][index]={'side':r['side'],**full,'quickPass':r['qr']}
                    r['qr']=full
                    check(r['side'],'QR',full['status'],metrics={'decodedCount':len(full['payloads'])},began=began)
                    if folder:
                        (folder/'result.json').write_text(json.dumps({
                            'status':full['status'],
                            'decodedCount':len(full['payloads']),
                            'payloadConflict':full['payload_conflict'],
                            'payloadsOmitted':True,
                        },indent=2),encoding='utf-8')
                    if full['status']=='ERROR':context['technical']=True
                context['pair_rejection']=reject_pair(records,expected_type)
                if context['pair_rejection']:return finish('PAIR_CHECK_AFTER_QR')
            else:
                for r in records:check(r['side'],'QR',r['qr']['status'],metrics={'reusedQuickPass':True,'decodedCount':len(r['qr']['payloads'])})
        else:
            for r in records:check(r['side'],'QR','SKIPPED',['QR_DISABLED_FOR_OCR_EVALUATION'])
        emit('EXTRACTING_AND_VALIDATING_FIELDS')
        return finish('COMPLETE')
