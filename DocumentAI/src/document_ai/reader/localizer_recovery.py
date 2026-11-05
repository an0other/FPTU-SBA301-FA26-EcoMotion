"""One extra segmentation attempt, only after NOT_FOUND; preserve source coordinates."""
import numpy as np


class RotationRecoveryLocator:
    def __init__(self,locator):self.locator=locator
    def __call__(self,image,source_hash):
        original=self.locator(image,source_hash)
        if original['status']!='NOT_FOUND':return original
        rotated=image.rotate(180,expand=True)
        retry=self.locator(rotated,source_hash)
        meta={'attempted':True,'rotationCounterClockwise':180,'primaryReason':original.get('reason'),
              'retryStatus':retry['status'],'maxExtraCalls':1}
        if retry['status']!='LOCATED':
            return {**(retry if retry['status']=='AMBIGUOUS' else original),'localizationRecovery':meta}
        transform=np.array([[-1.,0.,image.width-1],[0.,-1.,image.height-1],[0.,0.,1.]])
        def back(points):return [[image.width-1-float(x),image.height-1-float(y)] for x,y in points]
        result={**retry,'corners':back(retry['corners']),'localizationRecovery':meta,
                'method':retry.get('method','segmentation')+'+rotation-recovery/v1'}
        if retry.get('homography') is not None:result['homography']=(np.asarray(retry['homography'])@transform).tolist()
        if retry.get('cropContextCorners') is not None:result['cropContextCorners']=back(retry['cropContextCorners'])
        return result
