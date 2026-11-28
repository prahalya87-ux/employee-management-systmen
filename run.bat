@echo off
setlocal
cd /d "%~dp0"

set JDK=
if exist "%ProgramFiles%\Java\jdk-25.0.4.1\bin\javac.exe" set JDK=%ProgramFiles%\Java\jdk-25.0.4.1
if not defined JDK (
    for /d %%d in ("%ProgramFiles%\Java\jdk*") do set JDK=%%d
)
if not defined JDK set JDK=%JAVA_HOME%

if not exist "%JDK%\bin\javac.exe" (
    echo Could not find a JDK. Set JAVA_HOME or edit run.bat.
    pause
    exit /b 1
)

echo Compiling...
"%JDK%\bin\javac.exe" -encoding UTF-8 -cp "lib\*" -d out src\*.java
if errorlevel 1 ( echo Compile failed. & pause & exit /b 1 )

"%JDK%\bin\java.exe" -cp "out;lib\*" src.WebServer
pause
