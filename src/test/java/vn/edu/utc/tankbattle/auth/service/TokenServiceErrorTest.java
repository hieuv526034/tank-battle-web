package vn.edu.utc.tankbattle.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import vn.edu.utc.tankbattle.auth.config.AuthProperties;
import vn.edu.utc.tankbattle.auth.exception.AuthFailure;
import vn.edu.utc.tankbattle.auth.model.AuthSession;
import vn.edu.utc.tankbattle.auth.model.RefreshTokenRow;
import vn.edu.utc.tankbattle.auth.repository.AuthTokenRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/** Unit test nhánh lỗi refresh; kiểm tra commit/khóa thực tế cần integration test DB riêng. */
class TokenServiceErrorTest {
    private AuthTokenRepository repository;
    private TokenService service;

    @BeforeEach
    void setup() {
        repository = mock(AuthTokenRepository.class);
        AuthProperties properties =
                new AuthProperties(
                        "http://localhost:8080",
                        "tank-battle-api",
                        Duration.ofMinutes(5),
                        Duration.ofDays(7),
                        "test",
                        "",
                        "",
                        true,
                        false);
        service =
                new TokenService(
                        repository,
                        mock(JwtEncoder.class),
                        properties,
                        mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-token", "not-a-valid-uuid-prefix-of-36-characters.secret"})
    void malformedRefreshUsesEnumWithoutDatabaseCall(String raw) {
        assertFailure(raw, ValidateCode.AUTH_INVALID_REFRESH);
        verifyNoInteractions(repository);
    }

    @Test
    void reusedRefreshRevokesFamilyBeforeReturningCustomError() {
        String id = UUID.randomUUID().toString();
        String raw = id + ".random-test-entropy";
        String sessionId = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(Duration.ofDays(1));
        RefreshTokenRow stored =
                new RefreshTokenRow(
                        id,
                        sessionId,
                        TokenService.hash(raw),
                        expiry,
                        expiry.minus(Duration.ofDays(1)));
        when(repository.findRefresh(id)).thenReturn(Optional.of(stored));
        when(repository.lockSession(sessionId))
                .thenReturn(
                        Optional.of(
                                new AuthSession(
                                        sessionId, 1L, "utc_student", "USER", true, expiry, null)));

        assertFailure(raw, ValidateCode.AUTH_REFRESH_REUSED);

        InOrder order = inOrder(repository);
        order.verify(repository).findRefresh(id);
        order.verify(repository).lockSession(sessionId);
        order.verify(repository).findRefresh(id);
        order.verify(repository).revokeSession(sessionId);
        verify(repository, never()).consume(any());
        verify(repository, never()).createRefresh(any(), any(), any(), any());
    }

    @Test
    void wrongSecretDoesNotRevokeVictimsSessionEvenWhenTokenIdIsKnown() {
        String id = UUID.randomUUID().toString();
        String raw = id + ".wrong-secret";
        String sessionId = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(Duration.ofDays(1));
        when(repository.findRefresh(id))
                .thenReturn(
                        Optional.of(
                                new RefreshTokenRow(
                                        id,
                                        sessionId,
                                        TokenService.hash(id + ".correct-secret"),
                                        expiry,
                                        expiry.minus(Duration.ofDays(1)))));
        when(repository.lockSession(sessionId))
                .thenReturn(
                        Optional.of(
                                new AuthSession(
                                        sessionId, 1L, "utc_student", "USER", true, expiry, null)));

        assertFailure(raw, ValidateCode.AUTH_INVALID_REFRESH);
        verify(repository, never()).revokeSession(any());
    }

    private void assertFailure(String raw, ValidateCode code) {
        assertThatThrownBy(() -> service.refresh(raw))
                .isInstanceOfSatisfying(
                        AuthFailure.class,
                        failure ->
                                org.assertj.core.api.Assertions.assertThat(
                                                failure.getValidateCode())
                                        .isEqualTo(code));
    }
}
