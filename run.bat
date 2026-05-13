@echo off
title HaloDiscordBot Launcher

cd /d "%~dp0"

set JAR=build\libs\HaloDiscordBot.jar

if not exist "%JAR%" (
    echo ERROR: Jar not found at %JAR%
    echo Make sure you've built the project first.
    pause
    exit /b 1
)

java -jar "%JAR%"

echo.
echo Bot stopped or crashed.
pause