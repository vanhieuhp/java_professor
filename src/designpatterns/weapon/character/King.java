package designpatterns.weapon.character;

import designpatterns.weapon.behavior.impl.SwordBehavior;

public class King extends Character {
    public King() {
        setWeapon(new SwordBehavior());
    }

    @Override
    public void fight() {
        System.out.println("King is fighting");
        weapon.useWeapon();
    }
}
