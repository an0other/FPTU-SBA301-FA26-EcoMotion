from typing import Literal,Any
from pydantic import BaseModel,Field


class FieldResult(BaseModel):
    value:str|None=None
    source:Literal['OCR','QR','OCR_QR']|None=None
    status:str
    ocr:dict[str,Any]|None=None
    qrValue:str|None=None
    ocrCandidates:list[dict[str,Any]]=Field(default_factory=list)
    confidence:float|None=None
    bestEffort:bool=False
    needsReview:bool=False
    ocrAmbiguous:bool=False


class ReaderResponse(BaseModel):
    schemaVersion:str
    requestId:str
    code:str
    status:Literal['READ_SUCCESS','PARTIALLY_READ','RETAKE_REQUIRED','UNSUPPORTED_DOCUMENT','INCONSISTENT_DATA','REVIEW_REQUIRED','PROCESSING_ERROR']
    documentType:Literal['VN_DRIVING_LICENSE','VN_CITIZEN_ID']|None=None
    statusReasons:list[str]=Field(default_factory=list)
    expectedDocumentType:str
    data:dict[str,str|None]
    additionalData:dict[str,str|None]=Field(default_factory=dict)
    assessment:dict[str,Any]=Field(default_factory=dict)
    pipelineReport:dict[str,Any]=Field(default_factory=dict)
    imageAnalysis:dict[str,Any]=Field(default_factory=dict)
    recognitionScopeReport:list[dict[str,Any]]=Field(default_factory=list)
    printedTitleEvidence:dict[str,Any]=Field(default_factory=dict)
    executionReport:dict[str,Any]=Field(default_factory=dict)
    ocrLines:list[dict[str,Any]]=Field(default_factory=list)
    qrData:dict[str,Any]=Field(default_factory=dict)
    qrMetadata:dict[str,Any]=Field(default_factory=dict)
    fields:dict[str,FieldResult]
    ocrData:dict[str,Any]=Field(default_factory=dict)
    readerConfig:dict[str,Any]=Field(default_factory=dict)
    checks:list[dict[str,Any]]
    reasons:list[str]
    warnings:list[str]
    missingFields:list[str]
    inconsistentFields:list[str]
    optionalMissingFields:list[str]=Field(default_factory=list)
    optionalReviewFields:list[str]=Field(default_factory=list)
    coreComparison:dict[str,Any]|None=None
    orientations:list[dict[str,Any]]=Field(default_factory=list)
    images:list[dict[str,Any]]
    uncroppedSides:list[str]
    qr:dict[str,Any]
    verification:dict[str,str]
    limitations:list[str]
    elapsedMs:float
