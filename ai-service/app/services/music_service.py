import asyncio
import os
import threading
from io import BytesIO

import requests
import torch
from PIL import Image

_MODEL_LOCK = threading.Lock()
_PROCESSOR = None
_MODEL = None

FLORENCE_MODEL_ID = os.getenv("FLORENCE_MODEL_ID", "microsoft/Florence-2-base").strip()
FLORENCE_TASK_PROMPT = os.getenv("FLORENCE_TASK_PROMPT", "<MORE_DETAILED_CAPTION>").strip()
FLORENCE_MAX_NEW_TOKENS = int(os.getenv("FLORENCE_MAX_NEW_TOKENS", "128"))
FLORENCE_NUM_BEAMS = int(os.getenv("FLORENCE_NUM_BEAMS", "3"))
FLORENCE_API_URL = os.getenv("FLORENCE_API_URL", "").strip()
FLORENCE_API_TOKEN = os.getenv("FLORENCE_API_TOKEN", "").strip()

_MUSIC_LIBRARY = [
    {
        "song_name": "Sunset Lover",
        "artist": "Petit Biscuit",
        "keywords": ["sunset", "beach", "sea", "ocean", "shore", "golden hour"],
        "reason": "Fits warm outdoor scenes with sunset or seaside vibes.",
    },
    {
        "song_name": "Golden Hour",
        "artist": "JVKE",
        "keywords": ["golden", "sunlight", "evening", "romantic", "couple", "portrait"],
        "reason": "Matches soft lighting and romantic portrait moments.",
    },
    {
        "song_name": "Sunflower",
        "artist": "Post Malone, Swae Lee",
        "keywords": ["flower", "garden", "park", "outdoor", "bright", "happy"],
        "reason": "Works well for bright, playful outdoor imagery.",
    },
    {
        "song_name": "Night Changes",
        "artist": "One Direction",
        "keywords": ["night", "city", "street", "lights", "urban", "car"],
        "reason": "Pairs nicely with city-night and street-light scenes.",
    },
    {
        "song_name": "Midnight City",
        "artist": "M83",
        "keywords": ["skyline", "building", "cityscape", "downtown", "neon", "night"],
        "reason": "Strong fit for energetic urban and skyline visuals.",
    },
    {
        "song_name": "Until I Found You",
        "artist": "Stephen Sanchez",
        "keywords": ["wedding", "dress", "love", "smile", "together", "celebration"],
        "reason": "Complements intimate couple and celebration scenes.",
    },
    {
        "song_name": "Ocean Eyes",
        "artist": "Billie Eilish",
        "keywords": ["blue", "calm", "dreamy", "water", "reflection", "soft"],
        "reason": "Matches calm, dreamy imagery and cool tones.",
    },
    {
        "song_name": "Adventure of a Lifetime",
        "artist": "Coldplay",
        "keywords": ["travel", "mountain", "hiking", "adventure", "road", "landscape"],
        "reason": "Fits travel, adventure, and expansive landscape shots.",
    },
    {
        "song_name": "Good Days",
        "artist": "SZA",
        "keywords": ["relaxing", "home", "window", "selfie", "cozy", "morning"],
        "reason": "Works for casual, cozy, and reflective everyday moments.",
    },
]

_FALLBACK_SUGGESTIONS = [
    {
        "song_name": "Golden Hour",
        "artist": "JVKE",
        "reason": "General-purpose pick for aesthetic photo posts.",
    },
    {
        "song_name": "Good Days",
        "artist": "SZA",
        "reason": "General-purpose pick for chill lifestyle content.",
    },
    {
        "song_name": "Adventure of a Lifetime",
        "artist": "Coldplay",
        "reason": "General-purpose pick for energetic visual storytelling.",
    },
]


def _apply_florence_compatibility(model):
    for attr in ("_supports_sdpa", "_supports_flash_attn_2", "_supports_flex_attn"):
        if not hasattr(model, attr):
            setattr(model, attr, False)

    config = getattr(model, "config", None)
    if config is not None:
        setattr(config, "_attn_implementation", "eager")
        setattr(config, "use_cache", False)

        if not hasattr(config, "forced_bos_token_id"):
            setattr(config, "forced_bos_token_id", None)

        language_config = getattr(config, "language_config", None)
        if language_config is not None and not hasattr(language_config, "forced_bos_token_id"):
            setattr(language_config, "forced_bos_token_id", None)

    generation_config = getattr(model, "generation_config", None)
    if generation_config is not None:
        setattr(generation_config, "_attn_implementation", "eager")
        setattr(generation_config, "use_cache", False)
        if not hasattr(generation_config, "forced_bos_token_id"):
            setattr(generation_config, "forced_bos_token_id", None)


def _load_florence_model():
    global _PROCESSOR, _MODEL

    if _PROCESSOR is not None and _MODEL is not None:
        return _PROCESSOR, _MODEL

    with _MODEL_LOCK:
        if _PROCESSOR is not None and _MODEL is not None:
            return _PROCESSOR, _MODEL

        try:
            from transformers import AutoModelForCausalLM, AutoProcessor
        except ImportError as exc:
            raise RuntimeError(
                "Florence-2 dependencies are missing. Install `transformers` and `timm`, "
                "or configure FLORENCE_API_URL to use an existing Florence-2 HTTP endpoint."
            ) from exc

        device = "cuda" if torch.cuda.is_available() else "cpu"
        dtype = torch.float16 if device == "cuda" else torch.float32

        _PROCESSOR = AutoProcessor.from_pretrained(FLORENCE_MODEL_ID, trust_remote_code=True)

        model_kwargs = {
            "torch_dtype": dtype,
            "trust_remote_code": True,
        }

        # Florence-2 community/remote-code checkpoints can break on newer
        # transformers defaults that auto-enable SDPA. Force eager attention.
        try:
            _MODEL = AutoModelForCausalLM.from_pretrained(
                FLORENCE_MODEL_ID,
                attn_implementation="eager",
                **model_kwargs,
            )
        except TypeError:
            _MODEL = AutoModelForCausalLM.from_pretrained(
                FLORENCE_MODEL_ID,
                **model_kwargs,
            )

        _apply_florence_compatibility(_MODEL)
        _MODEL.to(device)
        _MODEL.eval()
        return _PROCESSOR, _MODEL


def _download_image(image_url: str) -> Image.Image:
    response = requests.get(image_url, timeout=30)
    response.raise_for_status()
    return Image.open(BytesIO(response.content)).convert("RGB")


def _extract_caption_value(value):
    if value is None:
        return None
    if isinstance(value, str):
        cleaned = value.strip()
        return cleaned or None
    if isinstance(value, list):
        for item in value:
            caption = _extract_caption_value(item)
            if caption:
                return caption
        return None
    if isinstance(value, dict):
        for key in ("caption", "generated_text", "text", "answer", "content"):
            caption = _extract_caption_value(value.get(key))
            if caption:
                return caption
        for nested_value in value.values():
            caption = _extract_caption_value(nested_value)
            if caption:
                return caption
    return str(value).strip() or None


def _describe_image_via_http_api(image_url: str) -> str:
    headers = {}
    if FLORENCE_API_TOKEN:
        headers["Authorization"] = f"Bearer {FLORENCE_API_TOKEN}"

    response = requests.post(
        FLORENCE_API_URL,
        json={
            "image_url": image_url,
            "task": FLORENCE_TASK_PROMPT,
        },
        headers=headers,
        timeout=90,
    )
    response.raise_for_status()
    scene_description = _extract_caption_value(response.json())
    if not scene_description:
        raise RuntimeError("Florence-2 API returned an empty description.")
    return scene_description


def _describe_image_locally(image_url: str) -> str:
    processor, model = _load_florence_model()
    image = _download_image(image_url)

    inputs = processor(text=FLORENCE_TASK_PROMPT, images=image, return_tensors="pt")
    model_device = next(model.parameters()).device
    inputs = {key: value.to(model_device) if hasattr(value, "to") else value for key, value in inputs.items()}

    with torch.inference_mode():
        generated_ids = model.generate(
            **inputs,
            max_new_tokens=FLORENCE_MAX_NEW_TOKENS,
            num_beams=FLORENCE_NUM_BEAMS,
            use_cache=False,
        )

    generated_text = processor.batch_decode(generated_ids, skip_special_tokens=False)[0]
    parsed_output = processor.post_process_generation(
        generated_text,
        task=FLORENCE_TASK_PROMPT,
        image_size=(image.width, image.height),
    )
    scene_description = _extract_caption_value(parsed_output)
    if not scene_description:
        raise RuntimeError("Florence-2 returned an empty description.")
    return scene_description


def _build_music_suggestions(scene_description: str):
    normalized = scene_description.lower()
    ranked = []

    for index, item in enumerate(_MUSIC_LIBRARY):
        matched_keywords = [keyword for keyword in item["keywords"] if keyword in normalized]
        if not matched_keywords:
            continue

        ranked.append(
            (
                len(matched_keywords),
                -index,
                {
                    "song_name": item["song_name"],
                    "artist": item["artist"],
                    "preview_url": None,
                    "reason": f"{item['reason']} Matched keywords: {', '.join(matched_keywords)}.",
                },
            )
        )

    if not ranked:
        return [
            {
                "song_name": item["song_name"],
                "artist": item["artist"],
                "preview_url": None,
                "reason": item["reason"],
            }
            for item in _FALLBACK_SUGGESTIONS
        ]

    ranked.sort(reverse=True)
    return [item for _, _, item in ranked[:3]]


def _suggest_music_sync(image_url: str):
    if FLORENCE_API_URL:
        scene_description = _describe_image_via_http_api(image_url)
    else:
        scene_description = _describe_image_locally(image_url)

    return {
        "scene_description": scene_description,
        "suggestions": _build_music_suggestions(scene_description),
    }


async def suggest_music_from_image(image_url: str):
    return await asyncio.to_thread(_suggest_music_sync, image_url)
