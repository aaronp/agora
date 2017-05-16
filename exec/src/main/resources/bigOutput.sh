#!/usr/bin/env bash
N=$1

for i in $(seq 0 1 `echo $((N))`); do
  find ../../ -name '*.scala' -exec cat {} \;
done

