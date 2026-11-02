"""Reading result only. Warnings about pilot validation are not approval decisions."""


def resolve_read_status(*,technical,failed_input,type_conflict,kind,qr_conflict,
                        mismatches,missing,uncertain,titles,duplicate_input,
                        uncropped_sides,unresolved_orientation,warnings):
    if technical:return 'PROCESSING_ERROR',['STEP_EXECUTION_ERROR']
    if failed_input:return 'RETAKE_REQUIRED',['INPUT_OR_CARD_QUALITY_UNSUITABLE']
    if type_conflict:return 'REVIEW_REQUIRED',['DOCUMENT_TYPE_CONFLICT']
    if not kind:
        return ('REVIEW_REQUIRED',['ORIENTATION_NOT_RESOLVED']) if unresolved_orientation else ('UNSUPPORTED_DOCUMENT',['DOCUMENT_TYPE_NOT_IDENTIFIED'])
    if qr_conflict or mismatches:
        return 'INCONSISTENT_DATA',(['QR_PAYLOAD_CONFLICT'] if qr_conflict else [])+(['OCR_QR_MISMATCH'] if mismatches else [])
    if missing:return 'PARTIALLY_READ',['REQUIRED_FIELDS_MISSING']
    blockers=[]
    if 'OCR_REGIONS_INCOMPLETE' in warnings:blockers.append('OCR_REGIONS_INCOMPLETE')
    if uncertain:blockers.append('REQUIRED_FIELDS_NEED_REVIEW')
    if not titles:blockers.append('TYPE_FROM_QR_ONLY')
    if duplicate_input:blockers.append('DUPLICATE_INPUT_IMAGES')
    if uncropped_sides:blockers.append('UNCROPPED_IMAGE_FALLBACK')
    if unresolved_orientation:blockers.append('ORIENTATION_NOT_RESOLVED')
    if 'POSSIBLE_SIDE_ORDER_ERROR' in warnings:blockers.append('POSSIBLE_SIDE_ORDER_ERROR')
    if kind=='VN_CITIZEN_ID' and 'ADDRESS_OCR_QR_NOT_CONFIRMED' in warnings:
        blockers.append('ADDRESS_OCR_QR_NOT_CONFIRMED')
    return ('REVIEW_REQUIRED',blockers) if blockers else ('READ_SUCCESS',[])
