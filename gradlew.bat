@echo off
setlocal

set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar;%APP_HOME%gradle\wrapper\gradle-wrapper-shared.jar

if not exist "%CLASSPATH%" (
  echo Missing Gradle wrapper jar: %CLASSPATH%
  exit /b 1
)

set JVM_OPTS=
if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java
) else (
  set JAVACMD=java
)

%JAVACMD% %JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
