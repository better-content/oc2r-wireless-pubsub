@echo off

set DIR=%~dp0

set APP_BASE_NAME=%~n0
set APP_HOME=%DIR%

@rem Add default JVM options here. You can edit this file to customize your JVM settings.
set DEFAULT_JVM_OPTS=

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set JAVACMD=java

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" set JAVACMD=%JAVA_HOME%\bin\java.exe
)

%JAVACMD% %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
