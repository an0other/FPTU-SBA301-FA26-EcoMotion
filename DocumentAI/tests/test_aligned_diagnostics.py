import numpy as np
import pytest
from PIL import Image
from document_ai.batch_evaluation import evaluate_report
from document_ai.diagnostics import aligned_from_report,compare_qr_sources
from document_ai.core import StepOutput,Status


def test_aligned_quality_failure_is_visible():
    report={'checks':[{'check':'QUALITY','image_id':'back','status':'PASS'}, {'check':'ALIGNED_QUALITY','image_id':'back','status':'FAIL','reasons':['BLUR']}, {'check':'QR','image_id':'back','status':'SKIPPED'}]}
    r=evaluate_report(report,{})
    assert r['qr_state']=='BLOCKED_BY_QUALITY'
    assert r['quality'][1]['check']=='ALIGNED_QUALITY'


def test_stored_transform_reconstruction_and_limits():
    image=Image.fromarray(np.arange(300,dtype=np.uint8).reshape(10,10,3))
    t={'image_id':'front','from':'exif_oriented_original','to':'manual_aligned','homography':np.eye(3).tolist(),'width':10,'height':10}
    aligned=aligned_from_report(image,{'transforms':[t]},'FRONT')
    assert np.array_equal(np.asarray(aligned),np.asarray(image))
    assert aligned_from_report(image,{'transforms':[t]},'BACK') is None
    t['width']=99999
    with pytest.raises(ValueError):aligned_from_report(image,{'transforms':[t]},'FRONT')


def test_separate_sources_and_payload_conflict():
    a=Image.new('RGB',(10,10));b=Image.new('RGB',(12,12));calls=[]
    def scanner(image,config):
        calls.append(image.size)
        return StepOutput(Status.PASS,payload={'values':[str(image.width)]})
    r=compare_qr_sources(a,b,scanner)
    assert calls==[(10,10),(12,12)]
    assert r['payload_conflict'] and r['status']=='INCONCLUSIVE'
    assert r['successful_sources']==['original','aligned']


def test_missing_aligned_does_not_claim_tested():
    def scanner(image,config):return StepOutput(Status.INCONCLUSIVE,payload={'values':[]})
    r=compare_qr_sources(Image.new('RGB',(10,10)),None,scanner)
    assert r['sources']['aligned']['status']=='SKIPPED'
