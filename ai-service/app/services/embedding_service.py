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

async def process_image_batch(urls: list[str]) -> list[list[float]]:
    if not urls:
        return []
    
    # Create headers
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    }

    async with httpx.AsyncClient(headers=headers) as client:
        tasks = [download_and_preprocess_image(client, url) for url in urls]
        img_tensors = await asyncio.gather(*tasks)

    batch_tensor = torch.stack(img_tensors)

    with torch.no_grad():
        embeddings = model(batch_tensor)

        avg_embedding = torch.mean(embeddings, dim=0)

    return {
        "average_embedding": avg_embedding.tolist()
    }