#!/bin/bash


mkdir -p out
javac -d out src/Shell.java
if [ $? -eq 0 ]; then
    java -cp out Shell
else
    echo "Ошибка компиляции."
fi