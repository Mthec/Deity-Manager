// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
package mod.wurmonline.mods.deitymanager;

import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.spells.Spell;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.sql.SQLException;
import java.util.Set;

public class DeityData {
    private String name;
    private int number;
    private int align;
    private int sex;
    private int power;
    private double faith;
    private int holyitem;
    private int favor;
    private float attack;
    private float vitality;
    private Set<Integer> spells;

    public final void save() throws SQLException {
        DeityDBInterface.saveDeityData(this);
    }

    public String getName() { return this.name; }

    public void setName(String name) {
        this.name = name;
    }

    public StringProperty getNameProperty () { return new SimpleStringProperty(this.name); }

    public int getNumber() {
        return this.number;
    }

    public void setNumber(int num) { this.number = num; }

    public int getAlignment() { return this.align; }

    public void setAlignment(int align) { this.align = align; }

    public int getSex() { return this.sex; }

    public void setSex(int sex) { this.sex = sex; }

    public int getPower() { return this.power; }

    public void setPower(int power) { this.power = power; }

    public double getFaith() {
        return this.faith;
    }

    public void setFaith(double faith) { this.faith = faith; }

    public int getHolyItem() { return this.holyitem; }

    public void setHolyItem(int holyitem) { this.holyitem = holyitem; }

    public int getFavor() { return this.favor; }

    public void setFavor(int favor) { this.favor = favor; }

    public float getAttack() { return this.attack; }

    public void setAttack(float attack) { this.attack = attack; }

    public float getVitality() { return this.vitality; }

    public void setVitality(float vitality) { this.vitality = vitality; }

    public Set<Integer> getSpells() { return this.spells; }

    public void setSpells(Set<Integer> spells) { this.spells = spells; }

    public boolean hasSpell(Spell spell) { return this.spells.contains(spell.number); }

    public void addSpell(Spell spell) { this.spells.add(spell.number); }

    public void removeSpell(Spell spell) { this.spells.remove(spell.number); }
}

