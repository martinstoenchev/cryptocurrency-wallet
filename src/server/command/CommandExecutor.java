package server.command;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.google.gson.Gson;
import server.asset.Asset;
import server.clientaccount.ClientAccount;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommandExecutor {

    private static final int SALT_LENGTH = 32;
    private static final int HASH_LENGTH = 64;
    private static final int PARALLELISM = 1;
    private static final int MEMORY = 15 * 1024;
    private static final int ITERATIONS = 2;

    private static final int USERNAME_INDEX = 0;
    private static final int WALLET_BALANCE_INDEX = 1;
    private static final int WALLET_ASSETS_INDEX = 2;
    private static final int PASSWORD_INDEX = 3;

    private static final String REGISTER_COMMAND = "register";
    private static final String LOGIN_COMMAND = "login";
    private static final String DEPOSIT_MONEY_COMMAND = "deposit-money";
    private static final String LIST_OFFERINGS_COMMAND = "list-offerings";
    private static final String BUY_COMMAND = "buy";
    private static final String SELL_COMMAND = "sell";
    private static final String GET_WALLET_SUMMARY_COMMAND = "get-wallet-summary";
    private static final String GET_WALLET_OVERALL_SUMMARY_COMMAND = "get-wallet-overall-summary";
    private static final String WHO_AM_I_COMMAND = "whoami";
    private static final String DISCONNECT = "disconnect";
    private static final String UNKNOWN_COMMAND = "Unknown command";

    private static final String API_KEY = "41E86874-C048-49AD-8F98-1940424ABB19";
    private static final String API_ENDPOINT_SCHEME = "https";
    private static final String API_ENDPOINT_HOST = "rest.coinapi.io";
    private static final String API_ENDPOINT_PATH = "/v1/assets";
    private static final String API_ENDPOINT_ID_PATH = "/v1/assets/%s";

    private static final String USER_ALREADY_EXIST_MESSAGE = "User with username %s already exists.";
    private static final String USER_SUCCESSFULLY_REGISTERED_MESSAGE = "User with username %s was successfully registered!";
    private static final String SUCCESSFULLY_LOGIN_MESSAGE = "Successfully log in to account %s";
    private static final String INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE = "Incorrectly given arguments";
    private static final String INCORRECT_PASSWORD_MESSAGE = "Incorrect password for username %s";
    private static final String NO_SUCH_USER_MESSAGE = "No such a user with username %s";
    private static final String SUCCESSFULLY_DEPOSIT_MONEY = "Successfully deposit $ %s to user %s";
    private static final String NOT_ENOUGH_MONEY_MESSAGE = "You have not enough money in the wallet";
    private static final String WALLET_SUMMARY_MESSAGE = "You have $ %s";
    private static final String WHO_AM_I_RESPONSE_MESSAGE = "You are %s";
    private static final String DISCONNECTING_FROM_SERVER_MESSAGE = "Disconnecting from the server";

    private static final String OFFERING_ARGUMENT = "--offering=";
    private static final String MONEY_ARGUMENT = "--money=";

    private static Gson GSON = new Gson();

    private final Path pathOfDatabaseFile;
    private final Path pathOfLogFile;

    private Map<String, ClientAccount> users;
    private String currentUser;

    private final Argon2PasswordEncoder encoder;
    private final HttpClient httpClient;

    public CommandExecutor(Path pathOfDatabaseFile, Path pathOfLogFile, HttpClient httpClient) {
        this.pathOfDatabaseFile = pathOfDatabaseFile;
        this.pathOfLogFile = pathOfLogFile;
        this.encoder = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY, ITERATIONS);
        this.httpClient = httpClient;
    }

    public CommandResponse execute(Command cmd) {
        return switch (cmd.command()) {
            case REGISTER_COMMAND                   -> register(cmd.arguments());
            case LOGIN_COMMAND                      -> login(cmd.arguments());
            case DEPOSIT_MONEY_COMMAND              -> depositMoney(cmd.arguments());
            case LIST_OFFERINGS_COMMAND             -> listOfferings();
            case BUY_COMMAND                        -> buy(cmd.arguments());
            case SELL_COMMAND                       -> sell(cmd.arguments());
            case GET_WALLET_SUMMARY_COMMAND         -> getWalletSummary();
            case GET_WALLET_OVERALL_SUMMARY_COMMAND -> getWalletOverallSummary();
            case WHO_AM_I_COMMAND                   -> whoAmI();
            case DISCONNECT                         -> disconnect();
            default                                 -> unknownCommand();
        };
    }

    public CommandResponse execute(Command cmd, Map<String, ClientAccount> users, String currentUser) {
        this.users = users;
        this.currentUser = currentUser;
        return execute(cmd);
    }

    private CommandResponse register(String[] arguments) {
        if (arguments.length != 2) {
            return new CommandResponse(StatusCode.FAIL, CommandType.REGISTER, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        String username = arguments[0];
        String password = arguments[1];

        try (var reader = Files.newBufferedReader(pathOfDatabaseFile, StandardCharsets.UTF_8)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(";");
                if (username.equals(tokens[USERNAME_INDEX])) {
                    return new CommandResponse(StatusCode.FAIL, CommandType.REGISTER, String.format(USER_ALREADY_EXIST_MESSAGE, username));
                }
            }
        } catch (IOException e) { // TODO: CREATE CUSTOM EXCEPTION FOR THIS CASE + WRITING IN LOGS
            throw new IllegalStateException("A problem occurred while reading from the database file", e);
        }

        String encodedPassword = encoder.encode(password);

        try (var writer = Files.newBufferedWriter(pathOfDatabaseFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(username + ";0;" + "{ };" + encodedPassword + System.lineSeparator());
            writer.flush();
        } catch (IOException e) { // TODO: CREATE CUSTOM EXCEPTION FOR THIS CASE + WRITING IN LOGS
            throw new IllegalStateException("A problem occurred while writing to the database file", e);
        }

        return new CommandResponse(StatusCode.OK, CommandType.REGISTER, String.format(USER_SUCCESSFULLY_REGISTERED_MESSAGE, username));
    }

    private CommandResponse login(String[] arguments) {
        if (arguments.length != 2) {
            return new CommandResponse(StatusCode.FAIL, CommandType.LOGIN, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        String username = arguments[0];
        String password = arguments[1];
        CommandResponse response = new CommandResponse(StatusCode.FAIL, CommandType.LOGIN, String.format(NO_SUCH_USER_MESSAGE, username));

        try (var reader = Files.newBufferedReader(pathOfDatabaseFile, StandardCharsets.UTF_8)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(";");
                if (username.equals(tokens[0])) {
                    if (encoder.matches(password, tokens[PASSWORD_INDEX])) {
                        response = new CommandResponse(StatusCode.OK, CommandType.LOGIN, String.format(SUCCESSFULLY_LOGIN_MESSAGE, username));
                    } else {
                        response = new CommandResponse(StatusCode.FAIL, CommandType.LOGIN, String.format(INCORRECT_PASSWORD_MESSAGE, username));
                    }
                }
            }
        } catch (IOException e) { // TODO: CREATE CUSTOM EXCEPTION FOR THIS CASE + WRITING IN LOGS
            throw new IllegalStateException("A problem occurred while reading from the database file", e);
        }

        return response;
    }

    private CommandResponse depositMoney(String[] arguments) {
        if (arguments.length != 1) {
            return new CommandResponse(StatusCode.FAIL, CommandType.DEPOSIT_MONEY, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        double amount;
        try {
            amount = Double.parseDouble(arguments[0]);
        } catch (NumberFormatException e) {
            return new CommandResponse(StatusCode.FAIL, CommandType.DEPOSIT_MONEY, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        users.get(currentUser).depositMoney(amount);
        writeMoneyToDatabaseFile(currentUser, users.get(currentUser).getWalletBalance());
        return new CommandResponse(StatusCode.OK, CommandType.DEPOSIT_MONEY, String.format(SUCCESSFULLY_DEPOSIT_MONEY, arguments[0], currentUser));
    }

    private CommandResponse listOfferings() {
        HttpResponse<String> response;

        try {
            URI uri = new URI(API_ENDPOINT_SCHEME, API_ENDPOINT_HOST, API_ENDPOINT_PATH, null);
            response = getResponse(uri);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(); // TODO: TO CREATE APPROPRIATE EXCEPTION !!!
        } catch (IOException | InterruptedException e) {
            throw new UnsupportedOperationException();
        }

        if (response.statusCode() == HttpsURLConnection.HTTP_OK) {
            Type type = new TypeToken<List<Asset>>() {}.getType();

            GSON = new GsonBuilder().registerTypeAdapter(type, new Asset.AssetListDeserializer()).create();
            List<Asset> list = GSON.fromJson(response.body(), type);

            return new CommandResponse(StatusCode.OK, CommandType.LIST_OFFERINGS, list.toString());
        }

        return new CommandResponse(StatusCode.FAIL, CommandType.LIST_OFFERINGS, "Failure with list offerings");
    }

    private CommandResponse buy(String[] arguments) {
        if (arguments.length != 2) {
            return new CommandResponse(StatusCode.FAIL, CommandType.BUY, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        String offering = getArgument(arguments[0], OFFERING_ARGUMENT);
        double amount;

        try {
            amount = Double.parseDouble(getArgument(arguments[1], MONEY_ARGUMENT));
        } catch (NumberFormatException e) {
            return new CommandResponse(StatusCode.FAIL, CommandType.DEPOSIT_MONEY, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        HttpResponse<String> response;

        try {
            URI uri = new URI(API_ENDPOINT_SCHEME, API_ENDPOINT_HOST, API_ENDPOINT_ID_PATH.formatted(offering), null);
            response = getResponse(uri);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(); // TODO: TO CREATE APPROPRIATE EXCEPTION !!!
        } catch (IOException | InterruptedException e) {
            throw new UnsupportedOperationException();
        }

        if (response.statusCode() == HttpsURLConnection.HTTP_OK) {
            Type type = new TypeToken<List<Asset>>() {}.getType();
            List<Asset> asset = GSON.fromJson(response.body(), type);

            if (users.get(currentUser).buyAsset(offering, amount, asset.get(0).getPriceUSD())) {
                writeAssetToDatabaseFile(currentUser, users.get(currentUser).getWalletBalance(), users.get(currentUser).getAssets());
                return new CommandResponse(StatusCode.OK, CommandType.BUY, "Asset successfully bought");
            } else {
                return new CommandResponse(StatusCode.FAIL, CommandType.BUY, "Not enough money in the wallet");
            }
        }

        return new CommandResponse(StatusCode.FAIL, CommandType.BUY, "Failure with asset buying");
    }

    private CommandResponse sell(String[] arguments) {
        if (arguments.length != 1) {
            return new CommandResponse(StatusCode.FAIL, CommandType.SELL, INCORRECTLY_GIVEN_ARGUMENTS_MESSAGE);
        }

        String offering = getArgument(arguments[0], OFFERING_ARGUMENT);

        HttpResponse<String> response;

        try {
            URI uri = new URI(API_ENDPOINT_SCHEME, API_ENDPOINT_HOST, API_ENDPOINT_ID_PATH.formatted(offering), null);
            response = getResponse(uri);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(); // TODO: TO CREATE APPROPRIATE EXCEPTION !!!
        } catch (IOException | InterruptedException e) {
            throw new UnsupportedOperationException();
        }

        if (response.statusCode() == HttpsURLConnection.HTTP_OK) {
            Type type = new TypeToken<List<Asset>>() {}.getType();
            List<Asset> asset = GSON.fromJson(response.body(), type);

            if (users.get(currentUser).sellAsset(offering, asset.get(0).getPriceUSD())) {
                writeAssetToDatabaseFile(currentUser, users.get(currentUser).getWalletBalance(), users.get(currentUser).getAssets());
                return new CommandResponse(StatusCode.OK, CommandType.SELL, "Asset successfully sold");
            }
        }

        return new CommandResponse(StatusCode.FAIL, CommandType.BUY, "Failure with asset selling");
    }

    private CommandResponse getWalletSummary() {
        String message = String.format(WALLET_SUMMARY_MESSAGE, users.get(currentUser).getWalletBalance());
        String assets = users.get(currentUser).getAssets().replace(",", System.lineSeparator());
        return new CommandResponse(StatusCode.OK, CommandType.GET_WALLET_SUMMARY, message + System.lineSeparator() + assets);
    }

    private CommandResponse getWalletOverallSummary() {
        throw new UnsupportedOperationException();
    }

    private CommandResponse whoAmI() {
        return new CommandResponse(StatusCode.OK, CommandType.WHO_AM_I, WHO_AM_I_RESPONSE_MESSAGE.formatted(currentUser));
    }

    private CommandResponse disconnect() {
        return new CommandResponse(StatusCode.OK, CommandType.DISCONNECT, DISCONNECTING_FROM_SERVER_MESSAGE);
    }

    private CommandResponse unknownCommand() {
        return new CommandResponse(StatusCode.FAIL, CommandType.UNKNOWN_COMMAND, UNKNOWN_COMMAND);
    }

    private String getArgument(String text, String argument) {
        return text.substring(argument.length());
    }

    private void writeMoneyToDatabaseFile(String username, double balance) {
        String tempFileName = "tempFile.txt";
        try {
            Files.move(pathOfDatabaseFile, Path.of(tempFileName));
            File oldContentFile = new File(String.valueOf(pathOfDatabaseFile));
            oldContentFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (var reader = Files.newBufferedReader(Path.of(tempFileName));
             var writer = Files.newBufferedWriter(pathOfDatabaseFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(";");
                if (!tokens[0].equals(username)) {
                    writer.write(line + System.lineSeparator());
                    writer.flush();
                } else {
                    writer.write(username + ";" + balance + ";" + tokens[WALLET_ASSETS_INDEX] + ";" + tokens[PASSWORD_INDEX] + System.lineSeparator());
                    writer.flush();
                }
            }
        } catch (IOException e) { // TODO: CREATE CUSTOM EXCEPTION FOR THIS CASE + WRITING IN LOGS
            throw new IllegalStateException("A problem occurred while writing to the database file", e);
        }

        File oldContentFile = new File(tempFileName);
        oldContentFile.delete();
    }

    private void writeAssetToDatabaseFile(String username, double balance, String assets) {
        String tempFileName = "tempFile.txt";
        try {
            Files.move(pathOfDatabaseFile, Path.of(tempFileName));
            File oldContentFile = new File(String.valueOf(pathOfDatabaseFile));
            oldContentFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (var reader = Files.newBufferedReader(Path.of(tempFileName));
             var writer = Files.newBufferedWriter(pathOfDatabaseFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(";");
                if (!tokens[0].equals(username)) {
                    writer.write(line + System.lineSeparator());
                    writer.flush();
                } else {
                    writer.write(username + ";" + balance + ";" + assets + ";" + tokens[PASSWORD_INDEX] + System.lineSeparator());
                    writer.flush();
                }
            }
        } catch (IOException e) { // TODO: CREATE CUSTOM EXCEPTION FOR THIS CASE + WRITING IN LOGS
            throw new IllegalStateException("A problem occurred while writing to the database file", e);
        }

        File oldContentFile = new File(tempFileName);
        oldContentFile.delete();
    }

    private HttpResponse<String> getResponse(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().header("X-CoinAPI-Key", API_KEY).uri(uri).build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
