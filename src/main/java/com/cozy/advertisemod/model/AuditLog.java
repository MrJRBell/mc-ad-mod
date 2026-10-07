package com.cozy.advertisemod.model;

/**
 * Immutable audit log record for tracking shop barrel lifecycle events.
 */
public class AuditLog {

    private final int id;
    private final String barrelId;
    private final String playerUuid;
    private final String playerName;
    private final String action;
    private final String details;
    private final String timestamp;

    public AuditLog(int id, String barrelId, String playerUuid, String playerName,
                    String action, String details, String timestamp) {
        this.id = id;
        this.barrelId = barrelId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public String getBarrelId() {
        return barrelId;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + action + " by " + playerName + " (" + details + ")";
    }
}

