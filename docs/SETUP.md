# Quickstart: Joshua Bible Reader

## Prerequisites
- Visual Studio Code with Dev Containers extension
- Docker

## Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/danzuep/Joshua.git
   cd Joshua
   ```

2. Open in VS Code:
   - File > Open Folder > Select the Joshua directory
   - When prompted, click "Reopen in Container" to start the devcontainer

3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

## Running the App
1. Connect an Android device or start an emulator (API 34+)
2. Run the app:
   ```bash
   ./gradlew installDebug
   ```
   Or use VS Code's Run and Debug panel

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
- If build fails, check that devcontainer is running and Android SDK is available
- For emulator issues, ensure KVM is enabled in the container
- Network issues: Check internet for translation downloads

## Local environment setup (PowerShell)

The devcontainer provides these main tools:

- Git
- Java 25
- Gradle 9.4
- `uv`
- Android SDK command line tools
- Android platform tools
- Android build tools 36.0.0
- Android SDK platform `android-36`

Below is a PowerShell-based setup sequence for a local Windows environment.

### 1) Install required tools

#### Install PowerShell and Git
```powershell
winget install -e --id Microsoft.PowerShell
winget install -e --id Git.Git
```

#### Install Java 25
```powershell
# winget search Microsoft.OpenJDK
winget install -e --id Microsoft.OpenJDK.25
```

#### Install Gradle 9.4
```powershell
# winget search Gradle
winget install -e --id Gradle.Gradle
```

Here’s an alternative PowerShell (`pwsh`) way to download and install Gradle 9.4.1 from the ZIP URL.

```powershell
$gradleUrl = 'https://services.gradle.org/distributions/gradle-9.4.1-bin.zip'
$installDir = "${env:USERPROFILE}/tools/gradle"
$tempZip = "${env:TEMP}gradle-9.4.1-bin.zip"
New-Item -ItemType Directory -Force -Path $installDir | Out-Null
Invoke-WebRequest -Uri $gradleUrl -OutFile $tempZip
Expand-Archive -Path $tempZip -DestinationPath $installDir -Force
```

This will create:

```powershell
%USERPROFILE%\tools\gradle\gradle-9.4.1\
```

then add Gradle to your PATH for the current session:

```powershell
$env:Path = "$(Join-Path $installDir 'gradle-9.4.1\bin');$env:Path"
```

or optionally add it permanently for your user:

```powershell
$gradleBin = Join-Path $installDir 'gradle-9.4.1\bin'
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')

if ($userPath -notlike "*$gradleBin*") {
    [Environment]::SetEnvironmentVariable('Path', "$userPath;$gradleBin", 'User')
}
```

#### Install `uv` for Spec-Driven Development
```powershell
# powershell -ExecutionPolicy Bypass -c "irm https://astral.sh/uv/install.ps1 | iex"
winget install -e --id astral-sh.uv
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git
```

### 2) Install Android SDK command line tools
```powershell
# winget search Google.PlatformTools
winget install -e --id Google.PlatformTools
```

Alternatively, create a local SDK directory, then download and extract the Android command line tools:

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
New-Item -ItemType Directory -Force -Path $env:ANDROID_HOME | Out-Null

$androidToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$zip = "$env:TEMP\cmdline-tools.zip"
$extract = "$env:TEMP\android-cmdline-tools"

Invoke-WebRequest -Uri $androidToolsUrl -OutFile $zip
New-Item -ItemType Directory -Force -Path $extract | Out-Null
Expand-Archive -Path $zip -DestinationPath $extract -Force

New-Item -ItemType Directory -Force -Path "$env:ANDROID_HOME\cmdline-tools" | Out-Null
Move-Item -Force "$extract\cmdline-tools" "$env:ANDROID_HOME\cmdline-tools\latest"

Remove-Item -Recurse -Force $zip, $extract
```

### 3) Add Android tools to your PATH

For the current PowerShell session:

```powershell
$env:PATH = "$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

To persist it for your user account:

```powershell
[Environment]::SetEnvironmentVariable(
  "PATH",
  "$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;" + [Environment]::GetEnvironmentVariable("PATH", "User"),
  "User"
)
```

### 4) Accept Android SDK licenses and install SDK components

```powershell
sdkmanager --sdk_root=$env:ANDROID_HOME --licenses
sdkmanager --sdk_root=$env:ANDROID_HOME `
  "platform-tools" `
  "build-tools;36.0.0" `
  "platforms;android-36"
```

### 5) Verify the setup

```powershell
git --version
java --version
gradle --version
uv --version
sdkmanager --list | Select-Object -First 20
```

### 6) Verify the build
```powershell
gradle assembleDebug
gradle clean assembleDebug --stacktrace --full-stacktrace --no-daemon --console=plain 2>&1 | tee .gradle/full.log
cat .gradle/full.log | awk 'BEGIN{r=0} /KaptBaseError/{k=NR} {a[NR]=$0} END{for(i=k-1;i>=1;i--) if (a[i] ~ /Caused by: /) {start=i; break} if(!start) start=k-200; for(i=start;i<=k+20;i++) if(a[i]) print a[i]}' .gradle/full.log
```

### Notes

- The devcontainer uses Linux paths and installs packages with `apt`, so the local Windows setup is equivalent rather than identical.
- If you want a closer match on Windows, consider using WSL2 and following the same steps inside a Linux shell.
- Make sure `JAVA_HOME` is set if your local tooling requires it.
```