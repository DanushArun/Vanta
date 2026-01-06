#!/usr/bin/env python3
"""
Download Silero VAD ONNX model for Vanta.
Run this once before building the app.
"""

import urllib.request
import os

MODEL_URL = "https://raw.githubusercontent.com/snakers4/silero-vad/master/src/silero_vad/data/silero_vad.onnx"
OUTPUT_PATH = "app/src/main/assets/silero_vad.onnx"

def main():
    # Create assets directory if needed
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    
    if os.path.exists(OUTPUT_PATH):
        print(f"✓ Model already exists: {OUTPUT_PATH}")
        return
    
    print(f"Downloading Silero VAD model...")
    urllib.request.urlretrieve(MODEL_URL, OUTPUT_PATH)
    print(f"✓ Downloaded to: {OUTPUT_PATH}")

if __name__ == "__main__":
    main()
