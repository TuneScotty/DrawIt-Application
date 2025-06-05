# DrawIt Android Client Documentation - Core Models

## Overview

This document details the core model classes in the DrawIt Android client. These models define the data structures used throughout the application and are essential for understanding how data flows between the server and UI components.

## Models

### User Model (`com.example.drawit_app.model.User`)

#### Purpose
The User model represents a player in the DrawIt application, storing profile information and player status.

#### Class Definition
```java
public class User {
    @Json(name = "userId")
    private String userId;
    
    @Json(name = "username")
    private String username;
    
    @Json(name = "avatarUrl")
    private String avatarUrl;
    
    @Json(name = "ready")
    private boolean ready;
    
    // Constructors, getters, and setters
}
```

#### Fields Explanation

| Field | Type | JSON Annotation | Description |
|-------|------|----------------|-------------|
| `userId` | String | @Json(name = "userId") | Unique identifier for the user |
| `username` | String | @Json(name = "username") | Display name shown in lobbies and games |
| `avatarUrl` | String | @Json(name = "avatarUrl") | URL to user's profile image |
| `ready` | boolean | @Json(name = "ready") | Indicates if player is ready in lobby |

#### Methods

##### Constructors
```java
// Default constructor
public User() {}

// Parameterized constructor
public User(String userId, String username, String avatarUrl) {
    this.userId = userId;
    this.username = username;
    this.avatarUrl = avatarUrl;
    this.ready = false;
}

// Full constructor
public User(String userId, String username, String avatarUrl, boolean ready) {
    this.userId = userId;
    this.username = username;
    this.avatarUrl = avatarUrl;
    this.ready = ready;
}
```

##### Getters and Setters
```java
public String getUserId() {
    return userId;
}

public void setUserId(String userId) {
    this.userId = userId;
}

public String getUsername() {
    return username;
}

public void setUsername(String username) {
    this.username = username;
}

public String getAvatarUrl() {
    return avatarUrl;
}

public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
}

public boolean isReady() {
    return ready;
}

public void setReady(boolean ready) {
    this.ready = ready;
}
```

#### Usage in Application

The User model is used for:
1. User authentication and profile management
2. Displaying player information in lobbies
3. Tracking player status during gameplay
4. Identifying the current user versus other players

#### Potential Issues

1. **JSON Deserialization**
   - Problem: Ensure @Json annotations match exact field names from server
   - Solution: Verify annotations match server API responses

2. **Default Values**
   - Problem: Some fields might be null in API responses
   - Solution: Initialize fields to appropriate defaults in constructors

3. **avatarUrl Handling**
   - Problem: Avatar URLs might be null or empty
   - Solution: Use placeholder image when avatarUrl is missing

### Lobby Model (`com.example.drawit_app.model.Lobby`)

#### Purpose
The Lobby model represents a game lobby where players gather before starting a game, storing lobby settings, player lists, and game configuration.

#### Class Definition
```java
public class Lobby {
    @Json(name = "lobbyId")
    private String lobbyId;
    
    @Json(name = "name")
    private String name;
    
    @Json(name = "hostId")
    private String hostId;
    
    @Json(name = "hostUser")
    private User hostUser;
    
    @Json(name = "maxPlayers")
    private int maxPlayers;
    
    @Json(name = "isPrivate")
    private boolean isPrivate;
    
    @Json(name = "password")
    private String password;
    
    @Json(name = "players")
    private List<User> players;
    
    @Json(name = "isLocked")
    private boolean isLocked;
    
    @Json(name = "numRounds")
    private int numRounds;
    
    @Json(name = "roundDurationSeconds")
    private int roundDurationSeconds;
    
    // Constructors, getters, and setters
}
```

#### Fields Explanation

| Field | Type | JSON Annotation | Description |
|-------|------|----------------|-------------|
| `lobbyId` | String | @Json(name = "lobbyId") | Unique identifier for the lobby |
| `name` | String | @Json(name = "name") | Display name of the lobby |
| `hostId` | String | @Json(name = "hostId") | User ID of the lobby host |
| `hostUser` | User | @Json(name = "hostUser") | Complete User object for the host |
| `maxPlayers` | int | @Json(name = "maxPlayers") | Maximum allowed players |
| `isPrivate` | boolean | @Json(name = "isPrivate") | Whether lobby requires password |
| `password` | String | @Json(name = "password") | Password for private lobbies |
| `players` | List<User> | @Json(name = "players") | List of players in the lobby |
| `isLocked` | boolean | @Json(name = "isLocked") | Whether lobby is locked |
| `numRounds` | int | @Json(name = "numRounds") | Number of rounds for the game |
| `roundDurationSeconds` | int | @Json(name = "roundDurationSeconds") | Time per round in seconds |

#### Methods

##### Constructors
```java
// Default constructor - initializes players list to avoid NPE
public Lobby() {
    players = new ArrayList<>();
}

// Minimal constructor
public Lobby(String lobbyId, String name, String hostId) {
    this.lobbyId = lobbyId;
    this.name = name;
    this.hostId = hostId;
    this.players = new ArrayList<>();
}

// Full constructor
public Lobby(String lobbyId, String name, String hostId, User hostUser, int maxPlayers, 
             boolean isPrivate, String password, List<User> players, boolean isLocked,
             int numRounds, int roundDurationSeconds) {
    this.lobbyId = lobbyId;
    this.name = name;
    this.hostId = hostId;
    this.hostUser = hostUser;
    this.maxPlayers = maxPlayers;
    this.isPrivate = isPrivate;
    this.password = password;
    this.players = players != null ? players : new ArrayList<>();
    this.isLocked = isLocked;
    this.numRounds = numRounds;
    this.roundDurationSeconds = roundDurationSeconds;
}
```

##### Important Getter Methods
```java
// Standard getters and setters for all fields...

// Special method to get host user with fallbacks
public User getHost() {
    // First priority: use hostUser field if available
    if (hostUser != null) {
        return hostUser;
    }
    
    // Second priority: find player with matching hostId
    if (players != null && hostId != null) {
        for (User player : players) {
            if (hostId.equals(player.getUserId())) {
                return player;
            }
        }
    }
    
    // Fallback: create minimal User with hostId
    if (hostId != null) {
        return new User(hostId, "Unknown Host", "");
    }
    
    // Last resort
    return new User("unknown", "Unknown Host", "");
}
```

#### Usage in Application

The Lobby model is used for:
1. Displaying lobby lists
2. Showing lobby details
3. Managing player lists
4. Configuring game settings
5. Tracking lobby state through WebSocket updates

#### Potential Issues

1. **Host User Resolution**
   - Problem: "Unknown Host" might appear if hostUser and players list don't contain host
   - Solution: Implement comprehensive fallback logic in getHost() method

2. **Null Players List**
   - Problem: players might be null from API
   - Solution: Initialize players as empty ArrayList in all constructors

3. **hostUser vs hostId Inconsistency**
   - Problem: hostUser might be populated while hostId is null or vice versa
   - Solution: Ensure getHost() properly handles all scenarios

4. **WebSocket Message Parsing**
   - Problem: hostUser field may be at payload level rather than nested in lobby
   - Solution: Ensure LobbyStateMessage properly extracts hostUser

### Game Model (`com.example.drawit_app.model.Game`)

#### Purpose
The Game model represents an active drawing and guessing game, tracking game state, player scores, rounds, and word information.

#### Class Definition
```java
public class Game {
    @Json(name = "gameId")
    private String gameId;
    
    @Json(name = "lobbyId")
    private String lobbyId;
    
    @Json(name = "status")
    private String status;
    
    @Json(name = "currentRound")
    private int currentRound;
    
    @Json(name = "totalRounds")
    private int totalRounds;
    
    @Json(name = "roundDurationSeconds")
    private int roundDurationSeconds;
    
    @Json(name = "currentDrawer")
    private String currentDrawer;
    
    @Json(name = "currentWord")
    private String currentWord;
    
    @Json(name = "usedWords")
    private List<String> usedWords;
    
    @Json(name = "players")
    private List<Player> players;
    
    @Json(name = "startTime")
    private String startTime;
    
    @Json(name = "endTime")
    private String endTime;
    
    @Json(name = "roundEndTime")
    private String roundEndTime;
    
    @Json(name = "createdAt")
    private String createdAt;
    
    // Last guess timestamps for rate limiting
    private Map<String, Long> lastGuessTimestamps;
    
    // Constructors, getters, and setters
}
```

#### Fields Explanation

| Field | Type | JSON Annotation | Description |
|-------|------|----------------|-------------|
| `gameId` | String | @Json(name = "gameId") | Unique identifier for the game |
| `lobbyId` | String | @Json(name = "lobbyId") | ID of the associated lobby |
| `status` | String | @Json(name = "status") | Current game state (waiting, starting, in_progress, etc.) |
| `currentRound` | int | @Json(name = "currentRound") | Current round number |
| `totalRounds` | int | @Json(name = "totalRounds") | Total number of rounds |
| `roundDurationSeconds` | int | @Json(name = "roundDurationSeconds") | Time per round in seconds |
| `currentDrawer` | String | @Json(name = "currentDrawer") | User ID of current drawer |
| `currentWord` | String | @Json(name = "currentWord") | Word being drawn |
| `usedWords` | List<String> | @Json(name = "usedWords") | Words already used in game |
| `players` | List<Player> | @Json(name = "players") | Player data including scores |
| `startTime` | String | @Json(name = "startTime") | When game started (ISO date string) |
| `endTime` | String | @Json(name = "endTime") | When game ended (ISO date string) |
| `roundEndTime` | String | @Json(name = "roundEndTime") | When current round ends (ISO date string) |
| `createdAt` | String | @Json(name = "createdAt") | Timestamp of game creation (ISO date string) |
| `lastGuessTimestamps` | Map<String, Long> | (Not serialized) | Tracks player guess timestamps for rate limiting |

#### Methods

##### Constructors
```java
// Default constructor
public Game() {
    players = new ArrayList<>();
    usedWords = new ArrayList<>();
    lastGuessTimestamps = new HashMap<>();
}

// Minimal constructor
public Game(String gameId, String lobbyId) {
    this.gameId = gameId;
    this.lobbyId = lobbyId;
    this.players = new ArrayList<>();
    this.usedWords = new ArrayList<>();
    this.lastGuessTimestamps = new HashMap<>();
}
```

##### Important Game Logic Methods
```java
// Rate limiting for guesses
public boolean isGuessRateLimited(String userId) {
    Long lastTimestamp = lastGuessTimestamps.get(userId);
    long currentTime = System.currentTimeMillis();
    
    if (lastTimestamp != null && currentTime - lastTimestamp < 1000) {
        return true; // Rate limited - less than 1 second since last guess
    }
    
    // Update timestamp and allow guess
    lastGuessTimestamps.put(userId, currentTime);
    return false;
}

// Set current word and track in usedWords
public void setCurrentWord(String word) {
    this.currentWord = word;
    if (word != null && !usedWords.contains(word)) {
        usedWords.add(word);
    }
}

// Find player by userId
public Player getPlayerById(String userId) {
    if (players == null || userId == null) return null;
    
    for (Player player : players) {
        if (userId.equals(player.getUserId())) {
            return player;
        }
    }
    return null;
}

// Check if current user is the drawer
public boolean isCurrentUserDrawing(String currentUserId) {
    return currentUserId != null && currentUserId.equals(currentDrawer);
}
```

#### Player Inner Class
```java
public static class Player {
    @Json(name = "userId")
    private String userId;
    
    @Json(name = "username") 
    private String username;
    
    @Json(name = "score")
    private int score;
    
    @Json(name = "hasGuessedCorrectly")
    private boolean hasGuessedCorrectly;
    
    // Constructor and getter/setter methods
    
    // Note special getter naming convention
    public boolean isHasGuessedCorrectly() {
        return hasGuessedCorrectly;
    }
    
    public void setHasGuessedCorrectly(boolean hasGuessedCorrectly) {
        this.hasGuessedCorrectly = hasGuessedCorrectly;
    }
    
    public void addPoints(int points) {
        this.score += points;
    }
}
```

#### Usage in Application

The Game model is used for:
1. Tracking active game state
2. Managing player scores and guessing status
3. Handling word selection and validation
4. Implementing game rules like guess rate limiting
5. Determining which player is drawing

#### Potential Issues

1. **Method Naming Conventions**
   - Problem: Some methods follow non-standard naming (e.g., isHasGuessedCorrectly)
   - Solution: Always verify exact method names before use

2. **Rate Limiting Implementation**
   - Problem: Relies on client-side timestamps which could be manipulated
   - Solution: Consider server-side validation for critical game rules

3. **Time String Parsing**
   - Problem: Date strings from server need proper parsing
   - Solution: Use appropriate date parsing utilities for ISO format

4. **Word List Management**
   - Problem: Potential duplicate words in usedWords list
   - Solution: Check for duplicates when adding words

### WebSocket Message Models

#### LobbyStateMessage (`com.example.drawit_app.network.message.LobbyStateMessage`)

#### Purpose
Represents WebSocket messages containing lobby state updates.

#### Class Definition
```java
public class LobbyStateMessage extends WebSocketMessage {
    
    public static final String TYPE = "lobby_state";
    
    @Json(name = "payload")
    private LobbyPayload payload;
    
    // Inner classes and methods
    
    public static class LobbyPayload {
        @Json(name = "lobby")
        private Lobby lobby;
        
        @Json(name = "hostUser")
        private User hostUser;
        
        @Json(name = "players")
        private List<User> players;
        
        @Json(name = "event")
        private String event;
        
        // Getters and setters
    }
}
```

#### Fields Explanation

| Field | Type | JSON Annotation | Description |
|-------|------|----------------|-------------|
| `payload` | LobbyPayload | @Json(name = "payload") | Container for lobby data |

#### LobbyPayload Fields

| Field | Type | JSON Annotation | Description |
|-------|------|----------------|-------------|
| `lobby` | Lobby | @Json(name = "lobby") | Lobby object with details |
| `hostUser` | User | @Json(name = "hostUser") | Host user at payload level |
| `players` | List<User> | @Json(name = "players") | Players list at payload level |
| `event` | String | @Json(name = "event") | Event type (e.g., "player_joined") |

#### Methods

```java
// Constructor
public LobbyStateMessage() {
    super(TYPE);
}

// Getters and setters
public LobbyPayload getPayload() {
    return payload;
}

public void setPayload(LobbyPayload payload) {
    this.payload = payload;
}

// LobbyPayload getters and setters
public static class LobbyPayload {
    // Getters and setters for all fields
    
    public Lobby getLobby() {
        return lobby;
    }
    
    public void setLobby(Lobby lobby) {
        this.lobby = lobby;
    }
    
    public User getHostUser() {
        return hostUser;
    }
    
    public void setHostUser(User hostUser) {
        this.hostUser = hostUser;
    }
    
    public List<User> getPlayers() {
        return players;
    }
    
    public void setPlayers(List<User> players) {
        this.players = players;
    }
    
    public String getEvent() {
        return event;
    }
    
    public void setEvent(String event) {
        this.event = event;
    }
}
```

#### Usage in Application

The LobbyStateMessage model is used for:
1. Parsing WebSocket messages of type "lobby_state"
2. Updating UI when lobby details change
3. Handling real-time player join/leave events
4. Processing host changes

#### Potential Issues

1. **Message Structure Changes**
   - Problem: Server may change message structure
   - Solution: Ensure WebSocket message parsing is robust and logs errors

2. **hostUser Location**
   - Problem: hostUser field might be at payload level or nested in lobby
   - Solution: Handle both cases in deserialization and UI logic

3. **Missing Fields**
   - Problem: Some fields might be null in WebSocket messages
   - Solution: Implement null checks and fallbacks

## Best Practices for Model Usage

### JSON Serialization/Deserialization

1. **Use Consistent Annotations**
   - Use @Json annotations consistently for all fields
   - Match annotation names exactly with server API field names

2. **Handle Null Values**
   - Initialize collections in constructors to avoid NullPointerExceptions
   - Provide fallbacks for missing or null fields

3. **Debugging Deserialization**
   - Add logging for parsing failures
   - Use Retrofit/Moshi debugging tools when issues arise

### Model Relationships

1. **User-Lobby Relationship**
   - A lobby contains a list of players (User objects)
   - A lobby has a host (hostId or hostUser)

2. **Game-Player Relationship**
   - A game contains a list of players (Player objects)
   - Players in game have additional game-specific properties (score, hasGuessedCorrectly)

3. **WebSocket Messages**
   - Messages contain payloads with model objects
   - Maintain consistent structure between model classes and message format

### Method Naming Conventions

1. **Verify Method Names**
   - Some methods have unconventional names (isHasGuessedCorrectly vs hasGuessedCorrectly)
   - Always check actual method signatures before use

2. **Use Consistent Getters/Setters**
   - Boolean getters use is* prefix (isReady, isLocked)
   - Non-boolean getters use get* prefix (getUsername, getLobbyId)

3. **Common Patterns to Watch For**
   - Use addPoints() not addScore() for incrementing player scores
   - Use isHasGuessedCorrectly() not hasGuessedCorrectly() for checking guess status

### Performance Considerations

1. **Avoid Duplicate Objects**
   - Reuse model instances when possible
   - Be careful about creating new instances in UI adapters

2. **Efficient Collections**
   - Use appropriate collection types (ArrayList for sequential access)
   - Pre-size collections when count is known

3. **Minimize Deep Copying**
   - Avoid unnecessary copying of model objects
   - Use immutable objects where appropriate
