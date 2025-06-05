# DrawIt Server Documentation - Data Models

## Overview

This document details the MongoDB data models used in the DrawIt application server. These models define the structure and relationships for users, lobbies, and games.

## Models

### User Model (`models/User.js`)

#### Purpose
The User model represents player accounts in the DrawIt application, storing authentication details, profile information, and player status.

#### Schema Definition
```javascript
const userSchema = new mongoose.Schema({
  userId: { type: String, required: true, unique: true },
  username: { type: String, required: true },
  password: { type: String, required: true },
  avatarUrl: { type: String, default: '' },
  ready: { type: Boolean, default: false },
  createdAt: { type: Date, default: Date.now }
});
```

#### Fields Explanation

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `userId` | String | Unique identifier for the user | Required, Unique |
| `username` | String | Display name shown in lobbies and games | Required |
| `password` | String | Hashed password for authentication | Required |
| `avatarUrl` | String | URL to user's profile image | Optional, Default: empty string |
| `ready` | Boolean | Indicates if player is ready in lobby | Optional, Default: false |
| `createdAt` | Date | Timestamp of account creation | Optional, Default: current time |

#### Methods

##### `userSchema.pre('save', async function(next)`
**Purpose**: Middleware that runs before saving a user document.

**Implementation Details**:
- Hashes the password if it's been modified
- Uses bcrypt with a salt factor of 10 for secure hashing
- Calls next() to continue with the save operation

##### `findByCredentials(username, password)`
**Purpose**: Static method to authenticate a user by username and password.

**Parameters**:
- `username`: User's username
- `password`: Plain text password

**Returns**: User document if authenticated, null otherwise

**Implementation Details**:
- Finds user by username
- Compares provided password with stored hash using bcrypt
- Returns user document if match, null otherwise

#### Usage in Server.js

The User model is used for:
1. User registration (`POST /users`)
2. User authentication (`POST /users/login`)
3. Retrieving user profiles (`GET /users/:userId`)
4. Updating user information (`PUT /users/:userId`)
5. Populating user data in lobby and game queries

#### Potential Issues

1. **Password Storage Security**
   - Problem: Ensure passwords are always properly hashed
   - Solution: Verify the pre-save hook always runs when password changes

2. **Unique Index Enforcement**
   - Problem: MongoDB unique indexes might not be created automatically
   - Solution: Ensure indexes are explicitly created in database setup

3. **User Deletion**
   - Problem: Deleting users can break references in lobbies and games
   - Solution: Implement cascade deletion or soft delete strategy

### Lobby Model (`models/Lobby.js`)

#### Purpose
The Lobby model represents game lobbies where players gather before starting a game, storing lobby settings, player lists, and game configuration.

#### Schema Definition
```javascript
const lobbySchema = new mongoose.Schema({
  lobbyId: { type: String, required: true, unique: true },
  name: { type: String, required: true },
  hostId: { type: String, required: true },
  maxPlayers: { type: Number, default: 8 },
  isPrivate: { type: Boolean, default: false },
  password: { type: String },
  players: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  isLocked: { type: Boolean, default: false },
  numRounds: { type: Number, default: 3 },
  roundDurationSeconds: { type: Number, default: 60 },
  createdAt: { type: Date, default: Date.now }
});
```

#### Fields Explanation

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `lobbyId` | String | Unique identifier for the lobby | Required, Unique |
| `name` | String | Display name of the lobby | Required |
| `hostId` | String | User ID of the lobby host | Required |
| `maxPlayers` | Number | Maximum allowed players | Optional, Default: 8 |
| `isPrivate` | Boolean | Whether lobby requires password | Optional, Default: false |
| `password` | String | Password for private lobbies | Optional |
| `players` | Array of ObjectIds | References to User documents | Ref: 'User' |
| `isLocked` | Boolean | Whether lobby is locked (no new joins) | Optional, Default: false |
| `numRounds` | Number | Number of rounds for the game | Optional, Default: 3 |
| `roundDurationSeconds` | Number | Time per round in seconds | Optional, Default: 60 |
| `createdAt` | Date | Timestamp of lobby creation | Optional, Default: current time |

#### Methods

No custom methods are directly defined on the Lobby schema, but several operations are performed in server.js:

##### Lobby Creation
```javascript
// Create new lobby
const newLobby = new Lobby({
  lobbyId,
  name: name.trim(),
  hostId,
  maxPlayers: maxPlayers || 5,
  numRounds: numRounds || 3,
  roundDurationSeconds: roundDurationSeconds || 60,
  isPrivate: isPrivate || false,
  password: (isPrivate && password) ? password : '',
  isLocked: false,
  players: [hostUser._id] // Important: Host must be added to players list
});
```

##### Lobby Population
```javascript
// Find lobby with populated players
const lobby = await Lobby.findOne({ lobbyId })
  .populate('players', 'userId username avatarUrl ready')
  .lean();
```

#### Usage in Server.js

The Lobby model is used for:
1. Creating lobbies (`POST /lobbies`)
2. Fetching lobby lists (`GET /lobbies`)
3. Getting lobby details (`GET /lobbies/:lobbyId`)
4. Joining lobbies (`POST /lobbies/:lobbyId/join`)
5. Updating lobby settings (`PUT /lobbies/:lobbyId`)
6. Locking/unlocking lobbies (`PUT /lobbies/:lobbyId/lock`)
7. WebSocket notifications via `notifyLobbyStateChange`

#### Potential Issues

1. **Host-Players Inconsistency**
   - Problem: Host might not be added to players array
   - Solution: Always explicitly add host to players array during creation

2. **Reference Type Mismatch**
   - Problem: hostId is stored as string but players are ObjectIds
   - Solution: Be consistent in how references are stored and document clearly

3. **Password Security**
   - Problem: Storing plain text passwords for private lobbies
   - Solution: Consider hashing lobby passwords or implementing token-based access

4. **Stale Lobbies**
   - Problem: Lobbies may remain in database after everyone leaves
   - Solution: Implement automatic cleanup for inactive lobbies

### Game Model (`models/Game.js`)

#### Purpose
The Game model represents active games, tracking game state, player scores, rounds, and word lists.

#### Schema Definition
```javascript
const gameSchema = new mongoose.Schema({
  gameId: { type: String, required: true, unique: true },
  lobbyId: { type: String, required: true },
  status: { 
    type: String, 
    enum: ['waiting', 'starting', 'in_progress', 'round_end', 'game_end'],
    default: 'waiting'
  },
  currentRound: { type: Number, default: 1 },
  totalRounds: { type: Number, required: true },
  roundDurationSeconds: { type: Number, required: true },
  currentDrawer: { type: String },
  currentWord: { type: String },
  usedWords: [{ type: String }],
  players: [{
    userId: { type: String, required: true },
    username: { type: String, required: true },
    score: { type: Number, default: 0 },
    hasGuessedCorrectly: { type: Boolean, default: false },
    lastGuessTimestamp: { type: Date }
  }],
  startTime: { type: Date },
  endTime: { type: Date },
  roundEndTime: { type: Date },
  createdAt: { type: Date, default: Date.now }
});
```

#### Fields Explanation

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `gameId` | String | Unique identifier for the game | Required, Unique |
| `lobbyId` | String | ID of the associated lobby | Required |
| `status` | String | Current game state | Enum, Default: 'waiting' |
| `currentRound` | Number | Current round number | Default: 1 |
| `totalRounds` | Number | Total number of rounds | Required |
| `roundDurationSeconds` | Number | Time per round in seconds | Required |
| `currentDrawer` | String | User ID of current drawer | Optional |
| `currentWord` | String | Word being drawn | Optional |
| `usedWords` | Array of Strings | Words already used in game | Optional |
| `players` | Array of Objects | Player data including scores | Required |
| `startTime` | Date | When game started | Optional |
| `endTime` | Date | When game ended | Optional |
| `roundEndTime` | Date | When current round ends | Optional |
| `createdAt` | Date | Timestamp of game creation | Default: current time |

#### Player Subdocument Fields

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `userId` | String | Player's user ID | Required |
| `username` | String | Player's username | Required |
| `score` | Number | Player's current score | Default: 0 |
| `hasGuessedCorrectly` | Boolean | Whether player guessed correctly this round | Default: false |
| `lastGuessTimestamp` | Date | Time of player's last guess | Optional |

#### Methods

##### `isGuessRateLimited(userId)`
**Purpose**: Checks if a player's guessing is rate-limited.

**Parameters**:
- `userId`: ID of the player making a guess

**Returns**: Boolean indicating if rate limit applies

**Implementation Details**:
- Finds player in players array
- Checks last guess timestamp against current time
- Returns true if less than 1 second since last guess
- Updates timestamp if no rate limit applies

#### Usage in Server.js

The Game model is used for:
1. Creating games (`POST /games`)
2. Fetching game details (`GET /games/:gameId`)
3. Processing guesses (`POST /games/:gameId/guess`)
4. Starting rounds (`PUT /games/:gameId/start-round`)
5. Ending rounds (`PUT /games/:gameId/end-round`)
6. WebSocket notifications for game state changes

#### Potential Issues

1. **Method Naming Inconsistency**
   - Problem: Some methods have non-standard naming patterns
   - Solution: Verify method names before use (e.g., use `isGuessRateLimited` not `getLastGuessTimestamps`)

2. **Race Conditions**
   - Problem: Concurrent updates to game state can cause inconsistencies
   - Solution: Use transactions or atomic operations for updates

3. **Word List Management**
   - Problem: No validation for word uniqueness or appropriateness
   - Solution: Implement proper word validation and ensure uniqueness in usedWords

4. **Time-Based Logic**
   - Problem: Server and client time may not be synchronized
   - Solution: Use server time consistently for all time-based operations

## Data Relationships

### User-Lobby Relationship
- One-to-many: A user can be in multiple lobbies
- Many-to-many: A lobby has multiple users
- Special relationship: One user is the host of a lobby

### Lobby-Game Relationship
- One-to-one: A lobby has one active game
- Game references lobby via lobbyId

### User-Game Relationship
- Many-to-many: A game has multiple players
- Player data is embedded in the game document rather than referenced

## Database Indexing Strategy

### User Collection
- `userId`: Unique index for quick lookups
- `username`: Index for authentication queries

### Lobby Collection
- `lobbyId`: Unique index for direct lookups
- `hostId`: Index for finding lobbies by host
- `isPrivate`, `isLocked`: Compound index for filtering available lobbies

### Game Collection
- `gameId`: Unique index for direct lookups
- `lobbyId`: Index for finding games by lobby
- `status`: Index for filtering active games

## Best Practices

1. **Always populate user references**
   ```javascript
   await Lobby.findOne({ lobbyId }).populate('players', 'userId username avatarUrl ready')
   ```

2. **Use lean() for read-only queries**
   ```javascript
   await Lobby.find({ isPrivate: false, isLocked: false }).lean()
   ```

3. **Validate object existence before updates**
   ```javascript
   const lobby = await Lobby.findOne({ lobbyId });
   if (!lobby) return res.status(404).json({ success: false, message: 'Lobby not found' });
   ```

4. **Use atomic operations for updates**
   ```javascript
   await Lobby.updateOne(
     { lobbyId },
     { $addToSet: { players: user._id } }
   );
   ```

5. **Handle database errors gracefully**
   ```javascript
   try {
     await lobby.save();
   } catch (error) {
     console.error('Database error:', error);
     return res.status(500).json({ success: false, message: 'Server error' });
   }
   ```
