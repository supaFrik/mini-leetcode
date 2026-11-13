package jsd.toiyeujava.mini_leetcode.auth.security;

import org.springframework.security.access.AccessDeniedException;

/** Request xác thực bằng Personal Access Token làm việc chỉ được phép trên web (HTTP 403, code PAT_FORBIDDEN). */
public class PatForbiddenException extends AccessDeniedException {
    public static final String CODE = "PAT_FORBIDDEN";

    public PatForbiddenException(String message) {
        super(message);
    }
}
