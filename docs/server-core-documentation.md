# DrawIt Server Documentation - Core Components

## Overview

The DrawIt server provides the backend infrastructure for the DrawIt drawing and guessing game. It handles user authentication, lobby management, game state, and real-time communication with clients.

## Technology Stack

- **Node.js**: JavaScript runtime environment
- **Express.js**: Web application framework
- **MongoDB**: NoSQL database for data persistence
- **Mongoose**: MongoDB object modeling tool
- **WebSocket (ws)**: Real-time bidirectional communication
- **JSON Web Tokens (JWT)**: Authentication mechanism

## Core Server File (`server.js`)

### Purpose
`server.js` is the main entry point for the DrawIt server application. It initializes the Express server, sets up middleware, defines API routes, establishes WebSocket connections, and implements game logic.

### Key Components

#### Server Initialization
```javascript
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const mongoose = require('mongoose');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
```

These lines import required libraries and initialize the Express application and WebSocket server.

#### Middleware Configuration
```javascript
app.use(cors()); // Allow cross-origin requests
app.use(express.json()); // Parse JSON request bodies
```

These middleware components enable CORS support and JSON parsing for incoming requests.

#### Database Connection
```javascript
mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/drawit', {
  useNewUrlParser: true,
  useUnifiedTopology: true
});
```

Establishes connection to MongoDB database using environment variable or default local connection.

#### WebSocket Connection Management
```javascript
// Maps to track WebSocket connections
const connections = new Map(); // userId -> WebSocket
const lobbyConnections = new Map(); // lobbyId -> Map(userId -> WebSocket)
const gameConnections = new Map(); // gameId -> Map(userId -> WebSocket)
```

These maps maintain the state of WebSocket connections for users, lobbies, and games.

### Authentication Functions

#### `generateToken(user)`
**Purpose**: Creates a JWT token for a user after successful authentication.

**Parameters**:
- `user`: User object containing userId and other user data

**Returns**: JWT token string

**Implementation Details**:
- Uses JWT to sign user data with a secret key
- Sets token expiration (typically 24 hours)
- Includes userId and username in token payload

#### `authenticateToken(req, res, next)`
**Purpose**: Middleware to verify JWT tokens in API requests.

**Parameters**:
- `req`: Express request object
- `res`: Express response object
- `next`: Express next middleware function

**Implementation Details**:
- Extracts token from Authorization header
- Verifies token signature using secret key
- Attaches decoded user information to request object
- Passes control to next middleware or returns 401 if invalid

#### `authenticateWebSocketConnection(req)`
**Purpose**: Validates WebSocket connection requests.

**Parameters**:
- `req`: WebSocket upgrade request

**Returns**: User object if authenticated, null otherwise

**Implementation Details**:
- Extracts token from URL query parameters
- Verifies token validity
- Returns decoded user information

### WebSocket Message Handling

#### `handleConnection(ws, req)`
**Purpose**: Sets up a new WebSocket connection.

**Parameters**:
- `ws`: WebSocket connection
- `req`: HTTP request object

**Implementation Details**:
- Authenticates the connection
- Registers the connection in the appropriate maps
- Sets up message and close event handlers
- Sends initial lobby list to client

#### `handleMessage(message, ws, userId)`
**Purpose**: Processes incoming WebSocket messages.

**Parameters**:
- `message`: Message data from client
- `ws`: WebSocket connection
- `userId`: User identifier

**Implementation Details**:
- Parses message JSON
- Routes to appropriate handler based on message type
- Handles various message types:
  - `join_lobby`: Connects user to a specific lobby
  - `leave_lobby`: Removes user from a lobby
  - `start_game`: Initiates a new game
  - `draw_line`: Broadcasts drawing data to players
  - `chat_message`: Sends chat messages to lobby
  - `guess`: Processes word guesses

#### `notifyLobbyStateChange(lobbyId)`
**Purpose**: Broadcasts lobby state changes to all connected clients in a lobby.

**Parameters**:
- `lobbyId`: Identifier for the lobby

**Implementation Details**:
- Fetches lobby data with populated player information
- Gets host user details
- Constructs a consistent message structure
- Sends the lobby state to all connected clients
- Broadcasts updated lobby list to all clients

#### `broadcastLobbyListUpdate()`
**Purpose**: Sends updated lobby list to all connected clients.

**Implementation Details**:
- Fetches all available lobbies
- Formats lobby data for clients
- Broadcasts to all connected WebSocket clients

#### `sendInitialLobbiesList(ws, userId)`
**Purpose**: Sends the initial list of lobbies to a newly connected client.

**Parameters**:
- `ws`: WebSocket connection
- `userId`: User identifier

**Implementation Details**:
- Fetches non-private and non-locked lobbies
- Formats lobby data for the client
- Sends formatted lobby list via WebSocket

### Potential Issues and Pitfalls

1. **WebSocket Message Structure Consistency**
   - Problem: Changes to message structure may break client compatibility
   - Solution: Maintain consistent message format and test thoroughly when making changes

2. **Player References Inconsistency**
   - Problem: System uses both userId (string) and MongoDB _id (ObjectId) to reference players
   - Solution: Be consistent in reference usage and document clearly which is used where

3. **Host Missing from Players List**
   - Problem: Host might not be properly added to the players list in MongoDB
   - Solution: Ensure host is explicitly added to players array when creating a lobby

4. **Race Conditions**
   - Problem: Concurrent updates to lobbies or games can cause data inconsistencies
   - Solution: Use MongoDB transactions or implement proper locking mechanisms

5. **Authentication Failures**
   - Problem: Token validation might fail silently
   - Solution: Add proper error handling and logging for authentication issues

6. **WebSocket Connection Leaks**
   - Problem: Connections might not be properly cleaned up when users disconnect
   - Solution: Ensure all cleanup happens in the close event handler

7. **Error Handling Gaps**
   - Problem: Some error conditions might not be handled properly
   - Solution: Implement comprehensive error handling throughout the codebase

## Performance Considerations

1. **Database Query Optimization**
   - Use proper indexes for frequently queried fields
   - Limit the fields returned in queries using projection
   - Use lean() for read-only queries to improve performance

2. **WebSocket Message Batching**
   - Consider batching small frequent updates to reduce overhead
   - Implement throttling for high-frequency events like drawing updates

3. **Connection Scaling**
   - Monitor active connections and implement limits if needed
   - Consider implementing a clustering solution for horizontal scaling

## Security Best Practices

1. **Token Management**
   - Use short-lived tokens and implement refresh token mechanism
   - Store JWT secret securely and rotate periodically

2. **Input Validation**
   - Validate all client input before processing
   - Use schema validation for request bodies

3. **Rate Limiting**
   - Implement rate limiting for authentication endpoints
   - Consider rate limiting WebSocket messages to prevent abuse

4. **CORS Configuration**
   - Restrict CORS to known origins in production
   - Don't use wildcard CORS in production environments
