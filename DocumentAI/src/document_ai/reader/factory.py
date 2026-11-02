"""Composition root for the local HTTP reader. Model modules load lazily."""
import os
import sys
from pathlib import Path
from .service import Reader,DEFAULT_OCR


def configured_reader():
    root=Path(__file__).resolve().parents[3]
    recognizer=os.getenv('DOCUMENT_AI_RECOGNIZER','vietocr-transformer')
    if recognizer not in {'paddle','vietocr-seq2seq','vietocr-transformer'}:
        raise ValueError('Invalid DOCUMENT_AI_RECOGNIZER')
    mode=os.getenv('DOCUMENT_AI_PERFORMANCE_MODE','balanced')
    pipeline=os.getenv('DOCUMENT_AI_PIPELINE_MODE','staged')
    optimization=os.getenv('DOCUMENT_AI_READER_OPTIMIZATION','efficient')
    if optimization not in {'baseline','efficient'}:raise ValueError('Invalid reader optimization')
    if pipeline not in {'staged','legacy'}:raise ValueError('Invalid pipeline mode')
    options={'field_profile':os.getenv('DOCUMENT_AI_FIELD_PROFILE',str(root/'configs/gplx-fields.pilot.json')),
             'qr_enabled':os.getenv('DOCUMENT_AI_QR_ENABLED','1')!='0',
             'performance_mode':mode,'recognition_scope':os.getenv('DOCUMENT_AI_RECOGNITION_SCOPE','full'),'qr_search':os.getenv('DOCUMENT_AI_QR_SEARCH','full')}
    if os.getenv('DOCUMENT_AI_CORNER_WEIGHTS'):
        from .localizers import load_locator
        options['locator']=load_locator(os.environ['DOCUMENT_AI_CORNER_WEIGHTS'],
            device=os.getenv('DOCUMENT_AI_LOCALIZER_DEVICE','cpu'),
            allow_legacy_pose=os.getenv('DOCUMENT_AI_ALLOW_LEGACY_POSE')=='1')
    if pipeline=='staged' and 'locator' in options and os.getenv('DOCUMENT_AI_LOCALIZER_RECOVERY','1')=='1':
        from .localizer_recovery import RotationRecoveryLocator
        options['locator']=RotationRecoveryLocator(options['locator'])
    if recognizer!='paddle':
        from document_ai.adapters.ocr import create_ocr
        from .vietocr_pipeline import VietOcrPipeline,VietOcrProcess
        default_python=root/'.venv-vietocr'/('Scripts/python.exe' if sys.platform=='win32' else 'bin/python')
        process=VietOcrProcess(os.getenv('DOCUMENT_AI_VIETOCR_PYTHON',str(default_python)),
            model='vgg_transformer' if recognizer=='vietocr-transformer' else 'vgg_seq2seq',
            device=os.getenv('DOCUMENT_AI_RECOGNIZER_DEVICE','cpu'),
            log=os.getenv('DOCUMENT_AI_VIETOCR_LOG'),cpu_threads=int(os.getenv('DOCUMENT_AI_VIETOCR_CPU_THREADS','4')),batch_size=int(os.getenv('DOCUMENT_AI_BATCH_SIZE','1')),transport=os.getenv('DOCUMENT_AI_CROP_TRANSPORT','png'))
        if mode=='optimized' or pipeline=='staged':
            from .vietocr_pipeline import DetectorOnlyVietOcrPipeline
            from .text_regions import PaddleTextRegions
            detector_mode=os.getenv('DOCUMENT_AI_DETECTOR_MODE','process' if pipeline=='staged' else 'inprocess')
            if detector_mode not in {'process','inprocess'}:raise ValueError('Invalid detector mode')
            if detector_mode=='process':
                from .detector_process import DetectorProcess
                detector=DetectorProcess(timeout=float(os.getenv('DOCUMENT_AI_DETECTOR_TIMEOUT','8')),
                    startup_timeout=float(os.getenv('DOCUMENT_AI_DETECTOR_STARTUP_TIMEOUT','120')),
                    log=os.getenv('DOCUMENT_AI_DETECTOR_LOG') or (str(Path(os.environ['DOCUMENT_AI_VIETOCR_LOG']).with_name('detector-worker.log')) if os.getenv('DOCUMENT_AI_VIETOCR_LOG') else None))
            else:detector=PaddleTextRegions()
            if pipeline=='staged':
                from .fast_orientation import ScaledDetector
                side=int(os.getenv('DOCUMENT_AI_DETECTION_MAX_SIDE','1280'))
                if not 256<=side<=2560:raise ValueError('Invalid detection max side')
                detector=ScaledDetector(detector,side)
            options['ocr']=DetectorOnlyVietOcrPipeline(create_ocr(DEFAULT_OCR),process,detector)
            if pipeline=='staged' and optimization=='efficient':
                options['ocr'].reuse_orientation_geometry=True
                options['ocr'].orientation_probe_limits=(2,4,6,12,None)
            if pipeline=='staged':
                orientation_ocr=os.getenv('DOCUMENT_AI_ORIENTATION_OCR','shared')
                if orientation_ocr not in {'shared','paddle'}:raise ValueError('Invalid orientation OCR')
                if orientation_ocr=='shared':options['ocr'].orientation_engine=options['ocr']
        else:options['ocr']=VietOcrPipeline(create_ocr(DEFAULT_OCR),process)
    if pipeline=='legacy':return Reader(**options)
    from .staged import StagedReader
    from .fast_orientation import PaddleOrientation
    orientation_mode=os.getenv('DOCUMENT_AI_ORIENTATION_MODE','model')
    if orientation_mode not in {'model','bounded'}:raise ValueError('Invalid orientation mode')
    if optimization=='efficient':
        # Stop redundant QR engines after a decode, but still inspect both images/sources.
        from document_ai.adapters.qr import scan_qr
        def fast_scanner(image,config=None,**kwargs):
            return scan_qr(image,{**(config or {}),'stop_after_source_success':True},**kwargs)
        options['scanner']=fast_scanner
    reader=StagedReader(**options,
        orientation_model=PaddleOrientation(os.getenv('DOCUMENT_AI_ORIENTATION_DEVICE','cpu')) if orientation_mode=='model' else None,
        preview_side=int(os.getenv('DOCUMENT_AI_PREVIEW_MAX_SIDE',str(min(1600,int(os.getenv('DOCUMENT_AI_DETECTION_MAX_SIDE','1280')))) if optimization=='efficient' else '960')),
        max_probes=int(os.getenv('DOCUMENT_AI_ORIENTATION_MAX_PROBES','4')),
        max_retries=int(os.getenv('DOCUMENT_AI_MAX_RETRIES','6')))

    reader.optimization_profile=optimization
    return reader
