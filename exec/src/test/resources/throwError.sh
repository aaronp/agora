#!/usr/bin/env bash
echo "first error output" >&2
echo "first info output"
echo "second error output" >&2
echo "second info output"
echo "about to exit with $1"
exit $1