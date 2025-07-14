package designpatterns.behavior.character;

import designpatterns.behavior.weapon.impl.KnifeBehavior;

public class Queen extends Character {
    public Queen() {
        setWeapon(new KnifeBehavior());
    }

    @Override
    public void fight() {
        System.out.println("Queen is fighting");
        weapon.useWeapon();
    }
}