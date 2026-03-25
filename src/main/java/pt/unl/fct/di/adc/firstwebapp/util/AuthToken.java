package pt.unl.fct.di.adc.firstwebapp.util;

import java.util.UUID;

public class AuthToken {

    public String tokenId;
    public String userId;
    public String role;
    public long issuedAt;
    public long expiresAt;

    // Default token validity: 1 hour (in milliseconds)
    public static final long TOKEN_VALIDITY = 1000 * 60 * 60;

    public AuthToken() {
    }

    public AuthToken(String userId, String role) {
        this.tokenId = UUID.randomUUID().toString();
        this.userId = userId;
        this.role = role;
        this.issuedAt = System.currentTimeMillis();
        this.expiresAt = this.issuedAt + TOKEN_VALIDITY;
    }
}
