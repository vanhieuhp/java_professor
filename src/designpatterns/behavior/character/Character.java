package designpatterns.behavior.character;

import designpatterns.behavior.weapon.WeaponBehavior;

public class Character {

    WeaponBehavior weapon;

    public Character() {
    }

    public void setWeapon(WeaponBehavior weapon) {
        this.weapon = weapon;
    }

    public void fight() {
        weapon.useWeapon();
    }
}
