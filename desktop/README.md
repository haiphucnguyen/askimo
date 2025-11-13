# Askimo Desktop

A Kotlin Compose Desktop application for the Askimo project.

## Features

- Modern Material Design 3 UI
- Simple chat interface with message bubbles
- User and AI message differentiation
- Responsive layout with scrollable message area

## Requirements

- JDK 21 or higher
- Gradle 8.x or higher

## Running the Application

To run the application in development mode:

```bash
./gradlew desktop:run
```

## Building the Application

To build the application:

```bash
./gradlew desktop:build
```

## Creating Native Distributions

You can create native distributions for different platforms:

```bash
# Create DMG for macOS
./gradlew desktop:packageDmg

# Create MSI for Windows
./gradlew desktop:packageMsi

# Create DEB for Linux
./gradlew desktop:packageDeb
```

## Project Structure

```
desktop/
├── build.gradle.kts          # Build configuration
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── io/askimo/desktop/
│       │       └── Main.kt    # Main application code
│       └── resources/         # Resources (icons, etc.)
└── README.md                  # This file
```

## Architecture

The application uses:
- **Compose Multiplatform**: For building the UI
- **Material 3**: For modern design components
- **Kotlin Coroutines**: For asynchronous operations

