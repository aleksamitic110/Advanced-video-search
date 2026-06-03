import os
from io import BytesIO

import numpy as np
from fastapi import FastAPI, File, UploadFile
from PIL import Image
from sentence_transformers import SentenceTransformer


MODEL_NAME = os.getenv("EMBEDDING_MODEL", "clip-ViT-B-32")

app = FastAPI(title="Video Search Embedding Service", version="0.1.0")
model: SentenceTransformer | None = None


def get_model() -> SentenceTransformer:
    global model
    if model is None:
        model = SentenceTransformer(MODEL_NAME)
    return model


@app.get("/api/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/api/embed/image")
async def embed_image(image: UploadFile = File(...)) -> dict:
    content = await image.read()
    pil_image = Image.open(BytesIO(content)).convert("RGB")
    embedding = get_model().encode([pil_image], normalize_embeddings=True)[0]
    vector = np.asarray(embedding, dtype=np.float32).tolist()
    return {
        "embedding": vector,
        "dimensions": len(vector),
        "model": MODEL_NAME,
    }
