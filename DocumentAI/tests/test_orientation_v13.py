import numpy as np
import pytest
from PIL import Image,ImageDraw
from document_ai.reader.corner_order import order_corners,quarter_turn_points
from document_ai.reader.orientation import resolve_orientation


def card():
    im=Image.new('RGB',(160,100),'black');ImageDraw.Draw(im).rectangle((0,0,20,20),fill='red');return im


class MarkerOcr:
    def extract(self,image):
        if image.getpixel((0,0))==(255,0,0):return [{'text':"DRIVER'S LICENSE"},{'text':'Full name:'}]
        return [{'text':'random background text'}]


@pytest.mark.parametrize('rotation',[0,90,180,270])
def test_all_four_input_orientations(rotation):
    r=resolve_orientation(card().rotate(rotation,expand=True),MarkerOcr())
    assert r['status']=='RESOLVED'
    assert r['rotationApplied']==(-rotation)%360
    assert r['image'].size==(160,100)
    assert len(r['attempts'])==4


def test_equal_clues_are_not_forced():
    class SameOcr:
        def extract(self,image):return [{'text':"DRIVER'S LICENSE"}]
    r=resolve_orientation(card(),SameOcr())
    assert r['status']=='INCONCLUSIVE'
    assert r['rotationApplied'] is None and r['lines']==[]


def test_geometric_order_does_not_depend_on_click_order():
    points=np.array([[10,10],[140,20],[130,80],[20,90]])
    a=order_corners(points);b=order_corners(points[[2,0,3,1]])
    assert np.array_equal(a,b)
    assert a[0].tolist()==[10,10]
    # 90deg CCW coordinate mapping uses original width.
    assert quarter_turn_points([[10,20]],160,100,90).tolist()==[[20,149]]


def test_partial_probe_error_is_not_claimed_resolved():
    class FlakyOcr:
        count=0
        def extract(self,image):
            self.count+=1
            if self.count==4:raise RuntimeError('test')
            return MarkerOcr().extract(image)
    assert resolve_orientation(card(),FlakyOcr())['status']=='INCONCLUSIVE'
