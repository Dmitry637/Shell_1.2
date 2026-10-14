import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
public class Shell {
    private static final String DEFAULT_PROMPT = "{username}@{hostname}:~$ ";
    private static final String DEFAULT_VFS_PATH = "vfs";
    private enum ExecutionResult {
        CONTINUE, EXIT, ERROR
    }
    private static class LaunchOptions {
        String vfsPath;
        String prompt;
        String startupScript;
        String configPath;
        boolean helpRequested;
    }
    private static class ConfigValues {
        String vfsPath;
        String prompt;
        String startupScript;
    }
    private static class EffectiveConfig {
        String vfsPath;
        String promptTemplate;
        String startupScript;
        String prompt;
        Path vfsDirectory;
    }
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        LaunchOptions cliOptions = parseCommandLineArgs(args);
        if (cliOptions == null || cliOptions.helpRequested) {
            if (cliOptions != null) {
                printUsage();
            }
            return;
        }
        ConfigValues configValues = loadConfigValues(cliOptions);
        EffectiveConfig config = buildEffectiveConfig(cliOptions, configValues);
        displayConfigInfo(config);
        boolean running = executeStartupScript(config);
        if (running) {
            runInteractiveLoop(config);
        }
        System.out.println("Программа завершена.");
    }
    private static LaunchOptions parseCommandLineArgs(String[] args) {
        try {
            return parseLaunchArguments(args);
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка параметров командной строки: "
                    + e.getMessage());
            printUsage();
            return null;
        }
    }
    private static ConfigValues loadConfigValues(LaunchOptions cliOptions) {
        ConfigValues configValues = new ConfigValues();
        if (cliOptions.configPath != null) {
            try {
                configValues = readConfig(Paths.get(cliOptions.configPath));
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("Ошибка чтения конфигурационного файла '"
                        + cliOptions.configPath + "': " + e.getMessage());
            }
        }
        return configValues;
    }
    private static EffectiveConfig buildEffectiveConfig(
            LaunchOptions cliOptions, ConfigValues configValues) {
        EffectiveConfig config = new EffectiveConfig();
        config.vfsPath = firstNonNull(
                configValues.vfsPath, cliOptions.vfsPath, DEFAULT_VFS_PATH);
        config.promptTemplate = firstNonNull(
                configValues.prompt, cliOptions.prompt, DEFAULT_PROMPT);
        config.startupScript = firstNonNull(
                configValues.startupScript, cliOptions.startupScript, null);
        String username = System.getProperty("user.name", "user");
        config.prompt = config.promptTemplate
                .replace("{username}", username)
                .replace("{hostname}", getHostname());
        config.vfsDirectory = Paths.get(config.vfsPath)
                .toAbsolutePath().normalize();
        return config;
    }
    private static void displayConfigInfo(EffectiveConfig config) {
        System.out.println("VFS: " + config.vfsDirectory);
        if (config.startupScript != null) {
            System.out.println("Стартовый скрипт: "
                    + Paths.get(config.startupScript)
                    .toAbsolutePath().normalize());
        }
    }
    private static boolean executeStartupScript(EffectiveConfig config) {
        if (config.startupScript != null) {
            return runStartupScript(
                    Paths.get(config.startupScript),
                    config.prompt,
                    config.vfsDirectory);
        }
        return true;
    }
    private static void runInteractiveLoop(EffectiveConfig config) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print(config.prompt);
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }
                if (executeCommand(input) == ExecutionResult.EXIT) {
                    break;
                }
            }
        }
    }
    private static LaunchOptions parseLaunchArguments(String[] args) {
        LaunchOptions options = new LaunchOptions();
        for (int i = 0; i < args.length; i++) {
            processArgument(args, i, options);
        }
        return options;
    }
    private static void processArgument(
            String[] args, int i, LaunchOptions options) {
        String arg = args[i];
        Option exact = Option.matchExact(arg);
        if (exact != null) {
            exact.applyWithNextValue(options, args, i);
            return;
        }
        applyPrefixedArgument(arg, options);
    }
    private static void applyPrefixedArgument(
            String arg, LaunchOptions options) {
        Option prefixed = Option.matchPrefix(arg);
        if (prefixed == null) {
            throw new IllegalArgumentException(
                    "неизвестный параметр: " + arg);
        }
        prefixed.applyInlineValue(options, arg);
    }
    private static String requireNextValue(
            String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException(
                    "для " + optionName + " требуется значение");
        }
        return requireOptionValue(optionName, args[index]);
    }
    private static String requireOptionValue(
            String optionName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "для " + optionName
                            + " требуется непустое значение");
        }
        return value;
    }
    private static String firstNonNull(
            String first, String second, String third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }
    private static ConfigValues readConfig(Path configPath)
            throws IOException {
        String content = Files.readString(
                configPath, StandardCharsets.UTF_8);
        Map<String, String> values = new JsonParser(content).parse();
        ConfigValues config = new ConfigValues();
        config.vfsPath = values.get("vfsPath");
        config.prompt = values.get("prompt");
        config.startupScript = values.get("startupScript");
        return config;
    }
    private static boolean runStartupScript(
            Path startupPath, String prompt, Path vfsDirectory) {
        if (!Files.isRegularFile(startupPath)) {
            System.out.println("Ошибка чтения стартового скрипта '"
                    + startupPath + "': файл не найден.");
            return true;
        }
        try (BufferedReader reader = Files.newBufferedReader(
                startupPath, StandardCharsets.UTF_8)) {
            return executeStartupScriptLines(reader, prompt);
        } catch (IOException e) {
            System.out.println("Ошибка чтения стартового скрипта '"
                    + startupPath + "': " + e.getMessage());
            return true;
        }
    }
    private static boolean executeStartupScriptLines(
            BufferedReader reader, String prompt) throws IOException {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!executeStartupScriptLine(line, lineNumber, prompt)) {
                return false;
            }
        }
        return true;
    }
    private static boolean executeStartupScriptLine(
            String line, int lineNumber, String prompt) {
        String command = line.trim();
        if (command.isEmpty() || command.startsWith("#")) {
            return true;
        }
        System.out.println(prompt + command);
        ExecutionResult result = executeCommand(command);
        if (result == ExecutionResult.ERROR) {
            System.out.println("Ошибка выполнения стартового скрипта, "
                    + "строка " + lineNumber + ".");
        }
        return result != ExecutionResult.EXIT;
    }
    private static ExecutionResult executeCommand(String input) {
        List<String> parts = parseCommandSafely(input);
        if (parts == null) {
            return ExecutionResult.ERROR;
        }
        if (parts.isEmpty()) {
            return ExecutionResult.CONTINUE;
        }
        return dispatchCommand(parts);
    }
    private static List<String> parseCommandSafely(String input) {
        try {
            return parseCommand(input);
        } catch (IllegalArgumentException e) {
            System.out.println("No closing quotation");
            return null;
        }
    }
    private static List<String> parseCommand(String input) {
        return new CommandTokenizer().tokenize(input);
    }
    private static ExecutionResult dispatchCommand(List<String> parts) {
        String command = parts.get(0);
        return switch (command) {
            case "ls", "cd" -> handleStubCommand(parts);
            case "exit" -> handleExitCommand(parts);
            default -> handleUnknownCommand(command);
        };
    }
    private static ExecutionResult handleStubCommand(List<String> parts) {
        String command = parts.get(0);
        String[] arguments = parts.subList(1, parts.size())
                .toArray(new String[0]);
        printStub(command, arguments);
        return ExecutionResult.CONTINUE;
    }
    private static ExecutionResult handleExitCommand(List<String> parts) {
        if (parts.size() > 1) {
            System.out.println("Ошибка: exit без аргументов.");
            return ExecutionResult.ERROR;
        }
        System.out.println("Shell завершен.");
        return ExecutionResult.EXIT;
    }
    private static ExecutionResult handleUnknownCommand(String command) {
        System.out.println("Ошибка: неизвестная команда: " + command);
        return ExecutionResult.ERROR;
    }
    private static void printStub(String command, String[] arguments) {
        System.out.print("Команда: " + command);
        if (arguments.length == 0) {
            System.out.println(";\nаргументы: нет");
        } else {
            System.out.println(";\nаргументы: "
                    + String.join(" ", arguments));
        }
    }
    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
    private static void printUsage() {
        System.out.println("Эмулятор Shell — Этап 2: Конфигурация");
        System.out.println();
        System.out.println("Использование:");
        System.out.println("  java -cp out Shell [параметры]");
        System.out.println();
        System.out.println("Параметры:");
        System.out.println("  --vfs, -v <path>       путь к VFS");
        System.out.println("  --prompt, -p <text>    приглашение");
        System.out.println("  --startup, -s <path>   стартовый скрипт");
        System.out.println("  --config, -c <path>    JSON-конфигурация");
        System.out.println("  --help, -h             показать справку");
        System.out.println();
        System.out.println("В prompt поддерживаются {username} и {hostname}.");
        System.out.println("Значения из config.json важнее параметров CLI.");
    }
    private enum Option {
        HELP("--help", "-h", null),
        VFS("--vfs", "-v", "--vfs="),
        PROMPT("--prompt", "-p", "--prompt="),
        STARTUP("--startup", "-s", "--startup="),
        CONFIG("--config", "-c", "--config=");
        private final String longFlag;
        private final String shortFlag;
        private final String prefix;
        Option(String longFlag, String shortFlag, String prefix) {
            this.longFlag = longFlag;
            this.shortFlag = shortFlag;
            this.prefix = prefix;
        }
        static Option matchExact(String arg) {
            for (Option option : values()) {
                if (option.isExact(arg)) {
                    return option;
                }
            }
            return null;
        }
        static Option matchPrefix(String arg) {
            for (Option option : values()) {
                if (option.hasPrefix(arg)) {
                    return option;
                }
            }
            return null;
        }
        private boolean isExact(String arg) {
            return arg.equals(longFlag) || arg.equals(shortFlag);
        }
        private boolean hasPrefix(String arg) {
            return prefix != null && arg.startsWith(prefix);
        }
        void applyWithNextValue(
                LaunchOptions options, String[] args, int index) {
            switch (this) {
                case HELP -> options.helpRequested = true;
                case VFS -> options.vfsPath =
                        requireNextValue(args, index + 1, longFlag);
                case PROMPT -> options.prompt =
                        requireNextValue(args, index + 1, longFlag);
                case STARTUP -> options.startupScript =
                        requireNextValue(args, index + 1, longFlag);
                case CONFIG -> options.configPath =
                        requireNextValue(args, index + 1, longFlag);
            }
        }
        void applyInlineValue(LaunchOptions options, String arg) {
            String value = requireOptionValue(
                    longFlag, arg.substring(prefix.length()));
            switch (this) {
                case VFS -> options.vfsPath = value;
                case PROMPT -> options.prompt = value;
                case STARTUP -> options.startupScript = value;
                case CONFIG -> options.configPath = value;
                default -> throw new IllegalArgumentException(
                        "неизвестный параметр: " + arg);
            }
        }
    }
    private static class CommandTokenizer {
        private final List<String> parts = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();
        private Character quote;
        List<String> tokenize(String input) {
            for (int i = 0; i < input.length(); i++) {
                if (!processChar(input, i)) {
                    break;
                }
            }
            finish();
            return parts;
        }
        private boolean processChar(String input, int i) {
            char ch = input.charAt(i);
            if (quote != null) {
                processQuotedChar(ch);
                return true;
            }
            return processUnquotedChar(input, i, ch);
        }
        private void processQuotedChar(char ch) {
            if (ch == quote) {
                quote = null;
            } else {
                current.append(ch);
            }
        }
        private boolean processUnquotedChar(String input, int i, char ch) {
            if (ch == '"' || ch == '\'') {
                quote = ch;
                return true;
            }
            if (isCommentStart(input, i, ch)) {
                return false;
            }
            if (Character.isWhitespace(ch)) {
                flushCurrent();
                return true;
            }
            current.append(ch);
            return true;
        }
        private boolean isCommentStart(String input, int i, char ch) {
            if (ch != '#') {
                return false;
            }
            return i == 0 || Character.isWhitespace(input.charAt(i - 1));
        }
        private void flushCurrent() {
            if (current.length() > 0) {
                parts.add(current.toString());
                current.setLength(0);
            }
        }
        private void finish() {
            if (quote != null) {
                throw new IllegalArgumentException("No closing quotation");
            }
            flushCurrent();
        }
    }
    private static class JsonParser {
        private static final Map<Character, String> JSON_ESCAPES = Map.of(
                '"', "\"",
                '\\', "\\\\",
                '/', "/",
                'b', "\b",
                'f', "\f",
                'n', "\n",
                'r', "\r",
                't', "\t");
        private final String json;
        private int index;
        JsonParser(String json) {
            this.json = json;
        }
        Map<String, String> parse() {
            skipWhitespace();
            expect('{');
            Map<String, String> result = new LinkedHashMap<>();
            while (peek() != '}') {
                parseEntry(result);
            }
            expect('}');
            skipWhitespace();
            if (index != json.length()) {
                throw new IllegalArgumentException(
                        "лишние символы после JSON-объекта");
            }
            return result;
        }
        private void parseEntry(Map<String, String> result) {
            String key = parseString();
            skipWhitespace();
            expect(':');
            String value = parseValue();
            result.put(key, value);
            skipWhitespace();
            char current = peek();
            if (current == ',') {
                index++;
            } else if (current != '}') {
                throw new IllegalArgumentException(
                        "ожидалась ',' или '}' в JSON");
            }
        }
        private String parseValue() {
            skipWhitespace();
            if (json.startsWith("null", index)) {
                index += 4;
                return null;
            }
            return parseString();
        }
        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < json.length()) {
                char ch = json.charAt(index++);
                if (ch == '"') {
                    return result.toString();
                }
                if (ch == '\\') {
                    handleEscape(result);
                } else {
                    result.append(ch);
                }
            }
            throw new IllegalArgumentException(
                    "незакрытая строка в JSON");
        }
        private void handleEscape(StringBuilder result) {
            if (index >= json.length()) {
                throw new IllegalArgumentException(
                        "незавершённая escape-последовательность");
            }
            char escaped = json.charAt(index++);
            if (escaped == 'u') {
                result.append(parseUnicode());
                return;
            }
            String replacement = JSON_ESCAPES.get(escaped);
            if (replacement == null) {
                throw new IllegalArgumentException(
                        "неподдерживаемая escape-последовательность: \\"
                                + escaped);
            }
            result.append(replacement);
        }
        private String parseUnicode() {
            if (index + 4 > json.length()) {
                throw new IllegalArgumentException(
                        "неполная Unicode escape-последовательность");
            }
            String hex = json.substring(index, index + 4);
            index += 4;
            try {
                return String.valueOf((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "некорректная Unicode escape-последовательность");
            }
        }
        private void skipWhitespace() {
            while (index < json.length()
                    && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
        private void expect(char expected) {
            if (index >= json.length()
                    || json.charAt(index) != expected) {
                throw new IllegalArgumentException(
                        "ожидался символ '" + expected + "'");
            }
            index++;
        }
        private char peek() {
            if (index >= json.length()) {
                return '\0';
            }
            return json.charAt(index);
        }
    }
}