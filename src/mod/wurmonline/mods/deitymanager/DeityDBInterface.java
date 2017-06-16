//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
import com.wurmonline.server.Constants;
import com.wurmonline.server.DbConnector;
import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.deities.Deity;
import com.wurmonline.server.spells.Spell;
import com.wurmonline.server.spells.SpellGenerator;
import com.wurmonline.server.spells.Spells;
import com.wurmonline.server.utils.DbUtilities;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeityDBInterface {
    private static final String GET_ALL_DEITIES = "SELECT * FROM DEITIES";
    private static final String GET_ALL_DEITY_SPELLS = "SELECT * FROM DEITY_SPELLS WHERE DEITY=?";
    private static final String ADD_SPELL_LINK = "INSERT INTO DEITY_SPELLS(SPELL,DEITY,ALLOWED) VALUES(?,?,?)";
    private static final String SAVE_DEITY = "UPDATE DEITIES SET NAME=?, ALIGNMENT=?, SEX=?, POWER=?, FAITH=?, HOLYITEM=?, FAVOR=?, ATTACK=?, VITALITY=? WHERE ID=?";
    private static final String SAVE_DEITY_SPELL = "UPDATE DEITY_SPELLS SET ALLOWED=? WHERE SPELL=? AND DEITY=?";
    private static final Logger logger = Logger.getLogger(DeityDBInterface.class.getName());
    private static final ConcurrentHashMap<String, DeityData> deityData = new ConcurrentHashMap<>();
    private static ResourceBundle messages = LocaleHelper.getBundle("DeityManager");

    public static void loadAllData() {
        deityData.clear();
        Connection deityCon = null;
        Connection spellCon = null;
        PreparedStatement deityPS = null;
        PreparedStatement spellPS = null;
        ResultSet drs = null;
        ResultSet srs = null;

        try {
            deityCon = DbConnector.getDeityDbCon();
            deityPS = deityCon.prepareStatement(GET_ALL_DEITIES);
            drs = deityPS.executeQuery();
            Method getSpellsDbCon = ReflectionUtil.getMethod(DbConnector.class, "getSpellsDbCon");
            Spell[] allSpells = Spells.getAllSpells();
            if (allSpells.length == 0) {
                SpellGenerator.createSpells();
                allSpells = Spells.getAllSpells();
            }
            spellCon = (Connection)getSpellsDbCon.invoke(DbConnector.class);
            if (!spellCon.getMetaData().getTables(null, null, "DEITY_SPELLS", null).next()) {
                Statement create = spellCon.createStatement();
                create.executeUpdate("CREATE TABLE DEITY_SPELLS\n" +
                        "(\n" +
                        "    _ID                 INTEGER         NOT NULL PRIMARY KEY,\n" +
                        "    SPELL               INTEGER         NOT NULL,\n" +
                        "    DEITY               TINYINT         NOT NULL,\n" +
                        "    ALLOWED             TINYINT(1)      NOT NULL\n" +
                        ");");
                create.close();
                fillSpellsDb(drs, allSpells, spellCon);
                drs = deityPS.executeQuery();
            }

            while(drs.next()) {
                String deityName = drs.getString("NAME");
                DeityData deity = new DeityData();
                deity.setName(deityName);
                deity.setNumber(drs.getInt("ID"));
                deity.setAlignment(drs.getByte("ALIGNMENT"));
                deity.setSex(drs.getByte("SEX"));
                deity.setPower(drs.getByte("POWER"));
                deity.setFaith(drs.getDouble("FAITH"));
                deity.setHolyItem(drs.getInt("HOLYITEM"));
                deity.setFavor(drs.getInt("FAVOR"));
                deity.setAttack(drs.getFloat("ATTACK"));
                deity.setVitality(drs.getFloat("VITALITY"));

                // Spells
                spellPS = spellCon.prepareStatement(GET_ALL_DEITY_SPELLS);
                spellPS.setInt(1, drs.getInt("ID"));
                srs = spellPS.executeQuery();

                Set<Integer> spells = new HashSet<>();
                while (srs.next()) {
                    if (srs.getByte("ALLOWED") == 1) {
                        spells.add(srs.getInt("SPELL"));
                    }
                }
                deity.setSpells(spells);
                DeityDBInterface.deityData.put(deityName, deity);
            }
        } catch (SQLException | NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
            logger.log(Level.WARNING, messages.getString("failed_to_load"), ex);
        } finally {
            DbUtilities.closeDatabaseObjects(deityPS, drs);
            DbUtilities.closeDatabaseObjects(spellPS, srs);
            DbConnector.returnConnection(deityCon);
            DbConnector.returnConnection(spellCon);
        }
    }

    public static DeityData[] getAllData () {
        return deityData.values().toArray(new DeityData[deityData.size()]);
    }

    public static DeityData getDeityData (String name) {
        return deityData.get(name);
    }

    public static void saveDeityData (DeityData deityData) throws SQLException {
        Connection deityCon = null;
        Connection spellCon = null;
        PreparedStatement deityPS;
        PreparedStatement spellPS;

        try {
            deityCon = DbConnector.getDeityDbCon();
            deityPS = deityCon.prepareStatement(SAVE_DEITY);
            deityPS.setString(1, deityData.getName());
            deityPS.setByte(2, (byte) deityData.getAlignment());
            deityPS.setByte(3, (byte) deityData.getSex());
            deityPS.setByte(4, (byte) deityData.getPower());
            deityPS.setDouble(5, deityData.getFaith());
            deityPS.setInt(6, deityData.getHolyItem());
            deityPS.setInt(7, deityData.getFavor());
            deityPS.setFloat(8, deityData.getAttack());
            deityPS.setFloat(9, deityData.getVitality());
            deityPS.setByte(10, (byte) deityData.getNumber());
            deityPS.executeUpdate();

            // Spells
            Method getSpellsDbCon = ReflectionUtil.getMethod(DbConnector.class, "getSpellsDbCon");
            spellCon = (Connection)getSpellsDbCon.invoke(DbConnector.class);
            boolean autoCommit = spellCon.getAutoCommit();
            spellCon.setAutoCommit(false);
            spellPS = spellCon.prepareStatement(SAVE_DEITY_SPELL);
            for (Spell spell : Spells.getAllSpells()) {
                if (deityData.hasSpell(spell)) {
                    spellPS.setByte(1, (byte)1);
                }
                else {
                    spellPS.setByte(1, (byte)0);
                }
                spellPS.setInt(2, spell.number);
                spellPS.setByte(3, (byte)deityData.getNumber());
                spellPS.addBatch();
            }
            spellPS.executeBatch();
            spellCon.commit();
            spellCon.setAutoCommit(autoCommit);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException ex) {
            logger.log(Level.WARNING, messages.getString("failed_to_commit"), ex);
        } finally {
            DbConnector.returnConnection(deityCon);
            DbConnector.returnConnection(spellCon);
        }
    }

    static void fillSpellsDb(ResultSet deities, Spell[] allSpells, Connection con) {
        Deity deity = null;
        try {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            PreparedStatement ps = con.prepareStatement(ADD_SPELL_LINK);
            while (deities.next()) {
                deity = Deities.getDeity(deities.getInt("ID"));
                Set<Spell> deitySpells = deity.getSpells();

                for (Spell spell : allSpells) {
                    ps.setInt(1, spell.number);
                    ps.setByte(2, (byte) deity.getNumber());
                    if (deitySpells.contains(spell)) {
                        ps.setByte(3, (byte) 1);
                    } else {
                        ps.setByte(3, (byte) 0);
                    }
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();
            con.commit();
            con.setAutoCommit(autoCommit);
        } catch(SQLException ex){
            String name = deity != null ? deity.getName() : "Unknown";
            logger.log(Level.WARNING, MessageFormat.format(messages.getString("failed_to_fill"), name), ex);
        }
    }
}
