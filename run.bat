@echo off
if not exist out mkdir out
javac -d out src\Shell.java
if %errorlevel% neq 0 (
    echo Ошибка компиляции.
    exit /b %errorlevel%
)
java -cp out Shell