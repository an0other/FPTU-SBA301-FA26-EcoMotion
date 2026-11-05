"""Reading completeness and printed-date checks, never a legal/authenticity verdict."""
from datetime import date,datetime,timedelta,timezone

GPLX_MAIN_FIELDS=('documentNumber','fullName','dateOfBirth','licenseClass','issueDate','expiryDate')
CORE_FIELDS=GPLX_MAIN_FIELDS[:4]


def assess_gplx(data,fields,ocr,status,today=None):
    today=today or datetime.now(timezone(timedelta(hours=7))).date()
    read=[k for k in GPLX_MAIN_FIELDS if ocr.get(k,{}).get('candidate') is not None]
    if status=='PROCESSING_ERROR':reading_status='ERROR'
    elif status in {'RETAKE_REQUIRED','UNSUPPORTED_DOCUMENT'}:reading_status='BLOCKED'
    elif all(k in read for k in GPLX_MAIN_FIELDS):reading_status='ALL_MAIN_FIELDS_READ'
    elif all(k in read for k in CORE_FIELDS):reading_status='CORE_FIELDS_READ'
    elif read:reading_status='PARTIAL'
    else:reading_status='NOT_READ'
    expiry=data.get('expiryDate');e=fields.get('expiryDate',{})
    expiration={'status':'UNKNOWN','value':expiry,'asOfDate':today.isoformat(),
        'source':e.get('source'),'legalValidity':'NOT_VERIFIED',
        'basis':'Printed expiry text only; not issuer lookup, revocation or eligibility check'}
    if expiry=='UNLIMITED':expiration['status']='NO_EXPIRY_PRINTED'
    elif expiry:
        try:
            expiration['status']='EXPIRED_DATE' if date.fromisoformat(expiry)<today else 'DATE_NOT_PASSED'
        except (ValueError,TypeError):pass
    # No inferred photo-forensics verdicts. These checks are explicitly unavailable.
    pending_checks={'digitalTampering':'NOT_IMPLEMENTED','screenOrPrintRecapture':'NOT_IMPLEMENTED',
                    'physicalDocumentAuthenticity':'NOT_IMPLEMENTED','holderOwnership':'NOT_VERIFIED'}
    return {'readingAssessment':{'status':reading_status,'scope':'OCR_ONLY',
                'mainFields':list(GPLX_MAIN_FIELDS),'ocrReadFields':read,
                'ocrMissingFields':[k for k in GPLX_MAIN_FIELDS if k not in read],
                'finalMissingFields':[k for k in GPLX_MAIN_FIELDS if data.get(k) is None]},
            'expiryCheck':expiration,'imageRiskChecks':pending_checks,
            'approval':{'status':'NOT_READY' if status in {'PROCESSING_ERROR','RETAKE_REQUIRED','UNSUPPORTED_DOCUMENT','INCONSISTENT_DATA'} or any(data.get(k) is None for k in CORE_FIELDS) else 'PENDING_MANUAL_REVIEW','automaticApproval':False,'documentVerified':False}}
