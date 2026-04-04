import torch
import torch.nn as nn
from torchvision import models, transforms

# Create singleton model
weights = models.MobileNet_V2_Weights.DEFAULT
model = models.mobilenet_v2(weights=weights)
model.classifier = nn.Identity()
model.eval()

# Set up preprocessing pipeline
preprocess = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])

def get_model():
    return model

def get_preprocess():
    return preprocess