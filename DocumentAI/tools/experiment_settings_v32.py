"""Print a named experiment's environment, without importing model runtimes."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from document_ai.reader.experiments import PRESETS,environment
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--name',choices=list(PRESETS),required=True);a=p.parse_args()
    print(json.dumps(environment(a.name)))
