package designpatterns.behavior.weapon.impl;

import designpatterns.behavior.weapon.WeaponBehavior;

public class BowAndArrowBehavior implements WeaponBehavior {
    public void useWeapon() {
        System.out.println("use bow and arrow");
    }
}

