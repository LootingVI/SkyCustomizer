@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo ERROR: JAVA_HOME is not set
exit /b 1
:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
:execute
"%JAVA_EXE%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
