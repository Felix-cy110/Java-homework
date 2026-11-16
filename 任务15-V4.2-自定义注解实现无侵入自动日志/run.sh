#!/bin/sh
# 固定当前任务为工作目录；源码及持久化数据不依赖其他任务。
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
mkdir -p out
find src -name '*.java' > out/sources.txt
"$JAVAC" -encoding UTF-8 -Xlint:all -classpath out -d out @out/sources.txt
exec "$JAVA" -Dfile.encoding=UTF-8 -cp out com.campus.reservation.SystemMain
