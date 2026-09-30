#!/usr/bin/env bash
set -euo pipefail

echo "=== Тест стартового скрипта ==="

# Скрипт с комментариями и командами
cat > /tmp/startup_full.sh <<'EOF'
# Это комментарий в начале
ls
# Комментарий в середине
cd /tmp
ls -la
# Выход из оболочки
exit
# Эта строка не должна выполниться
ls
EOF

echo ""
echo "--- Запуск полного скрипта ---"
java -cp out Shell --startup /tmp/startup_full.sh

echo ""
echo "--- Скрипт с ошибкой (неизвестная команда) ---"
cat > /tmp/startup_error.sh <<'EOF'
ls
bad_command
cd /tmp
EOF
java -cp out Shell --startup /tmp/startup_error.sh

echo ""
echo "--- Пустой скрипт ---"
cat > /tmp/startup_empty.sh <<'EOF'
EOF
java -cp out Shell --startup /tmp/startup_empty.sh

echo ""
echo "--- Скрипт только с комментариями ---"
cat > /tmp/startup_comments.sh <<'EOF'
# comment 1
# comment 2
EOF
java -cp out Shell --startup /tmp/startup_comments.sh

echo ""
echo "--- Несуществующий скрипт ---"
java -cp out Shell --startup /tmp/no_such_script.sh

echo ""
echo "=== Тесты стартового скрипта завершены ==="