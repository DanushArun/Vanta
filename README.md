# Vanta - Zero-UI Visual Assistant for the Blind

<p align="center">
  <strong>Making the inaccessible, accessible.</strong>
</p>

Vanta is a real-time multimodal AI assistant designed to help blind users navigate social situations. It uses the Gemini 2.0 Flash Live API to provide instant feedback about facial expressions, body language, and social dynamics.

## Features

### 🎭 Social Mode (Primary)
- **Vibe Check**: Real-time understanding of who's in the room, their expressions, and attention
- **Silent Exit Alerts**: Notifies when someone leaves without saying goodbye
- **Clock Directions**: "Sarah is at 2 o'clock, smiling at you"

### 🪞 Mirror Mode
- Quick appearance checks: clothing, stains, grooming
- Honest feedback before important meetings

### 🔇 Zero-UI Design
- No buttons, no menus - fully voice-controlled
- Barge-in: interrupt AI anytime by speaking
- TalkBack compatible

## Quick Start

### Prerequisites
- Android Studio (latest)
- Android device (API 29+) with camera
- Gemini API key

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/DanushArun/Vanta.git
   cd Vanta
   ```

2. **Download the Silero VAD model**
   ```bash
   python3 scripts/download_model.py
   ```

3. **Configure API key**
   ```bash
   cp .env.example .env
   # Edit .env and add your GEMINI_API_KEY
   ```

4. **Build and run**
   ```bash
   ./gradlew assembleDebug
   # Install on device via Android Studio
   ```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                           Android App                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   CameraX   │  │  AudioRecord │  │     Silero VAD         │  │
│  │   640x480   │  │    16kHz     │  │   (Speech Detection)   │  │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘  │
│         │                │                      │                │
│         │                │     ┌────────────────┤                │
│         ▼                ▼     ▼                │                │
│  ┌──────────────────────────────────────────┐   │                │
│  │           VantaCoordinator               │   │                │
│  │  • Routes camera frames + audio          │   │                │
│  │  • Handles barge-in detection            │◄──┘                │
│  │  • Manages state transitions             │                    │
│  └──────────────────┬───────────────────────┘                    │
│                     │                                            │
│                     ▼                                            │
│  ┌──────────────────────────────────────────┐                    │
│  │         GeminiLiveClient                 │                    │
│  │  • WebSocket to Gemini 2.0 Flash         │                    │
│  │  • Direct connection (no server)         │                    │
│  │  • Server VAD disabled (using Silero)    │                    │
│  └──────────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
                           │
                           │ WebSocket
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Gemini 2.0 Flash Live API                    │
└─────────────────────────────────────────────────────────────────┘
```

## Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

### TalkBack Testing

1. Enable TalkBack: Settings → Accessibility → TalkBack
2. Launch Vanta
3. Verify all states are announced:
   - "Vanta is connecting"
   - "Vanta is ready. Speak to ask a question."
   - "Vanta is speaking. You can interrupt anytime."

## Configuration

All settings are in `.env` or `app/src/main/java/com/vanta/core/config/`:

| Setting | Default | Description |
|---------|---------|-------------|
| `GEMINI_API_KEY` | - | Required API key |
| `CAMERA_FRAME_RATE` | 2 | FPS (1-4) |
| `CAMERA_JPEG_QUALITY` | 50 | JPEG compression |
| `VAD_RMS_THRESHOLD` | 800 | Speech detection sensitivity |

## Project Structure

```
app/src/main/java/com/vanta/
├── core/
│   ├── audio/          # Mic, Speaker, Silero VAD
│   ├── camera/         # CameraX manager
│   ├── common/         # Utilities, logging
│   └── config/         # VantaConfig, SystemPrompts
├── data/
│   └── network/        # GeminiLiveClient, messages
├── di/                 # Hilt modules
├── domain/
│   └── coordinator/    # Main orchestrator
├── service/            # Foreground service
└── ui/                 # MainActivity, Theme
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

MIT License - see [LICENSE](LICENSE)
