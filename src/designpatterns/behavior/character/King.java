package designpatterns.behavior.character;

import designpatterns.behavior.weapon.impl.SwordBehavior;

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
