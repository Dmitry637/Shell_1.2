import java.io.BufferedReader;
import java.io.IOException;
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
        CONTINUE,
        EXIT,
        ERROR
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
    public static void main(String[] args) {
        LaunchOptions cliOptions = parseCommandLineArgs(args);
        if (cliOptions == null) {
            return;
        }
        if (cliOptions.helpRequested) {
            printUsage();
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
            System.out.println("Ошибка параметров командной строки: " + e.getMessage());
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
    private static EffectiveConfig buildEffectiveConfig(LaunchOptions cliOptions, ConfigValues configValues) {
        EffectiveConfig config = new EffectiveConfig();
        config.vfsPath = firstNonNull(configValues.vfsPath, cliOptions.vfsPath, DEFAULT_VFS_PATH);
        config.promptTemplate = firstNonNull(configValues.prompt, cliOptions.prompt, DEFAULT_PROMPT);
        config.startupScript = firstNonNull(configValues.startupScript, cliOptions.startupScript, null);

        String username = System.getProperty("user.name", "user");
        String hostname = getHostname();
        config.prompt = buildPrompt(config.promptTemplate, username, hostname);
        config.vfsDirectory = Paths.get(config.vfsPath).toAbsolutePath().normalize();

        return config;
    }
    private static void displayConfigInfo(EffectiveConfig config) {
        System.out.println("VFS: " + config.vfsDirectory);
        if (config.startupScript != null) {
            System.out.println("Стартовый скрипт: "
                    + Paths.get(config.startupScript).toAbsolutePath().normalize());
        }
    }
    private static boolean executeStartupScript(EffectiveConfig config) {
        if (config.startupScript != null) {
            return runStartupScript(
                    Paths.get(config.startupScript),
                    config.prompt,
                    config.vfsDirectory
            );
        }
        return true;
    }
    private static void runInteractiveLoop(EffectiveConfig config) {
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.print(config.prompt);
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }
                ExecutionResult result = executeCommand(input);
                if (result == ExecutionResult.EXIT) {
                    running = false;
                }
            }
        }
    }
    private static LaunchOptions parseLaunchArguments(String[] args) {
        LaunchOptions options = new LaunchOptions();
        for (int i = 0; i < args.length; i++) {
            parseSingleArgument(args, i, options);
        }
        return options;
    }
    private static void parseSingleArgument(String[] args, int i, LaunchOptions options) {
        String arg = args[i];
        if (handleHelpOption(arg, options)) return;
        if (handleVfsOption(arg, args, i, options)) return;
        if (handlePromptOption(arg, args, i, options)) return;
        if (handleStartupOption(arg, args, i, options)) return;
        if (handleConfigOption(arg, args, i, options)) return;
        throw new IllegalArgumentException("неизвестный параметр: " + arg);
    }
    private static boolean handleHelpOption(String arg, LaunchOptions options) {
        if (arg.equals("--help") || arg.equals("-h")) {
            options.helpRequested = true;
            return true;
        }
        return false;
    }
    private static boolean handleVfsOption(String arg, String[] args, int i, LaunchOptions options) {
        if (arg.startsWith("--vfs=")) {
            options.vfsPath = requireOptionValue("--vfs", arg.substring("--vfs=".length()));
            return true;
        } else if (arg.equals("--vfs") || arg.equals("-v")) {
            options.vfsPath = requireNextValue(args, ++i, "--vfs");
            return true;
        }
        return false;
    }
    private static boolean handlePromptOption(String arg, String[] args, int i, LaunchOptions options) {
        if (arg.startsWith("--prompt=")) {
            options.prompt = requireOptionValue("--prompt", arg.substring("--prompt=".length()));
            return true;
        } else if (arg.equals("--prompt") || arg.equals("-p")) {
            options.prompt = requireNextValue(args, ++i, "--prompt");
            return true;
        }
        return false;
    }
    private static boolean handleStartupOption(String arg, String[] args, int i, LaunchOptions options) {
        if (arg.startsWith("--startup=")) {
            options.startupScript = requireOptionValue("--startup", arg.substring("--startup=".length()));
            return true;
        } else if (arg.equals("--startup") || arg.equals("-s")) {
            options.startupScript = requireNextValue(args, ++i, "--startup");
            return true;
        }
        return false;
    }
    private static boolean handleConfigOption(String arg, String[] args, int i, LaunchOptions options) {
        if (arg.startsWith("--config=")) {
            options.configPath = requireOptionValue("--config", arg.substring("--config=".length()));
            return true;
        } else if (arg.equals("--config") || arg.equals("-c")) {
            options.configPath = requireNextValue(args, ++i, "--config");
            return true;
        }
        return false;
    }
    private static String requireNextValue(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("для " + optionName + " требуется значение");
        }
        return requireOptionValue(optionName, args[index]);
    }
    private static String requireOptionValue(String optionName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("для " + optionName + " требуется непустое значение");
        }
        return value;
    }
    private static String firstNonNull(String first, String second, String third) {
        if (first != null) return first;
        if (second != null) return second;
        return third;
    }
    private static String buildPrompt(String template, String username, String hostname) {
        return template
                .replace("{username}", username)
                .replace("{hostname}", hostname);
    }
    private static ConfigValues readConfig(Path configPath) throws IOException {
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        Map<String, String> values = parseJsonObject(content);
        ConfigValues config = new ConfigValues();
        config.vfsPath = values.get("vfsPath");
        config.prompt = values.get("prompt");
        config.startupScript = values.get("startupScript");
        return config;
    }
    private static Map<String, String> parseJsonObject(String json) {
        int[] index = {0};
        skipWhitespace(json, index);
        expect(json, index, '{');
        Map<String, String> result = new LinkedHashMap<>();
        parseJsonObjectContent(json, index, result);
        skipWhitespace(json, index);
        if (index[0] != json.length()) {
            throw new IllegalArgumentException("лишние символы после JSON-объекта");
        }
        return result;
    }
    private static void parseJsonObjectContent(String json, int[] index, Map<String, String> result) {
        skipWhitespace(json, index);
        if (peek(json, index) == '}') {
            index[0]++;
            return;
        }
        while (true) {
            parseJsonObjectEntry(json, index, result);
            skipWhitespace(json, index);
            char current = peek(json, index);
            if (current == ',') {
                index[0]++;
                continue;
            }
            if (current == '}') {
                index[0]++;
                break;
            }
            throw new IllegalArgumentException("ожидалась ',' или '}' в JSON");
        }
    }
    private static void parseJsonObjectEntry(String json, int[] index, Map<String, String> result) {
        skipWhitespace(json, index);
        String key = parseJsonString(json, index);

        skipWhitespace(json, index);
        expect(json, index, ':');
        skipWhitespace(json, index);

        String value = parseJsonValue(json, index);
        result.put(key, value);
    }
    private static String parseJsonValue(String json, int[] index) {
        if (peek(json, index) == 'n' && json.startsWith("null", index[0])) {
            index[0] += 4;
            return null;
        }
        return parseJsonString(json, index);
    }
    private static String parseJsonString(String json, int[] index) {
        expect(json, index, '"');
        StringBuilder result = new StringBuilder();
        while (index[0] < json.length()) {
            char ch = json.charAt(index[0]++);
            if (ch == '"') {
                return result.toString();
            }
            if (ch == '\\') {
                handleJsonEscape(json, index, result);
            } else {
                result.append(ch);
            }
        }
        throw new IllegalArgumentException("незакрытая строка в JSON");
    }
    private static void handleJsonEscape(String json, int[] index, StringBuilder result) {
        if (index[0] >= json.length()) {
            throw new IllegalArgumentException("незавершённая escape-последовательность");
        }
        char escaped = json.charAt(index[0]++);

        switch (escaped) {
            case '"' -> result.append('"');
            case '\\' -> result.append('\\');
            case '/' -> result.append('/');
            case 'b' -> result.append('\b');
            case 'f' -> result.append('\f');
            case 'n' -> result.append('\n');
            case 'r' -> result.append('\r');
            case 't' -> result.append('\t');
            case 'u' -> result.append(parseUnicodeEscape(json, index));
            default -> throw new IllegalArgumentException(
                    "неподдерживаемая escape-последовательность: \\" + escaped);
        }
    }
    private static char parseUnicodeEscape(String json, int[] index) {
        if (index[0] + 4 > json.length()) {
            throw new IllegalArgumentException("неполная Unicode escape-последовательность");
        }
        String hex = json.substring(index[0], index[0] + 4);
        try {
            char value = (char) Integer.parseInt(hex, 16);
            index[0] += 4;
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("некорректная Unicode escape-последовательность");
        }
    }
    private static void skipWhitespace(String json, int[] index) {
        while (index[0] < json.length() && Character.isWhitespace(json.charAt(index[0]))) {
            index[0]++;
        }
    }
    private static void expect(String json, int[] index, char expected) {
        if (index[0] >= json.length() || json.charAt(index[0]) != expected) {
            throw new IllegalArgumentException("ожидался символ '" + expected + "'");
        }
        index[0]++;
    }
    private static char peek(String json, int[] index) {
        if (index[0] >= json.length()) {
            return '\0';
        }
        return json.charAt(index[0]);
    }
    private static boolean runStartupScript(Path startupPath, String prompt, Path vfsDirectory) {
        if (!Files.isRegularFile(startupPath)) {
            System.out.println("Ошибка чтения стартового скрипта '" + startupPath + "': файл не найден.");
            return true;
        }

        try (BufferedReader reader = Files.newBufferedReader(startupPath, StandardCharsets.UTF_8)) {
            return executeStartupScriptLines(reader, prompt, vfsDirectory);
        } catch (IOException e) {
            System.out.println("Ошибка чтения стартового скрипта '" + startupPath + "': " + e.getMessage());
            return true;
        }
    }
    private static boolean executeStartupScriptLines(BufferedReader reader, String prompt, Path vfsDirectory)
            throws IOException {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!executeStartupScriptLine(line, lineNumber, prompt, vfsDirectory)) {
                return false;
            }
        }
        return true;
    }
    private static boolean executeStartupScriptLine(String line, int lineNumber, String prompt, Path vfsDirectory) {
        String command = line.trim();
        if (command.isEmpty() || command.startsWith("#")) {
            return true;
        }

        System.out.println(prompt + command);
        ExecutionResult result = executeCommand(command);

        if (result == ExecutionResult.ERROR) {
            System.out.println("Ошибка выполнения стартового скрипта, строка " + lineNumber + ".");
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
    private static ExecutionResult dispatchCommand(List<String> parts) {
        String command = parts.get(0);
        String[] arguments = parts.subList(1, parts.size()).toArray(new String[0]);

        return switch (command) {
            case "ls", "cd" -> {
                printStub(command, arguments);
                yield ExecutionResult.CONTINUE;
            }
            case "exit" -> handleExitCommand(arguments);
            default -> {
                System.out.println("Ошибка: неизвестная команда: " + command);
                yield ExecutionResult.ERROR;
            }
        };
    }
    private static ExecutionResult handleExitCommand(String[] arguments) {
        if (arguments.length > 0) {
            System.out.println("Ошибка: exit без аргументов.");
            return ExecutionResult.ERROR;
        }
        System.out.println("Shell завершен.");
        return ExecutionResult.EXIT;
    }
    private static List<String> parseCommand(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;

        for (int i = 0; i < input.length(); i++) {
            if (!processCommandChar(input, i, parts, current, quote, tokenStarted)) {
                break;
            }
        }
        validateCommandParsing(quote, current, tokenStarted, parts);
        return parts;
    }
    private static boolean processCommandChar(String input, int i, List<String> parts,
                                              StringBuilder current, char quote, boolean tokenStarted) {
        char ch = input.charAt(i);

        if (quote != 0) {
            return processQuotedChar(ch, quote, current);
        }

        return processUnquotedChar(input, i, ch, parts, current, tokenStarted);
    }
    private static boolean processQuotedChar(char ch, char quote, StringBuilder current) {
        if (ch == quote) {
            return true;
        }
        current.append(ch);
        return true;
    }
    private static boolean processUnquotedChar(String input, int i, char ch, List<String> parts,
                                               StringBuilder current, boolean tokenStarted) {
        if (ch == '"' || ch == '\'') {
            return true;
        }

        if (ch == '#' && (i == 0 || Character.isWhitespace(input.charAt(i - 1)))) {
            return false;
        }

        if (Character.isWhitespace(ch)) {
            if (tokenStarted) {
                parts.add(current.toString());
                current.setLength(0);
            }
            return true;
        }

        current.append(ch);
        return true;
    }
    private static void validateCommandParsing(char quote, StringBuilder current,
                                               boolean tokenStarted, List<String> parts) {
        if (quote != 0) {
            throw new IllegalArgumentException("No closing quotation");
        }
        if (tokenStarted) {
            parts.add(current.toString());
        }
    }
    private static void printStub(String command, String[] arguments) {
        System.out.print("Команда: " + command);
        if (arguments.length == 0) {
            System.out.println(";\nаргументы: нет");
        } else {
            System.out.println(";\nаргументы: " + String.join(" ", arguments));
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
        System.out.println("  --vfs, -v <path>       путь к физическому приложению VFS");
        System.out.println("  --prompt, -p <text>    пользовательское приглашение");
        System.out.println("  --startup, -s <path>   путь к стартовому скрипту");
        System.out.println("  --config, -c <path>    путь к JSON-конфигурации");
        System.out.println("  --help, -h             показать эту справку");
        System.out.println();
        System.out.println("В prompt поддерживаются подстановки {username} и {hostname}.");
        System.out.println("Значения из config.json имеют приоритет над параметрами CLI.");
    }
    private static class EffectiveConfig {
        String vfsPath;
        String promptTemplate;
        String startupScript;
        String prompt;
        Path vfsDirectory;
    }
}