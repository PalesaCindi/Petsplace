/** An error with an HTTP status code and a friendly message shown on the website. */
final class ApiError extends RuntimeException {
    final int status;

    ApiError(int status, String message) {
        super(message);
        this.status = status;
    }

    static ApiError badRequest(String message) { return new ApiError(400, message); }
}
