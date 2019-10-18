#!/bin/bash
set -e

echo "$PGP_SECRET" | base64 --decode | gpg --import
if [[ -z $TRAVIS_TAG ]]; then
  command="publishSigned"
else
  command="; publishSigned; sonatypeBundleRelease"
fi
echo "Running: sbt ++$TRAVIS_SCALA_VERSION \"$command\""
exec sbt ++$TRAVIS_SCALA_VERSION "$command"
