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
        LaunchOptions cliOptions;
        try {
            cliOptions = parseLaunchArguments(args);
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка параметров командной строки: " + e.getMessage());
            printUsage();
            return;
        }

        if (cliOptions.helpRequested) {
            printUsage();
            return;
        }

        String username = System.getProperty("user.name", "user");
        String hostname = getHostname();

        ConfigValues configValues = new ConfigValues();
        if (cliOptions.configPath != null) {
            try {
                configValues = readConfig(Paths.get(cliOptions.configPath));
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("Ошибка чтения конфигурационного файла '"
                        + cliOptions.configPath + "': " + e.getMessage());
            }
        }

        // Значения конфигурационного файла имеют приоритет над CLI.
        String vfsPath = firstNonNull(configValues.vfsPath, cliOptions.vfsPath, DEFAULT_VFS_PATH);
        String promptTemplate = firstNonNull(configValues.prompt, cliOptions.prompt, DEFAULT_PROMPT);
        String startupScript = firstNonNull(configValues.startupScript, cliOptions.startupScript, null);

        Path vfsDirectory = Paths.get(vfsPath).toAbsolutePath().normalize();
        String prompt = buildPrompt(promptTemplate, username, hostname);

        System.out.println("VFS: " + vfsDirectory);
        if (startupScript != null) {
            System.out.println("Стартовый скрипт: "
                    + Paths.get(startupScript).toAbsolutePath().normalize());
        }

        boolean running = true;

        if (startupScript != null) {
            running = runStartupScript(
                    Paths.get(startupScript),
                    prompt,
                    vfsDirectory
            );
        }

        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                System.out.print(prompt);

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

        System.out.println("Программа завершена.");
    }

    private static LaunchOptions parseLaunchArguments(String[] args) {
        LaunchOptions options = new LaunchOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--help") || arg.equals("-h")) {
                options.helpRequested = true;
                continue;
            }

            if (arg.startsWith("--vfs=")) {
                options.vfsPath = requireOptionValue("--vfs", arg.substring("--vfs=".length()));
            } else if (arg.equals("--vfs") || arg.equals("-v")) {
                options.vfsPath = requireNextValue(args, ++i, "--vfs");
            } else if (arg.startsWith("--prompt=")) {
                options.prompt = requireOptionValue("--prompt", arg.substring("--prompt=".length()));
            } else if (arg.equals("--prompt") || arg.equals("-p")) {
                options.prompt = requireNextValue(args, ++i, "--prompt");
            } else if (arg.startsWith("--startup=")) {
                options.startupScript = requireOptionValue("--startup", arg.substring("--startup=".length()));
            } else if (arg.equals("--startup") || arg.equals("-s")) {
                options.startupScript = requireNextValue(args, ++i, "--startup");
            } else if (arg.startsWith("--config=")) {
                options.configPath = requireOptionValue("--config", arg.substring("--config=".length()));
            } else if (arg.equals("--config") || arg.equals("-c")) {
                options.configPath = requireNextValue(args, ++i, "--config");
            } else {
                throw new IllegalArgumentException("неизвестный параметр: " + arg);
            }
        }

        return options;
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
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
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
        skipWhitespace(json, index);

        if (peek(json, index) == '}') {
            index[0]++;
            skipWhitespace(json, index);
            if (index[0] != json.length()) {
                throw new IllegalArgumentException("лишние символы после JSON-объекта");
            }
            return result;
        }

        while (true) {
            skipWhitespace(json, index);
            String key = parseJsonString(json, index);
            skipWhitespace(json, index);
            expect(json, index, ':');
            skipWhitespace(json, index);

            if (peek(json, index) == 'n' && json.startsWith("null", index[0])) {
                index[0] += 4;
                result.put(key, null);
            } else {
                result.put(key, parseJsonString(json, index));
            }

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

        skipWhitespace(json, index);
        if (index[0] != json.length()) {
            throw new IllegalArgumentException("лишние символы после JSON-объекта");
        }

        return result;
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
            } else {
                result.append(ch);
            }
        }

        throw new IllegalArgumentException("незакрытая строка в JSON");
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

    private static boolean runStartupScript(
            Path startupPath,
            String prompt,
            Path vfsDirectory
    ) {
        if (!Files.isRegularFile(startupPath)) {
            System.out.println("Ошибка чтения стартового скрипта '" + startupPath + "': файл не найден.");
            return true;
        }

        try (BufferedReader reader = Files.newBufferedReader(startupPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String command = line.trim();

                // В стартовом скрипте используется стандартный для UNIX-подобных shell синтаксис комментариев: #.
                if (command.isEmpty() || command.startsWith("#")) {
                    continue;
                }

                System.out.println(prompt + command);

                ExecutionResult result = executeCommand(command);
                if (result == ExecutionResult.ERROR) {
                    System.out.println("Ошибка выполнения стартового скрипта, строка "
                            + lineNumber + ".");
                }
                if (result == ExecutionResult.EXIT) {
                    return false;
                }
            }

            return true;
        } catch (IOException e) {
            System.out.println("Ошибка чтения стартового скрипта '" + startupPath + "': " + e.getMessage());
            return true;
        }
    }

    private static ExecutionResult executeCommand(String input) {
        List<String> parts;

        try {
            parts = parseCommand(input);
        } catch (IllegalArgumentException e) {
            System.out.println("No closing quotation");
            return ExecutionResult.ERROR;
        }

        if (parts.isEmpty()) {
            return ExecutionResult.CONTINUE;
        }

        String command = parts.get(0);
        String[] arguments = parts.subList(1, parts.size()).toArray(new String[0]);

        switch (command) {
            case "ls", "cd" -> {
                printStub(command, arguments);
                return ExecutionResult.CONTINUE;
            }

            case "exit" -> {
                if (arguments.length > 0) {
                    System.out.println("Ошибка: exit без аргументов.");
                    return ExecutionResult.ERROR;
                }

                System.out.println("Shell завершен.");
                return ExecutionResult.EXIT;
            }

            default -> {
                System.out.println("Ошибка: неизвестная команда: " + command);
                return ExecutionResult.ERROR;
            }
        }
    }

    private static List<String> parseCommand(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                tokenStarted = true;
                continue;
            }

            if (ch == '"' || ch == '\'') {
                quote = ch;
                tokenStarted = true;
            } else if (ch == '#' && (i == 0 || Character.isWhitespace(input.charAt(i - 1)))) {
                // В стартовых скриптах # начинает комментарий до конца строки.
                break;
            } else if (Character.isWhitespace(ch)) {
                if (tokenStarted) {
                    parts.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
            } else {
                current.append(ch);
                tokenStarted = true;
            }
        }

        if (quote != 0) {
            throw new IllegalArgumentException("No closing quotation");
        }

        if (tokenStarted) {
            parts.add(current.toString());
        }

        return parts;
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
}
