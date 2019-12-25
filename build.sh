#!/usr/bin/env bash
mvn clean package
dirs=( main cache video-processor-jcodec )
files=( main cache jcodecvp )
mkdir run
for index in ${!dirs[*]}; do
  cp ./${dirs[$index]}/target/${dirs[$index]}-1.0-allinone.jar ./run/${files[$index]}.jar
done
mvn clean