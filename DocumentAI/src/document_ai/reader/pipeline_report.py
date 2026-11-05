"""Versioned orchestration report. Reading, image signals and human approval stay separate."""
from datetime import date,datetime,timedelta,timezone
from .document_authenticity import build_document_authenticity


def aggregate(rows):
    states=[r['status'] for r in rows]
    if not states or all(s=='SKIPPED' for s in states):return 'SKIPPED'
    if 'ERROR' in states:return 'ERROR'
    if any(s in {'FAIL','CONFLICT','MISMATCH','REJECTED_INPUT','NOT_FOUND'} for s in states):return 'FAIL'
    if all(s in {'PASS','LOCATED','IDENTIFIED','MATCH','RESOLVED','NO_CONFLICT_OBSERVED'} for s in states):return 'PASS'
    return 'INCONCLUSIVE'


def enrich(result,image_rows,mode,capabilities):
    checks=result.get('checks',[])
    stages=[]
    for name,steps in [('INPUT_QUALITY',{'FILE','QUALITY'}),('DOCUMENT_AND_PAIR',{'LOCALIZE','ORIENTATION','PAIR_PRECHECK','DOCUMENT_TYPE'}),
                       ('IMAGE_ANALYSIS',{'IMAGE_ANALYSIS'}),('OCR_QR',{'OCR','QR'}),('DATA_CONSISTENCY',{'CONSISTENCY'})]:
        selected=[c for c in checks if c['step'] in steps]
        stages.append({'id':name,'status':aggregate(selected),'checks':[
            {'side':c['side'],'step':c['step'],'status':c['status'],'reasons':c.get('reasons',[])} for c in selected]})
    image_state=aggregate(image_rows)
    result['imageAnalysis']={'schemaVersion':'image-analysis/0.1','mode':mode,'status':image_state,
        'checks':image_rows,'capabilities':capabilities,'authenticityVerified':False,
        'decisionPolicy':'OBSERVE_ONLY_NO_AUTOMATIC_REJECTION_OR_APPROVAL',
        'coverage':'NOT_RUN' if not image_rows or image_state=='SKIPPED' else 'EXPERIMENTAL_SIGNALS_ONLY',
        'limitations':['ELA/FFT do not establish tampering or screen/print recapture',
            'No validated digital tampering or recapture model is configured in this release']}
    status=result['status'];kind=result.get('documentType');required=['documentNumber','fullName','dateOfBirth']
    if kind=='VN_DRIVING_LICENSE':required+=['licenseClass']
    elif kind=='VN_CITIZEN_ID':required+=['sex']
    observed=result.get('ocrData',{});read=[k for k in required if observed.get(k,{}).get('candidate')]
    assessment=result.setdefault('assessment',{})
    assessment.setdefault('readingAssessment',{'status':'CORE_FIELDS_READ' if len(read)==len(required) else 'PARTIAL' if read else 'NOT_READ',
        'scope':'OCR_ONLY','mainFields':required,'ocrReadFields':read,'ocrMissingFields':[k for k in required if k not in read],
        'finalMissingFields':[k for k in required if result.get('data',{}).get(k) is None]})
    # CCCD now receives an assessment too; printed expiry is not issuer/legal validation.
    if kind=='VN_CITIZEN_ID':
        value=result.get('data',{}).get('expiryDate');field=result.get('fields',{}).get('expiryDate',{})
        now=datetime.now(timezone(timedelta(hours=7))).date();expiration='UNKNOWN'
        if value:
            try:expiration='EXPIRED_DATE' if date.fromisoformat(value)<now else 'DATE_NOT_PASSED'
            except (ValueError,TypeError):pass
        assessment['expiryCheck']={'status':expiration,'value':value,'asOfDate':now.isoformat(),
            'source':field.get('source'),'legalValidity':'NOT_VERIFIED','basis':'Printed expiry only; no issuer or revocation lookup'}
    assessment['documentAuthenticity']=build_document_authenticity(
        image_rows,
        assessment.get('expiryCheck'))
    approval=assessment.setdefault('approval',{})
    approval['status']='NOT_READY' if status in {'PROCESSING_ERROR','RETAKE_REQUIRED','UNSUPPORTED_DOCUMENT','INCONSISTENT_DATA','PARTIALLY_READ'} else 'PENDING_MANUAL_REVIEW'
    approval.update(automaticApproval=False,documentVerified=False)
    reasons=list(approval.get('reviewReasons',[]))+['MANUAL_APPROVAL_POLICY','AUTHENTICITY_NOT_VERIFIED']
    if any(r['status']=='ERROR' for r in image_rows):reasons.append('IMAGE_ANALYSIS_ERROR')
    if any(r['status']=='FAIL' for r in image_rows):reasons.append('IMAGE_ANALYZER_FLAG_REQUIRES_REVIEW')
    approval['reviewReasons']=list(dict.fromkeys(reasons))
    risk=assessment.setdefault('imageRiskChecks',{})
    risk.setdefault('digitalTampering','NOT_IMPLEMENTED');risk.setdefault('screenOrPrintRecapture','NOT_IMPLEMENTED')
    risk.setdefault('physicalDocumentAuthenticity','NOT_IMPLEMENTED');risk.setdefault('holderOwnership','NOT_VERIFIED')
    for key,analyzer_id in [('digitalTampering','digital_tampering'),('screenOrPrintRecapture','screen_print_recapture')]:
        states=[r['status'] for r in image_rows if r.get('id')==analyzer_id]
        if states:risk[key]=states[0] if len(set(states))==1 else aggregate([r for r in image_rows if r.get('id')==analyzer_id])
    risk['experimentalDiagnostics']=image_state
    workflow='TECHNICAL_FAILURE' if status=='PROCESSING_ERROR' else 'NEEDS_RETAKE' if status=='RETAKE_REQUIRED' else 'AWAITING_ADMIN_REVIEW'
    result['pipelineReport']={'schemaVersion':'document-pipeline/0.1','pipelineVersion':'0.32.0',
        'readingStatus':status,'workflowStatus':workflow,'stages':stages,
        'samePhysicalCardVerified':False,'automaticApproval':False,'documentVerified':False,
        'imageAnalysisDecisionUsed':False,'scope':'READING_CONSISTENCY_AND_EXPERIMENTAL_IMAGE_SIGNALS'}
    if image_state=='ERROR':result['warnings']=list(dict.fromkeys(result.get('warnings',[])+['IMAGE_ANALYSIS_ERROR_NOT_A_FRAUD_VERDICT']))
    return result
