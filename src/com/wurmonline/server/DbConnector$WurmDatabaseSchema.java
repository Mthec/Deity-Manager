package com.wurmonline.server;

import java.lang.String;

public enum DbConnector$WurmDatabaseSchema {
    CREATURES("WURMCREATURES"),
    SPELLS("WURMSPELLS"),
    DEITIES("WURMDEITIES"),
    ECONOMY("WURMECONOMY"),
    ITEMS("WURMITEMS"),
    LOGIN("WURMLOGIN"),
    LOGS("WURMLOGS"),
    PLAYERS("WURMPLAYERS"),
    TEMPLATES("WURMTEMPLATES"),
    ZONES("WURMZONES"),
    SITE("WURMSITE");

    private final java.lang.String database;

    private DbConnector$WurmDatabaseSchema(java.lang.String database) {
        this.database = database;
    }

    public java.lang.String toString() {
        return this.name();
    }

    public java.lang.String getDatabase() {
        return this.database;
    }
}
