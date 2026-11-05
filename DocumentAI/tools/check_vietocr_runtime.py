"""Inspect runtime support without loading weights or reading private images."""
import argparse
import json
import sys


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--device',choices=['cpu','cuda:0'],default='cpu')
    args=parser.parse_args()
    result={'python':sys.executable,'requestedDevice':args.device,'modelInferenceChecked':False}
    try:
        import torch
        import torchvision
        from vietocr.tool.predictor import Predictor
        result.update(torchVersion=torch.__version__,torchvisionVersion=torchvision.__version__,
                      cudaBuild=torch.version.cuda,cudaAvailable=torch.cuda.is_available(),vietocrImport=True)
        if args.device.startswith('cuda'):
            if not torch.cuda.is_available():raise RuntimeError('CUDA is unavailable in this Python environment')
            result.update(gpuName=torch.cuda.get_device_name(0),totalGpuMb=round(torch.cuda.get_device_properties(0).total_memory/1048576))
        result['status']='RUNTIME_AVAILABLE'
    except Exception as exc:
        result.update(status='ERROR',errorType=type(exc).__name__,message=str(exc))
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if result['status']=='RUNTIME_AVAILABLE' else 1


if __name__=='__main__':sys.exit(main())
