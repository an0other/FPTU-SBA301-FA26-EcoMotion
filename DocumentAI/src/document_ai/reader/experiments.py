"""Independent opt-in inference presets. No automatic winner or hardware switching."""
BASE=dict(fallback='profile',batch='legacy',decode='legacy',precision='fp32',preprocessor='numpy',
          fields='detected',batch_size=4,labels='owned',scheduler='legacy',rescue='full',
          pixel_reuse='0',scope='full',orientation='reference')
PRESETS={
 'v29':{**BASE,'fallback':'v29','labels':'legacy'},
 'fixed':dict(BASE),
 'targeted':{**BASE,'rescue':'targeted'},
 'exact':{**BASE,'batch':'exact','scheduler':'global'},
 'device':{**BASE,'batch':'exact','decode':'device','scheduler':'global'},
 'bucket':{**BASE,'batch':'bucket','decode':'device','scheduler':'global','batch_size':8},
 'direct':{**BASE,'fields':'direct'},
 'fp16':{**BASE,'batch':'bucket','decode':'device','scheduler':'global','precision':'fp16','batch_size':8},
 'reuse':{**BASE,'pixel_reuse':'1'},
 'core':{**BASE,'scope':'core','rescue':'targeted'},
 'orientation':{**BASE,'orientation':'text_verified'},
 'combined':{**BASE,'batch':'bucket','decode':'device','scheduler':'global','batch_size':8,
             'rescue':'targeted','pixel_reuse':'1','orientation':'text_verified'},
}


def environment(name):
    c=PRESETS[name]
    return {'DOCUMENT_AI_FALLBACK_POLICY':c['fallback'],'DOCUMENT_AI_BATCH_POLICY':c['batch'],
        'DOCUMENT_AI_DECODE_BACKEND':c['decode'],'DOCUMENT_AI_PRECISION':c['precision'],
        'DOCUMENT_AI_PREPROCESSOR':c['preprocessor'],'DOCUMENT_AI_FIELD_READ_MODE':c['fields'],
        'DOCUMENT_AI_BATCH_SIZE':str(c['batch_size']),'DOCUMENT_AI_BUCKET_WIDTH':'64',
        'DOCUMENT_AI_FIELD_LABEL_POLICY':c['labels'],'DOCUMENT_AI_BATCH_SCHEDULER':c['scheduler'],
        'DOCUMENT_AI_RESCUE_POLICY':c['rescue'],'DOCUMENT_AI_PIXEL_REUSE':c['pixel_reuse'],
        'DOCUMENT_AI_FIELD_SCOPE':c['scope'],'DOCUMENT_AI_ORIENTATION_EXIT':c['orientation']}
