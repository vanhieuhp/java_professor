package io.coreconcept.datainputstream;

import java.io.DataInputStream;
import java.io.FileInputStream;

public class DataInputExample {

    public static void main(String[] args) {
        try (DataInputStream dis = new DataInputStream(new FileInputStream("data.bin"))) {
            int i1 = dis.readInt();
            int i2 = dis.readInt();
            double d = dis.readDouble();
            boolean b = dis.readBoolean();
            String s = dis.readUTF();

            System.out.println("int: " + i1);
            System.out.println("int: " + i2);
            System.out.println("double: " + d);
            System.out.println("boolean: " + b);
            System.out.println("string: " + s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
