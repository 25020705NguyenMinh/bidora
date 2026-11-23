package vn.edu.auction.server.exception;

/** Không gửi được email qua dịch vụ email API. */
public class EmailSendException extends RuntimeException {

    public EmailSendException(Throwable cause) {
        super("Không thể gửi email lúc này, vui lòng thử lại sau", cause);
    }
}
