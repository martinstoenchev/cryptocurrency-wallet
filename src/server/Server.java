package server;

import server.clientaccount.ClientAccount;
import server.command.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int SERVER_PORT = 7777;
    private static final int BUFFER_SIZE = 1024;
    private static final String SERVER_HOST = "localhost";

    private int port;
    private boolean isServerWorking;

    private ByteBuffer buffer;
    private Selector selector;

    private final CommandExecutor commandExecutor;

    private final Map<String, ClientAccount> users;
    private final Set<CommandType> loginRequiredCommands;

    public Server(CommandExecutor commandExecutor, Map<String, ClientAccount> users) {
        this(SERVER_PORT, commandExecutor, users);
    }

    public Server(int port, CommandExecutor commandExecutor, Map<String, ClientAccount> users) {
        this.port = port;
        this.commandExecutor = commandExecutor;
        this.users = users;
        this.loginRequiredCommands = getLoginRequiredCommands();
    }

    public void start() {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            selector = Selector.open();
            configureServerSocketChannel(serverSocketChannel);
            buffer = ByteBuffer.allocate(BUFFER_SIZE);
            isServerWorking = true;

            while (isServerWorking) {
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    continue;
                }

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isReadable()) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        String clientInput;

                        try {
                            clientInput = getClientInput(clientChannel);
                        } catch (IOException e) {
                            writeClientOutput(clientChannel, "The server did not receive your input. Please, try again!");
                            continue;
                        }

                        if (clientInput == null) {
                            continue;
                        }

                        CommandType userCommand = getUserCommand(clientInput);
                        String username = getUsernameFromInput(clientInput);

                        if (userCommand == CommandType.REGISTER) {
                            if (key.attachment() == null) {
                                CommandResponse output = commandExecutor.execute(CommandCreator.newCommand(clientInput));

                                if (output.statusCode() == StatusCode.OK) {
                                    ClientAccount account = new ClientAccount(username);
                                    users.put(username, account);
                                }
                                writeClientOutput(clientChannel, output.message());
                            } else {
                                writeClientOutput(clientChannel, "You cannot create another registration while you are logged in");
                            }
                        } else if (userCommand == CommandType.LOGIN) {
                            if (users.get(username) != null && users.get(username).isLoggedIn()) {
                                writeClientOutput(clientChannel, "User " + username + " is already logged in");
                            } else {
                                CommandResponse output = commandExecutor.execute(CommandCreator.newCommand(clientInput));
                                if (output.statusCode() == StatusCode.OK) {
                                    users.get(username).setLoggedIn(true);
                                    key.attach(username);
                                }
                                writeClientOutput(clientChannel, output.message());
                            }
                        } else if (loginRequiredCommands.contains(userCommand)) {
                            String currentUser = (String) key.attachment();
                            if (currentUser == null) {
                                writeClientOutput(clientChannel, "You are not logged in");
                            } else {
                                CommandResponse output = commandExecutor.execute(CommandCreator.newCommand(clientInput), users, currentUser);
                                if (output.commandType() == CommandType.LIST_OFFERINGS) {
                                    String[] tokens = output.message().split(",");
                                    StringBuilder sb = new StringBuilder();

                                    for (String token : tokens) {
                                        if (sb.toString().getBytes(StandardCharsets.UTF_8).length + token.getBytes(StandardCharsets.UTF_8).length <= BUFFER_SIZE){
                                            sb.append(token);
                                        } else {
                                            break;
                                        }
                                    }
                                    writeClientOutput(clientChannel, sb.toString());
                                } else {
                                    writeClientOutput(clientChannel, output.message());
                                }
                            }
                        } else {
                            CommandResponse output = commandExecutor.execute(CommandCreator.newCommand(clientInput));
                            writeClientOutput(clientChannel, output.message());
                        }
                    } else if (key.isAcceptable()) {
                        accept(key);
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start server", e);
        }
    }

    public void stop() {
        isServerWorking = false;
        if (selector.isOpen()) {
            selector.wakeup();
        }
    }

    private void configureServerSocketChannel(ServerSocketChannel serverSocketChannel) throws IOException {
        serverSocketChannel.bind(new InetSocketAddress(SERVER_HOST, SERVER_PORT));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel accept = serverSocketChannel.accept();
        accept.configureBlocking(false);
        accept.register(selector, SelectionKey.OP_READ);
    }

    private String getClientInput(SocketChannel clientChannel) throws IOException {
        buffer.clear();

        int readBytes = clientChannel.read(buffer);
        if (readBytes < 0) {
            clientChannel.close();
            return null;
        }

        buffer.flip();
        byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);

        return new String(byteArray, StandardCharsets.UTF_8);
    }

    private void writeClientOutput(SocketChannel clientChannel, String output) throws IOException {
        buffer.clear();
        buffer.put(output.getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        try {
            clientChannel.write(buffer);
        } catch (IOException e) {
            // TODO: write this to log file
        }
    }

    private CommandType getUserCommand(String clientInput) {
        String[] tokens = clientInput.split(" ");

        return switch (tokens[0]) {
            case "register"                   -> CommandType.REGISTER;
            case "login"                      -> CommandType.LOGIN;
            case "deposit-money"              -> CommandType.DEPOSIT_MONEY;
            case "list-offerings"             -> CommandType.LIST_OFFERINGS;
            case "buy"                        -> CommandType.BUY;
            case "sell"                       -> CommandType.SELL;
            case "get-wallet-summary"         -> CommandType.GET_WALLET_SUMMARY;
            case "get-wallet-overall-summary" -> CommandType.GET_WALLET_OVERALL_SUMMARY;
            case "whoami"                     -> CommandType.WHO_AM_I;
            case "disconnect"                 -> CommandType.DISCONNECT;
            default                           -> CommandType.UNKNOWN_COMMAND;
        };
    }

    private String getUsernameFromInput(String clientInput) {
        String[] tokens = clientInput.split(" ");

        return tokens.length > 1 ? tokens[1] : null;
    }

    private Set<CommandType> getLoginRequiredCommands() {
        return Set.of(CommandType.DEPOSIT_MONEY,
                CommandType.LIST_OFFERINGS,
                CommandType.BUY,
                CommandType.SELL,
                CommandType.GET_WALLET_SUMMARY,
                CommandType.GET_WALLET_OVERALL_SUMMARY,
                CommandType.WHO_AM_I);
    }

    public static void main(String[] args) {
        String usersFileName = "users.txt";

        Path pathOfDatabaseFile = Path.of(usersFileName);
        Path pathOfLogFile = Path.of("logs.txt");

        File file = new File(usersFileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<String, ClientAccount> users = new HashMap<>();

        try (var reader = Files.newBufferedReader(pathOfDatabaseFile, StandardCharsets.UTF_8)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(";");
                String username = tokens[0];
                double balance = Double.parseDouble(tokens[1]);
                String assets = tokens[2];
                users.put(username, new ClientAccount(username, balance, assets));
            }

        } catch (IOException e) {
            System.out.println("CANNOT RECEIVE USERS INFORMATION");
            e.printStackTrace();
        }

        ExecutorService executorService = Executors.newCachedThreadPool();
        HttpClient httpClient = HttpClient.newBuilder().executor(executorService).build();

        Server server = new Server(new CommandExecutor(pathOfDatabaseFile, pathOfLogFile, httpClient), users);
        server.start();
    }

}
