#!/usr/bin/env bash

# consider http://blog.sokolenko.me/2014/11/javavm-options-production.html for additional options
java -server -Dlogback.configurationFile=/app/config/logback.xml -cp /app/config -jar /app/bin/agora-exec.jar $@