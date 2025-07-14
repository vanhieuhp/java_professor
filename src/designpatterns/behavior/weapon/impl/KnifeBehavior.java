package designpatterns.behavior.weapon.impl;

import designpatterns.behavior.weapon.WeaponBehavior;

public class KnifeBehavior implements WeaponBehavior {

    public void useWeapon() {
        System.out.println("use knife");
    }
}
