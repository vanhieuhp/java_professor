package io.coreconcept;

import java.io.ByteArrayInputStream;
import java.io.PushbackInputStream;

public class PushBackExample {

    public static void main(String[] args) {
        byte[] data = "abcdef".getBytes();
        try (PushbackInputStream pbis = new PushbackInputStream(new ByteArrayInputStream(data))) {
            int ch1 = pbis.read(); // 'a'
            int ch2 = pbis.read(); // 'b'

            System.out.println((char) ch1);
            System.out.println((char) ch2);

            pbis.unread(ch1);

            int ch3 = pbis.read();
            System.out.println((char) ch3);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
