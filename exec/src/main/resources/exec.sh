#!/usr/bin/env bash

# consider http://blog.sokolenko.me/2014/11/javavm-options-production.html for additional options
java -server -Dlogback.configurationFile=/config/logback.xml -cp /config -jar /app/agora-exec.jar $@