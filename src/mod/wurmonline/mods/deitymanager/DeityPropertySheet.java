package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
import com.wurmonline.server.spells.Spell;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mod.wurmonline.serverlauncher.LocaleHelper;
import org.controlsfx.control.PropertySheet;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeityPropertySheet extends VBox {
    private static Logger logger = Logger.getLogger(DeityPropertySheet.class.getName());
    private DeityData currentDeity;
    private ObservableList<PropertySheet.Item> list;
    private Set<Enum> changedProperties = new HashSet<>();
    private Map<String, Spell> stored_spells = new HashMap<>();
    private ResourceBundle messages = LocaleHelper.getBundle("DeityManager");

    public DeityPropertySheet(DeityData deity, Spell[] allSpells, boolean serverRunning) {
        currentDeity = deity;
        list = FXCollections.observableArrayList();
        String deity_category = messages.getString("deity_category");
        list.add(new deityItem(DeityPropertyType.NAME, deity_category, messages.getString("name"), messages.getString("name_description"), !serverRunning, deity.getName()));
        list.add(new deityItem(DeityPropertyType.NUMBER, deity_category, messages.getString("number"), messages.getString("number_description"), false, deity.getNumber()));
        list.add(new deityItem(DeityPropertyType.ALIGNMENT, deity_category, messages.getString("alignment"), messages.getString("alignment_description"), true, deity.getAlignment()));
        list.add(new deityItem(DeityPropertyType.SEX, deity_category, messages.getString("sex"), messages.getString("sex_description"), !serverRunning, deity.getSex()));
        list.add(new deityItem(DeityPropertyType.POWER, deity_category, messages.getString("power"), messages.getString("power_description"), true, deity.getPower()));
        list.add(new deityItem(DeityPropertyType.FAITH, deity_category, messages.getString("faith"), messages.getString("faith_description"), true, deity.getFaith()));
        list.add(new deityItem(DeityPropertyType.HOLYITEM, deity_category, messages.getString("holy_item"), messages.getString("holy_item_description"), !serverRunning, deity.getHolyItem()));
        list.add(new deityItem(DeityPropertyType.FAVOR, deity_category, messages.getString("favor"), messages.getString("favor_description"), true, deity.getFavor()));
        list.add(new deityItem(DeityPropertyType.ATTACK, deity_category, messages.getString("attack"), messages.getString("attack_description"), true, deity.getAttack()));
        list.add(new deityItem(DeityPropertyType.VITALITY, deity_category, messages.getString("vitality"), messages.getString("vitality_description"), true, deity.getVitality()));

        // Spells
        stored_spells.clear();
        for (Spell spell : allSpells) {
            boolean allowed = deity.hasSpell(spell);
            String category = allowed ? messages.getString("allowed") : messages.getString("not_allowed");
            stored_spells.put(spell.getName(), spell);
            list.add(new SpellItem(SpellPropertyType.ALLOWED, category, spell.getName(), messages.getString("description"), true, allowed));
        }


        PropertySheet propertySheet = new PropertySheet(list);
        VBox.setVgrow(propertySheet, Priority.ALWAYS);
        if (serverRunning) {
            this.getChildren().add(new Label(messages.getString("server_running")));
        }
        this.getChildren().add(propertySheet);
    }

    public DeityData getCurrentData() {
        return currentDeity;
    }

    public final String save() {
        String toReturn = "";
        boolean saveAtAll = false;

        for(PropertySheet.Item propertyItem : list) {
            if (!(propertyItem instanceof deityItem)) {
                continue;
            }
            deityItem item = (deityItem)propertyItem;
            if(changedProperties.contains(item.getPropertyType())) {
                saveAtAll = true;

                try {
                    switch(item.getPropertyType().ordinal() + 1) {
                        case 1:
                            currentDeity.setName(item.getValue().toString());
                            break;
                        case 2:
                            currentDeity.setNumber((Integer) item.getValue());
                            break;
                        case 3:
                            currentDeity.setAlignment((Integer) item.getValue());
                            break;
                        case 4:
                            currentDeity.setSex((Integer) item.getValue());
                            break;
                        case 5:
                            currentDeity.setPower((Integer) item.getValue());
                            break;
                        case 6:
                            currentDeity.setFaith((Double) item.getValue());
                            break;
                        case 7:
                            currentDeity.setHolyItem((Integer) item.getValue());
                            break;
                        case 8:
                            currentDeity.setFavor((Integer) item.getValue());
                            break;
                        case 9:
                            currentDeity.setAttack((Float) item.getValue());
                            break;
                        case 10:
                            currentDeity.setVitality((Float) item.getValue());
                            break;
                    }
                } catch (Exception ex) {
                    saveAtAll = false;
                    toReturn = toReturn + MessageFormat.format(messages.getString("invalid_value"), item.getCategory(), item.getValue());
                    logger.log(Level.INFO, MessageFormat.format(messages.getString("error"), ex.getMessage()), ex);
                }
            }
        }

        String name;
        if (changedProperties.size() > 0) {
            try {
                for (PropertySheet.Item item : list) {
                    if (!(item instanceof SpellItem)) {
                        continue;
                    }
                    name = item.getName();
                    Spell spell = stored_spells.get(name);
                    if ((boolean)item.getValue()) {
                        if (!currentDeity.hasSpell(spell)) {
                            currentDeity.addSpell(spell);
                        }
                    } else {
                        if (currentDeity.hasSpell(spell)) {
                            currentDeity.removeSpell(spell);
                        }
                    }
                }
                toReturn = "ok";
            } catch (Exception ex) {
                toReturn = ex.getMessage();
            }
        }

        if (toReturn.length() == 0 && saveAtAll) {
            try {
                currentDeity.save();
                toReturn = "ok";
            } catch (Exception ex) {
                toReturn = ex.getMessage();
            }
        }
        else {
            toReturn = "ok";
        }

        return toReturn;
    }

    public boolean haveChanges () {
        return changedProperties.size() > 0;
    }

    class deityItem implements PropertySheet.Item {
        private DeityPropertyType type;
        private String category;
        private String name;
        private String description;
        private boolean editable = true;
        private Object value;

        deityItem(DeityPropertyType aType, String aCategory, String aName, String aDescription, boolean aEditable, Object aValue) {
            type = aType;
            category = aCategory;
            name = aName;
            description = aDescription;
            editable = aEditable;
            value = aValue;
        }

        public DeityPropertyType getPropertyType() {
            return type;
        }

        public Class<?> getType() {
            return value.getClass();
        }

        public String getCategory() {
            return category;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isEditable() {
            return editable;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object aValue) {
            if(!value.equals(aValue)) {
                changedProperties.add(type);
            }

            value = aValue;
        }

        @Override
        public Optional<ObservableValue<?>> getObservableValue() {
            return null;
        }
    }

    private enum DeityPropertyType {
        NAME,
        NUMBER,
        ALIGNMENT,
        SEX,
        POWER,
        FAITH,
        HOLYITEM,
        FAVOR,
        ATTACK,
        VITALITY;

        DeityPropertyType() {
        }
    }

    class SpellItem implements PropertySheet.Item {
        private SpellPropertyType type;
        private String category;
        private String name;
        private String description;
        private boolean editable = true;
        private Object value;

        SpellItem (SpellPropertyType aType, String aCategory, String aName, String aDescription, boolean aEditable, Object aValue) {
            type = aType;
            category = aCategory;
            name = aName;
            description = aDescription;
            editable = aEditable;
            value = aValue;
        }

        public SpellPropertyType getPropertyType() {
            return type;
        }

        public Class<?> getType() {
            return value.getClass();
        }

        public String getCategory() {
            return category;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isEditable() {
            return editable;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object aValue) {
            if(!value.equals(aValue)) {
                changedProperties.add(type);
            }
            value = aValue;
        }

        @Override
        public Optional<ObservableValue<? extends Object>> getObservableValue() {
            return null;
        }
    }

    private enum SpellPropertyType {
        ALLOWED;

        SpellPropertyType() {
        }
    }
}
