from fastapi import FastAPI
from app.api import embedding_router, face_router, music_router
import uvicorn

app = FastAPI(title="AI Microservice", version="1.0.0")

# === ROUTES ===
app.include_router(embedding_router.router)
app.include_router(face_router.router)
app.include_router(music_router.router)

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, workers=1)
