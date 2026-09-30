#!/usr/bin/env bash
set -euo pipefail

echo "=== Тест команд эмулятора ==="

echo ""
echo "--- ls без аргументов ---"
echo "ls" | java -cp out Shell

echo ""
echo "--- ls с аргументами ---"
echo "ls -la /tmp" | java -cp out Shell

echo ""
echo "--- cd ---"
echo "cd /tmp" | java -cp out Shell

echo ""
echo "--- exit ---"
echo "exit" | java -cp out Shell

echo ""
echo "--- exit с аргументом (ошибка) ---"
printf "exit 1\nexit\n" | java -cp out Shell

echo ""
echo "--- Неизвестная команда ---"
printf "foobar\nexit\n" | java -cp out Shell

echo ""
echo "--- Пустая строка ---"
printf "\n\n\nexit\n" | java -cp out Shell

echo ""
echo "--- Команда с кавычками ---"
echo 'echo "hello world"' | java -cp out Shell

echo ""
echo "--- Команда с одинарными кавычками ---"
echo "echo 'hello world'" | java -cp out Shell

echo ""
echo "--- Незакрытая кавычка ---"
printf 'echo "unclosed\nexit\n' | java -cp out Shell

echo ""
echo "--- Комментарий в команде ---"
echo "ls # это комментарий" | java -cp out Shell

echo ""
echo "=== Тесты команд завершены ==="