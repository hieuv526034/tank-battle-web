package vn.edu.utc.tankbattle.auth.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.utc.tankbattle.auth.entity.UserEntity;
import vn.edu.utc.tankbattle.auth.exception.OidcAuthFailure;
import vn.edu.utc.tankbattle.auth.repository.ExternalIdentityRepository;
import vn.edu.utc.tankbattle.auth.repository.UserRepository;
import vn.edu.utc.tankbattle.common.constant.AppConstants.TextKind;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;
import vn.edu.utc.tankbattle.common.util.TextNormalizer;
import vn.edu.utc.tankbattle.common.validation.ValidationRules;

/**
 * Tạo tài khoản riêng cho subject Google. Không tự liên kết bằng email và không ghi Google token
 * vào DB. Tạo user/identity cùng transaction; va chạm khi callback đồng thời được rollback rồi đọc
 * lại.
 */
@Service
public class GoogleAccountService {
    private final ExternalIdentityRepository identities;
    private final UserRepository users;
    private final TransactionTemplate transactions;

    /**
     * Nhận kho identity, kho user và TransactionTemplate cho luồng tạo liên kết Google.
     * TransactionTemplate cho phép rollback callback thua cuộc trước khi mở transaction khác đọc
     * liên kết đã thắng.
     */
    public GoogleAccountService(
            ExternalIdentityRepository identities,
            UserRepository users,
            TransactionTemplate transactions) {
        this.identities = identities;
        this.users = users;
        this.transactions = transactions;
    }

    /**
     * Tìm tài khoản bằng provider GOOGLE + subject đã được OIDC kiểm chứng, hoặc tạo user/identity
     * cùng transaction. Username Google được sinh riêng; tên chỉ được dùng khi tạo lần đầu. Luồng
     * này không nối tài khoản local theo tên/email. Nếu callback đồng thời chạm
     * unique(provider,subject), transaction thua rollback cả user mới rồi đọc lại liên kết ở
     * transaction mới.
     *
     * @param subject định danh ổn định của tài khoản Google, không phải email
     * @param displayName tên gợi ý từ provider, được chuẩn hóa hoặc dùng tên mặc định
     * @return entity tài khoản local tương ứng với identity Google
     */
    public UserEntity resolve(String subject, String displayName) {
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new OidcAuthFailure(ValidateCode.AUTH_OAUTH_FAILED);
        }
        try {
            return transactions.execute(
                    status ->
                            find(subject)
                                    .orElseGet(
                                            () -> {
                                                UserEntity user = new UserEntity();
                                                user.setUsername(
                                                        "g_"
                                                                + UUID.randomUUID()
                                                                        .toString()
                                                                        .replace("-", "")
                                                                        .substring(0, 20));
                                                String name = TextNormalizer.name(displayName);
                                                if (!ValidationRules.valid(
                                                        TextKind.DISPLAY_NAME, name)) {
                                                    name = "Người chơi Google";
                                                }
                                                user.setDisplayName(
                                                        name.substring(
                                                                0, Math.min(name.length(), 30)));
                                                UserEntity saved = users.saveAndFlush(user);
                                                identities.insert(saved.getId(), subject);
                                                return saved;
                                            }));
        } catch (DataIntegrityViolationException concurrentCallback) {
            return transactions.execute(
                    status ->
                            find(subject)
                                    .orElseThrow(
                                            () ->
                                                    new OidcAuthFailure(
                                                            ValidateCode.AUTH_OAUTH_FAILED)));
        }
    }

    /**
     * Tra userId của subject Google rồi đọc UserEntity tương ứng. Helper được gọi trong transaction
     * resolve, giữ việc tra identity tách khỏi quyết định tạo tài khoản.
     */
    private Optional<UserEntity> find(String subject) {
        return identities.userId(subject).flatMap(users::findById);
    }
}
