#!/bin/sh
                
TARGET=$(readlink -f "key-abbrevmgr-0.1")
                
mkdir -p $TARGET
function download() {
    # Use curl or wget
    if command -v wget >/dev/null 2>&1; then
        wget -timestamping -O    "$2" "$1"
    elif command -v curl >/dev/null 2>&1; then
        curl -L -o "$2" "$1"
    else
        echo "Error: Neither curl nor wget is installed."
        exit 1
    fi
}
            
download 'https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.2.10/kotlin-stdlib-2.2.10.jar'\
         "$TARGET/kotlin-stdlib-2.2.10.jar"
download 'https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/1.9.0/kotlinx-serialization-json-1.9.0.jar'\
         "$TARGET/kotlinx-serialization-json-1.9.0.jar"
download 'https://repo.maven.apache.org/maven2/io/github/wadoon/key/key-abbrevmgr/0.1/key-abbrevmgr-0.1.jar'\
         "$TARGET/key-abbrevmgr-0.1.jar"

                 
echo "Extend your classpath by following Jars, either using the whole folder, or by single Jars."
echo "java -cp key-2.14.4-dev.jar:$TARGET/*"
echo "or: java -cp key-2.14.4-dev.jar:$TARGET/kotlin-stdlib-2.2.10.jar:$TARGET/kotlinx-serialization-json-1.9.0.jar:$TARGET/key-abbrevmgr-0.1.jar"                