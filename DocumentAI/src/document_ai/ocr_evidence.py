import io
from PIL import ImageDraw
from document_ai.adapters.ocr import crop_line


def evidence_for_lines(image, lines):
    evidence = {}
    overlay = image.copy().convert("RGB")
    draw = ImageDraw.Draw(overlay)
    for index, line in enumerate(lines):
        polygon = [tuple(point) for point in line["polygon"]]
        draw.line(polygon+[polygon[0]],fill="red",width=2)
        draw.text(polygon[0],str(index),fill="blue")
        try:
            crop = crop_line(image,line["polygon"])
            stream = io.BytesIO(); crop.save(stream,format="PNG")
            evidence[f"ocr-line-{index:03d}.png"] = stream.getvalue()
        except ValueError:
            continue
    stream = io.BytesIO();overlay.save(stream,format="PNG")
    evidence["ocr-overlay.png"] = stream.getvalue()
    return evidence
