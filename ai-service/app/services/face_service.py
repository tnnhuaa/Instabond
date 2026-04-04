import os
from pathlib import Path

os.environ.setdefault("DEEPFACE_HOME", str(Path(__file__).resolve().parents[2] / ".deepface"))

from deepface import DeepFace
import numpy as np
from app.core.database import users_collection
from app.services.deepface_utils import safe_deepface_represent
import cv2
import requests
import tempfile

async def process_face_tagging(image_url: str):
    response = requests.get(image_url)
    with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tmp:
        tmp.write(response.content)
        tmp_path = tmp.name

    try:
        results = safe_deepface_represent(
            img_path=tmp_path,
            model_name='Facenet',
            enforce_detection=False,
        )

        if not results or (len(results) == 1 and results[0]['face_confidence'] == 0):
            return []

        cursor = users_collection.find({"face_embedding": {"$exists": True}})
        stored_users = await cursor.to_list(length=None)
        
        if not stored_users:
            return []

        detected_faces = []

        for face in results:
            target_embedding = np.array(face["embedding"])
            
            region = face["facial_area"]
            center_x = region["x"] + (region["w"] / 2)
            center_y = region["y"] + (region["h"] / 2)

            best_match_user_id = None
            max_similarity = -1

            for user in stored_users:
                source_embedding = np.array(user["face_embedding"])

                if source_embedding.shape != target_embedding.shape:
                    print(f"Bỏ qua user {user.get('_id')} vì size vector ({source_embedding.shape}) không khớp ({target_embedding.shape})")
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
                    "position": {"x": float(center_x), "y": float(center_y)}
                })

        return detected_faces
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
