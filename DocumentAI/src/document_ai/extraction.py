from document_ai.name_comparison import unaccent_name
import re
import unicodedata
from datetime import date,datetime


def normalize(value,kind):
    value=unicodedata.normalize("NFC",value).strip()
    if kind=="identifier":
        return re.sub(r"[\s.-]","",value).upper()
    if kind=="expiry" and " ".join(value.upper().split())=="KHÔNG THỜI HẠN":return "NO_EXPIRY"
    if kind in {"date","expiry"}:
        for fmt in ("%d/%m/%Y","%d-%m-%Y","%Y-%m-%d","%d.%m.%Y"):
            try:return datetime.strptime(value,fmt).date().isoformat()
            except ValueError:pass
        return None
    return " ".join(value.upper().split())


def extract_fields(lines_by_side,images_by_side,profile):
    fields=[]
    for spec in profile.get("fields",[]):
        side=spec["side"];items=lines_by_side.get(side,[]);image=images_by_side.get(side)
        selected=[]
        if image is not None:
            w,h=image.size;x1,y1,x2,y2=spec["roi"]
            for line in items:
                poly=line["polygon"];cx=sum(p[0] for p in poly)/len(poly)/w;cy=sum(p[1] for p in poly)/len(poly)/h
                if x1<=cx<=x2 and y1<=cy<=y2:selected.append(line)
        selected.sort(key=lambda x:(min(p[1] for p in x["polygon"]),min(p[0] for p in x["polygon"])))
        raw=" ".join(x["text"] for x in selected)
        conf=min((x["confidence"] for x in selected),default=None)
        if spec.get("pattern") and raw:
            match=re.search(spec["pattern"],raw)
            raw=match.group(1) if match and match.lastindex else match.group(0) if match else ""
        value=normalize(raw,spec.get("kind","text")) if raw else None
        fields.append({"name":spec["name"],"side":side,"source":"OCR","raw_value":raw or None,
            "value":value,"confidence":conf,"reliable":value is not None and conf is not None and conf>=spec.get("min_confidence",0.90)
                and not any(x.get("recognition_status") in {"DISAGREEMENT","SECONDARY_ERROR"} for x in selected)
                and (not spec.get("require_agreement",False) or bool(selected) and all(x.get("recognition_status")=="AGREEMENT" for x in selected))
                and (not spec.get("validation_pattern") or re.fullmatch(spec["validation_pattern"],value or "") is not None),
            "recognition_statuses":[x.get("recognition_status","UNKNOWN") for x in selected],
            "kind":spec.get("kind","text"),"required":spec.get("required",False)})
    return fields


def read_qr(image):
    from document_ai.adapters.qr import scan_qr
    return scan_qr(image).payload["values"]


def qr_fields(payloads,side,config):
    result=[]
    if len(payloads)!=1:return result
    # Only explicitly configured field positions; no universal GPLX/CCCD format assumed.
    parts=payloads[0].split(config["delimiter"])
    for spec in config.get("fields",[]):
        index=spec["index"]
        if 0<=index<len(parts):
            raw=parts[index];value=normalize(raw,spec.get("kind","text"))
            result.append({"name":spec["name"],"side":side,"source":"QR","raw_value":raw,
                "value":value,"confidence":None,"reliable":bool(value),"kind":spec.get("kind","text"),"required":False})
    return result


def compare(fields,declared,profile):
    results=[]
    for spec in profile.get("comparisons",[]):
        a=next((f for f in fields if all(f.get(k)==v for k,v in spec["left"].items())),None)
        b=next((f for f in fields if all(f.get(k)==v for k,v in spec["right"].items())),None)
        status="INCONCLUSIVE";reason="INSUFFICIENT_RELIABLE_DATA"
        if a and b and a["reliable"] and b["reliable"]:
            mode=spec.get("comparison_mode","strict")
            if mode=="unaccented_name":
                if a.get("kind")!="text" or b.get("kind")!="text" or spec.get("semantic_field")!="person_name":
                    raise ValueError("Unaccented comparison is for explicit person_name text only")
                left,right=unaccent_name(a["value"]),unaccent_name(b["value"])
                status="PASS" if left and left==right else "FAIL"
                reason="UNACCENTED_TEXT_MATCH" if status=="PASS" else "NORMALIZED_TEXT_DIFFERENCE"
            elif mode=="strict":
                status="PASS" if a["value"]==b["value"] else "FAIL";reason="MATCH" if status=="PASS" else "DATA_MISMATCH"
            else:raise ValueError("Unknown comparison_mode")
        results.append({"id":spec["id"],"status":status,"required":spec.get("required",True),"reason":reason,
            "left":a,"right":b,"comparison_mode":spec.get("comparison_mode","strict"),"identity_verified":False})
    for name,value in declared.items():
        f=next((x for x in fields if x["name"]==name and x["source"]=="OCR"),None)
        norm=normalize(value,f["kind"]) if f else None
        status="INCONCLUSIVE" if not f or not f["reliable"] or norm is None else "PASS" if f["value"]==norm else "FAIL"
        results.append({"id":"declared:"+name,"status":status,"required":True,
            "reason":"INSUFFICIENT_RELIABLE_DATA" if status=="INCONCLUSIVE" else "MATCH" if status=="PASS" else "DATA_MISMATCH",
            "left":f,"right":{"value":norm,"source":"DECLARED"}})
    for spec in profile.get("rules",[]):
        f=next((x for x in fields if all(x.get(k)==v for k,v in spec["field"].items())),None)
        status="INCONCLUSIVE";reason="INSUFFICIENT_RELIABLE_DATA"
        if f and f["reliable"]:
            if spec["type"]=="not_expired":
                try:status="PASS" if (f.get("kind")=="expiry" and f["value"]=="NO_EXPIRY") or date.fromisoformat(f["value"])>=date.today() else "FAIL";reason="DATE_VALID" if status=="PASS" else "DOCUMENT_EXPIRED"
                except ValueError:pass
            else:reason="UNSUPPORTED_RULE"
        results.append({"id":spec["id"],"status":status,"required":spec.get("required",True),"reason":reason})
    return results
