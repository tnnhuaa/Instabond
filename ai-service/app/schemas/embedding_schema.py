from pydantic import BaseModel
from typing import List

class EmbeddingRequest(BaseModel):
    urls: List[str]

class EmbeddingResponse(BaseModel):
    average_embedding: List[float]