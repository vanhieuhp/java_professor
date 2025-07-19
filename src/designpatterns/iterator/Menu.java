package designpatterns.iterator;

import java.util.Iterator;

public interface Menu {

    Iterator<MenuItem> createIterator();
}
