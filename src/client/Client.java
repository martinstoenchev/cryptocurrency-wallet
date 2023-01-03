package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client {

    private static final int SERVER_PORT = 7777;
    private static final int BUFFER_SIZE = 1024;
    private static final String INVALID_INPUT = "invalid input";
    private static final String SERVER_HOST = "localhost";

    private static ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

    public static void main(String[] args) {

        try (SocketChannel socketChannel = SocketChannel.open();
             Scanner scanner = new Scanner(System.in)) {

            socketChannel.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT));
            System.out.println("Connected to the server");

            while (true) {
                System.out.print("$ ");
                String command = scanner.nextLine();

                if (command == null || command.trim().isEmpty() || System.lineSeparator().equals(command)) {
                    System.out.println(INVALID_INPUT);
                    continue;
                }

                buffer.clear();
                buffer.put(command.getBytes());
                buffer.flip();

                try {
                    socketChannel.write(buffer);
                } catch (IOException e) {
                    System.out.println("There is a problem with information sending. Please, try again!");
                    continue;
                }

                buffer.clear();
                try {
                    socketChannel.read(buffer);
                } catch (IOException e) {
                    System.out.println("There is a problem with information receiving. Please, try again!");
                    continue;
                }
                buffer.flip();

                byte[] byteArray = new byte[buffer.remaining()];
                buffer.get(byteArray);
                String reply = new String(byteArray, StandardCharsets.UTF_8);

                System.out.println(reply);

                if (command.equals("disconnect")) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("There is a problem with the network communication", e);
        }

    }

}
