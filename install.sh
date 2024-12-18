#!/bin/bash


# Use the JAVA_CMD environment variable if defined, otherwise try JAVA_HOME
if [ -n "$JAVACMD" ]; then
  _JAVACMD="$JAVACMD"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  _JAVACMD="$JAVA_HOME/bin/java"
else
  _JAVACMD="java"
fi

# Parse Java version
JAVA_VERSION=0
java_version_output=$("$_JAVACMD" -Xms32M -Xmx32M -version 2>&1 | grep -i version)
JAVA_VERSION=$(echo "$java_version_output" | awk -F '"' '{print $2}' | awk -F[.-] '{print ($1 == "1" ? $2 : $1)}')

# Check if Java is JDK (if javac command is available)
if command -v javac &> /dev/null; then
  echo "Java JDK is installed."
else
  echo "Please install Java JDK 11+ and re-run this script."
  exit 1
fi

# Check if Java version is 11 or higher
if [ "$JAVA_VERSION" -lt 11 ]; then
  echo "Java version 11 or higher is required."
  echo "Please install JDK 11 or higher and re-run this script."
  exit 1
fi

# Function to download and set up Maven temporarily
setup_maven() {
  echo "Maven not found."
  read -p "Would you like to download a portable Maven version? (Y/N) " MAVEN_CHOICE
  if [[ "$MAVEN_CHOICE" =~ ^[Yy]$ ]]; then
    echo "Downloading Maven..."
    MAVEN_VERSION=3.8.4
    MAVEN_BASE_URL=https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries
    MAVEN_FILE=apache-maven-$MAVEN_VERSION-bin.tar.gz

    # Create a temporary directory for Maven
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR" || exit

    # Download and extract Maven
    wget "$MAVEN_BASE_URL/$MAVEN_FILE"
    tar -xzf "$MAVEN_FILE"
    
    # Set up Maven environment variable
    export MAVEN_HOME="$TEMP_DIR/apache-maven-$MAVEN_VERSION"
    export PATH="$MAVEN_HOME/bin:$PATH"
    echo "Temporary Maven setup complete. Don’t forget to add it to your system PATH later if needed."
  else
    echo "Please install Maven manually and re-run this script."
    exit 1
  fi
}

# Function to download and set up npm temporarily
setup_npm() {
  echo "npm not found."
  read -p "Would you like to download Node.js (which includes npm) temporarily? (Y/N) " NPM_CHOICE
  if [[ "$NPM_CHOICE" =~ ^[Yy]$ ]]; then
    echo "Downloading Node.js and npm..."
    NODE_VERSION=v16.14.0
    NODE_BASE_URL=https://nodejs.org/dist/$NODE_VERSION
    NODE_FILE=node-$NODE_VERSION-linux-x64.tar.gz

    # Create a temporary directory for Node.js/npm
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR" || exit

    # Download and extract Node.js/npm
    wget "$NODE_BASE_URL/$NODE_FILE"
    tar -xzf "$NODE_FILE"
    
    # Set up npm environment variable
    export NODE_HOME="$TEMP_DIR/node-$NODE_VERSION-linux-x64"
    export PATH="$NODE_HOME/bin:$PATH"
    echo "Temporary Node.js and npm setup complete. Don’t forget to add them to your system PATH later if needed."
  else
    echo "Please install npm manually and re-run this script."
    exit 1
  fi
}

# Check if Maven is installed
if command -v mvn &> /dev/null; then
  echo "Maven is already installed."
else
  setup_maven
fi

# Check if npm is installed
if command -v npm &> /dev/null; then
  echo "npm is already installed."
else
  setup_npm
fi

# Install project dependencies
echo "Installing all the dependencies for the project. This will take some time, please be patient."

echo "Installing simulator..."
cd ./simulator || exit
mvn clean install -Dmaven.compiler.source="$JAVA_VERSION" -Dmaven.compiler.target="$JAVA_VERSION"
cd ..

echo "Installing executor..."
cd ./executor || exit
mvn clean package -Dmaven.compiler.source="$JAVA_VERSION" -Dmaven.compiler.target="$JAVA_VERSION"
cd ..

echo "Installing webapp..."
cd ./webapp || exit
cd ./frontend || exit
npm install
npm run build
cd ..

cd ./backend || exit
npm install

echo "All dependencies installed successfully!"
