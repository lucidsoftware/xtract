#!/bin/bash
set -e

echo "$PGP_SECRET" | base64 --decode | gpg --import
if [[ $GITHUB_REF == refs/tags/* ]]; then
  command="; publishSigned; sonatypeBundleRelease"
  version="${GITHUB_REF#refs/tags/}"
else
  command="publishSigned"
  version="${GITHUB_REF#refs/branches/}-SNAPSHOT"
fi
export SBT_OPTS="-Dbuild.version=$version"
echo "Running: sbt \"$command\""
exec sbt "$command"
