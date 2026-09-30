@echo off
setlocal

cd /d "%~dp0"

echo ==========================================
echo        Shell Emulator - Stage 2
echo ==========================================
echo.

if not exist "out" mkdir "out"

echo Compiling...

javac -encoding UTF-8 -d out src\Shell.java

if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful.
echo.

java -cp out Shell %*

echo.
pause
endlocal