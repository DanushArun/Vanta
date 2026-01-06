# Vanta

A Zero-UI multimodal visual assistant for the blind, powered by Gemini 2.0 Flash Live API.

## Overview

Vanta is an Android application designed to bridge the "social void" by providing real-time audio descriptions of visual and social contexts. The app leverages direct WebSocket connections to Google's Gemini API for low-latency, conversational assistance.

## Features

- **Social Mode** – Real-time "vibe checks" and social cue detection
- **Silent Exit Alerts** – Notifications when people leave the environment
- **Mirror Mode** – Appearance checks and self-descriptions
- **Zero-UI Design** – Fully audio-driven interface optimized for accessibility

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (minimal UI for accessibility)
- **Networking**: OkHttp WebSocket
- **Camera**: CameraX
- **DI**: Hilt
- **API**: Gemini 2.0 Flash Live (Multimodal)

## Setup

1. Clone the repository
2. Copy `.env.example` to `.env` and add your Gemini API key
3. Add the API key to `local.properties`:
   ```
   GEMINI_API_KEY=your_api_key_here
   ```
4. Build and run with Android Studio

## Requirements

- Android SDK 29+ (Android 10)
- Android Studio Hedgehog or later
- Gemini API access

## License

Proprietary – All rights reserved.
