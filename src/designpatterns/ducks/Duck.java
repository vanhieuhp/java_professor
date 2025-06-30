package designpatterns.ducks;

public abstract class Duck {

   FlyBehavior flyBehavior;
   QuackBehavior quackBehavior;

   public abstract void display();

   public void performFly(){
       flyBehavior.fly();
   }

   public void performQuack(){
       quackBehavior.quack();
   }

   public void swim(){
       System.out.println("All ducks float, even decoys!");
   }

   public void setFlyBehavior(FlyBehavior flyBehavior) {
       this.flyBehavior = flyBehavior;
   }

   public void setQuackBehavior(QuackBehavior quackBehavior) {
       this.quackBehavior = quackBehavior;
   }

   public void setPerformQuack(QuackBehavior quackBehavior) {
       this.quackBehavior = quackBehavior;
   }
}
