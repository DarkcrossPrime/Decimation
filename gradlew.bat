@echo off
setlocal
set GRADLE_VERSION=8.8
set BASE_DIR=%~dp0
set BOOT_DIR=%BASE_DIR%.gradle-wrapper
set DIST_DIR=%BOOT_DIR%\gradle-%GRADLE_VERSION%
set ZIP=%BOOT_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if exist "%DIST_DIR%\bin\gradle.bat" goto run

if not exist "%BOOT_DIR%" mkdir "%BOOT_DIR%"
echo Gradle %GRADLE_VERSION% is not cached; downloading it...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ZIP%'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BOOT_DIR%'"
if errorlevel 1 exit /b 1
del "%ZIP%"

:run
call "%DIST_DIR%\bin\gradle.bat" %*
endlocal
