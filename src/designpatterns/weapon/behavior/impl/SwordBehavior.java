package designpatterns.weapon.behavior.impl;

import designpatterns.weapon.behavior.WeaponBehavior;

public class SwordBehavior implements WeaponBehavior {
    public void useWeapon() {
        System.out.println("use cutting with a sword");
    }
}