#!/usr/bin/env bash
FROM=$1
TO=$2
DELAY=$3
NUMS=$(seq `echo $((FROM))` 1 `echo $((TO))`)
for i in $NUMS
do
  echo $i
  sleep $DELAY
done
