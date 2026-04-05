from pydantic import BaseModel
from typing import List

class AiImageAnalyzeRequest(BaseModel):
    image_url: str

class Position(BaseModel):
    x: float
    y: float

class DetectedFace(BaseModel):
    matched_user_id: str
    confidence: float
    position: Position

class AiTagResponse(BaseModel):
    detected_faces: List[DetectedFace]