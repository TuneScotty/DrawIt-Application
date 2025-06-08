const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const jwt = require('jsonwebtoken');
const WebSocket = require('ws');
const http = require('http');
const mongoose = require('mongoose');
const bcrypt = require('bcrypt');

// Import models from separate files
const User = require('./models/User');
const Lobby = require('./models/Lobby');
const Game = require('./models/Game');
const Drawing = require('./models/Drawing');

const app = express();
// Create HTTP server to attach both Express and WebSocket
const server = http.createServer(app);

const PORT = process.env.PORT;
const JWT_SECRET = process.env.JWT_SECRET;
const MONGODB_URI = process.env.MONGODB_URI;

console.log('Connecting to MongoDB at:', MONGODB_URI);

// Connect to MongoDB with better error handling
mongoose.connect(MONGODB_URI)
  .then(() => console.log('Connected to MongoDB'))
  .catch(err => {
    console.error('MongoDB connection error:', err);
    process.exit(1); // Exit if can't connect to database
  });

// Middleware
app.use(cors({
  origin: '*', // Configure this properly for production
  credentials: true
}));
app.use(bodyParser.json({ limit: '10mb' })); // Increase limit for drawing data
app.use(bodyParser.urlencoded({ extended: true }));

// Helper functions
const generateId = () => Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);

// Middleware to verify JWT token
const authenticateToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ 
      success: false,
      message: 'Authentication token required',
      data: null
    });
  }
  
  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ 
        success: false,
        message: 'Invalid or expired token',
        data: null
      });
    }
    
    req.user = user;
    next();
  });
};

// =================== WEBSOCKET SERVER ===================

// Create WebSocket server
const wss = new WebSocket.Server({ 
  server: server,
  path: '/ws'
});

// Connected clients
const clients = new Map();
// Map of lobbyId -> Map of userId -> WebSocket
const lobbyConnections = new Map();
// Map of gameId -> Map of userId -> WebSocket
const gameConnections = new Map();
// Track last activity of each connection
const lastActivity = new Map();
// Heartbeat interval (30 seconds)
const HEARTBEAT_INTERVAL = 30000;

// WebSocket connection handler
wss.on('connection', (ws, req) => {
  console.log('WebSocket client connected');
  console.log('WebSocket headers:', req.headers);
  
  // Setup WebSocket ping/pong for connection health monitoring
  ws.isAlive = true;
  ws.on('pong', () => {
    ws.isAlive = true;
  });
  
  // Send connection confirmation to all clients whether authenticated or not
  ws.send(JSON.stringify({
    type: 'connection_established',
    message: 'Connected to DrawIt server'
  }));
  
  // Try to get user ID from token
  const userId = getUserIdFromRequest(req);
  
  if (userId) {
    // Store authenticated client connection with userId
    clients.set(userId, ws);
    lastActivity.set(userId, Date.now());
    
    // Send initial lobbies list to the client
    sendInitialLobbiesList(ws);
    
    // Handle messages from client
    ws.on('message', async (message) => {
      try {
        const data = JSON.parse(message);
        
        switch (data.type) {
          case 'join_lobby':
            if (data.lobbyId) {
              handleJoinLobby(ws, userId, data.lobbyId);
            }
            break;
          case 'leave_lobby':
            // Handle leave lobby logic
            break;
          case 'set_ready':
            // Handle set ready logic
            break;
          case 'request_lobbies_update':
            // Client is requesting an updated list of lobbies
            sendInitialLobbiesList(ws);
            break;
          case 'game_state_request':
            // Client is explicitly requesting game state data
            if (data.game_id) {
              console.log(`Received game_state_request for game ${data.game_id} from user ${userId}`);
              
              // Check if the game exists
              const game = await Game.findOne({ gameId: data.game_id })
                .populate('players', 'userId username avatarUrl ready')
                .exec();
              
              if (game) {
                // Send game state directly to the requesting client
                console.log(`Sending requested game state for ${data.game_id} to user ${userId}`);
                
                // Create game state message
                const gameStateMessage = {
                  type: 'game_state',
                  gamePayload: {
                    event: 'update',
                    game: game
                  }
                };
                
                // Send to the requesting client
                safelySendMessage(ws, JSON.stringify(gameStateMessage), 'game state response');
                
                // Also update the game connections map if needed
                if (!gameConnections.has(data.game_id)) {
                  gameConnections.set(data.game_id, new Map());
                }
                
                // Add this user to the game connections if not already there
                const connections = gameConnections.get(data.game_id);
                if (!connections.has(userId)) {
                  connections.set(userId, ws);
                  console.log(`Added user ${userId} to game connections for ${data.game_id}`);
                }
              } else {
                console.error(`Game ${data.game_id} not found for game_state_request`);
                ws.send(JSON.stringify({
                  type: 'error',
                  message: `Game ${data.game_id} not found`
                }));
              }
            } else {
              console.error('Invalid game_state_request - missing game_id');
              ws.send(JSON.stringify({
                type: 'error',
                message: 'Invalid game_state_request - missing game_id'
              }));
            }
            break;
          case 'start_game':
            // Handle game start event
            if (data.lobbyId && data.gameId) {
              console.log(`Received start_game message for lobby ${data.lobbyId} with gameId ${data.gameId}`);
              
              // Find the game and broadcast the start_game event to all players in the lobby
              handleStartGame(userId, data.lobbyId, data.gameId);
            } else {
              console.error('Invalid start_game message - missing lobbyId or gameId');
              ws.send(JSON.stringify({
                type: 'error',
                message: 'Invalid start_game message - missing lobbyId or gameId'
              }));
            }
            break;
          case 'chat_message':
            // Handle chat message
            if (data.game_id && data.message) {
              console.log(`Received chat_message for game ${data.game_id} from user ${userId}: ${data.message}`);
              
              try {
                // Find the user who sent the message
                const sender = await User.findOne({ userId: userId }).lean();
                
                if (!sender) {
                  console.error(`User ${userId} not found for chat message`);
                  return;
                }
                
                // Create the chat message object
                const chatMessage = {
                  type: 'chat_message',
                  game_id: data.game_id,
                  message: data.message,
                  timestamp: data.timestamp || Date.now(),
                  sender: {
                    userId: sender.userId,
                    username: sender.username,
                    avatarUrl: sender.avatarUrl
                  }
                };
                
                // Broadcast to all clients in the game
                broadcastToGame(data.game_id, JSON.stringify(chatMessage));
                
                console.log(`Chat message broadcast to all players in game ${data.game_id}`);
              } catch (error) {
                console.error(`Error handling chat message: ${error.message}`);
                ws.send(JSON.stringify({
                  type: 'error',
                  message: 'Error processing chat message'
                }));
              }
            } else {
              console.error('Invalid chat_message - missing game_id or message');
              ws.send(JSON.stringify({
                type: 'error',
                message: 'Invalid chat_message - missing game_id or message'
              }));
            }
            break;
          default:
            console.log('Unknown message type:', data.type);
        }
      } catch (error) {
        console.error('Error handling WebSocket message:', error);
      }
    });
    
    // Handle client disconnect
    ws.on('close', () => {
      console.log(`WebSocket client disconnected: ${userId}`);
      clients.delete(userId);
      lastActivity.delete(userId);
      
      // Remove from all lobby connections
      for (const [lobbyId, connections] of lobbyConnections.entries()) {
        if (connections.has(userId)) {
          connections.delete(userId);
          // Notify others in the lobby that this user has left
          // Notify clients about the updated lobby state
          notifyLobbyStateChange(lobbyId);
        }
      }
    });
  } else {
    // Unauthenticated connection - still allow connection but with limited functionality
    console.log('WebSocket client connected without authentication');
    
    // Simple message handler for unauthenticated clients
    ws.on('message', (message) => {
      try {
        const data = JSON.parse(message.toString());
        console.log('Received message from unauthenticated client:', data);
        
        // Inform client authentication is needed for this action
        ws.send(JSON.stringify({
          type: 'auth_required',
          message: 'Authentication required for this action'
        }));
      } catch (error) {
        console.error('Error handling unauthenticated WebSocket message:', error);
      }
    });
    
    // Handle disconnection for unauthenticated users
    ws.on('close', () => {
      console.log('Unauthenticated WebSocket client disconnected');
    });
  }
});

// Send initial list of lobbies to a client
async function sendInitialLobbiesList(ws) {
  try {
    // Get all available lobbies
    const lobbies = await Lobby.find({ isLocked: false, isPrivate: false })
      .populate('players', 'userId username')
      .lean();
      
    // For each lobby, get the host details
    const lobbiesWithDetails = await Promise.all(lobbies.map(async (lobby) => {
      const hostUser = await User.findOne({ userId: lobby.hostId }, 'userId username avatarUrl').lean();
      return {
        ...lobby,
        hostUser: hostUser || { userId: lobby.hostId, username: 'Unknown Host' }
      };
    }));
    
    // Send the lobbies list to the client
    ws.send(JSON.stringify({
      type: 'lobbies_update',
      payload: {
        lobbies: lobbiesWithDetails,
        event: 'initial'
      }
    }));
  } catch (error) {
    console.error(`Error sending initial lobbies list: ${error.message}`);
  }
}

// Get user ID from authorization header
function getUserIdFromRequest(req) {
  console.log('WebSocket headers:', req.headers);
  try {
    const authHeader = req.headers.authorization;
    
    if (authHeader && authHeader.startsWith('Bearer ') && authHeader !== 'Bearer null') {
      const token = authHeader.substring(7);
      // Validate token format before verification to avoid errors
      if (token && token.split('.').length === 3) {
        try {
          const decoded = jwt.verify(token, JWT_SECRET);
          return decoded.userId;
        } catch (tokenError) {
          console.log('Invalid token:', tokenError.message);
          return null;
        }
      }
    }
    return null;
  } catch (error) {
    console.error('Error extracting userId from request:', error);
    return null;
  }
}

// Handle client joining a lobby
async function handleJoinLobby(ws, userId, lobbyId) {
  try {
    console.log(`WebSocket client joining lobby - User ${userId} attempting to join lobby ${lobbyId}`);
    
    // Verify the lobby exists
    const lobby = await Lobby.findOne({ lobbyId }).populate('players', 'userId username avatarUrl ready');
    if (!lobby) {
      console.log(`Lobby ${lobbyId} not found for WebSocket join request`);
      ws.send(JSON.stringify({
        type: 'error',
        message: 'Lobby not found'
      }));
      return;
    }
    
    // Check if the user is in the players list
    const userInLobby = lobby.players.some(player => player.userId === userId);
    console.log(`User ${userId} is ${userInLobby ? 'already in' : 'not in'} lobby ${lobbyId} player list`);
    
    // Add to lobby connections map regardless of player status - they might be viewing before joining
    if (!lobbyConnections.has(lobbyId)) {
      console.log(`Creating new connection map for lobby ${lobbyId}`);
      lobbyConnections.set(lobbyId, new Map());
    }
    
    const connections = lobbyConnections.get(lobbyId);
    connections.set(userId, ws);
    console.log(`Added WebSocket connection for user ${userId} to lobby ${lobbyId} connections map`);
    console.log(`Total connections for lobby ${lobbyId}: ${connections.size}`);
    
    // Always get fresh lobby data from DB to ensure latest state
    const freshLobby = await Lobby.findOne({ lobbyId })
      .populate('players', 'userId username avatarUrl ready')
      .lean();
      
    // Get host user details
    const hostUser = await User.findOne({ userId: freshLobby.hostId }, 'userId username avatarUrl').lean();
    
    // Prepare lobby data with consistent structure
    const lobbyData = {
      lobbyId: freshLobby.lobbyId,
      name: freshLobby.name,
      hostId: freshLobby.hostId,
      maxPlayers: freshLobby.maxPlayers,
      isPrivate: freshLobby.isPrivate,
      isLocked: freshLobby.isLocked,
      numRounds: freshLobby.numRounds,
      roundDurationSeconds: freshLobby.roundDurationSeconds,
      playerCount: freshLobby.players ? freshLobby.players.length : 0
    };
    
    console.log(`WebSocket join - User ${userId} connected to lobby ${lobbyId}`);
    console.log(`Host user: ${JSON.stringify(hostUser)}`);
    console.log(`Players count: ${freshLobby.players ? freshLobby.players.length : 0}`);
    console.log(`Player list: ${JSON.stringify(freshLobby.players.map(p => p.username))}`);
    
    // Notify the joining client about successful connection
    const joinMessage = JSON.stringify({
      type: 'lobby_joined',
      lobbyId,
      payload: {
        lobby: lobbyData,
        hostUser: hostUser,
        players: freshLobby.players || [],
        event: 'joined',
        user_id: userId
      }
    });
    
    safelySendMessage(ws, joinMessage, `join confirmation to ${userId}`);
    lastActivity.set(userId, Date.now());
    
    // For all clients in the lobby, send fresh state
    setTimeout(() => {
      notifyLobbyStateChange(lobbyId);
    }, 100); // Small delay to ensure database is consistent
  } catch (error) {
    console.error(`Error handling join lobby: ${error.message}`);
    ws.send(JSON.stringify({
      type: 'error',
      message: 'Failed to join lobby'
    }));
  }
}

// Safely send a message to a WebSocket client with error handling
function safelySendMessage(ws, message, description = 'message') {
  if (ws && ws.readyState === WebSocket.OPEN) {
    try {
      ws.send(message);
      return true;
    } catch (error) {
      console.error(`Failed to send ${description}: ${error.message}`);
      return false;
    }
  }
  return false;
}

// Broadcast message to all clients in a lobby
function broadcastToLobby(lobbyId, message, excludeUserId = null) {
  const connections = lobbyConnections.get(lobbyId);
  if (!connections) {
    console.log(`No connections found for lobby ${lobbyId}`);
    return;
  }
  
  let deliveredCount = 0;
  let failedCount = 0;
  
  for (const [userId, ws] of connections.entries()) {
    if (excludeUserId !== userId) {
      const success = safelySendMessage(ws, message, `lobby message to ${userId} in ${lobbyId}`);
      if (success) {
        deliveredCount++;
        lastActivity.set(userId, Date.now());
      } else {
        failedCount++;
      }
    }
  }
  
  console.log(`Broadcast to lobby ${lobbyId}: delivered to ${deliveredCount} clients, failed for ${failedCount} clients`);
}

// Broadcast message to all clients in a game
function broadcastToGame(gameId, message, excludeUserId = null) {
  const connections = gameConnections.get(gameId);
  if (!connections) {
    console.log(`No connections found for game ${gameId}`);
    return;
  }
  
  let deliveredCount = 0;
  let failedCount = 0;
  
  for (const [userId, ws] of connections.entries()) {
    if (excludeUserId !== userId) {
      const success = safelySendMessage(ws, message, `game message to ${userId} in game ${gameId}`);
      if (success) {
        deliveredCount++;
        lastActivity.set(userId, Date.now());
      } else {
        failedCount++;
      }
    }
  }
  
  console.log(`Broadcast to game ${gameId}: delivered to ${deliveredCount} clients, failed for ${failedCount} clients`);
}

// Check if a WebSocket is still valid
function isWebSocketValid(ws) {
  return ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING);
}

// Clean up stale connections
function cleanupStaleConnections() {
  const now = Date.now();
  const staleTimeout = 5 * 60 * 1000; // 5 minutes
  
  // Clean up client connections
  for (const [userId, lastTime] of lastActivity.entries()) {
    if (now - lastTime > staleTimeout) {
      console.log(`Removing stale connection for user ${userId}`);
      const ws = clients.get(userId);
      if (ws) {
        try {
          ws.terminate();
        } catch (err) {
          console.error(`Error terminating stale connection: ${err.message}`);
        }
      }
      clients.delete(userId);
      lastActivity.delete(userId);
      
      // Also remove from lobby connections
      for (const [lobbyId, connections] of lobbyConnections.entries()) {
        if (connections.has(userId)) {
          connections.delete(userId);
          console.log(`Removed stale user ${userId} from lobby ${lobbyId}`);
        }
      }
      
      // Also remove from game connections
      for (const [gameId, connections] of gameConnections.entries()) {
        if (connections.has(userId)) {
          connections.delete(userId);
          console.log(`Removed stale user ${userId} from game ${gameId}`);
        }
      }
    }
  }
  
  // Clean up empty lobby connections
  for (const [lobbyId, connections] of lobbyConnections.entries()) {
    if (connections.size === 0) {
      lobbyConnections.delete(lobbyId);
      console.log(`Removed empty lobby connection map for lobby ${lobbyId}`);
    }
  }
  
  // Clean up empty game connections
  for (const [gameId, connections] of gameConnections.entries()) {
    if (connections.size === 0) {
      gameConnections.delete(gameId);
      console.log(`Removed empty game connection map for game ${gameId}`);
    }
  }
}

// A helper function to get all active user IDs in a lobby from the connections map
function getActiveUserIdsInLobby(lobbyId) {
  if (!lobbyConnections.has(lobbyId)) {
    return [];
  }
  
  const connections = lobbyConnections.get(lobbyId);
  return Array.from(connections.keys());
}

// Force a refresh of the lobby player list from the database
async function refreshLobbyPlayerList(lobbyId) {
  try {
    console.log(`Force refreshing lobby player list for ${lobbyId}`);
    // Get the fresh lobby data
    const freshLobby = await Lobby.findOne({ lobbyId })
      .populate('players', 'userId username avatarUrl ready')
      .lean();
      
    if (!freshLobby) {
      console.log(`Lobby ${lobbyId} not found during refresh`);
      return null;
    }
    
    // Get list of active user connections for this lobby
    const activeUserIds = getActiveUserIdsInLobby(lobbyId);
    
    // Log any potential discrepancies between active connections and players
    if (activeUserIds.length > 0) {
      console.log(`Active connections in lobby ${lobbyId}: ${activeUserIds.join(', ')}`);
      
      // Check for users with active connections but not in player list
      for (const activeUserId of activeUserIds) {
        const isInPlayerList = freshLobby.players.some(player => player.userId === activeUserId);
        if (!isInPlayerList) {
          console.log(`⚠️ User ${activeUserId} has active connection but is NOT in player list!`);
        }
      }
      
      // Check for players without active connections
      for (const player of freshLobby.players) {
        if (!activeUserIds.includes(player.userId)) {
          console.log(`⚠️ Player ${player.userId} (${player.username}) is in player list but has NO active connection!`);
        }
      }
    }
    
    console.log(`Fresh lobby data for ${lobbyId}:`);
    console.log(`- Name: ${freshLobby.name}`);
    console.log(`- Players: ${freshLobby.players.length} (${freshLobby.players.map(p => p.username).join(', ')})`);
    
    return freshLobby;
  } catch (error) {
    console.error(`Error refreshing lobby player list: ${error.message}`);
    return null;
  }
}

// Notify all clients in a lobby about state change
async function notifyLobbyStateChange(lobbyId) {
  try {
    console.log(`\n====== NOTIFYING LOBBY STATE CHANGE: ${lobbyId} ======`);
    // Force a refresh to get the most up-to-date player list
    const lobby = await refreshLobbyPlayerList(lobbyId);
    
    if (!lobby) {
      console.log(`Cannot notify state change - lobby ${lobbyId} not found`);
      return;
    }
    
    if (!lobbyConnections.has(lobbyId)) {
      console.log(`Cannot notify state change - no connections for lobby ${lobbyId}`);
      return;
    }
    
    // Get host user details
    const hostUser = await User.findOne({ userId: lobby.hostId }, 'userId username avatarUrl').lean();
    
    const connections = lobbyConnections.get(lobbyId);
    
    // Prepare lobby data with consistent structure
    const lobbyData = {
      lobbyId: lobby.lobbyId,
      name: lobby.name,
      hostId: lobby.hostId,
      maxPlayers: lobby.maxPlayers,
      isPrivate: lobby.isPrivate,
      isLocked: lobby.isLocked,
      numRounds: lobby.numRounds,
      roundDurationSeconds: lobby.roundDurationSeconds,
      playerCount: lobby.players ? lobby.players.length : 0,
      // Do NOT include hostUser nested in the lobby object
    };
    
    // Log what we're sending for debugging
    console.log(`Sending lobby state update for lobby: ${lobbyId}`);
    console.log(`Host user: ${JSON.stringify(hostUser)}`);
    console.log(`Players count: ${lobby.players ? lobby.players.length : 0}`);
    console.log(`Players: ${JSON.stringify(lobby.players.map(p => ({ userId: p.userId, username: p.username })))}`);
    console.log(`Connected clients in lobby: ${connections.size}`);
    
    // Log connected users for debugging
    console.log('Connected users in this lobby:');
    for (const [connUserId, _] of connections.entries()) {
      const inPlayers = lobby.players.some(p => p.userId === connUserId);
      console.log(`- User ${connUserId}: ${inPlayers ? 'in player list' : 'NOT in player list'}`);
    }
    
    // Check for database inconsistencies
    console.log(`Comparing database state vs connections:`);
    console.log(`- Database shows ${lobby.players.length} players`);
    console.log(`- Connection map shows ${connections.size} connections`);
    
    // Check for missing connections
    for (const player of lobby.players) {
      if (!connections.has(player.userId)) {
        console.log(`WARNING: Player ${player.userId} is in the lobby but has no WebSocket connection`);
      }
    }
    
    // Broadcast to all connected clients in the lobby
    let deliveredCount = 0;
    let failedCount = 0;
    
    for (const [userId, ws] of connections.entries()) {
      const message = JSON.stringify({
          type: 'lobby_state',
          payload: {
            lobby: lobbyData,
            hostUser: hostUser, // Include hostUser at the payload level, not nested in lobby
            players: lobby.players || [],
            event: 'updated',
            user_id: userId
          }
        });
        
      const success = safelySendMessage(ws, message, `lobby state update to ${userId} in ${lobbyId}`);
      if (success) {
        deliveredCount++;
        lastActivity.set(userId, Date.now());
      } else {
        failedCount++;
      }
    }
    
    console.log(`Lobby state update for ${lobbyId}: delivered to ${deliveredCount} clients, failed for ${failedCount} clients`);
    
    // Also broadcast to all clients that are not in the lobby
    // to update their lobby list
    broadcastLobbyListUpdate();
  } catch (error) {
    console.error(`Error notifying lobby state change: ${error.message}`);
  }
}

// Function to broadcast lobby list updates to all connected clients
async function broadcastLobbyListUpdate() {
  try {
    // Get all available lobbies
    const lobbies = await Lobby.find({ isLocked: false, isPrivate: false })
      .populate('players', 'userId username')
      .lean();
      
    // For each lobby, get the host details
    const lobbiesWithDetails = await Promise.all(lobbies.map(async (lobby) => {
      const hostUser = await User.findOne({ userId: lobby.hostId }, 'userId username avatarUrl').lean();
      return {
        ...lobby,
        hostUser: hostUser || { userId: lobby.hostId, username: 'Unknown Host' }
      };
    }));
    
    // Create the message to send
    const message = JSON.stringify({
      type: 'lobbies_update',
      payload: {
        lobbies: lobbiesWithDetails,
        event: 'updated'
      }
    });
    
    // Send to all connected clients
    for (const [userId, ws] of clients.entries()) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
      }
    } 
  } catch (error) {
    console.error(`Error broadcasting lobby list update: ${error.message}`);
  }
}

// Handle game start event
async function handleStartGame(userId, lobbyId, gameId) {
  try {
    console.log(`\n====== HANDLING GAME START EVENT ======`);
    console.log(`Initiator: User ${userId}`);
    console.log(`Lobby ID: ${lobbyId}`);
    console.log(`Game ID: ${gameId}`);
    
    // Verify lobby exists
    const lobby = await Lobby.findOne({ lobbyId }).populate('players', 'userId username avatarUrl');
    if (!lobby) {
      console.log(`Cannot start game - lobby ${lobbyId} not found`);
      return;
    }
    
    // Verify user is the host
    if (userId !== lobby.hostId) {
      console.log(`Cannot start game - user ${userId} is not the host of lobby ${lobbyId}`);
      return;
    }
    
    // Check for existing game or create a new transient one
    let game = await Game.findOne({ gameId }).populate('players', 'userId username avatarUrl');
    
    // If game doesn't exist, create a transient one
    if (!game) {
      console.log(`Game ${gameId} not found - creating transient game for lobby ${lobbyId}`);
      
      // Use lobby settings for the game parameters
      const numRounds = lobby.numRounds;
      const roundDuration = lobby.roundDurationSeconds;
      
      // Select a random player to be the first drawer
      const randomPlayerIndex = Math.floor(Math.random() * lobby.players.length);
      const firstDrawer = lobby.players[randomPlayerIndex];
      
      // Select a random word for the first round
      const wordList = ['apple', 'banana', 'car', 'dog', 'elephant', 'flower', 'guitar', 'house', 'island', 'jacket'];
      const randomWord = wordList[Math.floor(Math.random() * wordList.length)];
      
      // Initialize player scores array
      const playerScores = lobby.players.map(player => ({
        userId: player.userId,
        username: player.username,
        score: 0
      }));
      
      // Create a new game using lobby players
      game = new Game({
        gameId,
        lobbyId,
        hostId: userId,
        players: lobby.players,
        maxRounds: numRounds,
        roundDurationSeconds: roundDuration,
        currentRound: 1,
        currentDrawer: {
          _id: firstDrawer._id,
          userId: firstDrawer.userId,
          username: firstDrawer.username,
          avatarUrl: firstDrawer.avatarUrl
        },
        wordToGuess: randomWord,
        timeRemaining: roundDuration,
        playerScores: playerScores,
        status: 'active',
        createdAt: new Date(),
        isTransient: true
      });
      
      console.log(`Selected ${firstDrawer.username} as the first drawer with word: ${randomWord}`);
      
      // Save the game to database temporarily
      await game.save();
      console.log(`Created transient game ${gameId} for lobby ${lobbyId}`);
    }
    
    // Log game details
    console.log(`Found/created valid game for game start event`);
    console.log(`Game has ${game.players?.length || 0} players`);
    console.log(`Lobby has ${lobby.players.length} players`);
    
    // Lock the lobby to indicate it's in-game
    lobby.isLocked = true;
    await lobby.save();
    
    console.log(`Locked lobby ${lobbyId} for game start`);
    
    // Prepare a start_game message with the same structure as game_state for compatibility
    const startGameMessage = JSON.stringify({
      type: 'start_game',
      gamePayload: {
        game: game
      }
    });
    
    // Also prepare a standard game_state message for compatibility
    const gameStateMessage = JSON.stringify({
      type: 'game_state',
      gamePayload: {
        game: game
      }
    });
    
    // Find all connections for this lobby
    if (!lobbyConnections.has(lobbyId)) {
      console.log(`No connections found for lobby ${lobbyId}`);
      console.log(`Creating new connection map for lobby ${lobbyId}`);
      lobbyConnections.set(lobbyId, new Map());
      // Don't return here, continue processing so the game can be set up correctly
    }
    
    // Create a connection map for the game, copying all connections from the lobby
    // This ensures all players can receive game updates
    if (!gameConnections.has(gameId)) {
      console.log(`Creating new connection map for game ${gameId}`);
      const gameConnectionMap = new Map();
      
      // Copy all lobby connections to the game connections map
      const lobbyConnectionMap = lobbyConnections.get(lobbyId);
      if (lobbyConnectionMap) {
        for (const [userId, ws] of lobbyConnectionMap.entries()) {
          gameConnectionMap.set(userId, ws);
          console.log(`Copied connection for user ${userId} from lobby to game`);
        }
      }
      
      gameConnections.set(gameId, gameConnectionMap);
      console.log(`Created game connection map with ${gameConnectionMap.size} connections`);
    }
    
    const connections = lobbyConnections.get(lobbyId);
    console.log(`Found ${connections.size} connections for lobby ${lobbyId}`);
    
    // First broadcast the start_game message to support synchronized client transitions
    console.log(`Broadcasting start_game event to all players in lobby ${lobbyId}`);
    let deliveredCount = 0;
    
    // Get game connections to ensure all players get the message
    const gameConnectionMap = gameConnections.get(gameId);
    
    if (gameConnectionMap && gameConnectionMap.size > 0) {
      console.log(`Using game connection map with ${gameConnectionMap.size} connections`);
      // Use game connections as primary message delivery method
      for (const [connUserId, ws] of gameConnectionMap.entries()) {
        const success = safelySendMessage(ws, startGameMessage, 
          `start_game message to ${connUserId} in game ${gameId}`);
        
        if (success) {
          deliveredCount++;
          console.log(`✓ Delivered start_game event to user ${connUserId} (via game connections)`);
          lastActivity.set(connUserId, Date.now());
        } else {
          console.log(`✗ Failed to deliver start_game event to user ${connUserId} (via game connections)`);
        }
      }
    } else {
      // Fallback to lobby connections if game connections aren't available
      console.log(`No game connections available, using ${connections.size} lobby connections as fallback`);
      for (const [connUserId, ws] of connections.entries()) {
        const success = safelySendMessage(ws, startGameMessage, 
          `start_game message to ${connUserId} in ${lobbyId}`);
        
        if (success) {
          deliveredCount++;
          console.log(`✓ Delivered start_game event to user ${connUserId} (via lobby connections)`);
          lastActivity.set(connUserId, Date.now());
        } else {
          console.log(`✗ Failed to deliver start_game event to user ${connUserId} (via lobby connections)`);
        }
      }
    }
    
    console.log(`Start game event delivered to ${deliveredCount} clients total`);
    
    // Also broadcast the standard game_state message as a fallback with a small delay
    setTimeout(() => {
      console.log(`Broadcasting backup game_state event to all players in game ${gameId}`);
      // First try to use game connections
      broadcastToGame(gameId, gameStateMessage);
      
      // Also send to lobby connections as an additional fallback
      console.log(`Also sending backup game_state event to all players in lobby ${lobbyId} as extra fallback`);
      broadcastToLobby(lobbyId, gameStateMessage);
    }, 1000);
    
    // Update all clients about lobbies (since this one is now locked)
    broadcastLobbyListUpdate();
  } catch (error) {
    console.error(`Error handling game start: ${error.message}`);
  }
}

// Notify all clients in a game about state change
async function notifyGameStateChange(gameId) {
  try {
    // Populate both players and currentDrawer to ensure we have complete information
    const game = await Game.findOne({ gameId })
      .populate('players', 'userId username avatarUrl')
      .populate('currentDrawer', 'userId username avatarUrl');
    
    if (!game) {
      console.log(`Cannot notify game state change - game ${gameId} not found`);
      return;
    }
    
    // Make sure we have the current drawer information
    if (!game.currentDrawer && game.players && game.players.length > 0) {
      console.log(`Game ${gameId} is missing currentDrawer, selecting one from players`);
      // Select a random player as the drawer if not set
      const randomPlayerIndex = Math.floor(Math.random() * game.players.length);
      game.currentDrawer = game.players[randomPlayerIndex]._id;
      await game.save();
    }
    
    // Make sure we have a word to guess
    if (!game.wordToGuess) {
      console.log(`Game ${gameId} is missing wordToGuess, selecting a random word`);
      const wordList = ['apple', 'banana', 'car', 'dog', 'elephant', 'flower', 'guitar', 'house', 'island', 'jacket'];
      game.wordToGuess = wordList[Math.floor(Math.random() * wordList.length)];
      await game.save();
    }
    
    // Make sure we have timeRemaining
    if (!game.timeRemaining) {
      console.log(`Game ${gameId} is missing timeRemaining, setting to roundDurationSeconds`);
      game.timeRemaining = game.roundDurationSeconds || 60;
      await game.save();
    }
    
    // Create a complete game state message with all required fields
    const message = JSON.stringify({
      type: 'game_state',
      gamePayload: {
        game: game,
        currentDrawer: game.currentDrawer,
        wordToGuess: game.wordToGuess,
        timeRemaining: game.timeRemaining
      }
    });
    
    // Send the message to all players in the game using the game connections
    console.log(`Notifying game state change for game ${gameId} with ${game.players?.length || 0} players`);
    broadcastToGame(gameId, message);
    
    // As a fallback, also send to the lobby in case some players haven't been properly moved to the game yet
    const lobby = await Lobby.findOne({ lobbyId: game.lobbyId });
    if (lobby) {
      console.log(`Also broadcasting game state to associated lobby ${lobby.lobbyId}`);
      broadcastToLobby(lobby.lobbyId, message);
    }
  } catch (error) {
    console.error('Error notifying game state change:', error);
  }
}

// Function to check for inconsistencies between DB and connections
async function verifyLobbyIntegrity(lobbyId) {
  try {
    const lobby = await Lobby.findOne({ lobbyId })
      .populate('players', 'userId username avatarUrl ready')
      .lean();
      
    if (!lobby) return;
    if (!lobbyConnections.has(lobbyId)) return;
    
    const connections = lobbyConnections.get(lobbyId);
    
    // Check if any database players are missing WebSocket connections
    for (const player of lobby.players) {
      if (!connections.has(player.userId)) {
        console.log(`INTEGRITY WARNING: Player ${player.userId} (${player.username}) is in lobby ${lobbyId} database but has no WebSocket connection`);
      }
    }
    
    // Check if any connections are for users not in the player list
    for (const [userId, _] of connections.entries()) {
      const playerInDb = lobby.players.some(p => p.userId === userId);
      if (!playerInDb) {
        console.log(`INTEGRITY WARNING: Connection for user ${userId} exists but user is not in lobby ${lobbyId} player list`); 
      }
    }
  } catch (error) {
    console.error(`Error verifying lobby integrity: ${error.message}`);
  }
}

// Set up interval for WebSocket health checks
const interval = setInterval(() => {
  console.log('Running WebSocket health check...');
  let activeConnections = 0;
  let terminatedConnections = 0;
  
  wss.clients.forEach(ws => {
    if (!ws.isAlive) {
      console.log('Terminating inactive WebSocket connection');
      ws.terminate();
      terminatedConnections++;
      return;
    }
    
    ws.isAlive = false;
    ws.ping(() => {});
    activeConnections++;
  });
  
  console.log(`WebSocket health check: ${activeConnections} active, ${terminatedConnections} terminated`);
  
  // Clean up stale connections
  cleanupStaleConnections();
  
  // Check for integrity issues in all active lobbies
  for (const [lobbyId, _] of lobbyConnections.entries()) {
    verifyLobbyIntegrity(lobbyId);
  }
}, HEARTBEAT_INTERVAL);

// Clean up interval on server shutdown
process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully');
  clearInterval(interval);
  clearInterval(syncInterval);
  
  // Close all WebSocket connections
  for (const ws of clients.values()) {
    try {
      ws.close(1000, 'Server shutting down');
    } catch (err) {
      console.error(`Error closing WebSocket connection: ${err.message}`);
    }
  }
  
  // Close the WebSocket server
  wss.close();
  
  // Close the HTTP server
  server.close(() => {
    console.log('HTTP server closed');
    process.exit(0);
  });
});

// Force sync of WebSocket connections with database state
async function syncLobbyConnectionsWithDatabase() {
  console.log('\n===== SYNCING LOBBY CONNECTIONS WITH DATABASE =====');
  try {
    // Get all lobbies from database
    const lobbies = await Lobby.find({}).populate('players', 'userId username avatarUrl ready');
    console.log(`Found ${lobbies.length} lobbies in database`);
    
    // Check each lobby's connections
    for (const lobby of lobbies) {
      // Skip if there are no players
      if (!lobby.players || lobby.players.length === 0) continue;
      
      const lobbyId = lobby.lobbyId;
      console.log(`\nChecking lobby ${lobbyId} - ${lobby.name}`);
      console.log(`Database shows ${lobby.players.length} players: ${lobby.players.map(p => p.username).join(', ')}`);
      
      // Create lobby connections map if it doesn't exist
      if (!lobbyConnections.has(lobbyId)) {
        console.log(`No connection map exists for lobby ${lobbyId}, creating one`);
        lobbyConnections.set(lobbyId, new Map());
      }
      
      const connections = lobbyConnections.get(lobbyId);
      console.log(`Connection map has ${connections.size} entries`);
      
      // Log any missing connections
      for (const player of lobby.players) {
        if (!connections.has(player.userId)) {
          console.log(`Player ${player.userId} (${player.username}) has no WebSocket connection in this lobby`);
        }
      }
    }
    
    console.log('===== SYNC COMPLETE =====\n');
  } catch (error) {
    console.error(`Error syncing lobby connections: ${error.message}`);
  }
}

// Run a sync operation periodically
const syncInterval = setInterval(() => {
  syncLobbyConnectionsWithDatabase();
}, 60000); // Run every minute

// =================== AUTH ENDPOINTS ===================

app.post('/auth/register', async (req, res) => {
  try {
    console.log('Register request received:', { ...req.body, password: '[HIDDEN]' });
    const { username, email, password } = req.body;
    
    // Validation
    if (!username || !email || !password) {
      return res.status(400).json({ 
        success: false,
        message: 'Username, email and password are required',
        data: null
      });
    }

    if (password.length < 6) {
      return res.status(400).json({ 
        success: false,
        message: 'Password must be at least 6 characters long',
        data: null
      });
    }

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return res.status(400).json({ 
        success: false,
        message: 'Please provide a valid email address',
        data: null
      });
    }
    
    // Check if username or email already exists
    const existingUser = await User.findOne({ 
      $or: [{ username: username }, { email: email }] 
    });
    
    if (existingUser) {
      const message = existingUser.username === username ? 
        'Username already exists' : 'Email already in use';
      return res.status(409).json({ 
        success: false,
        message: message,
        data: null
      });
    }
    
    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);
    
    // Create new user
    const userId = generateId();
    const newUser = new User({
      userId,
      username,
      email,
      password: hashedPassword,
      avatarUrl: `https://api.dicebear.com/7.x/avataaars/svg?seed=${username}`,
      totalGamesPlayed: 0,
      gamesWon: 0,
      ready: false
    });
    
    await newUser.save();
    
    // Generate JWT token
    const token = jwt.sign({ 
      userId: newUser.userId, 
      username: newUser.username 
    }, JWT_SECRET, { expiresIn: '7d' });
    
    // Prepare response without password
    const userResponse = {
      userId: newUser.userId,
      username: newUser.username,
      email: newUser.email,
      avatarUrl: newUser.avatarUrl,
      totalGamesPlayed: newUser.totalGamesPlayed,
      gamesWon: newUser.gamesWon,
      ready: newUser.ready
    };
    
    console.log(`User registered successfully: ${username}`);
    
    res.status(201).json({
      success: true,
      message: "Registration successful",
      data: { 
        token, 
        user: userResponse 
      }
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ 
      success: false,
      message: 'Server error during registration',
      data: null
    });
  }
});

app.post('/auth/login', async (req, res) => {
  try {
    console.log('Login request received:', { ...req.body, password: '[HIDDEN]' });
    const { username, password } = req.body;
    
    if (!username || !password) {
      return res.status(400).json({ 
        success: false, 
        message: 'Username and password are required',
        data: null
      });
    }
    
    // Find user by username or email
    const user = await User.findOne({ 
      $or: [{ username: username }, { email: username }] 
    });
    
    if (!user) {
      return res.status(401).json({ 
        success: false, 
        message: 'Invalid credentials',
        data: null
      });
    }

    // Compare password
    const isValidPassword = await bcrypt.compare(password, user.password);
    if (!isValidPassword) {
      return res.status(401).json({ 
        success: false, 
        message: 'Invalid credentials',
        data: null
      });
    }
    
    // Generate JWT token
    const token = jwt.sign({ 
      userId: user.userId, 
      username: user.username 
    }, JWT_SECRET, { expiresIn: '7d' });
    
    // Prepare response without password
    const userResponse = {
      userId: user.userId,
      username: user.username,
      email: user.email,
      avatarUrl: user.avatarUrl,
      totalGamesPlayed: user.totalGamesPlayed,
      gamesWon: user.gamesWon,
      ready: user.ready
    };
    
    console.log(`User logged in successfully: ${username}`);
    
    res.json({
      success: true,
      message: "Login successful",
      data: { 
        token, 
        user: userResponse 
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ 
      success: false,
      message: 'Server error during login',
      data: null
    });
  }
});

app.post('/auth/logout', authenticateToken, (req, res) => {
  res.json({
    success: true,
    message: 'Logged out successfully',
    data: null
  });
});

// Refresh token endpoint
app.post('/auth/refresh-token', async (req, res) => {
  try {
    const { token } = req.body;
    
    if (!token) {
      return res.status(400).json({
        success: false,
        message: 'Token is required',
        data: null
      });
    }
    
    // Verify the current token - this will throw an error if token is invalid
    // but will work even if token is expired, which is what we want for refresh
    let decoded;
    try {
      decoded = jwt.verify(token, JWT_SECRET, { ignoreExpiration: true });
    } catch (err) {
      return res.status(403).json({
        success: false,
        message: 'Invalid token',
        data: null
      });
    }
    
    // Get user details to include in new token
    const user = await User.findOne({ userId: decoded.userId });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found',
        data: null
      });
    }
    
    // Generate new token
    const newToken = jwt.sign({
      userId: user.userId,
      username: user.username
    }, JWT_SECRET, { expiresIn: '7d' });
    
    // Prepare user response
    const userResponse = {
      userId: user.userId,
      username: user.username,
      email: user.email,
      avatarUrl: user.avatarUrl,
      totalGamesPlayed: user.totalGamesPlayed,
      gamesWon: user.gamesWon,
      ready: user.ready
    };
    
    res.json({
      success: true,
      message: 'Token refreshed successfully',
      data: {
        token: newToken,
        user: userResponse
      }
    });
  } catch (error) {
    console.error('Token refresh error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during token refresh',
      data: null
    });
  }
});

// =================== USER ENDPOINTS ===================

// Get user by ID - new endpoint for client requests
app.get('/users/:userId', authenticateToken, async (req, res) => {
  try {
    const user = await User.findOne({ userId: req.params.userId });
    
    if (!user) {
      return res.status(404).json({ 
        success: false,
        message: 'User not found',
        data: null
      });
    }
    
    const userResponse = {
      userId: user.userId,
      username: user.username,
      email: user.email,
      avatarUrl: user.avatarUrl,
      totalGamesPlayed: user.totalGamesPlayed,
      gamesWon: user.gamesWon,
      ready: user.ready
    };
    
    res.json({
      success: true,
      message: 'User retrieved successfully',
      data: userResponse
    });
  } catch (error) {
    console.error('User retrieval error:', error);
    res.status(500).json({ 
      success: false,
      message: 'Server error while retrieving user',
      data: null
    });
  }
});

app.get('/users/profile', authenticateToken, async (req, res) => {
  try {
    const user = await User.findOne({ userId: req.user.userId });
    
    if (!user) {
      return res.status(404).json({ 
        success: false,
        message: 'User not found',
        data: null
      });
    }
    
    const userResponse = {
      userId: user.userId,
      username: user.username,
      email: user.email,
      avatarUrl: user.avatarUrl,
      totalGamesPlayed: user.totalGamesPlayed,
      gamesWon: user.gamesWon,
      ready: user.ready
    };
    
    res.json({
      success: true,
      message: 'Profile retrieved successfully',
      data: userResponse
    });
  } catch (error) {
    console.error('Profile retrieval error:', error);
    res.status(500).json({ 
      success: false,
      message: 'Server error while retrieving profile',
      data: null
    });
  }
});

app.put('/users/profile', authenticateToken, async (req, res) => {
  try {
    const { username, email, avatarUrl } = req.body;
    
    const user = await User.findOne({ userId: req.user.userId });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found',
        data: null
      });
    }
    
    // Check if new username/email already exists (excluding current user)
    if (username && username !== user.username) {
      const existingUser = await User.findOne({ 
        username: username, 
        userId: { $ne: req.user.userId } 
      });
      if (existingUser) {
        return res.status(409).json({
          success: false,
          message: 'Username already exists',
          data: null
        });
      }
      user.username = username;
    }
    
    if (email && email !== user.email) {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email)) {
        return res.status(400).json({ 
          success: false,
          message: 'Please provide a valid email address',
          data: null
        });
      }
      
      const existingUser = await User.findOne({ 
        email: email, 
        userId: { $ne: req.user.userId } 
      });
      if (existingUser) {
        return res.status(409).json({
          success: false,
          message: 'Email already in use',
          data: null
        });
      }
      user.email = email;
    }
    
    if (avatarUrl) user.avatarUrl = avatarUrl;
    
    await user.save();
    
    const userResponse = {
      userId: user.userId,
      username: user.username,
      email: user.email,
      avatarUrl: user.avatarUrl,
      totalGamesPlayed: user.totalGamesPlayed,
      gamesWon: user.gamesWon,
      ready: user.ready
    };
    
    res.json({
      success: true,
      message: 'Profile updated successfully',
      data: userResponse
    });
  } catch (error) {
    console.error('Profile update error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating profile',
      data: null
    });
  }
});

// =================== LOBBY ENDPOINTS ===================

app.get('/lobbies', authenticateToken, async (req, res) => {
  try {
    const lobbies = await Lobby.find({ 
      isLocked: false,
      isPrivate: false 
    }).populate('players', 'userId username avatarUrl ready');
    
    const lobbiesResponse = lobbies.map(lobby => ({
      lobbyId: lobby.lobbyId,
      name: lobby.name,
      hostId: lobby.hostId,
      maxPlayers: lobby.maxPlayers,
      isPrivate: lobby.isPrivate,
      players: lobby.players,
      isLocked: lobby.isLocked,
      numRounds: lobby.numRounds,
      roundDurationSeconds: lobby.roundDurationSeconds,
      playerCount: lobby.players.length,
      createdAt: lobby.createdAt
    }));
    
    res.json({
      success: true,
      message: 'Lobbies retrieved successfully',
      data: { lobbies: lobbiesResponse }
    });
  } catch (error) {
    console.error('Error retrieving lobbies:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while retrieving lobbies',
      data: null
    });
  }
});

app.post('/lobbies', authenticateToken, async (req, res) => {
  try {
    console.log('Lobby creation request body:', JSON.stringify(req.body));
    const { name, maxPlayers, isPrivate, password, numRounds, roundDurationSeconds } = req.body;
    const hostId = req.user.userId;
    
    if (!name || name.trim().length === 0) {
      return res.status(400).json({
        success: false,
        message: 'Lobby name is required',
        data: null
      });
    }
    
    // Get host user record from database
    const hostUser = await User.findOne({ userId: hostId });
    if (!hostUser) {
      return res.status(404).json({
        success: false,
        message: 'Host user not found',
        data: null
      });
    }
    
    console.log(`Creating lobby with host ${hostId}, MongoDB ID: ${hostUser._id}`);
    
    // Create lobby
    const lobbyId = generateId();
    const newLobby = new Lobby({
      lobbyId,
      name: name.trim(),
      hostId, // User ID string
      maxPlayers: maxPlayers || 5,
      numRounds: numRounds || 3,
      roundDurationSeconds: roundDurationSeconds || 60,
      isPrivate: isPrivate || false,
      password: (isPrivate && password) ? password : '',
      isLocked: false,
      players: [hostUser._id] // Add host MongoDB ObjectId to players list
    });
    
    console.log('About to save lobby with players:', JSON.stringify(newLobby));
    
    // Ensure players list contains host
    if (!newLobby.players || newLobby.players.length === 0) {
      console.log('Players array is empty or missing - manually adding host');
      newLobby.players = [hostUser._id];
    }

    console.log('Before save - numRounds:', newLobby.numRounds, 'roundDurationSeconds:', newLobby.roundDurationSeconds);
    
    await newLobby.save();
    
    // Extra step to ensure host is in players list (in case the above code isn't working)
    const updateResult = await Lobby.updateOne(
      { lobbyId: lobbyId },
      { $addToSet: { players: hostUser._id } }
    );
    console.log(`Force-updated players list: ${JSON.stringify(updateResult)}`);

    // Find the saved lobby and populate the players
    const savedLobby = await Lobby.findOne({ lobbyId }).populate('players', 'userId username avatarUrl ready');
    console.log('Saved lobby:', JSON.stringify(savedLobby));
    
    // After saving, notify all clients about the new lobby
    broadcastLobbyListUpdate();
    
    res.status(201).json({
      success: true,
      message: 'Lobby created successfully',
      data: savedLobby
    });
  } catch (error) {
    console.error('Error creating lobby:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while creating lobby',
      data: null
    });
  }
});

app.get('/lobbies/:lobbyId', authenticateToken, async (req, res) => {
  try {
    const lobby = await Lobby.findOne({ lobbyId: req.params.lobbyId })
      .populate('players', 'userId username avatarUrl ready');
    
    if (!lobby) {
      return res.status(404).json({
        success: false,
        message: 'Lobby not found',
        data: null
      });
    }
    
    res.json({
      success: true,
      message: 'Lobby details retrieved successfully',
      data: lobby
    });
  } catch (error) {
    console.error('Error retrieving lobby details:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while retrieving lobby details',
      data: null
    });
  }
});

app.post('/lobbies/:lobbyId/join', authenticateToken, async (req, res) => {
  try {
    console.log(`HTTP JOIN REQUEST: User ${req.user.userId} attempting to join lobby ${req.params.lobbyId}`);
    const { password } = req.body;
    const lobby = await Lobby.findOne({ lobbyId: req.params.lobbyId });
    
    if (!lobby) {
      return res.status(404).json({
        success: false,
        message: 'Lobby not found',
        data: null
      });
    }
    
    if (lobby.isLocked) {
      return res.status(403).json({
        success: false,
        message: 'Lobby is locked',
        data: null
      });
    }
    
    if (lobby.players.length >= lobby.maxPlayers) {
      return res.status(403).json({
        success: false,
        message: 'Lobby is full',
        data: null
      });
    }
    
    if (lobby.isPrivate && lobby.password !== password) {
      return res.status(403).json({
        success: false,
        message: 'Incorrect password',
        data: null
      });
    }
    
    const user = await User.findOne({ userId: req.user.userId });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found',
        data: null
      });
    }
    
    // Check if user is already in lobby
    const alreadyInLobby = lobby.players.some(playerId => 
      playerId.toString() === user._id.toString()
    );
    
    if (!alreadyInLobby) {
      // Add user to players list
      lobby.players.push(user._id);
      await lobby.save();
      
      console.log(`User ${user.username} (${user.userId}) joined lobby ${lobby.lobbyId}`);
      
      // Notify all clients in lobby via WebSocket
      notifyLobbyStateChange(req.params.lobbyId);
      
      // Also broadcast to all clients to update lobby lists
      broadcastLobbyListUpdate();
    }
    
    // Return lobby with populated players and host details
    const updatedLobby = await Lobby.findOne({ lobbyId: req.params.lobbyId })
      .populate('players', 'userId username avatarUrl ready');
    
    // Get host details
    const hostUser = await User.findOne({ userId: updatedLobby.hostId }, 'userId username avatarUrl');
    
    res.json({
      success: true,
      message: 'Joined lobby successfully',
      data: {
        ...updatedLobby.toObject(),
        hostUser: hostUser
      }
    });
  } catch (error) {
    console.error('Error joining lobby:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while joining lobby',
      data: null
    });
  }
});

app.delete('/lobbies/:lobbyId/leave', authenticateToken, async (req, res) => {
  try {
    const lobby = await Lobby.findOne({ lobbyId: req.params.lobbyId });
    
    if (!lobby) {
      return res.status(404).json({
        success: false,
        message: 'Lobby not found',
        data: null
      });
    }
    
    const user = await User.findOne({ userId: req.user.userId });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found',
        data: null
      });
    }
    
    // Remove player from lobby
    lobby.players = lobby.players.filter(playerId => 
      !playerId.equals(user._id)
    );
    
    // If host leaves and there are other players, transfer host
    if (lobby.hostId === req.user.userId) {
      if (lobby.players.length > 0) {
        const newHost = await User.findById(lobby.players[0]);
        if (newHost) {
          lobby.hostId = newHost.userId;
        }
      } else {
        // No players left, delete lobby
        await Lobby.deleteOne({ lobbyId: req.params.lobbyId });
        return res.json({
          success: true,
          message: 'Left lobby and lobby deleted (no players remaining)',
          data: null
        });
      }
    }
    
    await lobby.save();
    
    // Notify all clients in lobby via WebSocket
    notifyLobbyStateChange(req.params.lobbyId);
    
    res.json({
      success: true,
      message: 'Left lobby successfully',
      data: null
    });
  } catch (error) {
    console.error('Error leaving lobby:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while leaving lobby',
      data: null
    });
  }
});

app.put('/lobbies/:lobbyId/settings', authenticateToken, async (req, res) => {
  try {
    const { name, maxPlayers, isPrivate, password, numRounds, roundDurationSeconds } = req.body;
    const lobby = await Lobby.findOne({ lobbyId: req.params.lobbyId });
    
    if (!lobby) {
      return res.status(404).json({
        success: false,
        message: 'Lobby not found',
        data: null
      });
    }
    
    if (lobby.hostId !== req.user.userId) {
      return res.status(403).json({
        success: false,
        message: 'Only the host can update lobby settings',
        data: null
      });
    }
    
    if (name) lobby.name = name.trim();
    if (maxPlayers) {
      if (maxPlayers < lobby.players.length) {
        return res.status(400).json({
          success: false,
          message: 'Cannot set max players below current player count',
          data: null
        });
      }
      lobby.maxPlayers = maxPlayers;
    }
    if (isPrivate !== undefined) lobby.isPrivate = isPrivate;
    if (password !== undefined) lobby.password = password;
    if (numRounds !== undefined) lobby.numRounds = numRounds;
    if (roundDurationSeconds !== undefined) lobby.roundDurationSeconds = roundDurationSeconds;
    
    await lobby.save();
    
    // Notify all clients in lobby via WebSocket
    notifyLobbyStateChange(req.params.lobbyId);
    
    res.json({
      success: true,
      message: 'Lobby settings updated successfully',
      data: lobby
    });
  } catch (error) {
    console.error('Error updating lobby settings:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating lobby settings',
      data: null
    });
  }
});

app.put('/lobbies/:lobbyId/lock', authenticateToken, async (req, res) => {
  try {
    const isLocked = req.body.isLocked !== undefined ? req.body.isLocked : req.body;
    const lobby = await Lobby.findOne({ lobbyId: req.params.lobbyId });
    
    if (!lobby) {
      return res.status(404).json({
        success: false,
        message: 'Lobby not found',
        data: null
      });
    }
    
    if (lobby.hostId !== req.user.userId) {
      return res.status(403).json({
        success: false,
        message: 'Only the host can lock/unlock the lobby',
        data: null
      });
    }
    
    lobby.isLocked = isLocked;
    await lobby.save();
    
    // Notify all clients in lobby via WebSocket
    notifyLobbyStateChange(req.params.lobbyId);
    
    res.json({
      success: true,
      message: `Lobby ${isLocked ? 'locked' : 'unlocked'} successfully`,
      data: lobby
    });
  } catch (error) {
    console.error('Error toggling lobby lock:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while toggling lobby lock',
      data: null
    });
  }
});

// =================== GAME ENDPOINTS ===================

app.post('/lobbies/:lobbyId/start-game', authenticateToken, async (req, res) => {
  try {
    const lobby = await Lobby.findOne({ lobbyId: req.params.lobbyId })
      .populate('players');
    
    if (!lobby) {
      return res.status(404).json({
        success: false,
        message: 'Lobby not found',
        data: null
      });
    }
    
    if (lobby.hostId !== req.user.userId) {
      return res.status(403).json({
        success: false,
        message: 'Only the host can start the game',
        data: null
      });
    }
    
    if (lobby.players.length < 2) {
      return res.status(400).json({
        success: false,
        message: 'At least 2 players required to start game',
        data: null
      });
    }
    
    const gameId = generateId();
    const newGame = new Game({
      gameId,
      lobbyId: lobby.lobbyId,
      players: lobby.players.map(p => p._id),
      currentRound: 1,
      maxRounds: lobby.numRounds,
      roundDurationSeconds: lobby.roundDurationSeconds,
      status: 'active'
    });
    
    await newGame.save();
    
    // Lock the lobby
    lobby.isLocked = true;
    await lobby.save();
    
    // Notify clients about lobby and game state changes
    notifyLobbyStateChange(req.params.lobbyId);
    notifyGameStateChange(gameId);
  } catch (error) {
    console.error('Error starting game:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while starting game',
      data: null
    });
  }
});

server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
