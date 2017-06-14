package com.wurmonline.server.database;

import javax.annotation.concurrent.Immutable;

@Immutable
public enum WurmDatabaseSchema {
    CREATURES("WURMCREATURES", "creatures"),
    SPELLS("WURMSPELLS", "spells"),
    DEITIES("WURMDEITIES", "deities"),
    ECONOMY("WURMECONOMY", "economy"),
    ITEMS("WURMITEMS", "items"),
    LOGIN("WURMLOGIN", "login"),
    LOGS("WURMLOGS", "logs"),
    PLAYERS("WURMPLAYERS", "players"),
    TEMPLATES("WURMTEMPLATES", "templates"),
    ZONES("WURMZONES", "zones");

    private final String database;
    private final String migration;

    private WurmDatabaseSchema(String database, String migration) {
        this.database = database;
        this.migration = migration;
    }

    public String getDatabase() {
        return this.database;
    }

    public String getMigration() {
        return this.migration;
    }
}

