# 🤖 Kukki AI ✨

> **A Next-Generation, On-Device Gemini-Styled AI Voice Companion & Assistant for Android**

![Android](https://img.shields.io/badge/Platform-Android_API_26+-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin_%26_Java-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![AI Engine](https://img.shields.io/badge/AI_Engine-Arm_AiChat_GGUF-8AB4F8?style=for-the-badge&logo=openai&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

---

## 🌟 Overview

**Kukki AI** is a powerful, privacy-focused, on-device AI voice companion and digital assistant built for Android. Featuring local LLM inference, native Android automation, live global internet web search, and a Google Gemini-inspired translucent floating overlay interface, Kukki offers a seamless hands-free AI experience directly triggered from your phone's Power Button.

---

## ✨ Key Features

### 1. 🎨 Gemini-Inspired OLED Interface
- **Dark Obsidian Design**: Styled in midnight dark slate (`#131314`) with glassmorphic cards and glowing 4-point AI sparkle star logos.
- **Floating Input Capsule Bar**: A pill-shaped floating input bar with attachment upload, text input, speech microphone, and action buttons.
- **Model Selector Badge**: Top pill badge displaying `"Kukki AI ✨"`.

### 2. ⚡ Translucent Floating Assistant Overlay
- **Power Button & Gesture Trigger**: Responds to native Android Assistant intents (`android.intent.action.ASSIST`, `android.intent.action.VOICE_COMMAND`, `SEARCH_LONG_PRESS`). Long-pressing your phone's Power button triggers Kukki over any app or home screen.
- **Floating Bottom Capsule**: A translucent 40% dimmed overlay that displays a floating capsule bar at the bottom of the screen while keeping your underlying app visible.
- **Auto-Listening Mode**: Automatically activates voice listening as soon as the overlay pops up—no tapping required.

### 3. 🚀 Lightning-Fast Mid-Sentence Voice Recognition (< 100ms)
- **Partial Results Execution (`onPartialResults`)**: Voice recognition evaluates spoken words in real time. Commands like *"Open WhatsApp"*, *"Call Mom"*, or *"Launch Settings"* execute mid-sentence as soon as spoken.
- **Continuous Listening Recovery**: Automatically handles speech recognizer timeouts or microphone busy states, ensuring the floating capsule stays actively listening.

### 4. 📞 Direct WhatsApp Calling & Messaging
- **Direct WhatsApp VoIP Voice Calls**: Queries Android's `ContactsContract.Data` provider for WhatsApp's native VoIP MIME type (`vnd.android.cursor.item/vnd.com.whatsapp.voip.call`) to initiate direct WhatsApp voice calls.
- **Pre-filled WhatsApp Messaging**: Launches WhatsApp chats directly with pre-filled message text.

### 5. 🔊 Hands-Free Phone Calls & Speakerphone Mode
- **Speakerphone Integration**: Saying *"Call Mom on speaker"* or *"Make a call to Dad on speakerphone"* places the call and automatically turns on your phone's speakerphone (`AudioManager.setSpeakerphoneOn(true)`).
- **High-Confidence Contact Matching**: Uses fuzzy name matching with a strict **78% confidence threshold** to prevent accidental dialing.

### 6. 📄 Attachment & Document Reader
- **File & PDF Content Scanner**: Reads attached text files, source code (`.java`, `.kt`, `.py`, `.json`, `.csv`, `.md`, `.html`), and PDF documents via `InputStream` parsing.
- **Prompt Injection**: Automatically extracts document text and feeds it directly into Kukki's prompt for instant summarization, study assistance, or homework analysis.

### 7. 🌐 Live Global Internet Knowledge Search
- **HTTPS Background Web Search**: Queries live web knowledge from DuckDuckGo and Wikipedia APIs in background threads over HTTPS for real-time facts, news, and current events.

### 8. 🎓 Master Tutor & Multilingual Persona Engine
- **Multilingual Fluency**: Speaks and understands all global languages (English, Hindi, Spanish, French, German, Japanese, Mandarin, Arabic, Portuguese, Bengali, Russian, and regional dialects).
- **Adaptive Personas**:
  - **Master Tutor**: Step-by-step problem solver in Mathematics, Physics, Chemistry, Biology, Coding, and History.
  - **Empathetic Friend**: Warm, conversational chit-chat.
  - **Therapist / Listener**: Supportive, active listener for thoughts and feelings.
  - **Expert Advisor**: Clear, structured guidance.
- **Speech Synthesis (TTS) Tuning**: Calibrated to natural human speech cadence (0.96x rate, 1.02x pitch) with markdown symbol stripping (`**`, `#`, URLs) before speaking.

### 9. 🚲 Origin & Ownership Identity Story
Whenever asked *"Who made you?"*, *"Who designed you?"*, *"Who is your owner?"*, or *"Why are you named Kukki?"*, Kukki responds:
> *"I was designed by my master Anshuman, and my name was kept after the name of a bike which was named by my master and his friend and co-founder of KUKKI, master Aayush."*

---

## 🛠️ Tech Stack & Architecture

| Component | Technology |
| :--- | :--- |
| **Language** | Kotlin & Java (Android SDK 35) |
| **UI Framework** | Android View System + Material3 + Custom Glassmorphic XML Drawables |
| **AI Inference Engine** | Arm AiChat / On-Device GGUF Inference Engine (`com.arm.aichat`) |
| **Voice Speech Engine** | `android.speech.SpeechRecognizer` + `android.speech.tts.TextToSpeech` |
| **Networking** | Native Java `HttpURLConnection` + `HTTPS` |
| **Data Persistence** | `SharedPreferences` (`kukki_prefs`) & App Internal Files Directory |

---

## 📱 Getting Started & Setup

### Building the APK
Clone the repository and build the debug APK using Gradle:
```bash
git clone https://github.com/anshumannnraj/kukkiAI.git
cd kukkiAI
./gradlew assembleDebug
```
The compiled APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### Setting Kukki as Default Power Button Assistant
1. Go to phone **Settings** > **Apps** > **Default Apps** > **Digital Assistant** (or ask Kukki *"Set default assistant"*).
2. Select **Kukki** as your Default Voice Assistant.
3. Long-press your Power button anytime to trigger Kukki's floating bottom capsule!

---

## 👥 Credits & Creators

- **Creator & Lead Designer**: Master Anshuman ([@anshumannnraj](https://github.com/anshumannnraj))
- **Co-Founder**: Master Aayush
