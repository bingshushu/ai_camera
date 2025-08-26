# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AICamera is an Android camera application built with Kotlin and Jetpack Compose. The app integrates RTSP streaming capabilities and follows modern Android development patterns.

## Architecture

- **Package Structure**: `com.ai.bb.camera`
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: Single Activity with Compose UI
- **Target SDK**: Android API 36 (minimum API 24)
- **Build System**: Gradle with Kotlin DSL and Version Catalogs

## Key Dependencies

- **RTSP Client**: Uses `rtsp-client-android` (v5.3.7) for streaming functionality
- **Compose BOM**: 2025.08.00 for UI components
- **Kotlin**: 2.2.10 with Compose compiler plugin

## Development Commands

### Building
```bash
./gradlew build
```

### Running Tests
```bash
# Unit tests
./gradlew test

# Instrumentation tests
./gradlew connectedAndroidTest
```

### Installing Debug Build
```bash
./gradlew installDebug
```

### Clean Build
```bash
./gradlew clean
```

## Project Structure

- `app/src/main/java/com/ai/bb/camera/` - Main application code
  - `MainActivity.kt` - Single activity hosting Compose UI
  - `ui/theme/` - Theme definitions (Color.kt, Theme.kt, Type.kt)
- `app/src/main/res/` - Android resources
- `app/src/test/` - Unit tests
- `app/src/androidTest/` - Instrumentation tests

## Permissions

The app requires INTERNET permission for RTSP streaming functionality.

## Build Configuration

- Java 11 compatibility
- Kotlin JVM target: 11
- Uses Gradle Version Catalogs (`gradle/libs.versions.toml`)
- ProGuard enabled for release builds