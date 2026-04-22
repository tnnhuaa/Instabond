import asyncio
from deepface import DeepFace
import numpy as np
from app.core.database import users_collection
import cv2
import requests
import tempfile
import os

def _extract_face_features_sync(image_url: str):
    response = requests.get(image_url, timeout=30)
    with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
        tmp.write(response.content)
        tmp_path = tmp.name

    try:
        img = cv2.imread(tmp_path)
        if img is None:
            return None, None, None
        img_height, img_width = img.shape[:2]
        
        results = DeepFace.represent(img_path=tmp_path, model_name='Facenet', enforce_detection=False)
        
        return results, img_height, img_width
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


async def process_face_tagging(image_url: str):
    features = await asyncio.to_thread(_extract_face_features_sync, image_url)
    results, img_height, img_width = features

    # Check if valid results were returned
    if not results or (len(results) == 1 and results[0].get('face_confidence', 0) == 0):
        return []

    # Perform the asynchronous database query on the main event loop
    cursor = users_collection.find({"face_embedding": {"$exists": True}})
    stored_users = await cursor.to_list(length=None)
    
    if not stored_users:
        return []

    detected_faces = []

    # Execute the original mathematical logic for similarity comparison
    for face in results:
        target_embedding = np.array(face["embedding"])
        
        region = face["facial_area"]
        
        center_x = region["x"] + (region["w"] / 2)
        center_y = region["y"] + (region["h"] / 2)

        relative_x = center_x / img_width
        relative_y = center_y / img_height

        best_match_user_id = None
        max_similarity = -1

        for user in stored_users:
            source_embedding = np.array(user["face_embedding"])

            if source_embedding.shape != target_embedding.shape:
                continue
            
            similarity = np.dot(source_embedding, target_embedding) / (
                np.linalg.norm(source_embedding) * np.linalg.norm(target_embedding)
            )

            if similarity > max_similarity:
                max_similarity = similarity
                best_match_user_id = str(user["_id"])

        if max_similarity > 0.4:
            detected_faces.append({
                "matched_user_id": best_match_user_id,
                "confidence": round(float(max_similarity), 4),
                "position": {
                    "x": round(float(relative_x), 4), 
                    "y": round(float(relative_y), 4)
                }
            })

    return detected_faces