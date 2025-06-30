package designpatterns.weapon.character;

import designpatterns.weapon.behavior.WeaponBehavior;

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
