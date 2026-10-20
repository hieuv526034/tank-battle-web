package vn.edu.utc.tankbattle.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.utc.tankbattle.auth.config.AuthProperties;
import vn.edu.utc.tankbattle.auth.dto.response.AuthTokenResponse;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.exception.AuthFailure;
import vn.edu.utc.tankbattle.auth.model.AuthSession;
import vn.edu.utc.tankbattle.auth.model.RefreshTokenRow;
import vn.edu.utc.tankbattle.auth.model.TokenGrant;
import vn.edu.utc.tankbattle.auth.repository.AuthTokenRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.event.ApiSessionsRevokedEvent;

/**
 * Quản lý phiên API và vòng đời access/refresh: cấp mới, xoay refresh, thu hồi và dọn phiên hết
 * hạn. Controller gọi sau xác thực; JwtConfiguration cung cấp bộ ký còn repository giữ SQL
 * phiên/token.
 *
 * <p>Token thô chỉ tồn tại trong response/cookie; DB giữ SHA-256 của refresh ngẫu nhiên. Khóa
 * auth_session trước khi đọc lại refresh để hai lần rotate không cùng thành công. Sự kiện revoke
 * được listener xử lý sau commit để đồng bộ hiệu lực JWT với việc đóng socket.
 */
@Service
public class TokenService {
    private final AuthTokenRepository tokens;
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    /**
     * Nhận repository phiên/token, bộ ký JWT, cấu hình TTL và publisher sự kiện thu hồi. Repository
     * giữ SQL; service quyết định transaction, rotation và thời điểm phát sự kiện.
     */
    public TokenService(
            AuthTokenRepository tokens,
            JwtEncoder encoder,
            AuthProperties properties,
            ApplicationEventPublisher events) {
        this.tokens = tokens;
        this.encoder = encoder;
        this.properties = properties;
        this.events = events;
    }

    /**
     * Mở một auth_session mới cho tài khoản enabled trong cùng transaction với refresh token đầu
     * tiên. Hạn refresh xác định tuổi thọ tối đa của phiên; grant tạo access JWT và refresh ngẫu
     * nhiên để controller đặt cookie.
     *
     * @param user entity đã xác thực bởi mật khẩu hoặc session web
     * @return access response và refresh thô chỉ dành cho bước ghi cookie
     */
    @Transactional
    public TokenGrant issue(UserEntity user) {
        if (!user.isEnabled()) {
            throw invalid();
        }
        Instant deadline = Instant.now().plus(properties.refreshTtl());
        String sessionId = UUID.randomUUID().toString();
        tokens.createSession(sessionId, user.getId(), deadline);
        return grant(
                new AuthSession(
                        sessionId,
                        user.getId(),
                        user.getUsername(),
                        user.getRole().name(),
                        true,
                        deadline,
                        null));
    }

    /**
     * Đổi refresh token chưa được sử dụng sang một cặp token mới, giữ nguyên family/sid. Đọc token
     * để tìm sid, khóa session rồi đọc lại token nhằm tránh hai request cùng rotate thành công. Nếu
     * token đã consumed thì revoke cả phiên và phát sự kiện đóng socket; noRollbackFor=AuthFailure
     * giữ việc revoke được commit trước khi trả AUTH_007. Lỗi ký/DB ngoài dự kiến vẫn rollback để
     * không consume token mà mất cặp token trả về.
     *
     * @param rawToken refresh thô lấy từ cookie HttpOnly
     * @return cặp token mới của cùng phiên
     */
    @Transactional(noRollbackFor = AuthFailure.class)
    public TokenGrant refresh(String rawToken) {
        String tokenId = tokenId(rawToken);
        RefreshTokenRow initial = tokens.findRefresh(tokenId).orElseThrow(TokenService::invalid);
        AuthSession session =
                tokens.lockSession(initial.sessionId()).orElseThrow(TokenService::invalid);
        // Đọc lại sau khi đã lấy khóa; request khác có thể vừa consume token này.
        RefreshTokenRow stored = tokens.findRefresh(tokenId).orElseThrow(TokenService::invalid);
        if (!MessageDigest.isEqual(
                hash(rawToken).getBytes(StandardCharsets.US_ASCII),
                stored.tokenHash().getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        if (stored.consumedAt() != null) {
            tokens.revokeSession(session.id());
            events.publishEvent(new ApiSessionsRevokedEvent(session.id(), null));
            // Không rollback thu hồi khi trả lỗi replay về người gọi.
            throw new AuthFailure(ValidateCode.AUTH_REFRESH_REUSED);
        }
        Instant now = Instant.now();
        if (session.revokedAt() != null
                || !session.enabled()
                || !session.expiresAt().isAfter(now)
                || !stored.expiresAt().isAfter(now)) {
            throw invalid();
        }
        tokens.consume(tokenId);
        return grant(session);
    }

    /**
     * Tạo claims access JWT và refresh token ngẫu nhiên cho một phiên đã được xác thực. Access hết
     * hạn không muộn hơn phiên; refresh chỉ lưu SHA-256 trong DB, giá trị thô đi trong TokenGrant
     * để ghi cookie. Được gọi bên trong transaction issue/refresh để việc tạo refresh và thay đổi
     * phiên có cùng kết quả commit.
     */
    private TokenGrant grant(AuthSession session) {
        Instant issuedAt = Instant.now();
        Instant accessDeadline = issuedAt.plus(properties.accessTtl());
        if (session.expiresAt().isBefore(accessDeadline)) {
            accessDeadline = session.expiresAt();
        }
        // sid nối JWT với phiên trong DB; decoder kiểm tra phiên còn hiệu lực ở mỗi request.
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(properties.issuer())
                        .audience(List.of(properties.audience()))
                        .subject(Long.toString(session.userId()))
                        .issuedAt(issuedAt)
                        .expiresAt(accessDeadline)
                        .id(UUID.randomUUID().toString())
                        .claim("sid", session.id())
                        .claim("username", session.username())
                        .claim("roles", List.of(session.role()))
                        .claim("token_kind", "access")
                        .build();
        JwsHeader header =
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
        String access = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        // Refresh không phải JWT: UUID để tra bản ghi, secret ngẫu nhiên để chứng minh quyền dùng.
        String refreshId = UUID.randomUUID().toString();
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String refresh =
                refreshId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        // Chỉ lưu hash; token thô trả về controller để ghi cookie HttpOnly, không lưu/log lại.
        tokens.createRefresh(refreshId, session.id(), hash(refresh), session.expiresAt());
        return new TokenGrant(
                new AuthTokenResponse(
                        access,
                        "Bearer",
                        Math.max(0, accessDeadline.getEpochSecond() - issuedAt.getEpochSecond()),
                        session.userId(),
                        session.username()),
                refresh,
                session.expiresAt());
    }

    /**
     * Thu hồi một sid bằng transaction rồi phát ApiSessionsRevokedEvent. Listener đóng socket sau
     * commit; request Bearer tiếp theo bị decoder từ chối dù JWT chưa tới hạn.
     */
    @Transactional
    public void revoke(String sessionId) {
        tokens.revokeSession(sessionId);
        events.publishEvent(new ApiSessionsRevokedEvent(sessionId, null));
    }

    /**
     * Thu hồi toàn bộ phiên API của một user bằng một UPDATE theo userId. Sự kiện sau commit đóng
     * socket của người đó; các HttpSession web có vòng đời riêng.
     */
    @Transactional
    public void revokeAll(long userId) {
        tokens.revokeAll(userId);
        events.publishEvent(new ApiSessionsRevokedEvent(null, userId));
    }

    /**
     * Kiểm tra kích thước/cấu trúc refresh thô và lấy phần UUID làm khóa tìm DB. Cấu trúc sai được
     * trả AUTH_INVALID_REFRESH; kiểm tra secret/hash thực hiện sau khi khóa phiên trong refresh.
     */
    private static String tokenId(String rawToken) {
        if (rawToken == null || rawToken.length() > 150) {
            throw invalid();
        }
        int separator = rawToken.indexOf('.');
        if (separator != 36) {
            throw invalid();
        }
        try {
            return UUID.fromString(rawToken.substring(0, separator)).toString();
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
    }

    /**
     * Băm refresh ngẫu nhiên bằng SHA-256 để DB không cần lưu giá trị token thô. Đây là hash của bí
     * mật có entropy cao do server sinh; mật khẩu người dùng được băm riêng bằng PasswordEncoder.
     */
    public static String hash(String rawToken) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", impossible);
        }
    }

    /**
     * Tạo lỗi AUTH_INVALID_REFRESH thống nhất cho refresh thiếu, sai hoặc hết hiệu lực. Thông điệp
     * được dịch tại tầng trả response, không nằm trong service.
     */
    private static AuthFailure invalid() {
        return new AuthFailure(ValidateCode.AUTH_INVALID_REFRESH);
    }

    /**
     * Dọn token/phiên đã hết hạn theo lô mỗi ngày lúc 03:15 UTC. Giữ refresh consumed tới hết hạn
     * phiên để còn phát hiện replay; chạy trong transaction của tác vụ định kỳ.
     */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void cleanup() {
        tokens.deleteExpired(Instant.now());
    }
}
