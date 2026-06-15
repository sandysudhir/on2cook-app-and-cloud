# GitHub Setup

## Repository

Create a private GitHub repository named `on2cook-application`, then push this local repository to it.

Recommended remote:

```bash
git remote add origin https://github.com/<owner>/on2cook-application.git
git push -u origin main
```

## Required GitHub Secrets

Add these in GitHub under **Settings > Secrets and variables > Actions > New repository secret**.

| Secret | Purpose |
| --- | --- |
| `ON2COOK_ANDROID_KEYSTORE_BASE64` | Base64 encoded `ontocook_keystore.jks` file. |
| `ON2COOK_ANDROID_STORE_PASSWORD` | Android keystore password. |
| `ON2COOK_ANDROID_KEY_ALIAS` | Android signing key alias. |
| `ON2COOK_ANDROID_KEY_PASSWORD` | Android signing key password. |

The ZIP contained `ProjectData/ontocook_keystore.jks`, but that file is intentionally excluded from git.

## Android Releases

The Android release workflow runs when a tag matching `android-v*` is pushed.

Example:

```bash
git tag android-v26.2
git push origin android-v26.2
```

The workflow builds from `android-app/` and uploads APK/AAB artifacts to a GitHub Release.

## Local Build Requirements

To build locally, install:

- Android Studio
- JDK 17
- Android SDK platform 33

Then run:

```bash
cd android-app
./gradlew assembleRelease
```

On Windows:

```powershell
cd android-app
.\gradlew.bat assembleRelease
```

## Current Import Notes

- Imported source from `On2Cook_individual_streams_v11_functional_recipe_device_queue.zip`.
- Excluded generated APKs, JVM crash logs, IDE metadata, build outputs, and the private keystore.
- The ZIP did not include ESP32 firmware source, so `firmware-esp32/` remains a placeholder.
