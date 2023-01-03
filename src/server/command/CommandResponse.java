package server.command;

public record CommandResponse(StatusCode statusCode, CommandType commandType, String message) {
}
