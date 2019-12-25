#!/usr/bin/env bash
mvn clean package
dirs=( main cache video-processor-jcodec )
files=( main cache jcodecvp )
mkdir run
for index in ${!dirs[*]}; do
  cp ./${dirs[$index]}/target/${dirs[$index]}-1.0-allinone.jar ./run/${files[$index]}.jar
done
mvn clean
>>>>>>> 2e4b229a4dc91fc89208c9ebe7ff37006a7c8760
