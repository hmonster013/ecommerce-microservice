package org.de013.userservice.service;

import java.time.Instant;

public interface TokenBlacklistService {

    /**
     * Adds a token to the blacklist.
     *
     * @param token The JWT token to be blacklisted.
     * @param expiryTime The expiration time of the token.
     */
    void blacklistToken(String token, Instant expiryTime);

    /**
     * Checks if a token is blacklisted.
     *
     * @param token The JWT token to check.
     * @return true if the token is in the blacklist, false otherwise.
     */
    boolean isTokenBlacklisted(String token);
}