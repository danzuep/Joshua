# Quickstart: Joshua Bible Reader

## Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 19+ (KitKat 4.4+)
- JDK 11+

## Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/xizzhu/Joshua.git
   cd Joshua
   ```

2. Open in Android Studio:
   - File > Open > Select the Joshua directory
   - Wait for Gradle sync to complete

3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

## Running the App
1. Connect an Android device or start an emulator (API 19+)
2. Run the app:
   ```bash
   ./gradlew installDebug
   ```
   Or use Android Studio's Run button

## First Use
1. The app will show a list of available translations
2. Download a translation (e.g., KJV) - requires internet
3. Once downloaded, you can read offline
4. Try searching for "love" to see search functionality
5. Bookmark a verse by long-pressing

## Development
- Unit tests: `./gradlew test`
- Integration tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

## Troubleshooting
- If build fails, check Android SDK installation
- For emulator issues, ensure HAXM/KVM is installed
- Network issues: Check internet for translation downloads