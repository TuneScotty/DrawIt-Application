const mongoose = require('mongoose');

const gameSchema = new mongoose.Schema({
  gameId: { type: String, required: true, unique: true },
  lobbyId: { type: String, required: true },
  players: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  currentRound: { type: Number, default: 1 },
  maxRounds: { type: Number, default: 3 },
  roundDurationSeconds: { type: Number, default: 60 },
  currentDrawer: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  wordToGuess: { type: String, default: '' },
  isTransient: { type: Boolean, default: false },
  playerScores: [{
    userId: { type: String },
    username: { type: String },
    score: { type: Number, default: 0 }
  }],
  status: { type: String, enum: ['waiting', 'active', 'finished'], default: 'waiting' },
  createdAt: { type: Date, default: Date.now }
});

// Middleware to ensure currentDrawer is set when game becomes active
gameSchema.pre('save', function(next) {
  // If the game is becoming active and no drawer is set, set the first player as drawer
  if (this.isModified('status') && this.status === 'active' && !this.currentDrawer && this.players.length > 0) {
    this.currentDrawer = this.players[0];
    console.log(`Setting first player as drawer: ${this.players[0]._id}`);
    
    // Also ensure wordToGuess is set
    if (!this.wordToGuess) {
      const defaultWords = ['apple', 'house', 'car', 'tree', 'sun', 'dog', 'cat'];
      this.wordToGuess = defaultWords[Math.floor(Math.random() * defaultWords.length)];
      console.log(`Setting default word to guess: ${this.wordToGuess}`);
    }
  }
  next();
});

module.exports = mongoose.model('Game', gameSchema);
