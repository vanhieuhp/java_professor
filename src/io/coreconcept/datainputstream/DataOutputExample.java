package io.coreconcept.datainputstream;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

public class DataOutputExample {

    public static void main(String[] args) {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream("data.bin"))) {
            dos.writeInt(42);
            dos.writeInt(1000);
            dos.writeDouble(3.14159);
            dos.writeBoolean(true);
            dos.writeUTF("Hello Java I/O");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
