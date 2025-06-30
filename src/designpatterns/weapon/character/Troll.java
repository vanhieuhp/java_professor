package designpatterns.weapon.character;

import designpatterns.weapon.behavior.impl.AxeBehavior;

public class Troll extends Character {
    public Troll() {
        setWeapon(new AxeBehavior());
    }

    @Override
    public void fight() {
        System.out.println("Troll is fighting");
        weapon.useWeapon();
    }
}
