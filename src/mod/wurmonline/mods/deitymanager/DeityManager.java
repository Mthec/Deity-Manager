package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
import com.wurmonline.server.ServerDirInfo;
import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.deities.Deity;
import com.wurmonline.server.spells.Spell;
import com.wurmonline.server.spells.Spells;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import javassist.*;
import mod.wurmonline.serverlauncher.LocaleHelper;
import mod.wurmonline.serverlauncher.gui.ServerGuiController;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;
import org.gotti.wurmunlimited.modloader.interfaces.WurmUIMod;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeityManager implements WurmMod, WurmUIMod, PreInitable, ServerStartedListener {
    private static Logger logger = Logger.getLogger(DeityManager.class.getName());
    ServerGuiController controller;
    DeityData[] deities;
    DeityPropertySheet deityPropertySheet;
    int lastSelectedDeity;
    private boolean rebuilding = false;
    private final ChangeListener<DeityData> listener = (observable, oldValue, newValue) -> deitiesListChanged();
    private ResourceBundle messages = LocaleHelper.getBundle("DeityManager");

    @Override
    public String getName() {
        return messages.getString("mod_name");
    }

    @Override
    public Region getRegion(ServerGuiController guiController) {
        controller = guiController;
        try {
            FXMLLoader fx = new FXMLLoader(DeityManager.class.getResource("DeityManager.fxml"), messages);
            fx.setClassLoader(this.getClass().getClassLoader());
            fx.setControllerFactory(param -> this);
            return fx.load();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        Label label = new Label(messages.getString("fxml_missing"));
        Pane pane = new Pane();
        pane.getChildren().add(label);
        return pane;
    }

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

            pool.get("com.wurmonline.server.DbConnector$WurmDatabaseSchema").detach();
            pool.makeClass(DeityManager.class.getResourceAsStream("DbConnector$WurmDatabaseSchema.class"));
            dbConnector.rebuildClassFile();

            dbConnector.getDeclaredMethod("initialize").insertAfter(
                    "final String dbUser = com.wurmonline.server.Constants.dbUser;" +
                    "final String dbPass = com.wurmonline.server.Constants.dbPass;" +
                    "String dbHost;" +
                    "String dbDriver;" +
                    "if(isSqlite()) {" +
                    "    dbHost = com.wurmonline.server.Constants.dbHost;" +
                    "    config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);" +
                    "    config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);" +
                    "    dbDriver = \"org.sqlite.JDBC\";" +
                    "    } else {" +
                    "    dbHost = com.wurmonline.server.Constants.dbHost + com.wurmonline.server.Constants.dbPort;" +
                    "    dbDriver = com.wurmonline.server.Constants.dbDriver;" +
                    "    }" +
                    "CONNECTORS.put(com.wurmonline.server.DbConnector.WurmDatabaseSchema.SPELLS, new com.wurmonline.server.DbConnector(" +
                    "dbDriver, dbHost, com.wurmonline.server.DbConnector.WurmDatabaseSchema.SPELLS.getDatabase(), dbUser, dbPass, \"spellsDbcon\"));");

            CtMethod method = CtNewMethod.make("public static final java.sql.Connection getSpellsDbCon() throws java.sql.SQLException {" +
                    "return refreshConnectionForSchema(com.wurmonline.server.DbConnector.WurmDatabaseSchema.SPELLS);}", dbConnector);
            dbConnector.addMethod(method);

        } catch (NotFoundException | CannotCompileException | IOException ex) {
            logger.warning(messages.getString("dbconnector_error"));
            ex.printStackTrace();
            System.exit(-1);
        }
    }

    @Override
    public void onServerStarted () {
        ServerDirInfo.getFileDBPath();
        try {
            if (deities.length == 0) {
                DeityDBInterface.loadAllData();
                deities = DeityDBInterface.getAllData();
            }
            for (DeityData deityData : deities) {
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

    @FXML
    ListView<DeityData> deitiesList;
    @FXML
    ScrollPane deityProperties;

    @FXML
    void populateDeitiesList () {
        rebuilding = true;

        deitiesList.getItems().clear();
        DeityDBInterface.loadAllData();
        deities = DeityDBInterface.getAllData();

        for (DeityData deity : deities) {
            deitiesList.getItems().add(deity);
            if (deity.getNumber() == lastSelectedDeity) {
                deitiesList.getSelectionModel().select(deity);
            }
        }

        if (deitiesList.getSelectionModel().getSelectedItem() == null) {
            deitiesList.getSelectionModel().selectFirst();
        }

        rebuilding = false;
        deitiesListChanged();
    }

    @FXML
    void deitiesListChanged () {
        if (!rebuilding) {
            DeityData selectedDeity = deitiesList.getSelectionModel().getSelectedItem();
            if (selectedDeity != null) {
                lastSelectedDeity = selectedDeity.getNumber();
                String name = selectedDeity.getName();
                logger.info(MessageFormat.format(messages.getString("selecting"), name));
                deityPropertySheet = new DeityPropertySheet(selectedDeity, Spells.getAllSpells(), controller.serverIsRunning());
                deityProperties.setContent(deityPropertySheet);
                deityPropertySheet.requestFocus();
            }
        }
    }

    ButtonType saveCheck() {
        if (deityPropertySheet != null && deityPropertySheet.haveChanges()) {
            ButtonType result = controller.showYesNoCancel(messages.getString("changes_title"), messages.getString("changes_header"), messages.getString("changes_message")).get();
            if (result == ButtonType.YES) {
                saveDeity();
            }
            return result;
        }
        return new ButtonType("", ButtonBar.ButtonData.NO);
    }

    @FXML
    void saveDeity () {
        String error = deityPropertySheet.save();
        if (error != null && error.length() > 0 ) {
            try {
                DeityDBInterface.saveDeityData(deityPropertySheet.getCurrentData());

                controller.showInformationDialog(messages.getString("saved_title"),
                        messages.getString("saved_header"),
                        messages.getString("saved_message"));

                if (controller.serverIsRunning()) {
                    onServerStarted();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                error += ex.getMessage();
            }
        }
        if (error != null && error.length() > 0 && !error.equalsIgnoreCase("ok")) {
            controller.showErrorDialog(messages.getString("save_error_title"),
                                    messages.getString("save_error_header"),
                                    MessageFormat.format(messages.getString("save_error_message"), error));
        }
        populateDeitiesList();
    }

    @FXML
    public void initialize () {
        ButtonType check = saveCheck();
        if (check == ButtonType.CANCEL) {
            return;
        }
        EventHandler<InputEvent> checkEvent = (event) -> {
            ButtonType result = saveCheck();
            if (result == ButtonType.CANCEL) {
                event.consume();
            }
        };
        deitiesList.getSelectionModel().selectedItemProperty().removeListener(listener);
        deitiesList.getSelectionModel().selectedItemProperty().addListener(listener);
        deitiesList.setCellFactory(new Callback<ListView<DeityData>, ListCell<DeityData>>() {
            @Override
            public ListCell<DeityData> call(ListView<DeityData> param) {
                ListCell<DeityData> cell = new ListCell<DeityData>() {
                    @Override
                    public void updateItem (DeityData item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            textProperty().bind(item.getNameProperty());
                        }
                    }
                };
                cell.addEventFilter(MouseEvent.MOUSE_PRESSED, checkEvent);
                cell.addEventFilter(KeyEvent.KEY_PRESSED, checkEvent);
                return cell;
            }
        });
        populateDeitiesList();
    }
}
