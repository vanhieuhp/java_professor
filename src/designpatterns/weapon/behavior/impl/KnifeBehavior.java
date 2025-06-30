package designpatterns.weapon.behavior.impl;

import designpatterns.weapon.behavior.WeaponBehavior;

public class KnifeBehavior implements WeaponBehavior {

    public void useWeapon() {
        System.out.println("use knife");
    }
}
