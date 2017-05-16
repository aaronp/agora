#!/usr/bin/env bash
echo "this is error output" >&2
echo "this is info output"
echo "about to exit with $1"
exit $1