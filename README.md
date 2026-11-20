# Presence Studio

A clean Android app for managing Discord Rich Presence with custom profiles.

> **Warning**: Using a Discord user token to set presence is against Discord's Terms of Service. Use at your own risk.

## Features

- Beautiful dark UI (Jetpack Compose)
- Multiple saved profiles (Roblox, Minecraft, Netflix, custom...)
- Live presence preview
- Start / Clear presence with one tap
- Discord Gateway connection with auto-reconnect
- Settings for token management

## Requirements

- Android 8.0+ (API 26)
- Android Studio Ladybug or newer recommended
- JDK 17

## How to build

1. Open the project in Android Studio
2. Let Gradle sync
3. Run on device/emulator (or use the existing GitHub Actions style workflow if you add one)

## How to use

1. Open **Settings** tab
2. Paste your Discord user token and save
3. Go to **Home**
4. Select a profile (or create a new one in Profiles)
5. Tap **Start Presence**

## Notes on Assets / Images

To show custom large/small images in Rich Presence you need:

1. A Discord Application (from Discord Developer Portal)
2. Upload assets under Rich Presence → Art Assets
3. Put the Application ID and the asset keys into the profile

Without an Application ID + uploaded assets, the text part of presence still works, but images will not appear.

## Project structure

```
app/src/main/java/com/presencestudio/
├── MainActivity.kt
├── MainViewModel.kt
├── data/Models.kt
├── discord/DiscordGateway.kt      ← WebSocket + Presence logic
└── ui/
    ├── PresenceApp.kt
    ├── theme/Theme.kt
    └── screens/
        ├── HomeScreen.kt
        ├── ProfilesScreen.kt
        └── SettingsScreen.kt
```

## Disclaimer

This project is for educational / personal use. The developers are not responsible for any action taken against accounts that use user tokens.
