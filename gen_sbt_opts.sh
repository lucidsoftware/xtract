#!/bin/bash
if [[ $GITHUB_REF == refs/tags/* ]]; then
  version="${GITHUB_REF#refs/tags/}"
else
  version="${GITHUB_REF#refs/branches/}-SNAPSHOT"
fi
echo "SBT_OPTS=-Dbuild.version=$version" >> $GITHUB_ENV

