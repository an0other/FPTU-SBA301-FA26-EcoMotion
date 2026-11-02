"""Conservative eKYC evidence summary; never equates image signals with authenticity."""


def _image_signal_status(rows, analyzer_id):
    matches=[row for row in rows if row.get('id')==analyzer_id]
    if not matches:
        return 'NOT_IMPLEMENTED'

    states={row.get('status') for row in matches}
    if 'ERROR' in states:
        return 'ERROR'
    if 'FAIL' in states:
        return 'FLAGGED_FOR_REVIEW'
    if states=={'SKIPPED'}:
        return 'NOT_RUN'
    if 'NOT_IMPLEMENTED' in states:
        return 'NOT_IMPLEMENTED'
    if states=={'PASS'}:
        return 'NO_ANOMALY_DETECTED'
    return 'INCONCLUSIVE'


def build_document_authenticity(image_rows, printed_expiry_check=None):
    """Summarize what eKYC evidence is missing without issuing a real/fake verdict."""
    expiry=printed_expiry_check or {}
    dimensions={
        'digitalIntegrity':{
            'status':_image_signal_status(image_rows,'digital_tampering'),
            'scope':'IMAGE_SIGNAL_ONLY',
        },
        'captureIntegrity':{
            'status':_image_signal_status(image_rows,'screen_print_recapture'),
            'scope':'IMAGE_SIGNAL_ONLY',
        },
        'issuerAndProvenance':{
            'status':'NOT_IMPLEMENTED',
            'scope':'ISSUER_SIGNATURE_OR_TRUSTED_SOURCE_REQUIRED',
        },
        'currentValidity':{
            'status':'NOT_IMPLEMENTED',
            'scope':'ISSUER_OR_REVOCATION_CHECK_REQUIRED',
        },
        'printedExpiryDate':{
            'status':expiry.get('status','NOT_ASSESSED'),
            'source':expiry.get('source'),
            'scope':'PRINTED_DATE_ONLY_NOT_ISSUER_VALIDITY',
            'legalValidity':'NOT_VERIFIED',
        },
        'holderBinding':{
            'status':'NOT_VERIFIED',
            'scope':'IDENTITY_MATCH_AND_LIVENESS_REQUIRED',
        },
        'documentPairBinding':{
            'status':'NOT_VERIFIED',
            'scope':'SAME_PHYSICAL_DOCUMENT_NOT_ESTABLISHED',
        },
    }
    return {
        'status':'NOT_ESTABLISHED',
        'verified':False,
        'decisionPolicy':'EVIDENCE_ONLY_NO_AUTOMATIC_AUTHENTICITY_VERDICT',
        'dimensions':dimensions,
        'limitations':[
            'OCR or QR readability does not prove issuer, validity, or document integrity',
            'A clean image signal only means no anomaly was detected by that configured check',
            'Document authenticity and holder ownership require independent evidence',
        ],
    }
