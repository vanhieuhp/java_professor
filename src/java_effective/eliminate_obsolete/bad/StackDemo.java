package java_effective.eliminate_obsolete.bad;

public class StackDemo {
    public static void main(String[] args) {
        StackBad<byte[]> leakStack = new StackBad<>();

        // push 1 million 1KB objects on the stack
        for (int i = 0; i < 1_000_000; i++) {
            leakStack.push(new byte[1024]); // each is ~1kb
        }

        // pop all of them
        for (int i = 0; i < 1_000_000; i++) {
            leakStack.popBad(); // size decrements, but array still holds all 1M references
        }

        // Result: those 1 million byte[] objects are STILL in memory!
        // The Stack "logically" is empty, but physically holds 1GB of dead objects.
        // System.gc() won't collect them — they're reachable via the array.
        System.out.println("Leaked ~1GB even though stack is 'empty'");
    }
}
