#!/usr/bin/env bash
set -euo pipefail

echo "=== Тест приоритета: конфиг переопределяет CLI ==="

# Создаём конфиг
cat > /tmp/priority_config.json <<'EOF'
{
  "vfsPath": "/FROM_CONFIG",
  "prompt": "CONFIG_PROMPT> ",
  "startupScript": null
}
EOF

echo ""
echo "--- CLI задаёт свои значения, конфиг их переопределяет ---"
echo "exit" | java -cp out Shell \
    --vfs /FROM_CLI \
    --prompt "CLI_PROMPT> " \
    --config /tmp/priority_config.json

echo ""
echo "--- Конфиг содержит null -> используются значения CLI ---"
cat > /tmp/null_config.json <<'EOF'
{
  "vfsPath": null,
  "prompt": null,
  "startupScript": null
}
EOF
echo "exit" | java -cp out Shell \
    --vfs /FROM_CLI \
    --prompt "CLI_PROMPT> " \
    --config /tmp/null_config.json

echo ""
echo "--- Конфиг пустой -> используются значения CLI ---"
cat > /tmp/empty_config.json <<'EOF'
{}
EOF
echo "exit" | java -cp out Shell \
    --vfs /FROM_CLI \
    --prompt "CLI_PROMPT> " \
    --config /tmp/empty_config.json

echo ""
echo "--- Несуществующий конфиг -> ошибка чтения ---"
echo "exit" | java -cp out Shell --config /tmp/no_such_file.json 2>&1 || true

echo ""
echo "--- Невалидный JSON ---"
echo "not json" > /tmp/bad.json
echo "exit" | java -cp out Shell --config /tmp/bad.json 2>&1 || true

echo ""
echo "=== Тесты приоритета завершены ==="