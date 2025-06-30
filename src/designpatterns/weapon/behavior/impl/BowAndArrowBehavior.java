package designpatterns.weapon.behavior.impl;

import designpatterns.weapon.behavior.WeaponBehavior;

public class BowAndArrowBehavior implements WeaponBehavior {
    public void useWeapon() {
        System.out.println("use bow and arrow");
    }
}

