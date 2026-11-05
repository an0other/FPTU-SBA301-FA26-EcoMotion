"""Image analyzer contract, separate from reading and eKYC decisions."""
from dataclasses import dataclass,field
from typing import Protocol
from PIL import Image


@dataclass(frozen=True)
class AnalysisInput:
    """Privacy-minimized image input; OCR text and identity fields are excluded."""

    image:Image.Image
    side:str
    sha256:str
    source_format:str


@dataclass
class AnalysisResult:
    """One analyzer's signal, not a document-authenticity verdict."""

    status:str
    reasons:list[str]=field(default_factory=list)
    metrics:dict=field(default_factory=dict)
    evidence:dict[str,bytes]=field(default_factory=dict)


class ImageAnalyzer(Protocol):
    """Replaceable adapter for one bounded image-analysis capability."""

    id:str
    version:str
    capabilities:tuple[str,...]
    def analyze(self,source:AnalysisInput)->AnalysisResult:...
