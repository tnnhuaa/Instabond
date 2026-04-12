from fastapi import APIRouter, HTTPException
from app.schemas.tag_schema import AiImageAnalyzeRequest, AiTagResponse
from app.services.face_service import process_face_tagging
import traceback

router = APIRouter(prefix="/api/ai", tags=["Face Recognition"])

@router.post("/suggest-tags", response_model=AiTagResponse)
async def suggest_tags(request: AiImageAnalyzeRequest):
    try:
        faces = await process_face_tagging(request.image_url)
        return {"detected_faces": faces}
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"AI Error: {str(e)}")