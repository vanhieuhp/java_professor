package java_effective.eliminate_obsolete.goold;

import java.util.Arrays;
import java.util.EmptyStackException;

public class StackGood<E> {

    private Object[] elements;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;

    public StackGood() {
        this.elements = new Object[DEFAULT_CAPACITY];
    }

    public void push(E e) {
        ensureCapacity();
        elements[size++] = e; // array stores the reference
    }

    public E popBad() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        E result = (E) elements[--size];
        elements[size] = null;
        return result;
    }

    public void ensureCapacity() {
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, 2 * size + 1);
        }
    }

}
