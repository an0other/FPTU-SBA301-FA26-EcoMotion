"""NumPy normalization for the experimental VietOCR batch predictor."""
import numpy as np


class Normalizer:
    def __init__(self,backend='numpy'):
        if backend!='numpy':raise ValueError('Only the NumPy preprocessor is supported')
        self.requested=backend
        self.actual='numpy'

    def prepare(self,batch):
        batch=np.asarray(batch)
        if batch.dtype!=np.uint8 or batch.ndim!=4 or batch.shape[-1]!=3:raise ValueError('Expected uint8 NHWC RGB batch')
        n,h,w,_=batch.shape
        if not 1<=n<=32 or not 1<=h<=128 or not 1<=w<=4096 or batch.size>32*1024*1024:raise ValueError('Preprocessor shape outside limits')
        # Float64 division then float32 exactly follows upstream np/255 -> FloatTensor.
        return np.ascontiguousarray((batch.astype(np.float64)/255.).transpose(0,3,1,2),dtype=np.float32)

    def report(self):
        return {'requested':self.requested,'actual':self.actual,
                'operation':'RGB_UINT8_NHWC_TO_FP32_NCHW_DIV255',
                'resize':'PIL_LANCZOS_UNCHANGED'}
