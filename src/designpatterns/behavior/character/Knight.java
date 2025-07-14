package designpatterns.behavior.character;

import designpatterns.behavior.weapon.impl.SwordBehavior;

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
