package server.command;

import java.util.Arrays;

public class CommandCreator {

    public static Command newCommand(String clientInput) {
        String[] tokens = clientInput.split(" ");
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);

        return new Command(tokens[0], args);
    }

}
