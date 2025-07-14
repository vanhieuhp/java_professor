package designpatterns.behavior.weapon.impl;

import designpatterns.behavior.weapon.WeaponBehavior;

public class AxeBehavior implements WeaponBehavior {
    public void useWeapon() {
        System.out.println("use chopping with an axe");
    }
}
