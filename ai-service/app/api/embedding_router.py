from fastapi import APIRouter, HTTPException
from app.schemas.embedding_schema import EmbeddingRequest, EmbeddingResponse
from app.services.embedding_service import process_image_batch

router = APIRouter(prefix="/api/ai", tags=["Embeddings"])

@router.post("/embeddings", response_model=EmbeddingResponse)
async def generate_embeddings(request: EmbeddingRequest):
    try:
        result = await process_image_batch(request.urls)

        return result
    except ValueError as ve:
        raise HTTPException(status_code=400, detail=str(ve))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Server error: {str(e)}")