#!/bin/bash
# Script di compilazione - Connections

set -e

if [ ! -f lib/gson-2.10.1.jar ]; then
    echo "[ERRORE] lib/gson-2.10.1.jar non trovato."
    exit 1
fi

rm -rf build ConnectionsServer.jar ConnectionsClient.jar
mkdir -p build

javac -d build -cp lib/gson-2.10.1.jar src/server/*.java
javac -d build -cp lib/gson-2.10.1.jar src/client/*.java

cd build
jar cfm ../ConnectionsServer.jar ../manifests/server_manifest.txt server/
jar cfm ../ConnectionsClient.jar ../manifests/client_manifest.txt client/
cd ..

echo "Build completata."
