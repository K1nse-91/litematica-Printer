package me.K1nse_.litematica.printer.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.K1nse_.litematica.printer.Debug;
import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.Reference;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import me.K1nse_.litematica.printer.utils.minecraft.StringUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class UpdateCheckerUtils {
    private static final String DEFAULT_REPOSITORY_URL = "https://github.com/Yur1Ca/litematica-printer";
    private static final String DOWNLOAD_URL = "https://openlist.hanauta.icu/Minecraft/Litematica-Printer";
    public static final String REPOSITORY_URL = resolveRepositoryUrl();
    private static final String RELEASES_API_URL = resolveReleasesApiUrl(REPOSITORY_URL);

    // 本地版本（从fabric.mod.json读取）
    public static final String LOCAL_VERSION = getVersionFromModJson();

    // 语义化版本号正则：匹配 v1.2.3.4、1.2、5 等格式，提取前四段数字部分
    public static final Pattern SEM_VER_PATTERN = Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?.*$");

    public static void checkForUpdates() {
        if (isSnapshotVersion(LOCAL_VERSION)) {
            Debug.alwaysWrite("Skip update check for development version: " + LOCAL_VERSION);
            return;
        }

        CompletableFuture.runAsync(() -> {
            // 获取 GitHub 最新正式版版本号（过滤 draft / prerelease / dev 标签）
            String latestOfficialVersion = getLatestOfficialPrinterVersion();
            if (latestOfficialVersion == null) {
                return;
            }
            // 解析本地版本和最新正式版为语义化版本对象
            SemanticVersion localSemVer = SemanticVersion.parse(LOCAL_VERSION);
            SemanticVersion latestSemVer = SemanticVersion.parse(latestOfficialVersion);
            // 版本解析失败则跳过
            if (localSemVer == null || latestSemVer == null) {
                Debug.alwaysWrite("Version parsing failed, local: " + LOCAL_VERSION + ", latest: " + latestOfficialVersion);
                return;
            }
            // 仅当最新正式版 > 本地版本时，触发更新提示
            if (latestSemVer.isHigherThan(localSemVer)) {
                Minecraft.getInstance().execute(() -> {
                    MessageUtils.addMessage(I18n.UPDATE_AVAILABLE.getName(LOCAL_VERSION, latestOfficialVersion)
                            .withStyle(ChatFormatting.YELLOW));
                    MessageUtils.addMessage(I18n.UPDATE_RECOMMENDATION.getName()
                            .withStyle(ChatFormatting.RED));
                    MessageUtils.addMessage(I18n.UPDATE_DOWNLOAD.getName()
                            .setStyle(Style.EMPTY
                                    //#if MC >= 12105
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(DOWNLOAD_URL)))
                                    //#else
                                    //$$ .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOAD_URL))
                                    //#endif
                                    .withUnderlined(true)
                                    .withColor(ChatFormatting.GREEN)));
                    MessageUtils.addMessage(I18n.UPDATE_REPOSITORY.getName()
                            .withStyle(ChatFormatting.WHITE));
                    MessageUtils.addMessage(StringUtils.literal(REPOSITORY_URL)
                            .setStyle(Style.EMPTY
                                    //#if MC >= 12105
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(REPOSITORY_URL)))
                                    //#else
                                    //$$ .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, REPOSITORY_URL))
                                    //#endif
                                    .withUnderlined(true)
                                    .withColor(ChatFormatting.BLUE)));
                    MessageUtils.addMessage(
                            StringUtils.literal("------------------------").withStyle(ChatFormatting.GRAY));
                });
            }
        });
    }

    /**
     * 获取GitHub最新**正式版**版本号（过滤预发布版/dev/beta/alpha）
     *
     * @return 最新正式版tag_name，无则返回null
     */
    public static String getLatestOfficialPrinterVersion() {
        try {
            URI uri = URI.create(RELEASES_API_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            // 模拟浏览器请求，避免GitHub API拒绝
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            try (InputStream inputStream = conn.getInputStream();
                 Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
                scanner.useDelimiter("\\A");
                if (scanner.hasNext()) {
                    String response = scanner.next();
                    JsonArray releases = JsonParser.parseString(response).getAsJsonArray();
                    for (int i = 0; i < releases.size(); i++) {
                        JsonObject release = releases.get(i).getAsJsonObject();
                        if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) {
                            continue;
                        }

                        String tagName = release.get("tag_name").getAsString();
                        if (isSnapshotVersion(tagName)) {
                            continue;
                        }

                        return tagName;
                    }
                }
            }
        } catch (Exception exception) {
            Debug.alwaysWrite("Failed to check update: " + exception.getMessage());
            Minecraft.getInstance().execute(() -> MessageUtils.addMessage(I18n.UPDATE_FAILED.getName()));
            exception.printStackTrace();
        }
        return null;
    }

    private static String resolveRepositoryUrl() {
        try {
            ModContainer container = getModContainer();
            Optional<String> sourcesUrl = container.getMetadata().getContact().get("sources");
            if (sourcesUrl.isPresent() && !sourcesUrl.get().isBlank()) {
                return normalizeRepositoryUrl(sourcesUrl.get());
            }
        } catch (Exception exception) {
            Debug.alwaysWrite("Failed to resolve repository URL from metadata: " + exception.getMessage());
        }
        return DEFAULT_REPOSITORY_URL;
    }

    private static String resolveReleasesApiUrl(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return "https://api.github.com/repos/Yur1Ca/litematica-printer/releases";
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "https://api.github.com/repos/Yur1Ca/litematica-printer/releases";
            }

            String[] parts = path.replaceFirst("^/", "").split("/");
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return "https://api.github.com/repos/Yur1Ca/litematica-printer/releases";
            }

            return "https://api.github.com/repos/" + parts[0] + "/" + parts[1].replaceAll("\\.git$", "") + "/releases";
        } catch (Exception exception) {
            Debug.alwaysWrite("Failed to resolve releases API URL from repository URL: " + exception.getMessage());
            return "https://api.github.com/repos/Yur1Ca/litematica-printer/releases";
        }
    }

    /**
     * 从fabric.mod.json读取本地Mod版本号（保留原逻辑）
     */
    private static String getVersionFromModJson() {
        ModContainer container = getModContainer();
        Optional<Path> modPathOptional = container.findPath("fabric.mod.json");
        if (modPathOptional.isEmpty()) {
            System.out.println("Cannot find fabric.mod.json file");
            return "unknown";
        }
        Path modPath = modPathOptional.get();
        try (InputStream inputStream = Files.newInputStream(modPath);
             InputStreamReader reader = new InputStreamReader(inputStream)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.get("version").getAsString();
        } catch (Exception e) {
            System.out.println("Cannot read mod version: ");
            e.printStackTrace();
            return "unknown";
        }
    }

    private static ModContainer getModContainer() {
        return FabricLoader.getInstance()
                .getModContainer(Reference.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("未找到对应 mod: " + Reference.MOD_ID));
    }

    private static String normalizeRepositoryUrl(String repositoryUrl) {
        String normalized = repositoryUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.replaceAll("\\.git$", "");
    }

    /**
     * 判断是否为快照版本（dev/beta/alpha/snapshot）
     */
    @SuppressWarnings("SameParameterValue")
    private static boolean isSnapshotVersion(String version) {
        if (version == null || "unknown".equals(version)) {
            return true;
        }
        String normalized = version.strip().toLowerCase();
        return normalized.matches(".*(?:[-+_.]?dev(?:[-+_.]?\\d+)?)$")
                || normalized.matches(".*(?:[-+_.]?beta\\d*)$")
                || normalized.matches(".*(?:[-+_.]?alpha\\d*)$")
                || normalized.matches(".*(?:[-+_.]?snapshot(?:[-+_.]?\\d+)?)$");
    }

    /**
     * 语义化版本号工具类：解析、比较（支持 x.y.z.w / x.y.z / x.y / x 格式，忽略 v 前缀和后缀）
     *
     * @param major 主版本
     * @param minor 次版本
     * @param patch 补丁版本
     * @param build 构建版本
     */
    private record SemanticVersion(int major, int minor, int patch, int build) {
        /**
         * 解析版本号字符串为SemanticVersion对象
         * 支持：v1.2.3.4、1.2、5、1.3.0-dev、v2.0-beta等格式
         */
        public static SemanticVersion parse(String versionStr) {
            if (versionStr == null || versionStr.isBlank()) {
                return null;
            }
            var matcher = SEM_VER_PATTERN.matcher(versionStr);
            if (!matcher.matches()) {
                return null;
            }
            // 解析主、次、补丁、构建版本，未指定则为0
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            int build = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
            return new SemanticVersion(major, minor, patch, build);
        }

        /**
         * 判断当前版本是否高于目标版本
         * 比较规则：主版本>次版本>补丁版本，依次比较
         */
        public boolean isHigherThan(SemanticVersion target) {
            if (target == null) {
                return false;
            }
            if (this.major > target.major) {
                return true;
            } else if (this.major == target.major) {
                if (this.minor > target.minor) {
                    return true;
                } else if (this.minor == target.minor) {
                    if (this.patch > target.patch) {
                        return true;
                    } else if (this.patch == target.patch) {
                        return this.build > target.build;
                    }
                }
            }
            return false;
        }
    }
}
