FROM python:3.11-slim

# System libraries required by OpenCV, DeepFace, and other CV/ML packages
RUN apt-get update && apt-get install -y --no-install-recommends \
        libglib2.0-0 \
        libsm6 \
        libxext6 \
        libxrender1 \
        libgomp1 \
        libgl1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python dependencies (cached layer – only re-runs if requirements.txt changes)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy pre-downloaded MobileNetV2 checkpoint so Docker doesn't re-download it
COPY .torch .torch

# Copy application source
COPY app app

EXPOSE 8000

CMD ["python", "-m", "app.main"]
