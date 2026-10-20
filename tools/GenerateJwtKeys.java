import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Set;

/**
 * Sinh RSA 2048 bit dạng PKCS8/X509 để JwtConfiguration đọc được.
 * Chạy: java tools/GenerateJwtKeys.java secrets
 * Không ghi đè key đang có, không in nội dung key; thư mục secrets và PEM đã gitignore.
 */
public final class GenerateJwtKeys {
    private GenerateJwtKeys() {}

    /** Tạo hai file PEM trong thư mục đích; production giữ nguyên cặp này qua redeploy. */
    public static void main(String[] args) throws GeneralSecurityException, IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Cần một tham số: thư mục chứa cặp JWT key.");
        }
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        Path privateFile = directory.resolve("jwt-private.pem");
        Path publicFile = directory.resolve("jwt-public.pem");
        if (Files.exists(privateFile) || Files.exists(publicFile)) {
            throw new IllegalStateException("Đã có JWT key; từ chối ghi đè để tránh mất hiệu lực token.");
        }
        Files.createDirectories(directory);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        writePem(privateFile, "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(publicFile, "PUBLIC KEY", pair.getPublic().getEncoded());
        System.out.println("Đã tạo cặp JWT PEM; không đưa thư mục này vào Git hoặc Docker image.");
    }

    /** Ghi PEM mới với dòng base64 64 ký tự; trên Unix thu hẹp quyền về chủ file. */
    private static void writePem(Path file, String label, byte[] encoded) throws IOException {
        String content =
                "-----BEGIN " + label + "-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                        + "\n-----END " + label + "-----\n";
        Files.writeString(file, content, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(
                    file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }
}
