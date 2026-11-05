import qrcode
from PIL import Image
from document_ai.adapters.qr import scan_qr


def qr(text):return qrcode.make(text).convert('RGB')

def test_qr_payload_is_preserved():
    result=scan_qr(qr('DOCUMENT-AI-TEST-001'))
    assert result.status=='PASS'
    assert result.payload['values']==['DOCUMENT-AI-TEST-001']
    assert not result.metrics['authenticity_verified']

def test_multiple_payloads_do_not_pass():
    a,b=qr('CARD-A'),qr('CARD-B')
    canvas=Image.new('RGB',(a.width+b.width+100,max(a.height,b.height)+60),'white')
    canvas.paste(a,(20,20));canvas.paste(b,(a.width+60,20))
    result=scan_qr(canvas)
    assert result.status=='INCONCLUSIVE'
    assert result.reasons==['MULTIPLE_QR_PAYLOADS']

def test_no_qr_is_not_fraud():
    result=scan_qr(Image.new('RGB',(300,300),'white'),{'max_attempts':3})
    assert result.status=='INCONCLUSIVE'
    assert result.reasons==['QR_NOT_DETECTED']
    assert len(result.metrics['attempts'])<=3
