# நம்ம ஊரு ஆப் (Namma Ooru App)

Namma Ooru App is an offline-first, vernacular-centric Android application designed to bridge the digital gap for rural and semi-urban communities in Tamil Nadu. It enables users to browse local shops and place orders using **Native Colloquial Tamil Voice Commands**, circumventing the need for typing.

The application automatically transcribes spoken Tamil to text and seamlessly dispatches the order (both text transcript and audio note) directly to the shopkeeper's WhatsApp, along with a stubbed IVR telephony alert.

## ✨ Key Features

- **🗣️ Native Tamil Speech-to-Text (STT):** Uses Android's native `SpeechRecognizer` configured specifically for regional Tamil (`ta-IN`) to convert spoken orders into text.
- **🎙️ Voice Recording & Compression:** Utilizes `MediaRecorder` to capture, encode (AAC), and store audio locally for order reference.
- **💬 Automated WhatsApp Dispatch:** Dynamically constructs structural message templates and uses Android deep-linking (`Intent.ACTION_VIEW`) to route orders directly to the merchant's WhatsApp, gracefully handling cases where WhatsApp isn't installed.
- **📞 Automated IVR Alert Stub:** Includes repository-level integration plans for outbound telephony (e.g., Exotel/Twilio) to dial the merchant's number when a digital order is placed.
- **🏪 Local Shop Directory:** A robust repository structure categorizing local businesses (Hotels, Pharmacies, Groceries) and Phase-2 Service Workers (Plumbers, Electricians) dynamically.
- **🎨 Modern Jetpack Compose UI:** Built entirely in Jetpack Compose utilizing Material Design 3 and a custom "Natural Tones" aesthetic for a warm, welcoming interface.

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture Flow:** Clean Architecture principles with state management.
- **Concurrency:** Kotlin Coroutines & Flows
- **Hardware Integration:** 
  - `Manifest.permission.RECORD_AUDIO` pipeline with Compose side-effects.
  - Native `SpeechRecognizer` and `MediaRecorder`.
- **Navigation:** Jetpack Navigation Compose (`NavHost`)

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana (or newer)
- Minimum SDK: API 24 (Android 7.0)
- Target SDK: API 34 (Android 14)

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/najibcode/NammaOoruApp.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Run the app on a physical device or an emulator. 
   *(Note: For the best Speech-to-Text and Audio Recording experience, testing on a physical Android device is highly recommended).*

## 📱 Screenshots & Workflows

1. **Home Screen:** Browse local business categories (Hotels, Shops, Medical).
2. **Order Screen (Mic Action):** The user taps the giant pulsing mic and speaks their order in Tamil.
3. **Dispatch:** The app requests runtime mic permissions, records the audio, transcribes it, and builds a confirmation screen.
4. **WhatsApp Handoff:** Upon confirmation, the app fires an Intent to WhatsApp with the user's details, text transcript, and configured dispatch parameters.

## 📂 Project Structure

```text
com.example
├── data/
│   └── ShopRepository.kt       # Data layer & IVR API Stubs
├── domain/
│   ├── Shop.kt                 # Shop Entity model
│   └── ServiceWorker.kt        # Service Worker Entity model
├── ui/
│   ├── components/             # Reusable UI widgets (Bottom Nav, Toolbars)
│   ├── navigation/             # Route configurations
│   ├── screens/                # Main feature screens (Home, Order, Orders, Help)
│   └── theme/                  # Material 3 typography, colors, and shapes
└── MainActivity.kt             # Main entry point and Navigation Host
```

## 🔐 Permissions
The application requires the following runtime permission for its core functionality:
- `android.permission.RECORD_AUDIO`: Required to listen to the user's voice for the Speech-to-Text engine and to save an audio note.

## 🤝 Contributing
Contributions, issues, and feature requests are welcome! 
Feel free to check the issues page.

## 📄 License
This project is licensed under the MIT License.
