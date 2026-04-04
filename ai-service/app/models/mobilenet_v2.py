import os
from pathlib import Path

import torch
import torch.nn as nn
from torchvision import models, transforms

os.environ.setdefault("TORCH_HOME", str(Path(__file__).resolve().parents[2] / ".torch"))

_model = None

# Set up preprocessing pipeline
preprocess = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])

def get_model():
    global _model
    if _model is None:
        weights = models.MobileNet_V2_Weights.DEFAULT
        _model = models.mobilenet_v2(weights=weights)
        _model.classifier = nn.Identity()
        _model.eval()
    return _model

def get_preprocess():
    return preprocess
