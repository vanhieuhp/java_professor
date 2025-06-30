package designpatterns.weapon.character;

import designpatterns.weapon.behavior.impl.SwordBehavior;

public class Knight extends Character {
    public Knight() {
        setWeapon(new SwordBehavior());
    }

    @Override
    public void fight() {
        System.out.println("Knight is fighting");
        weapon.useWeapon();
    }
}
