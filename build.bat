@echo off
REM Script di compilazione - Connections

REM Trova JAVA_HOME dal runtime java
for /f "tokens=2 delims==" %%a in ('java -XshowSettings:properties 2^>^&1 ^| findstr "java.home"') do set JAVA_HOME_DETECTED=%%a
REM Rimuovi spazi iniziali
for /f "tokens=*" %%b in ("%JAVA_HOME_DETECTED%") do set JAVA_HOME_DETECTED=%%b

if not defined JAVA_HOME_DETECTED (
    echo [ERRORE] Impossibile determinare la directory del JDK.
    exit /b 1
)

REM jar.exe puo' essere in JAVA_HOME/bin o JAVA_HOME/../bin
set JAR_CMD=
if exist "%JAVA_HOME_DETECTED%\bin\jar.exe" (
    set "JAR_CMD=%JAVA_HOME_DETECTED%\bin\jar.exe"
) else (
    for %%d in ("%JAVA_HOME_DETECTED%\..") do set "JDK_PARENT=%%~fd"
)
if not defined JAR_CMD if defined JDK_PARENT (
    if exist "%JDK_PARENT%\bin\jar.exe" (
        set "JAR_CMD=%JDK_PARENT%\bin\jar.exe"
    )
)
if not defined JAR_CMD (
    echo [ERRORE] jar.exe non trovato. Verifica l'installazione del JDK.
    exit /b 1
)

if not exist lib\gson-2.10.1.jar (
    echo [ERRORE] lib\gson-2.10.1.jar non trovato.
    exit /b 1
)

if exist build rmdir /S /Q build
if exist ConnectionsServer.jar del ConnectionsServer.jar
if exist ConnectionsClient.jar del ConnectionsClient.jar
mkdir build

javac -d build -cp lib\gson-2.10.1.jar src\server\*.java
if errorlevel 1 (
    echo [ERRORE] Compilazione server fallita.
    exit /b 1
)

javac -d build -cp lib\gson-2.10.1.jar src\client\*.java
if errorlevel 1 (
    echo [ERRORE] Compilazione client fallita.
    exit /b 1
)

cd build
"%JAR_CMD%" cfm ..\ConnectionsServer.jar ..\manifests\server_manifest.txt server\
if errorlevel 1 (
    cd ..
    echo [ERRORE] Creazione ConnectionsServer.jar fallita.
    exit /b 1
)
"%JAR_CMD%" cfm ..\ConnectionsClient.jar ..\manifests\client_manifest.txt client\
if errorlevel 1 (
    cd ..
    echo [ERRORE] Creazione ConnectionsClient.jar fallita.
    exit /b 1
)
cd ..

echo Build completata.
