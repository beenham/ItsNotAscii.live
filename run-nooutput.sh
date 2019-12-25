#!/usr/bin/env bash
nohup java -cp ./run/main.jar live.itsnotascii.Main -n itsnotascii -v &
nohup java -cp ./run/cache.jar live.itsnotascii.CacheMain -n itsnotascii -p 25521 -v &
nohup java -cp ./run/jcodecvp.jar live.itsnotascii.JCodecProcessorMain -n itsnotascii -p 25522 -v