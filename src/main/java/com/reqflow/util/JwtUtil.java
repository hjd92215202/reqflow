package com.reqflow.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.mindrot.jbcrypt.BCrypt;
import java.util.Date;

public class JwtUtil {
    private static final String SECRET = "reqflow_secret_key_987654321_token";
    private static final long EXPIRATION = 86400000; // 24小时

    public static String generateToken(Long userId, String username) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION))
                .sign(Algorithm.HMAC256(SECRET));
    }

    public static Long verifyTokenAndGetUserId(String token) {
        try {
            var verifier = JWT.require(Algorithm.HMAC256(SECRET)).build();
            var decodedJWT = verifier.verify(token);
            return decodedJWT.getClaim("userId").asLong();
        } catch (Exception e) {
            return null;
        }
    }

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}