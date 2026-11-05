from document_ai.document_comparison import parse_qr,compare_document

def test_observed_schema_not_universal():
 assert parse_qr(['123456789012\nNGUYỄN AN\n31/02/2000\nA1\nREGION'])['status']=='INCONCLUSIVE'
 assert parse_qr(['OTHER-FORMAT'])['status']=='INCONCLUSIVE'
 assert parse_qr([])['reasons']==['QR_MISSING_OR_AMBIGUOUS']

def test_name_text_match_does_not_bypass_confidence():
 r={'qrPayloads':{'BACK':['123456789012\nNGUYỄN AN\n01/01/2000\nA1\nREGION']},'ocrLines':{'FRONT':[
 {'text':'Full name:','confidence':.99,'polygon':[[0,0],[100,0],[100,20],[0,20]]},
 {'text':'NGUYEN AN','confidence':.85,'polygon':[[150,0],[250,0],[250,20],[150,20]]}]}}
 c=compare_document(r)
 item=next(x for x in c['comparisons'] if x['field']=='fullName')
 assert item['text_result']=='MATCH' and item['status']=='INCONCLUSIVE'
 assert c['document_validity']=='NOT_VERIFIED'
 assert compare_document(r,'strict')['comparisons'][1]['text_result']=='DIFFERENT'
