from typing import List, Optional

from pydantic import BaseModel


class AiImageAnalyzeRequest(BaseModel):
    image_url: str


class MusicSuggestion(BaseModel):
    song_name: str
    artist: str
    preview_url: Optional[str] = None
    reason: Optional[str] = None


class AiMusicResponse(BaseModel):
    scene_description: str
    suggestions: List[MusicSuggestion]
