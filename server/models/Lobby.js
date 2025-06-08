const mongoose = require('mongoose');

const lobbySchema = new mongoose.Schema({
  lobbyId: { type: String, required: true, unique: true },
  name: { type: String, required: true },
  hostId: { type: String, required: true },
  maxPlayers: { type: Number, default: 8 },
  isPrivate: { type: Boolean, default: false },
  players: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  isLocked: { type: Boolean, default: false },
  numRounds: { type: Number, default: 3 },
  roundDurationSeconds: { type: Number, default: 60 },
  createdAt: { type: Date, default: Date.now },
  lastActivity: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Lobby', lobbySchema);
