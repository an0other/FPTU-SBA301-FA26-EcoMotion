import qrcode
import numpy as np
from PIL import Image
from document_ai.adapters.qr import scan_qr,native_box


def test_original_can_decode_when_aligned_cannot():
 original=qrcode.make('ORIGINAL-SOURCE-TEST').convert('RGB')
 aligned=Image.new('RGB',original.size,'white')
 result=scan_qr(original,alternate_image=aligned)
 assert result.status=='PASS'
 assert result.payload['values']==['ORIGINAL-SOURCE-TEST']
 assert any(x['source']=='original' for x in result.metrics['successful_attempts'])
 assert result.metrics['sources']['aligned']


def test_disagreeing_sources_do_not_choose_one():
 a=qrcode.make('SOURCE-A').convert('RGB');b=qrcode.make('SOURCE-B').convert('RGB')
 result=scan_qr(a,alternate_image=b)
 assert result.status=='INCONCLUSIVE' and result.reasons==['MULTIPLE_QR_PAYLOADS']


def test_preview_coordinates_map_to_original():
 box=native_box([[10,10],[30,10],[30,30],[10,30]],.25,(500,500))
 assert box[0]<40 and box[2]>120
 assert box[0]>=0 and box[2]<=500


def test_shared_attempt_cap_across_sources():
 image=Image.new('RGB',(300,300),'white')
 result=scan_qr(image,{'max_attempts':2},alternate_image=image.copy())
 assert len(result.metrics['attempts'])<=2
 assert result.status=='INCONCLUSIVE'


def test_saved_evidence_opt_in():
 image=Image.new('RGB',(300,300),'white')
 assert not scan_qr(image,{'max_attempts':1}).evidence
 assert scan_qr(image,{'max_attempts':1},save_evidence=True).evidence
