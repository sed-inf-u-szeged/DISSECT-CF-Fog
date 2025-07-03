@echo off
setlocal

rem We use the value the JAVACMD environment variable, if defined
rem and then try JAVA_HOME
set "_JAVACMD=%JAVACMD%"
if "%_JAVACMD"=="" (
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)
if "%_JAVACMD%"=="" set _JAVACMD=java

rem Parses x out of 1.x; for example 8 out of java version 1.8.0_xx
rem Otherwise, parses the major version; 9 out of java version 9-ea
set JAVA_VERSION=0
for /f "tokens=3" %%g in ('%_JAVACMD% -Xms32M -Xmx32M -version 2^>^&1 ^| findstr /i "version"') do (
  set JAVA_VERSION=%%g
)
set JAVA_VERSION=%JAVA_VERSION:"=%
for /f "delims=.-_ tokens=1-2" %%v in ("%JAVA_VERSION%") do (
  if /I "%%v" EQU "1" (
    set JAVA_VERSION=%%w
  ) else (
    set JAVA_VERSION=%%v
  )
)


rem Check if java is JDK
rem Check if javac (Java compiler) is available
for /f "tokens=*" %%i in ('javac -version 2^>nul') do set JDK=true
if not defined JDK (
    call Please install Java JDK 11+ and re-run this script.
    pause
    exit /b 1
) else (
    echo Java JDK is installed.
)

rem Check if java version is 11 or higher
if %JAVA_VERSION% LSS 11 (
    echo Java version 11 or higher is required.
    echo Please install JDK 11 or higher and re-run this script.
    pause
    exit /b 1
)

goto :start

rem Function to download and set up Maven temporarily
:setup_maven
    echo Maven not found.
    echo Would you like to download a portable Maven version? (Y/N)
    set /p MAVEN_CHOICE="> "
    if /I "%MAVEN_CHOICE%"=="Y" (
        echo Downloading Maven...
        set MAVEN_VERSION=3.8.4
        set MAVEN_BASE_URL=https://downloads.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries
        set MAVEN_FILE=apache-maven-%MAVEN_VERSION%-bin.zip

        rem Create a temporary directory for Maven
        set TEMP_DIR=%TEMP%\maven_temp
        mkdir %TEMP_DIR%
        cd %TEMP_DIR%

        rem Download and extract Maven
        powershell -command "(New-Object Net.WebClient).DownloadFile('%MAVEN_BASE_URL%/%MAVEN_FILE%', '%TEMP_DIR%\%MAVEN_FILE%')"
        powershell Expand-Archive -Path '%TEMP_DIR%\%MAVEN_FILE%' -DestinationPath '%TEMP_DIR%'

        rem Set up Maven environment variable
        set MAVEN_HOME=%TEMP_DIR%\apache-maven-%MAVEN_VERSION%
        set PATH=%MAVEN_HOME%\bin;%PATH%
        echo Temporary Maven setup complete. Don't forget to add it to your system PATH later if needed.
    ) else (
        echo Please install Maven manually and re-run this script.
        pause
        exit /b 1
    )
    goto :eomi

rem Function to download and set up npm temporarily
:setup_npm
    echo npm not found.
    echo Would you like to download Node.js (which includes npm) temporarily? (Y/N)
    set /p NPM_CHOICE="> "
    if /I "%NPM_CHOICE%"=="Y" (
        echo Downloading Node.js and npm...
        set NODE_VERSION=v16.14.0
        set NODE_BASE_URL=https://nodejs.org/dist/%NODE_VERSION%/node-%NODE_VERSION%-win-x64.zip
        set NODE_FILE=node-%NODE_VERSION%-win-x64.zip

        rem Create a temporary directory for Node.js/npm
        set TEMP_DIR=%TEMP%\node_temp
        mkdir %TEMP_DIR%
        cd %TEMP_DIR%

        rem Download and extract Node.js/npm
        powershell -command "(New-Object Net.WebClient).DownloadFile('%NODE_BASE_URL%', '%TEMP_DIR%\%NODE_FILE%')"
        powershell Expand-Archive -Path '%TEMP_DIR%\%NODE_FILE%' -DestinationPath '%TEMP_DIR%'

        rem Set up npm environment variable
        set NODE_HOME=%TEMP_DIR%\node-%NODE_VERSION%-win-x64
        set PATH=%NODE_HOME%;%NODE_HOME%\bin;%PATH%
        echo Temporary Node.js and npm setup complete. Don't forget to add them to your system PATH later if needed.
    ) else (
        echo Please install npm manually and re-run this script.
        pause
        exit /b 1
    )
    goto :eonpmi

:start
rem Check if mvn (Maven) is installed correctly
for /f "tokens=*" %%i in ('mvn -version 2^>nul') do set MAVEN_INSTALLED=true
if not defined MAVEN_INSTALLED (
    call :setup_maven
) else (
    echo Maven is already installed.
)

:eomi

rem Check if npm (Node.js) is installed correctly
for /f "tokens=*" %%i in ('npm -v 2^>nul') do set NPM_INSTALLED=true
if not defined NPM_INSTALLED (
    call :setup_npm
) else (
    echo npm is already installed.
)

:eonpmi

rem Run the script to install the dependencies
echo Installing all the dependencies for the project
echo This will take some time, please be patient

echo Installing root Maven project...
cd ..
call mvn clean install -Dmaven.compiler.source=%JAVA_VERSION% -Dmaven.compiler.target=%JAVA_VERSION%
cd modules

echo Installing executor...
cd ./executor
call mvn clean package -Dmaven.compiler.source=%JAVA_VERSION% -Dmaven.compiler.target=%JAVA_VERSION%
cd ..

echo Installing webapp frontend...
cd ./webapp/frontend
call npm install
call npm run build
cd ../..

echo Installing webapp backend...
cd ./webapp/backend
call npm install

echo All dependencies installed successfully!
pause
