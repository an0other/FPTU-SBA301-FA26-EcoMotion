"""Lossy name equivalence; never changes recognition text or verifies identity."""
import unicodedata


def unaccent_name(text):
    if not isinstance(text,str):raise ValueError('Name must be text')
    text=unicodedata.normalize('NFD',text.replace('Đ','D').replace('đ','d'))
    text=''.join(c for c in text if unicodedata.category(c)!='Mn')
    return ' '.join(text.upper().split())


def compare_names(left,right):
    a,b=unaccent_name(left),unaccent_name(right)
    exact=unicodedata.normalize('NFC',left)==unicodedata.normalize('NFC',right)
    matched=bool(a and b) and a==b
    return {'left_original':left,'right_original':right,'left_normalized':a,'right_normalized':b,
        'exact_match':exact,'accent_insensitive_match':matched,
        'result':'EXACT_MATCH' if exact and a else 'UNACCENTED_MATCH' if matched else 'INSUFFICIENT_TEXT' if not a or not b else 'DIFFERENT_NORMALIZED_TEXT',
        'lossy':True,'identity_verified':False,
        'note':'Ignores accents, case and whitespace. Does not repair missing letters; distinct names may collapse.'}
