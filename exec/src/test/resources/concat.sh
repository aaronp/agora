#!/usr/bin/env bash

for i in "$@"; do
  eval "cat $i"
done

