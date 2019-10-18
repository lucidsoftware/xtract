#!/bin/bash
set -e

echo "$PGP_SECRET" | base64 --decode | gpg --import
echo "Running: sbt ++$TRAVIS_SCALA_VERSION \"; publishSigned; sonatypeBundleRelease"
exec sbt ++$TRAVIS_SCALA_VERSION "; publishSigned; sonatypeBundleRelease"
