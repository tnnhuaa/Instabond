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

def _extract_batch_embeddings_sync(image_urls: list):
    embeddings = []
    
    for url in image_urls:
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
        except requests.RequestException as e:
            print(f"Failed to download image {url}: {e}")
            continue

        with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
            tmp.write(response.content)
            tmp_path = tmp.name
            
        try:
            results = DeepFace.represent(img_path=tmp_path, model_name='Facenet', enforce_detection=False)
            
            # Using .get() for safety in case 'face_confidence' key is missing
            if results and len(results) > 0 and results[0].get('face_confidence', 0) > 0:
                embeddings.append(results[0]["embedding"])
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    if not embeddings:
        raise ValueError("No valid face embeddings found in the provided images.")
        
    # Calculate the average embedding
    avg_embedding = np.mean(embeddings, axis=0).tolist()
    
    return {"average_embedding": avg_embedding}

async def process_image_batch(image_urls: list):
    return await asyncio.to_thread(_extract_batch_embeddings_sync, image_urls)