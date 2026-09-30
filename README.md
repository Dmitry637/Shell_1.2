# Эмулятор командной оболочки UNIX-подобной ОС

## Этап 2 — Конфигурация

В этом этапе REPL из этапа 1 был дополнен настройкой через параметры командной строки и JSON-конфигурационный файл.

Проект остаётся простым: весь основной код находится в одном файле `src/Shell.java`, внешние Java-библиотеки не требуются.

## Что реализовано

### Параметры командной строки

Поддерживаются четыре основных параметра:

```text
--vfs, -v <path>       путь к физическому приложению VFS
--prompt, -p <text>    пользовательское приглашение REPL
--startup, -s <path>   путь к стартовому скрипту
--config, -c <path>    путь к JSON-конфигурации
```

Также есть `--help` / `-h`.

Поддерживаются две формы записи:

```text
--vfs ./vfs
--vfs=./vfs
```

В пользовательском приглашении можно использовать:

```text
{username}
{hostname}
```

Например:

```text
{username}@{hostname}:config$ 
```

### JSON-конфигурация

Пример `config.json`:

```json
{
  "vfsPath": "./vfs",
  "prompt": "{username}@{hostname}:config$ ",
  "startupScript": "./tests/startup.shell"
}
```

Конфигурационный файл содержит три параметра:

- `vfsPath`
- `prompt`
- `startupScript`

`configPath` передаётся только через CLI, потому что сначала нужно указать программе, какой конфигурационный файл открыть.

### Приоритет конфигурации

Если одно и то же значение указано и в CLI, и в JSON, используется значение из JSON.

Например:

```bash
./run.sh --prompt '[CLI]$ ' --config ./config.json
```

Если в `config.json` задан другой `prompt`, он имеет приоритет.

Если параметр не указан ни в JSON, ни в CLI, используется значение по умолчанию:

- VFS: `./vfs`
- prompt: `{username}@{hostname}:~$ `
- стартовый скрипт: отсутствует

### Стартовый скрипт

Стартовый скрипт выполняется **до перехода в интерактивный REPL**.

Поддерживаются пустые строки, комментарии с `#` и комментарии после пробела вне кавычек, например:

```text
# Проверка запуска
ls
cd /tmp
ls "hello world"
```

Перед каждой командой стартового скрипта печатается тот же prompt, поэтому выполнение визуально похоже на ручной ввод:

```text
student@pc:config$ ls
Команда: ls;
аргументы: нет
```

Команды стартового скрипта проходят через тот же обработчик, что и интерактивные команды. Поэтому для них работают `ls`, `cd`, `exit`, неизвестные команды и ошибка `No closing quotation`.

При ошибке выполнения выводится дополнительное сообщение с номером строки скрипта.

### Обработка ошибок конфигурации

Если файл конфигурации не найден, повреждён или содержит некорректный JSON, программа сообщает об ошибке чтения и продолжает запуск, используя доступные CLI-параметры и значения по умолчанию.

JSON разбирается небольшим встроенным парсером прямо в `Shell.java`, поэтому подключать стороннюю библиотеку JSON не требуется.

## Запуск

### Linux / macOS

```bash
chmod +x run.sh
./run.sh
```

С параметрами:

```bash
./run.sh --vfs ./vfs --prompt '[TEST]$ ' --startup ./tests/startup.simple
```

С конфигурацией:

```bash
./run.sh --config ./config.json
```

### Windows

```bat
run.bat
```

С параметрами:

```bat
run.bat --vfs .\vfs --prompt "[TEST]$ " --startup .\tests\startup.simple
```

## Пример интерактивной работы

```text
student@pc:~$ ls -la
Команда: ls;
аргументы: -la

student@pc:~$ ls "hello world"
Команда: ls;
аргументы: hello world

student@pc:~$ unknown
Ошибка: неизвестная команда: unknown

student@pc:~$ ls "ff
No closing quotation

student@pc:~$ exit test
Ошибка: exit без аргументов.

student@pc:~$ exit
Shell завершен.
Программа завершена.
```

## Структура проекта

```text
Shell_1.2-master/
├── src/
│   └── Shell.java
├── tests/
│   ├── startup.shell
│   ├── startup.simple
│   ├── bad-config.json
│   ├── Java_tests
│   └── scripts/
│       ├── test_cli_vfs.sh
│       ├── test_cli_prompt.sh
│       ├── test_cli_startup.sh
│       ├── test_cli_all.sh
│       ├── test_cli_config.sh
│       └── test_bad_config.sh
├── vfs/
│   └── README.txt
├── config.json
├── run.sh
├── run.bat
└── README.md
```

## Проверка этапа 2

Для быстрой проверки можно последовательно запустить:

```bash
./tests/scripts/test_cli_vfs.sh
./tests/scripts/test_cli_prompt.sh
./tests/scripts/test_cli_startup.sh
./tests/scripts/test_cli_config.sh
./tests/scripts/test_bad_config.sh
```

Скрипты проверяют:

1. передачу пути VFS через CLI;
2. передачу пользовательского prompt через CLI;
3. передачу стартового скрипта через CLI;
4. работу конфигурационного файла и приоритет JSON над CLI;
5. ошибку чтения некорректного JSON.

## Соответствие требованиям этапа 2

| Требование | Реализация |
|---|---|
| Путь к VFS через CLI | `--vfs` / `-v` |
| Пользовательский prompt через CLI | `--prompt` / `-p` |
| Путь к стартовому скрипту через CLI | `--startup` / `-s` |
| Путь к JSON-конфигурации через CLI | `--config` / `-c` |
| JSON с тремя настройками | `vfsPath`, `prompt`, `startupScript` |
| Чтение CLI и config | `parseLaunchArguments()` + `readConfig()` |
| Приоритет config над CLI | `firstNonNull(config, cli, default)` |
| Ошибка чтения config | `try/catch` в `main` |
| Комментарии в startup script | строки, начинающиеся с `#` |
| Выполнение startup-команд как интерактивных | общий метод `executeCommand()` |
| Ошибки startup script | сообщение с номером строки |
| Скрипты тестирования | `tests/scripts/*.sh` |

## Рекомендуемый commit

```text
feat: add stage 2 configuration support
```
