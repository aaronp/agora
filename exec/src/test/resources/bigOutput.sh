#!/usr/bin/env bash
DIR=$1
N=$2

echo "searching under $DIR $N times"
for i in $(seq 0 1 `echo $((N))`); do
  find $DIR -name '*.scala' -exec cat {} \;
done
echo "DONE!"

