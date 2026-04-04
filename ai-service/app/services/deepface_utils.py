import os
from pathlib import Path

import gdown
import requests
from deepface import DeepFace
from deepface.models.facial_recognition.Facenet import FACENET128_WEIGHTS

FACENET128_FALLBACK_URLS = [
    FACENET128_WEIGHTS,
    "https://github.com/serengil/deepface_models/releases/download/pre-trained-weights/facenet_weights.h5",
    "https://drive.google.com/uc?id=1971Xk5RwedbudGgTIrGAL4F7Aifu7id1",
]
GITHUB_RELEASES_API = "https://api.github.com/repos/serengil/deepface_models/releases/latest"


def _deepface_home() -> Path:
    raw_home = os.environ.get("DEEPFACE_HOME", "")
    if raw_home:
        return Path(raw_home)
    return Path(__file__).resolve().parents[2] / ".deepface"


def _find_facenet_weight_files() -> list[Path]:
    home = _deepface_home()
    if not home.exists():
        return []
    return list(home.glob("**/facenet_weights.h5"))


def _facenet_weights_target() -> Path:
    home = _deepface_home()
    target_dir = home / ".deepface" / "weights"
    target_dir.mkdir(parents=True, exist_ok=True)
    return target_dir / "facenet_weights.h5"


def _looks_like_invalid_weight_file(path: Path) -> bool:
    if not path.exists():
        return True

    try:
        if path.stat().st_size < 1024 * 1024:
            return True

        with path.open("rb") as handle:
            signature = handle.read(16)

        if signature.startswith(b"Not Found"):
            return True

        if signature.startswith(b"<!DOCTYPE") or signature.startswith(b"<html"):
            return True

        return False
    except Exception:
        return True


def _looks_like_corrupted_facenet_weights(exc: Exception) -> bool:
    message = str(exc).lower()
    return (
        "facenet_weights.h5" in message
        or "loading the pre-trained weights" in message
        or "interruption during the download" in message
        or "unable to synchronously open file" in message
    )


def _repair_facenet_weights() -> bool:
    removed_any = False
    for path in _find_facenet_weight_files():
        try:
            path.unlink(missing_ok=True)
            print(f"Deleted corrupted Facenet weights: {path}")
            removed_any = True
        except Exception as repair_error:
            print(f"Could not delete corrupted Facenet weights {path}: {repair_error}")
    return removed_any


def _candidate_facenet_urls() -> list[str]:
    env_url = os.environ.get("FACENET_WEIGHTS_URL", "").strip()
    candidates = []
    if env_url:
        candidates.append(env_url)
    candidates.extend(_latest_release_asset_urls())
    candidates.extend(FACENET128_FALLBACK_URLS)
    return list(dict.fromkeys(candidates))


def _latest_release_asset_urls() -> list[str]:
    try:
        response = requests.get(
            GITHUB_RELEASES_API,
            timeout=30,
            headers={
                "Accept": "application/vnd.github+json",
                "User-Agent": "Instabond-AI-Service/1.0",
            },
        )
        response.raise_for_status()
        payload = response.json()
        assets = payload.get("assets", [])
        return [
            asset.get("browser_download_url", "").strip()
            for asset in assets
            if asset.get("name") == "facenet_weights.h5" and asset.get("browser_download_url")
        ]
    except Exception as exc:
        print(f"Could not resolve latest Facenet asset URL from GitHub API: {exc}")
        return []


def _download_facenet_weights() -> Path:
    target_path = _facenet_weights_target()
    temp_path = target_path.with_suffix(".tmp")
    errors = []

    for source_url in _candidate_facenet_urls():
        try:
            local_source = Path(source_url)
            if local_source.exists():
                local_bytes = local_source.read_bytes()
                temp_path.write_bytes(local_bytes)
            elif "drive.google.com" in source_url:
                gdown.download(source_url, str(temp_path), quiet=False, fuzzy=True)
            else:
                response = requests.get(
                    source_url,
                    stream=True,
                    timeout=120,
                    allow_redirects=True,
                    headers={"User-Agent": "Instabond-AI-Service/1.0"},
                )
                response.raise_for_status()

                with temp_path.open("wb") as handle:
                    for chunk in response.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            handle.write(chunk)

            if _looks_like_invalid_weight_file(temp_path):
                raise ValueError(f"Downloaded Facenet weights are invalid: {temp_path}")

            temp_path.replace(target_path)
            print(f"Downloaded Facenet weights from {source_url} to: {target_path}")
            return target_path
        except Exception as exc:
            errors.append(f"{source_url} -> {exc}")
        finally:
            if temp_path.exists():
                temp_path.unlink(missing_ok=True)

    raise ValueError("Could not download Facenet weights from any known source: " + " | ".join(errors))


def ensure_facenet_weights_ready() -> Path:
    target_path = _facenet_weights_target()
    if _looks_like_invalid_weight_file(target_path):
        _repair_facenet_weights()
        return _download_facenet_weights()
    return target_path


def safe_deepface_represent(img_path: str, model_name: str = "Facenet", enforce_detection: bool = False):
    if model_name.lower() == "facenet":
        ensure_facenet_weights_ready()

    try:
        return DeepFace.represent(
            img_path=img_path,
            model_name=model_name,
            enforce_detection=enforce_detection,
        )
    except Exception as exc:
        if model_name.lower() != "facenet" or not _looks_like_corrupted_facenet_weights(exc):
            raise

        repaired = _repair_facenet_weights()
        if not repaired and not _looks_like_invalid_weight_file(_facenet_weights_target()):
            raise

        ensure_facenet_weights_ready()

        print("Retrying DeepFace after repairing Facenet weights...")
        return DeepFace.represent(
            img_path=img_path,
            model_name=model_name,
            enforce_detection=enforce_detection,
        )
