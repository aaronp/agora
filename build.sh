#!/usr/bin/env bash

#sbt clean coverage test "project rest" it:test cucumber "project root" coverageAggregate
sbt clean test cucumber