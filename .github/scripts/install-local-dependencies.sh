#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after downloading the pinned JARs.
# Hash-qualified versions prevent different private JARs sharing a Maven cache key.
sha256sum --check .github/dependencies.sha256

mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/json-simple-1.1.jar" -DgroupId="local" -DartifactId="json-simple" \
    -Dversion="1.1-tfmc-2d9484f4c649" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/BungeeCord.jar" -DgroupId="local" -DartifactId="BungeeCord" \
    -Dversion="1.0-tfmc-3bc6fa2477eb" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MMOItems-6.10.jar" -DgroupId="local" -DartifactId="MMOItems" \
    -Dversion="6.10-tfmc-c84700df5942" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MythicLib-1.7.jar" -DgroupId="local" -DartifactId="MythicLib" \
    -Dversion="1.7-tfmc-660ff2a6ec86" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MMOCore-1.13.1.jar" -DgroupId="local" -DartifactId="MMOCore" \
    -Dversion="1.13.1-tfmc-14850d745437" -Dpackaging=jar -DgeneratePom=true "$@"
