package vn.edu.utc.tankbattle.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import vn.edu.utc.tankbattle.common.constant.AppConstants.ValidateCode;

/**
 * Kiểm tra hợp đồng source bằng JUnit/JDK, không cần Node hoặc database. Những kiểm tra này bảo vệ
 * mã lỗi/bản dịch, ranh giới package, tài liệu bàn giao và cấu hình deploy khi thành viên sửa
 * source.
 */
class SourceContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path MAIN = ROOT.resolve("src/main/java/vn/edu/utc/tankbattle");

    @Test
    void errorCodesAreUniqueAndEveryBundleHasExactlyTheCatalogKeys() throws IOException {
        Set<String> codes = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ValidateCode code : ValidateCode.values()) {
            assertTrue(codes.add(code.getCode()), "Trùng mã public: " + code.name());
            assertTrue(keys.add(code.getMessageKey()), "Trùng message key: " + code.name());
            assertTrue(code.getCode().matches("[A-Z]+_[0-9]{3}"));
            assertEquals(code.name().split("_")[0], code.getCode().split("_")[0]);
            assertTrue(code.getHttpStatus() >= 200 && code.getHttpStatus() <= 599);
        }
        for (String filename :
                List.of(
                        "messages.properties",
                        "messages_vi.properties",
                        "messages_en.properties")) {
            Properties bundle = new Properties();
            try (Reader reader =
                    Files.newBufferedReader(
                            ROOT.resolve("src/main/resources/i18n").resolve(filename),
                            StandardCharsets.UTF_8)) {
                bundle.load(reader);
            }
            assertEquals(keys, bundle.stringPropertyNames(), "Bundle thiếu/thừa key: " + filename);
            for (String key : keys) {
                assertFalse(bundle.getProperty(key).isBlank(), "Bản dịch rỗng: " + key);
            }
        }
    }

    @Test
    void packagesMatchTheirPathsAndModulesDoNotDependOnEachOther() throws IOException {
        try (Stream<Path> children = Files.list(MAIN)) {
            assertEquals(
                    Set.of("auth", "common", "business"),
                    children.filter(Files::isDirectory)
                            .map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet()));
        }
        Pattern declaration = Pattern.compile("(?m)^package\\s+([\\w.]+);");
        for (Path file : files(MAIN, ".java")) {
            String source = Files.readString(file);
            Matcher match = declaration.matcher(source);
            assertTrue(match.find(), "Thiếu package: " + file);
            String expected =
                    ROOT.resolve("src/main/java")
                            .relativize(file.getParent())
                            .toString()
                            .replace('\\', '.')
                            .replace('/', '.');
            assertEquals(expected, match.group(1), file.toString());
            String module = MAIN.relativize(file).getName(0).toString();
            String forbidden =
                    switch (module) {
                        case "common" -> "auth|business";
                        case "auth" -> "business";
                        case "business" -> "auth";
                        default -> null;
                    };
            if (forbidden != null) {
                assertFalse(
                        Pattern.compile(
                                        "import vn\\.edu\\.utc\\.tankbattle\\.(?:"
                                                + forbidden
                                                + ")\\.")
                                .matcher(source)
                                .find(),
                        "Sai hướng dependency: " + file);
            }
        }
    }

    @Test
    void javaUsesExplicitVariableTypesAndInstantForEvents() throws IOException {
        Pattern inferredDeclaration = Pattern.compile("\\bvar\\s+[A-Za-z_$][\\w$]*\\s*(?:=|:|;)");
        List<Path> sources = new ArrayList<>(files(MAIN, ".java"));
        sources.addAll(files(ROOT.resolve("src/test/java"), ".java"));
        for (Path file : sources) {
            String source = Files.readString(file);
            assertFalse(
                    inferredDeclaration.matcher(source).find(),
                    "Biến phải khai báo kiểu tường minh: " + file);
            if (file.startsWith(MAIN)
                    && !file.getFileName().toString().equals("JdbcTimeUtils.java")) {
                assertFalse(
                        Pattern.compile("\\b(?:LocalDateTime|OffsetDateTime)\\b")
                                .matcher(source)
                                .find(),
                        "Mốc sự kiện phải dùng Instant: " + file);
            }
        }
    }

    @Test
    void businessAndAuthExceptionsDoNotReceiveLiteralMessages() throws IOException {
        Pattern literal =
                Pattern.compile(
                        "(?:new\\s+(?:BusinessException|AuthFailure)|BusinessException\\.of)\\(\\s*\"");
        for (Path file : files(MAIN, ".java")) {
            assertFalse(
                    literal.matcher(Files.readString(file)).find(),
                    "Lỗi phải nhận ValidateCode: " + file);
        }
    }

    @Test
    void allRelativeDocumentationLinksResolveAfterMovingToAgents() throws IOException {
        List<Path> documents =
                new ArrayList<>(List.of(ROOT.resolve("README.md"), ROOT.resolve("AGENTS.md")));
        documents.addAll(files(ROOT.resolve(".agents"), ".md"));
        Pattern markdownLink = Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)");
        for (Path document : documents) {
            Matcher links = markdownLink.matcher(Files.readString(document));
            while (links.find()) {
                String link = links.group(1);
                if (link.matches("(?i)^(?:[a-z][a-z0-9+.-]*:|#|//).*") || link.isBlank()) {
                    continue;
                }
                String relative = URLDecoder.decode(link.split("#", 2)[0], StandardCharsets.UTF_8);
                assertTrue(
                        Files.exists(document.getParent().resolve(relative).normalize()),
                        "Link thiếu trong " + document + ": " + link);
            }
        }
    }

    @Test
    void demoEnvironmentCanBeCommittedButCannotEnterDockerImage() throws IOException {
        String ignore = Files.readString(ROOT.resolve(".gitignore"));
        assertTrue(ignore.lines().anyMatch(line -> line.equals("!.env")));
        assertTrue(ignore.lines().anyMatch(line -> line.equals("!render.env")));
        String dockerIgnore = Files.readString(ROOT.resolve(".dockerignore"));
        assertTrue(dockerIgnore.lines().anyMatch(line -> line.equals("**/.env")));
        assertTrue(dockerIgnore.lines().anyMatch(line -> line.equals("render.env")));
        assertTrue(dockerIgnore.lines().anyMatch(line -> line.equals("**/*.pem")));
        String production =
                Files.readString(ROOT.resolve("src/main/resources/application-prod.yml"));
        assertTrue(production.contains("${SPRING_DATASOURCE_PASSWORD}"));
        assertFalse(production.matches("(?s).*npg_[A-Za-z0-9]{12,}.*"));
    }

    /** Thu thập file theo đuôi; đóng stream để Windows giải phóng handle khi build/clean. */
    private List<Path> files(Path directory, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }
}
