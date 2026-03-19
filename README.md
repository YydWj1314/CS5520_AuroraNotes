# Aurora Notes Native Java (Android Studio)

This is a **pure native Android Studio** runnable Java + XML starter version inspired by the original React Native AuroraNotesMobile app.

## What is included
- Native Android Studio project
- Java Activities + RecyclerView + XML layouts
- Login screen
- Notes list screen
- Create / edit note screen
- Search notes
- Pin notes
- In-memory repository for immediate local testing

## Why this is not a 1:1 automatic conversion
React Native UI and business logic cannot be directly transformed into a native Java Android app file-for-file. This project is a **native Android v1 port** that recreates the core mobile app flow in a form that opens and runs in Android Studio.

## How to run
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an emulator or device.

## Next steps to match the original app more closely
- Replace `NoteRepository` with Room + Supabase sync
- Add attachments / uploads
- Add AI summarization module
- Add weather / translation / news screens
- Add real authentication instead of the placeholder login flow

## Package
`com.auroranotesnative`
