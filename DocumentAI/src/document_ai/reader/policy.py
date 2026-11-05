"""Reading consistency only: not authenticity or legal eligibility."""

GPLX_CORE_FIELDS = ('documentNumber', 'fullName', 'dateOfBirth', 'licenseClass')


def gplx_core_comparison(fields, qr_conflict=False):
    outcomes = {key: fields.get(key, {}).get('status', 'MISSING')
                for key in GPLX_CORE_FIELDS}
    if qr_conflict or any(v == 'MISMATCH' for v in outcomes.values()):
        status = 'MISMATCH'
    elif all(v in {'EXACT_MATCH', 'UNACCENTED_MATCH'} for v in outcomes.values()):
        status = 'MATCH'
    else:
        status = 'PARTIAL'
    return {
        'status': status,
        'fields': list(GPLX_CORE_FIELDS),
        'fieldStatuses': outcomes,
        'nameMode': 'ACCENT_INSENSITIVE_NO_LETTER_REPAIR',
        'excludedFields': ['address', 'nationality', 'issueDate', 'expiryDate'],
        'documentValidity': 'NOT_VERIFIED',
    }
