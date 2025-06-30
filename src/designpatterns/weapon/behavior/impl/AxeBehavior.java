package designpatterns.weapon.behavior.impl;

import designpatterns.weapon.behavior.WeaponBehavior;

public class AxeBehavior implements WeaponBehavior {
    public void useWeapon() {
        System.out.println("use chopping with an axe");
    }
}
