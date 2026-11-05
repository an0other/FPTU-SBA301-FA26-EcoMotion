"""Printed-title evidence only. Never infer a type from the requested type."""
import re
from document_ai.name_comparison import unaccent_name

ALIASES = {
    'VN_DRIVING_LICENSE': ("DRIVER'S LICENSE", 'DRIVING LICEN', 'GIAY PHEP LAI XE'),
    'VN_CITIZEN_ID': ('CAN CUOC CONG DAN', 'CITIZEN IDENTITY', 'THE CAN CUOC'),
}
SUPPORT = ('FULL NAME','HO VA TEN','HO TEN','DATE OF BIRTH','NGAY SINH','HANG/CLASS',
           'HANG / CLASS','CLASS:','SO/NO','SO / NO','SO DINH DANH','GIOI TINH')


def spacing_match(alias, text):
    # Only whitespace tolerance. No spelling, character or number substitution.
    pattern = r'\s*'.join(re.escape(part) for part in alias.split())
    return re.search(r'(?<![A-Z0-9])'+pattern+r'(?![A-Z0-9])', text) is not None


def title_evidence(lines):
    text = ' '.join(unaccent_name(line['text']) for line in lines if isinstance(line.get('text'),str))
    support = [alias for alias in SUPPORT if spacing_match(alias,text)]
    matches=[]
    for kind, aliases in ALIASES.items():
        strict=[a for a in aliases if a in text]
        spacing=[a for a in aliases if a not in strict and spacing_match(a,text)]
        if strict or spacing and support:
            matches.append({'documentType':kind,'aliases':strict or spacing,
                'method':'EXISTING_TITLE_ALIAS' if strict else 'TITLE_WHITESPACE_TOLERANT_WITH_FIELD_CLUE',
                'supportingFieldClues':support})
    kinds=[v['documentType'] for v in matches]
    return {'status':'IDENTIFIED' if len(kinds)==1 else 'CONFLICT' if kinds else 'INCONCLUSIVE',
            'documentType':kinds[0] if len(kinds)==1 else None,'matches':matches,
            'scope':'PRINTED_TITLE_TEXT_ONLY','authenticityVerified':False}
