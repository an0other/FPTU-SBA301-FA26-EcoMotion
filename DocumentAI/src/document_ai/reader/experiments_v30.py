"""Reproducible experiment presets. No automatic model/backend selection."""
PRESETS={
 'v29':dict(fallback='v29',batch='legacy',decode='legacy',precision='fp32',preprocessor='numpy',fields='detected',batch_size=4),
 'fixed':dict(fallback='profile',batch='legacy',decode='legacy',precision='fp32',preprocessor='numpy',fields='detected',batch_size=4),
 'device':dict(fallback='profile',batch='exact',decode='device',precision='fp32',preprocessor='numpy',fields='detected',batch_size=4),
 'bucket':dict(fallback='profile',batch='bucket',decode='device',precision='fp32',preprocessor='numpy',fields='detected',batch_size=8),
 'direct':dict(fallback='profile',batch='legacy',decode='legacy',precision='fp32',preprocessor='numpy',fields='direct',batch_size=4),
 'fp16':dict(fallback='profile',batch='bucket',decode='device',precision='fp16',preprocessor='numpy',fields='detected',batch_size=8),
 'combined':dict(fallback='profile',batch='bucket',decode='device',precision='fp32',preprocessor='numpy',fields='direct',batch_size=8),
}


def environment(name):
    c=PRESETS[name]
    return {'DOCUMENT_AI_FALLBACK_POLICY':c['fallback'],'DOCUMENT_AI_BATCH_POLICY':c['batch'],
        'DOCUMENT_AI_DECODE_BACKEND':c['decode'],'DOCUMENT_AI_PRECISION':c['precision'],
        'DOCUMENT_AI_PREPROCESSOR':c['preprocessor'],'DOCUMENT_AI_FIELD_READ_MODE':c['fields'],
        'DOCUMENT_AI_FIELD_LABEL_POLICY':'legacy','DOCUMENT_AI_BATCH_SCHEDULER':'legacy','DOCUMENT_AI_RESCUE_POLICY':'full','DOCUMENT_AI_PIXEL_REUSE':'0','DOCUMENT_AI_FIELD_SCOPE':'full','DOCUMENT_AI_ORIENTATION_EXIT':'reference','DOCUMENT_AI_BATCH_SIZE':str(c['batch_size']),'DOCUMENT_AI_BUCKET_WIDTH':'64'}
