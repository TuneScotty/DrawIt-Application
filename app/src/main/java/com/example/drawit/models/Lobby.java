package com.example.drawit.models;

public class Lobby {
    private String id;
    private String name;
    private String creator;
    private int playersCount;
    private boolean hasPassword;
    private String enteredPassword;

    public Lobby(String id, String name, String creator, int playersCount, boolean hasPassword) {
        this.id = id;
        this.name = name;
        this.creator = creator;
        this.playersCount = playersCount;
        this.hasPassword = hasPassword;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCreator() { return creator; }
    public int getPlayersCount() { return playersCount; }
    public boolean hasPassword() { return hasPassword; }
    public String getEnteredPassword() { return enteredPassword; }
    public void setEnteredPassword(String password) { this.enteredPassword = password; }
} 