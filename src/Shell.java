import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.net.UnknownHostException;
public class Shell {
    public static void main(String[] args) {
        String username = System.getProperty("user.name", "user");
        String hostname = getHostname();
        Path currentDirectory = Paths.get("").toAbsolutePath().normalize();
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.print(username + "@" + hostname + ":~$ ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }
                List<String> parts;
                try {
                    parts = parseCommand(input);
                } catch (IllegalArgumentException e) {
                    System.out.println("No closing quotation");
                    continue;
                }
                String command = parts.get(0);
                String[] arguments = parts.subList(1, parts.size()).toArray(new String[0]);
                switch (command) {
                    case "ls", "cd" -> printStub(command, arguments);

                    case "exit" -> {
                        if (arguments.length > 0)
                            System.out.println("Ошибка: exit без аргументов.");
                        else {
                            running = false;
                            System.out.println("Shell завершен.");
                        }
                    }
                    default -> System.out.println("Ошибка: неизвестная команда: " + command);
                }
            }
        }
        System.out.println("Программа завершена.");
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
}
