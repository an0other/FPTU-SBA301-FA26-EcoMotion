"""Image-signal adapters; none of these signals alone establishes document authenticity."""
from .contracts import AnalysisResult


class ElaFftDiagnostics:
    id='ela_fft_diagnostics'
    version='ela-fft/diagnostic-v1'
    capabilities=('compression_metrics','frequency_metrics')
    implementation='EXPERIMENTAL_DIAGNOSTICS'
    scope='IMAGE_SIGNAL_ONLY'

    def analyze(self,source):
        from document_ai.imaging import diagnostics
        result=diagnostics(b'',source.image)
        metrics={**result.metrics,'sourceFormat':source.source_format,'sourceSize':list(source.image.size),
                 'analysisMaxSide':1200,'source':'EXIF_ORIENTED_ORIGINAL_BEFORE_RECTIFICATION',
                 'thresholdsValidated':False,'authenticityConclusionUsed':False}
        reasons=list(result.reasons)
        evidence=result.evidence
        if source.source_format!='JPEG':
            # A JPEG re-encode difference from PNG is not evidence of previous JPEG compression.
            metrics={k:v for k,v in metrics.items() if not k.startswith('ela_') and k!='jpeg_reencode_quality'}
            metrics['elaApplicability']='NOT_APPLICABLE_NON_JPEG'
            reasons.append('ELA_NOT_APPLICABLE_NON_JPEG');evidence={}
        return AnalysisResult('INCONCLUSIVE',reasons,metrics,evidence)


class UnconfiguredModel:
    version='not-configured/v1'
    implementation='NOT_CONFIGURED'
    scope='IMAGE_SIGNAL_ONLY'

    def __init__(self,id,capabilities):
        self.id=id
        self.capabilities=tuple(capabilities)

    def analyze(self,source):
        return AnalysisResult('NOT_IMPLEMENTED',['VALIDATED_MODEL_NOT_CONFIGURED'],
            {'modelLoaded':False,'thresholdsValidated':False,'decisionUsed':False})
