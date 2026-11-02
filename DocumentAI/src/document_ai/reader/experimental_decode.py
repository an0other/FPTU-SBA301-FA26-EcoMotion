"""Opt-in greedy decoder: keep token history/probabilities on the model device.

Matches VietOCR 0.3.13's EOS and confidence aggregation, including tokens after
an individual row's EOS until all rows finish. Still one scalar termination
sync per step. No beam search, QR correction, KV-cache or native-graph claim.
"""
import numpy as np


def device_translate(img,model,max_seq_length=128,sos_token=1,eos_token=2):
    import torch
    model.eval();count=img.shape[0];device=img.device
    steps=0;history=torch.full((1,count),sos_token,device=device,dtype=torch.long)
    probabilities=[];seen_eos=torch.zeros(count,device=device,dtype=torch.bool)
    with torch.inference_mode():
        memory=model.transformer.forward_encoder(model.cnn(img))
        pe=getattr(getattr(model.transformer,'pos_enc',None),'pe',None)
        capacity=int(pe.shape[0]) if pe is not None else max_seq_length+1
        limit=min(max_seq_length+1,capacity)
        while steps<limit:
            output,memory=model.transformer.forward_decoder(history,memory)
            # Upstream copies the whole prefix distribution to CPU each step.
            # Only the last timestep is used for greedy decoding.
            probs=torch.softmax(output[:,-1,:],dim=-1)
            values,indices=torch.topk(probs,5,dim=-1)
            token=indices[:,0];probability=values[:,0]
            history=torch.cat((history,token.unsqueeze(0)),dim=0)
            probabilities.append(probability)
            seen_eos=seen_eos | (token==eos_token);steps+=1
            if bool(seen_eos.all().item()):break
        ids=history.transpose(0,1).cpu().numpy()
        ps=torch.stack(probabilities,dim=1).cpu().numpy().astype(np.float64)
        mask=ids[:,1:]>3;ps=ps*mask
        denominator=(ps>0).sum(axis=1)
        scores=np.divide(ps.sum(axis=1),denominator,out=np.full(count,np.nan),where=denominator>0)
    truncated=~np.any(ids==eos_token,axis=1)
    # Missing EOS is not a complete prediction; downstream invalid confidence
    # handling may retry using the reference decoder under the existing budget.
    scores[truncated]=np.nan
    return ids,scores,{'decoder':'device-greedy','steps':steps,'terminationScalarSyncs':steps,
        'fullDistributionCpuCopiesPerStep':0,'truncatedIndexes':np.flatnonzero(truncated).tolist()}
