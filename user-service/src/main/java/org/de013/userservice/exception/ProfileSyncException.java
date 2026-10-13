package org.de013.userservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.Map;

@Getter
public class ProfileSyncException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ProfileSyncException(HttpStatus status, String code) {
        this(status, code, Map.of());
    }

    public ProfileSyncException(HttpStatus status, String code, Map<String, String> fields) {
        super(code);
        this.status = status;
        this.code = code;
        this.fields = Map.copyOf(fields);
    }
}
