package vn.edu.utc.tankbattle.common.config;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import vn.edu.utc.tankbattle.common.constant.AppConstants;

/** Accept-Language vi/en; mặc định tiếng Việt và không phụ thuộc locale của máy chủ. */
@Configuration
public class LocaleConfig {
    /**
     * Tạo resolver đọc Accept-Language cho các request HTTP. Spring MVC dùng bean này để chọn
     * vi/en; SecurityErrorWriter và handshake socket dùng chung bean nên có cùng quy tắc mặc định.
     * Danh sách hỗ trợ và ngôn ngữ mặc định lấy từ AppConstants, độc lập với ngôn ngữ của máy chủ.
     *
     * @return resolver dùng trong suốt vòng đời ứng dụng
     */
    @Bean
    public AcceptHeaderLocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(
                AppConstants.SUPPORTED_LANGUAGES.stream().map(Locale::forLanguageTag).toList());
        resolver.setDefaultLocale(Locale.forLanguageTag(AppConstants.DEFAULT_LANGUAGE));
        return resolver;
    }

    /**
     * Nối Bean Validation với bundle i18n của ứng dụng. Nhờ đó các annotation như ValidText,
     * NotNull và Min dùng cùng message key với lỗi nghiệp vụ, thay vì thông báo mặc định của thư
     * viện.
     *
     * @param messages nguồn messages_vi/messages_en do Spring quản lý
     * @return validator được MVC dùng trước khi gọi controller
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messages) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages);
        return validator;
    }
}
