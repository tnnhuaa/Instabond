from fastapi import FastAPI
from app.api.embedding_router import router as embedding_router
import uvicorn

app = FastAPI(title="AI Microservice", version="1.0.0")

# === ROUTES ===
app.include_router(embedding_router)

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)