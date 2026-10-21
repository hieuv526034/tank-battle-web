package vn.edu.utc.tankbattle.auth.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.filter.AuthRateLimitFilter;
import vn.edu.utc.tankbattle.auth.security.AuthErrorHandler;
import vn.edu.utc.tankbattle.auth.service.AccountService;
import vn.edu.utc.tankbattle.auth.service.GoogleOidcUserService;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.event.WebSessionClosedEvent;
import vn.edu.utc.tankbattle.common.exception.BusinessException;

/**
 * Hai filter chain: /api nhận Bearer JWT, trang Thymeleaf dùng session/form/OIDC.
 *
 * <p>Refresh bằng cookie vẫn cần CSRF; chỉ API Bearer thuần mới bỏ kiểm tra CSRF. Mọi entry point,
 * failure handler và access denied handler đều gọi AuthErrorHandler.
 */
@Configuration
public class SecurityConfig {
    /**
     * Cấp bộ băm PBKDF2 kèm salt để dùng khi đăng ký, kiểm tra đăng nhập và seed local. Dùng cùng
     * encoder ở mọi nơi giúp mật khẩu đã lưu được xác minh nhất quán; DB chỉ giữ hash do encoder
     * tạo.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Chuyển tài khoản DB thành UserDetails để Spring Security kiểm tra đăng nhập bằng mật khẩu.
     * AccountService chuẩn hóa username và đọc tài khoản; role/enabled được đưa vào UserDetails.
     * Tài khoản chỉ liên kết Google không có password hash sẽ bị vô hiệu hóa với luồng mật khẩu;
     * thông báo đăng nhập không phân biệt tài khoản không tồn tại.
     */
    @Bean
    UserDetailsService users(AccountService store) {
        return username -> {
            try {
                UserEntity user = store.credentials(username);
                boolean passwordLogin = user.getPasswordHash() != null;
                return User.withUsername(user.getUsername())
                        .password(
                                passwordLogin
                                        ? user.getPasswordHash()
                                        : "PASSWORD_LOGIN_UNAVAILABLE")
                        .roles(user.getRole().name())
                        .disabled(!user.isEnabled() || !passwordLogin)
                        .build();
            } catch (BusinessException missing) {
                // Không phân biệt tài khoản chưa tồn tại hay tài khoản chỉ dùng Google.
                throw new UsernameNotFoundException(
                        ValidateCode.AUTH_INVALID_CREDENTIALS.getMessageKey());
            }
        };
    }

    /**
     * Lấy AuthenticationManager do Spring lắp từ UserDetailsService và PasswordEncoder.
     * AuthApiController gọi bean này để kiểm tra thông tin đăng nhập trước khi TokenService cấp
     * JWT.
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Tắt việc servlet container tự đăng ký AuthRateLimitFilter như một filter toàn cục. Filter này
     * đã được gắn vào hai SecurityFilterChain; tắt đăng ký tự động giúp mỗi request chỉ bị đếm một
     * lần.
     */
    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> preventDuplicateFilter(AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Thiết lập chain ưu tiên cao cho /api/**: danh tính lấy từ JWT, không tạo HttpSession.
     * Register/login/refresh/csrf được phép gọi trước đăng nhập; API còn lại yêu cầu Bearer hợp lệ.
     * Các thao tác auth dùng cookie vẫn bắt buộc CSRF, kể cả khi gửi Bearer. Post-processor gắn
     * matcher và error handler lên filter cuối cùng do Spring tạo. Mọi lỗi xác thực, phân quyền và
     * rate limit được chuyển sang mã lỗi/bundle chung.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            AuthRateLimitFilter throttle,
            AuthErrorHandler errors,
            BearerTokenFilterPostProcessor bearerFailures,
            ApiCsrfFilterPostProcessor apiCsrf)
            throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        // JWT lưu roles dạng USER/ADMIN; Spring kiểm tra quyền theo ROLE_USER/ROLE_ADMIN.
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setPrincipalClaimName("username");
        authentication.setJwtGrantedAuthoritiesConverter(authorities);

        // @Order(1) chọn chain này trước chain web: API không lấy danh tính từ session trình duyệt.
        http.securityMatcher("/api/**")
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(RequestCacheConfigurer::disable)
                .csrf(
                        config ->
                                config.csrfTokenRepository(csrf)
                                        // API JWT không tạo phiên web: nhận Bearer ở mỗi request
                                        // không được coi là login mới rồi xóa cookie CSRF.
                                        // CsrfFilter/matcher vẫn kiểm POST auth như bên dưới.
                                        .sessionAuthenticationStrategy(
                                                new NullAuthenticatedSessionStrategy())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .withObjectPostProcessor(apiCsrf))
                .authorizeHttpRequests(
                        access ->
                                access.requestMatchers(
                                                "/api/auth/csrf",
                                                "/api/auth/login",
                                                "/api/auth/register",
                                                "/api/auth/refresh")
                                        .permitAll()
                                        // Khách được xem bảng hạng; các đường dẫn cá nhân vẫn cần
                                        // JWT.
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/rankings",
                                                "/api/rankings/campaign")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        resource ->
                                resource.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
                                        .authenticationEntryPoint(errors::unauthenticated)
                                        .accessDeniedHandler(errors::denied)
                                        .withObjectPostProcessor(bearerFailures))
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(errors::unauthenticated)
                                        .accessDeniedHandler(errors::denied))
                .addFilterBefore(throttle, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Thiết lập chain cho trang web, form login, Google OIDC và đường dẫn handshake WebSocket. Danh
     * tính web được giữ bằng HttpSession, CSRF của session bảo vệ form. /ws/game được đi qua chain
     * để interceptor chuyên trách kiểm tra Origin và ticket/session. Logout phát sự kiện đóng
     * socket của session; Google chỉ bật khi có ClientRegistrationRepository từ cấu hình profile.
     */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurity(
            HttpSecurity http,
            AuthRateLimitFilter throttle,
            GoogleOidcUserService googleUsers,
            ObjectProvider<ClientRegistrationRepository> clients,
            ApplicationEventPublisher events,
            AuthErrorHandler errors,
            GoogleRedirectFilterPostProcessor googleFailures)
            throws Exception {
        http.authorizeHttpRequests(
                        access ->
                                access.requestMatchers(
                                                HttpMethod.GET,
                                                "/rooms",
                                                "/rooms/{code}",
                                                "/play/{id}",
                                                "/history",
                                                "/profile",
                                                "/saves",
                                                "/help",
                                                "/offline",
                                                "/auth/session")
                                        .permitAll()
                                        .requestMatchers(
                                                "/",
                                                "/ws/game",
                                                "/login",
                                                "/register",
                                                "/leaderboard",
                                                "/offline/**",
                                                "/css/**",
                                                "/js/**",
                                                "/icons/**",
                                                "/fonts/**",
                                                "/game-rules.json",
                                                "/game-content.json",
                                                "/campaign-stages.json",
                                                "/sw.js",
                                                "/manifest.webmanifest",
                                                "/actuator/health/**",
                                                "/error",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/oauth2/**",
                                                "/login/oauth2/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .defaultSuccessUrl("/rooms", true)
                                        .failureHandler(errors::loginFailure)
                                        .permitAll())
                .logout(
                        logout ->
                                logout.addLogoutHandler(
                                                (request, response, auth) -> {
                                                    HttpSession session = request.getSession(false);
                                                    if (session != null) {
                                                        events.publishEvent(
                                                                new WebSessionClosedEvent(
                                                                        session.getId()));
                                                    }
                                                })
                                        .logoutSuccessUrl("/?loggedout"))
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(errors::unauthenticated)
                                        .accessDeniedHandler(errors::denied))
                .addFilterBefore(throttle, UsernamePasswordAuthenticationFilter.class);

        // Chỉ tạo filter OAuth khi đã có cấu hình client. Gọi getIfAvailable() đơn lẻ
        // không ngăn oauth2Login đòi ClientRegistrationRepository lúc http.build().
        ClientRegistrationRepository registrations = clients.getIfAvailable();
        if (registrations != null) {
            http.oauth2Login(
                    google ->
                            google.clientRegistrationRepository(registrations)
                                    .loginPage("/login")
                                    .userInfoEndpoint(info -> info.oidcUserService(googleUsers))
                                    .defaultSuccessUrl("/rooms", true)
                                    .failureHandler(errors::googleFailure)
                                    .withObjectPostProcessor(googleFailures));
        }
        return http.build();
    }
}
