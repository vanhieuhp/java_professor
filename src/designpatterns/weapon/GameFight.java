package designpatterns.weapon;

import designpatterns.weapon.behavior.impl.BowAndArrowBehavior;
import designpatterns.weapon.behavior.impl.SwordBehavior;
import designpatterns.weapon.character.Character;

public class GameFight {
    public static void main(String[] args) {
        Character player = new Character();
        player.setWeapon(new SwordBehavior());
        player.fight();

        System.out.println("--------------------------------");
        player.setWeapon(new BowAndArrowBehavior());
        player.fight();

        System.out.println("--------------------------------");

    }
}
