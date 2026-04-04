import traceback

from fastapi import APIRouter, HTTPException

from app.schemas.music_schema import AiImageAnalyzeRequest, AiMusicResponse
from app.services.music_service import suggest_music_from_image

router = APIRouter(prefix="/api/ai", tags=["Music Suggestion"])


@router.post("/suggest-music", response_model=AiMusicResponse)
async def suggest_music(request: AiImageAnalyzeRequest):
    try:
        return await suggest_music_from_image(request.image_url)
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"AI Error: {str(e)}")
