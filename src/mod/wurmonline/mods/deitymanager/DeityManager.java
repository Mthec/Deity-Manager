package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
import com.wurmonline.server.ServerDirInfo;
import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.deities.Deity;
import com.wurmonline.server.spells.Spell;
import com.wurmonline.server.spells.Spells;
import javassist.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

public class DeityManager implements WurmServerMod, PreInitable, ServerStartedListener {
    private static Logger logger = Logger.getLogger(DeityManager.class.getName());
    private ResourceBundle messages = LocaleHelper.getBundle("DeityManager");

    @Override
    public void preInit() {
        ClassPool pool = HookManager.getInstance().getClassPool();
        try {

            CtClass deity = pool.get("com.wurmonline.server.deities.Deity");
            CtMethod method = CtNewMethod.make("public void removeSpell(com.wurmonline.server.spells.Spell spell) {" +
                    "this.spells.remove(spell);" +
                    "if(spell.targetCreature && this.creatureSpells.contains(spell)) { " +
                    "    this.creatureSpells.remove(spell);" +
                    "}" +
                    "if(spell.targetItem && this.itemSpells.contains(spell)) {" +
                    "    this.itemSpells.remove(spell);" +
                    "}" +
                    "if(spell.targetWound && this.woundSpells.contains(spell)) {" +
                    "    this.woundSpells.remove(spell);" +
                    "}" +
                    "if(spell.targetTile && this.tileSpells.contains(spell)) {" +
                    "    this.tileSpells.remove(spell);" +
                    "}" +
                    "}", deity);
            deity.addMethod(method);
        } catch (NotFoundException | CannotCompileException ex) {
            logger.warning(messages.getString("removespell_error"));
            ex.printStackTrace();
            System.exit(-1);
        }

        try {
            CtClass spellGenerator = pool.get("com.wurmonline.server.spells.SpellGenerator");
            CtMethod createSpells = spellGenerator.getDeclaredMethod("createSpells");
            createSpells.insertBefore("{ if (com.wurmonline.server.spells.Spells.getAllSpells().length != 0) { return; } }");
        } catch (NotFoundException | CannotCompileException ex) {
            logger.warning(messages.getString("spell_generator_error"));
            ex.printStackTrace();
            System.exit(-1);
        }

        try {
            CtClass dbConnector = pool.get("com.wurmonline.server.DbConnector");

            pool.get("com.wurmonline.server.database.WurmDatabaseSchema").detach();
            pool.makeClass(DeityManager.class.getResourceAsStream("WurmDatabaseSchema.class"));

            CtClass helper = pool.get("com.wurmonline.server.DbConnector$ConfigHelper");

            helper.getDeclaredMethod("buildConnectors").insertAfter(
                            "$_.put(com.wurmonline.server.database.WurmDatabaseSchema.SPELLS, " +
                            "new com.wurmonline.server.DbConnector(this.factoryForSchema(com.wurmonline.server.database.WurmDatabaseSchema.SPELLS), \"spellsDbcon\"));");

            CtMethod method = CtNewMethod.make("public static java.sql.Connection getSpellsDbCon() throws java.sql.SQLException {" +
                    "return refreshConnectionForSchema(com.wurmonline.server.database.WurmDatabaseSchema.SPELLS);}", dbConnector);
            dbConnector.addMethod(method);

            HookManager.getInstance().registerHook("com.wurmonline.server.gui.WurmServerGuiMain", "start", "(Ljavafx/stage/Stage;)V", new InvocationHandlerFactory() {
                @Override
                public InvocationHandler createInvocationHandler() {
                    return (o, method1, objects) -> {
                        method1.invoke(o, objects);
                        DeityManagerWindow controller = new DeityManagerWindow();
                        controller.start();
                        return null;
                    };
                }
            });

        } catch (NotFoundException | CannotCompileException | IOException ex) {
            ex.printStackTrace();
            logger.warning(messages.getString("dbconnector_error"));
            System.exit(-1);
        }
    }

    @Override
    public void onServerStarted () {
        ServerDirInfo.getFileDBPath();
        try {
            DeityDBInterface.loadAllData();
            for (DeityData deityData : DeityDBInterface.getAllData()) {
                Deity deity = Deities.getDeity(deityData.getNumber());
                deity.alignment = deityData.getAlignment();
                deity.setPower((byte)deityData.getPower());
                ReflectionUtil.setPrivateField(deity, ReflectionUtil.getField(Deity.class, "faith"), deityData.getFaith());
                deity.setFavor(deityData.getFavor());
                ReflectionUtil.setPrivateField(deity, ReflectionUtil.getField(Deity.class, "attack"), deityData.getAttack());
                ReflectionUtil.setPrivateField(deity, ReflectionUtil.getField(Deity.class, "vitality"), deityData.getVitality());

                Method removeSpell = ReflectionUtil.getMethod(Deity.class, "removeSpell");
                Set<Spell> deitySpells = deity.getSpells();
                for (Spell spell : Spells.getAllSpells()) {
                    if (deitySpells.contains(spell)) {
                        if (!deityData.hasSpell(spell)) {
                            logger.info(MessageFormat.format(messages.getString("removing_spell"), spell.getName(), deity.getName()));
                            removeSpell.invoke(deity, spell);
                            assert !deity.hasSpell(spell);
                        }
                    } else {
                        if (deityData.hasSpell(spell)) {
                            logger.info(MessageFormat.format(messages.getString("adding_spell"), spell.getName(), deity.getName()));
                            deity.addSpell(spell);
                            assert deity.hasSpell(spell);
                        }
                    }
                }
            }
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | NoSuchFieldException ex) {
            logger.warning(messages.getString("applying_server_settings_error"));
            ex.printStackTrace();
        }
    }
}
