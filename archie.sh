#!/usr/bin/env bash

# Initial Boilerplate - See https://github.com/xwmx/bash-boilerplate/blob/master/bash-simple for details
###############################################################################

set -o nounset
set -o errexit
trap 'echo "Aborting due to errexit on line $LINENO. Exit code: $?" >&2' ERR
set -o errtrace
set -o pipefail
#IFS=$'\n\t' # TODO: why doesn't this work?
_ME="$(basename "${0}")"


# Start of script
###############################################################################
if [[ "${1:--h}" =~ ^-h|--help$ ]]; then
  mvn exec:java -q -Dexec.mainClass="tinkerbeast.App" -Dexec.args="--help"  
else
  # TODO: add -q flag once Cli is finalised
  mvn exec:java -Dexec.mainClass="tinkerbeast.App" -Dexec.args="${*}"
fi








