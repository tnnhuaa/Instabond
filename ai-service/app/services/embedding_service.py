import os
from deepface import DeepFace
import numpy as np
import requests
import tempfile
import httpx
import asyncio
import torch
from PIL import Image
from io import BytesIO
from app.models.mobilenet_v2 import get_model, get_preprocess

model = get_model()
preprocess = get_preprocess()

async def download_and_preprocess_image(client: httpx.AsyncClient, url: str) -> torch.Tensor:
    try:
        response = await client.get(url)
        response.raise_for_status()
        img = Image.open(BytesIO(response.content)).convert('RGB')
        return preprocess(img)
    except Exception as e:
        raise ValueError(f"Error processing - {url}: {str(e)}")

async def process_image_batch(image_urls: list):
    embeddings = []
    
    for url in image_urls:
        response = requests.get(url)
        with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
            tmp.write(response.content)
            tmp_path = tmp.name
            
        try:
            results = DeepFace.represent(img_path=tmp_path, model_name='Facenet', enforce_detection=False)
            
            if results and len(results) > 0 and results[0]['face_confidence'] > 0:
                embeddings.append(results[0]["embedding"])
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
                print(f"Temporary file deleted: {tmp_path}")
    if not embeddings:
        raise ValueError("No valid face embeddings found in the provided images.")
        
    avg_embedding = np.mean(embeddings, axis=0).tolist()
    
    return {"average_embedding": avg_embedding}