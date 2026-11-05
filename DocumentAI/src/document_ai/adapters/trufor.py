import io
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

from document_ai.core import Status, StepOutput


class TruForAnalyzer:
    def analyze(self,raw,image,config):
        command=config.get("command")
        if not isinstance(command,list) or not command or not all(isinstance(v,str) for v in command):
            return StepOutput(Status.ERROR,["TRUFOR_COMMAND_NOT_CONFIGURED"])
        if not any("{input}" in x for x in command) or not any("{output}" in x for x in command):
            return StepOutput(Status.ERROR,["TRUFOR_COMMAND_PLACEHOLDERS_REQUIRED"])
        try:
            with tempfile.TemporaryDirectory(prefix="document-ai-") as folder:
                base=Path(folder);source=base/"input.jpg";output=base/"output";output.mkdir()
                source.write_bytes(raw)
                args=[x.replace("{input}",str(source)).replace("{output}",str(output)) for x in command]
                subprocess.run(args,cwd=config.get("cwd"),timeout=config.get("timeout_seconds",120),
                    stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True,shell=False)
                outputs=list(output.rglob("*.npz"))
                if len(outputs)!=1:return StepOutput(Status.ERROR,["TRUFOR_OUTPUT_MISSING_OR_AMBIGUOUS"])
                with np.load(outputs[0],allow_pickle=False) as result:
                    score=float(np.asarray(result["score"]).squeeze())
                    heat=np.asarray(result["map"]).squeeze()
                if not np.isfinite(score) or not 0<=score<=1 or heat.ndim!=2 or not np.isfinite(heat).all():
                    return StepOutput(Status.ERROR,["TRUFOR_OUTPUT_INVALID"])
                buf=io.BytesIO();Image.fromarray(np.clip(heat*255,0,255).astype(np.uint8)).save(buf,format="PNG")
                status=Status.INCONCLUSIVE;reasons=["THRESHOLDS_NOT_VALIDATED"]
                if config.get("thresholds_validated",False):
                    lo=config.get("pass_max");hi=config.get("suspicious_min")
                    if lo is None or hi is None or not 0<=lo<hi<=1:
                        return StepOutput(Status.ERROR,["INVALID_FORENSICS_THRESHOLDS"])
                    status=Status.PASS if score<=lo else Status.FAIL if score>=hi else Status.INCONCLUSIVE
                    reasons=[] if status==Status.PASS else ["TAMPERING_SIGNAL"] if status==Status.FAIL else ["FORENSICS_GRAY_ZONE"]
                return StepOutput(status,reasons,{"trufor_score":score,"score_is_authenticity_probability":False,
                    "map_coordinates":"upstream_input_decoding"},evidence={"trufor-map.png":buf.getvalue()},
                    implementation="trufor/external-process")
        except subprocess.TimeoutExpired:return StepOutput(Status.ERROR,["TRUFOR_TIMEOUT"])
        except (OSError,ValueError,KeyError,subprocess.CalledProcessError):return StepOutput(Status.ERROR,["TRUFOR_EXECUTION_ERROR"])
