"""Run replaceable image analyzers; errors and missing checks never become PASS."""
import json,os,re,time
from collections.abc import Sequence
from pathlib import Path
from .contracts import AnalysisInput,AnalysisResult,ImageAnalyzer
from .analyzers import ElaFftDiagnostics,UnconfiguredModel

STATUSES={'PASS','FAIL','INCONCLUSIVE','NOT_IMPLEMENTED','SKIPPED','ERROR'}


class ImageAnalysisRunner:
    def __init__(
        self,
        mode: str='off',
        analyzers: Sequence[ImageAnalyzer] | None=None,
    ):
        if mode not in {'off','diagnostic'}:raise ValueError('Image analysis mode must be off or diagnostic')
        self.mode=mode
        self.analyzers: tuple[ImageAnalyzer,...] = tuple(analyzers) if analyzers is not None else (
            ElaFftDiagnostics(),UnconfiguredModel('digital_tampering',('digital_tampering',)),
            UnconfiguredModel('screen_print_recapture',('screen_recapture','print_recapture')))
        ids=[a.id for a in self.analyzers]
        if len(ids)!=len(set(ids)) or any(not re.fullmatch('[a-z][a-z0-9_]{0,63}',key) for key in ids):raise ValueError('Invalid/duplicate analyzer id')

    def capabilities(self):
        return [{
            'id':analyzer.id,
            'version':analyzer.version,
            'capabilities':list(analyzer.capabilities),
            'implementation':getattr(
                analyzer,
                'implementation',
                'CUSTOM_ANALYZER_REQUIRES_VALIDATION'),
            'scope':getattr(analyzer,'scope','IMAGE_SIGNAL_ONLY'),
            'authenticityVerified':False,
        } for analyzer in self.analyzers]

    def run(self,source,evidence_dir=None):
        if source.side not in {'FRONT','BACK'}:raise ValueError('Invalid source side')
        rows=[]
        for analyzer in self.analyzers:
            began=time.perf_counter();evidence=[]
            try:
                if self.mode=='off':result=AnalysisResult('SKIPPED',['IMAGE_ANALYSIS_DISABLED'])
                else:
                    # One original decoded image per side, never a warped OCR crop or QR content.
                    # Copy isolates plugins from mutation of the reader's source image.
                    isolated=AnalysisInput(source.image.copy(),source.side,source.sha256,source.source_format)
                    result=analyzer.analyze(isolated)
                if not isinstance(result,AnalysisResult) or result.status not in STATUSES:raise ValueError('Invalid analyzer contract')
                json.dumps({'reasons':result.reasons,'metrics':result.metrics},allow_nan=False)
                if not isinstance(result.reasons,list) or not all(isinstance(v,str) for v in result.reasons):raise ValueError('Invalid reasons')
                if not isinstance(result.metrics,dict):raise ValueError('Invalid metrics')
                if not isinstance(result.evidence,dict):raise ValueError('Invalid evidence')
                if evidence_dir:
                    folder=Path(evidence_dir)/source.side.lower()/'image-analysis'/analyzer.id
                    for name,content in result.evidence.items():
                        if not re.fullmatch(r'[a-zA-Z0-9_-]+\.(png|jpg|json)',name) or not isinstance(content,bytes):raise ValueError('Unsafe evidence output')
                        folder.mkdir(parents=True,exist_ok=True);(folder/name).write_bytes(content)
                        evidence.append(str(folder/name))
            except Exception as error:
                result=AnalysisResult('ERROR',['IMAGE_ANALYZER_EXECUTION_ERROR'],{'exceptionType':type(error).__name__})
            rows.append({'id':analyzer.id,'version':analyzer.version,'side':source.side,
                'status':result.status,'reasons':result.reasons,'metrics':result.metrics,
                'inputSha256':source.sha256,'inputSource':'EXIF_ORIENTED_ORIGINAL_BEFORE_RECTIFICATION',
                'evidence':evidence,'durationMs':round((time.perf_counter()-began)*1000,2),
                'decisionUsed':False,'scope':'IMAGE_SIGNAL_ONLY_NOT_DOCUMENT_AUTHENTICITY'})
        return rows


def configured_analysis():
    return ImageAnalysisRunner(os.getenv('DOCUMENT_AI_IMAGE_ANALYSIS','off'))
