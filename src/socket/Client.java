package socket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 1234;

        Socket socket = new Socket(host, port);
        System.out.println("Connected to server!");

        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter output = new PrintWriter(socket.getOutputStream(), true);

        output.println("Hello from Client!");

        String response = input.readLine();

        System.out.println("Received from server: " + response);
        socket.close();
    }
}
