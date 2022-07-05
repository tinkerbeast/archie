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


_archie_path=$(dirname $(realpath $0))
if [[ -f "${_archie_path}/pom.xml" ]]; then
   # TODO: printf based on verbosity
   #printf 'Using pom.xml at %s\n' ${_archie_path}
   true
else
  exit 1
fi

if [[ "${1:--h}" =~ ^-h|--help$ ]]; then
  mvn exec:java -f "${_archie_path}/pom.xml" -q -Dexec.mainClass="tinkerbeast.App" -Dexec.args="--help"  
else
  # TODO: add -q flag once Cli is finalised
  mvn exec:java -f "${_archie_path}/pom.xml" -Dexec.mainClass="tinkerbeast.App" -Dexec.args="${*}"
fi








