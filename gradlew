#!/bin/sh
#
# Minimal Gradle wrapper script for this demo repo.
# It expects `gradle/wrapper/gradle-wrapper.jar` to exist (we generate/extract it if missing).

set -eu

PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' >/dev/null; then
    PRG="$link"
  else
    PRG="$(dirname "$PRG")/$link"
  fi
done

APP_HOME=$(cd "$(dirname "$PRG")" && pwd -P)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar:$APP_HOME/gradle/wrapper/gradle-wrapper-shared.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "Missing Gradle wrapper jar: $CLASSPATH" 1>&2
  exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" ${DEFAULT_JVM_OPTS:-} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

