package designpatterns.behavior;

import designpatterns.behavior.weapon.impl.BowAndArrowBehavior;
import designpatterns.behavior.weapon.impl.SwordBehavior;
import designpatterns.behavior.character.Character;

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
