const mongoose = require('mongoose');

// Define the drawing schema
const drawingSchema = new mongoose.Schema({
  drawingId: {
    type: String,
    required: true,
    unique: true
  },
  title: {
    type: String,
    required: true
  },
  imageData: {
    type: String,
    required: true
  },
  authorId: {
    type: String,
    required: true
  },
  prompt: {
    type: String,
    default: ''
  },
  ratings: [{
    userId: String,
    score: Number
  }],
  averageRating: {
    type: Number,
    default: 0
  },
  gameId: {
    type: String,
    default: null
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
});

// Calculate average rating before saving
drawingSchema.pre('save', function(next) {
  if (this.ratings.length > 0) {
    const totalScore = this.ratings.reduce((sum, rating) => sum + rating.score, 0);
    this.averageRating = totalScore / this.ratings.length;
  }
  next();
});

// Create the Drawing model
const Drawing = mongoose.model('Drawing', drawingSchema);

module.exports = Drawing;
