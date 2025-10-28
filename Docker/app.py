# app.py
from fastapi import FastAPI, UploadFile, File, HTTPException, Header
from fastapi.responses import JSONResponse
from transformers import pipeline
from PIL import Image
import io
import time, os

SERVICE_TOKEN = os.getenv("SERVICE_TOKEN", "dev-token")
app = FastAPI(title="ML Caption Server")

print("Start Model loading...")
pipe = pipeline("image-to-text", model="ydshieh/vit-gpt2-coco-en")
print("✅ Model loaded.")
#pipe = pipeline("image-to-text", model="./model")

@app.post("/analyze-image")
async def analyze_image(image: UploadFile = File(...), authorization: str = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing service token")
    token = authorization.split(" ", 1)[1]
    if token != SERVICE_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid service token")

    content = await image.read()
    img = Image.open(io.BytesIO(content))
    start = time.time()
    try:
        out = pipe(img, max_new_tokens=32)[0]["generated_text"]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    latency = int((time.time() - start) * 1000)
    return JSONResponse({
        "model": "ydshieh/vit-gpt2-coco-en",
        "caption": out,
        "latency_ms": latency
    })
