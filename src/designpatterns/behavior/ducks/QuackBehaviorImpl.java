package designpatterns.behavior.ducks;

public class QuackBehaviorImpl implements QuackBehavior {


    @Override
    public void quack() {
        System.out.println( "Quack");
    }
}
