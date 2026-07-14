@echo off
REM easy-db CLI interactive tool wrapper for Windows
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%target\easy-db-server.jar
set CLASSES_DIR=%SCRIPT_DIR%target\classes

if not exist "%JAR_PATH%" (
    echo Building easy-db...
    cd /d "%SCRIPT_DIR%"
    mvn -q package -DskipTests >nul 2>&1
)

set CP=%CLASSES_DIR%;%JAR_PATH%
java -cp "%CP%" client.cli.CliClient %*
