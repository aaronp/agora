#!/usr/bin/env bash
FROM=$1
TO=$2
NUMS=$(seq `echo $((FROM))` 1 `echo $((TO))`)
for i in $NUMS
do
  echo $i
  sleep 1
done