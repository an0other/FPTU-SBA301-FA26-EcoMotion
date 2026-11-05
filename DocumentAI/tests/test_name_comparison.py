from document_ai.name_comparison import compare_names,unaccent_name

def test_d_and_diacritics():
 assert unaccent_name('VŨ TIẾN ĐƠN')=='VU TIEN DON'
 assert compare_names('VŨ TIẾN ĐƠN','VU TIÉN DON')['result']=='UNACCENTED_MATCH'
 assert compare_names('VŨ TIẾN ĐƠN','VÜ TIÉN DON')['accent_insensitive_match']

def test_missing_letter_is_not_covered_up():
 assert not compare_names('VŨ TIẾN ĐƠN','VÜ TIN ĐON')['accent_insensitive_match']

def test_collisions_are_not_identity_proof():
 r=compare_names('HÀ','HẠ')
 assert r['accent_insensitive_match'] and not r['identity_verified']
 assert not compare_names('','')['accent_insensitive_match']

def test_pipeline_policy_opt_in_and_confidence_preserved():
 from document_ai.extraction import compare
 fields=[{'name':'fullName','kind':'text','source':'OCR','value':'VU TIÉN DON','reliable':True},
         {'name':'fullName','kind':'text','source':'QR','value':'VŨ TIẾN ĐƠN','reliable':True}]
 spec={'id':'name','left':{'source':'OCR'},'right':{'source':'QR'}}
 assert compare(fields,{}, {'comparisons':[spec]})[0]['status']=='FAIL'
 spec.update(comparison_mode='unaccented_name',semantic_field='person_name')
 result=compare(fields,{}, {'comparisons':[spec]})[0]
 assert result['status']=='PASS' and result['reason']=='UNACCENTED_TEXT_MATCH'
 fields[0]['reliable']=False
 assert compare(fields,{}, {'comparisons':[spec]})[0]['status']=='INCONCLUSIVE'
