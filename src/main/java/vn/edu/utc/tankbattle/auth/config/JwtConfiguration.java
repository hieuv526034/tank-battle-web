package vn.edu.utc.tankbattle.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import vn.edu.utc.tankbattle.auth.repository.AuthTokenRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/**
 * RS256 với cặp khóa RSA. Encoder giữ private key; decoder chỉ dùng public key. Production bắt buộc
 * cung cấp khóa ổn định từ secret/file ngoài repository.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfiguration {
    /**
     * Chuẩn bị cặp khóa RSA dùng chung cho encoder/decoder khi ứng dụng khởi động. Nếu cấu hình
     * đường dẫn PEM thì đọc public key X509 và private key PKCS8, kiểm tra cùng modulus và tối
     * thiểu 2048 bit. Test được tạo khóa tạm khi đã bật ephemeralKeys; prod thiếu khóa ổn định sẽ
     * dừng khởi động để tránh đổi khóa sau mỗi lần restart.
     *
     * @param properties cấu hình tank.auth, chứa đường dẫn resource và keyId
     * @param resources bộ đọc resource file/classpath của Spring
     * @param environment các profile dùng để chặn khóa tạm ở prod
     * @return JWK chứa cặp khóa, chỉ encoder sử dụng phần private key
     */
    @Bean
    RSAKey signingKey(AuthProperties properties, ResourceLoader resources, Environment environment)
            throws GeneralSecurityException, IOException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        boolean ephemeral = properties.privateKey().isBlank() || properties.publicKey().isBlank();
        if (ephemeral) {
            // Bao gồm cả prod mặc định khi không truyền spring.profiles.active.
            boolean production = environment.acceptsProfiles(Profiles.of("prod"));
            if (!properties.ephemeralKeys() || production) {
                throw new IllegalStateException("Phải cấu hình JWT_PRIVATE_KEY và JWT_PUBLIC_KEY.");
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        } else {
            try (InputStream publicInput =
                            resources.getResource(properties.publicKey()).getInputStream();
                    InputStream privateInput =
                            resources.getResource(properties.privateKey()).getInputStream()) {
                publicKey = RsaKeyConverters.x509().convert(publicInput);
                privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            }
        }
        if (publicKey == null
                || privateKey == null
                || publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("Khóa JWT không hợp lệ hoặc không cùng một cặp RSA.");
        }
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .build();
    }

    /**
     * Tạo bộ ký access token RS256 từ cặp khóa đã kiểm tra. TokenService cung cấp claims và keyId
     * cho encoder; bean này chỉ thực hiện việc mã hóa/ký JWT.
     */
    @Bean
    JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    /**
     * Tạo bộ kiểm chứng Bearer JWT bằng public key RS256 và chuỗi validator. Kiểm tra chữ ký,
     * issuer/thời gian, audience, loại access, username, sid và phiên còn hiệu lực trong DB. Lookup
     * sid giúp logout/replay thu hồi access token ngay ở request sau. Lỗi DB được phân loại 503;
     * claim/token sai là AUTH_005.
     *
     * @return decoder được Resource Server gọi trước khi request tới controller
     */
    @Bean
    JwtDecoder jwtDecoder(RSAKey key, AuthProperties properties, AuthTokenRepository sessions)
            throws com.nimbusds.jose.JOSEException {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()),
                        jwt -> {
                            try {
                                String sessionId = jwt.getClaimAsString("sid");
                                boolean valid =
                                        jwt.getAudience() != null
                                                && jwt.getAudience().contains(properties.audience())
                                                && "access"
                                                        .equals(jwt.getClaimAsString("token_kind"))
                                                && jwt.getClaimAsString("username") != null
                                                && jwt.getExpiresAt() != null
                                                && jwt.getIssuedAt() != null
                                                && sessionId != null
                                                && sessions.active(
                                                        sessionId,
                                                        Long.parseLong(jwt.getSubject()));
                                if (valid) {
                                    return OAuth2TokenValidatorResult.success();
                                }
                            } catch (DataAccessException unavailable) {
                                // Để failure handler trả 503 custom, không đổ lỗi cho token của
                                // người chơi.
                                throw new InternalAuthenticationServiceException(
                                        ValidateCode.SYS_SERVICE_UNAVAILABLE.getMessageKey(),
                                        unavailable);
                            } catch (IllegalArgumentException invalidClaim) {
                                // Claim sai kiểu/subject sai định dạng cũng bị từ chối.
                            }
                            return OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error(
                                            ValidateCode.AUTH_INVALID_TOKEN.getCode(),
                                            ValidateCode.AUTH_INVALID_TOKEN.getMessageKey(),
                                            null));
                        }));
        return decoder;
    }
}
