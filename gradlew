#!/usr/bin/env sh

dirname=$(dirname "$0")
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$dirname" && pwd)

# Add default JVM options here.
DEFAULT_JVM_OPTS=""

# Find java executable
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/bin/java" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
  fi
fi
if [ -z "$JAVACMD" ] ; then
  JAVACMD=java
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
