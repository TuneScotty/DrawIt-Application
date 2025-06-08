# DrawIt

A real-time multiplayer drawing and guessing game for Android where players take turns drawing while others try to guess the word.

## Features

- **Real-time Gameplay**: Play with friends with instant drawing updates via WebSockets
- **Lobby System**: Create, join, or browse game lobbies
- **In-game Chat**: Communicate with other players while guessing
- **Drawing Tools**: Intuitive drawing canvas with color selection and stroke width options
- **User Management**: Create accounts, customize avatars, and track game history
- **Round-based Gameplay**: Take turns drawing and guessing words within time limits

## Tech Stack

- **Language**: Java 11
- **Architecture**: MVVM with Repository Pattern
- **Networking**:
  - WebSockets for real-time game communication
  - Retrofit + OkHttp for REST API calls
  - Moshi for JSON serialization/deserialization
- **Dependency Injection**: Dagger Hilt
- **UI Components**:
  - Data Binding & View Binding
  - Material Components
  - Custom drawing views
- **Image Loading**: Glide
- **Local Storage**: Room Database
- **Security**: Android Security Crypto

## Getting Started

### Prerequisites

- Android Studio Flamingo (2022.2.1) or newer
- JDK 17+
- Android SDK API level 35

### Setup & Installation

1. Clone the repository:
   ```
   git clone https://github.com/TuneScotty/DrawIt-Application.git
   ```

2. Open the project in Android Studio.

3. Sync Gradle files and install dependencies.

4. Build the project:
   ```
   ./gradlew build
   ```

5. Run the app on an emulator or physical device:
   ```
   ./gradlew installDebug
   ```

## Architecture Overview

The app follows the MVVM (Model-View-ViewModel) architecture pattern with the following components:

### Core Components

- **View Layer**: Activities and Fragments in the `view` package responsible for UI rendering
- **ViewModel Layer**: ViewModels in the `viewmodel` package that handle UI logic and state management
- **Repository Layer**: Repositories in the `repository` package that coordinate data from different sources
- **Network Layer**: WebSocketService and API services in the `network` package for communication with backend
- **Model Layer**: Data models in the `model` package representing domain entities

### Key Systems

- **WebSocket Communication**: Real-time game state updates using OkHttp WebSockets
- **WebSocketMessageConverter**: Utility for standardized WebSocket message handling between raw JSON and typed model objects
- **Repository Pattern**: Centralized data management with clear separation of concerns
- **Dependency Injection**: Hilt provides application-wide dependency management

## License

This project is licensed under the MIT License - see the LICENSE file for details.
