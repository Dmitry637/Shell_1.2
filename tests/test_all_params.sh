#!/usr/bin/env bash
set -euo pipefail

echo "=== Тест 1: --help ==="
java -cp out Shell --help

echo ""
echo "=== Тест 2: -h (короткая форма) ==="
java -cp out Shell -h

echo ""
echo "=== Тест 3: --vfs с отдельным значением ==="
echo "exit" | java -cp out Shell --vfs /tmp/my_vfs

echo ""
echo "=== Тест 4: --vfs= (через =) ==="
echo "exit" | java -cp out Shell --vfs=/tmp/my_vfs

echo ""
echo "=== Тест 5: -v (короткая форма) ==="
echo "exit" | java -cp out Shell -v /tmp/my_vfs

echo ""
echo "=== Тест 6: --prompt ==="
echo "exit" | java -cp out Shell --prompt "my_shell> "

echo ""
echo "=== Тест 7: -p ==="
echo "exit" | java -cp out Shell -p ">>> "

echo ""
echo "=== Тест 8: --startup ==="
cat > /tmp/test_startup.sh <<'EOF'
# Тестовый стартовый скрипт
ls
cd /tmp
echo "script done"
EOF
chmod +x /tmp/test_startup.sh
java -cp out Shell --startup /tmp/test_startup.sh

echo ""
echo "=== Тест 9: -s ==="
java -cp out Shell -s /tmp/test_startup.sh

echo ""
echo "=== Тест 10: --config ==="
cat > /tmp/test_config.json <<'EOF'
{
  "vfsPath": "/config_vfs",
  "prompt": "cfg> ",
  "startupScript": "/tmp/test_startup.sh"
}
EOF
java -cp out Shell --config /tmp/test_config.json

echo ""
echo "=== Тест 11: -c ==="
java -cp out Shell -c /tmp/test_config.json

echo ""
echo "=== Тест 12: Все параметры вместе ==="
echo "exit" | java -cp out Shell \
    --vfs /tmp/vfs \
    --prompt "all> " \
    --startup /tmp/test_startup.sh \
    --config /tmp/test_config.json

echo ""
echo "=== Тест 13: Неизвестный параметр ==="
java -cp out Shell --unknown 2>&1 || true

echo ""
echo "=== Тест 14: Отсутствие значения ==="
java -cp out Shell --vfs 2>&1 || true

echo ""
echo "=== Тест 15: Пустое значение ==="
java -cp out Shell --vfs= 2>&1 || true

echo ""
echo "=== Все CLI-тесты завершены ==="