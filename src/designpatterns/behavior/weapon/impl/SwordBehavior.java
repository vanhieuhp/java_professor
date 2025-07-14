package designpatterns.behavior.weapon.impl;

import designpatterns.behavior.weapon.WeaponBehavior;

public class SwordBehavior implements WeaponBehavior {
    public void useWeapon() {
        System.out.println("use cutting with a sword");
    }
}