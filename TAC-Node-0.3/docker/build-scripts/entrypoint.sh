#!/bin/bash
shopt -s nullglob
NETWORKS="mainnet testnet stagenet"

logEcho() {
  echo $1 | gosu tac tee -a /var/log/tac/tac.log
}

mkdir -p $WVDATA $WVLOG
chmod 700 $WVDATA $WVLOG || :

user="$(id -u)"
if [ "$user" = '0' ]; then
  find $WVDATA \! -user tac -exec chown tac '{}' +
  find $WVLOG \! -user tac -exec chown tac '{}' +
fi

[ -z "${TAC_CONFIG}" ] && TAC_CONFIG="/etc/tac/tac.conf"
if [[ ! -f "$TAC_CONFIG" ]]; then
  logEcho "Custom '$TAC_CONFIG' not found. Using a default one for '${TAC_NETWORK,,}' network."
  if [[ $NETWORKS == *"${TAC_NETWORK,,}"* ]]; then
    touch "$TAC_CONFIG"
    echo "tac.blockchain.type=${TAC_NETWORK}" >>$TAC_CONFIG

    sed -i 's/include "local.conf"//' "$TAC_CONFIG"
    for f in /etc/tac/ext/*.conf; do
      echo "Adding $f extension config to tac.conf"
      echo "include required(\"$f\")" >>$TAC_CONFIG
    done
    echo 'include "local.conf"' >>$TAC_CONFIG
  else
    echo "Network '${TAC_NETWORK,,}' not found. Exiting."
    exit 1
  fi
else
  echo "Found custom '$TAC_CONFIG'. Using it."
fi

[ -n "${TAC_WALLET_PASSWORD}" ] && JAVA_OPTS="${JAVA_OPTS} -Dtac.wallet.password=${TAC_WALLET_PASSWORD}"
[ -n "${TAC_WALLET_SEED}" ] && JAVA_OPTS="${JAVA_OPTS} -Dtac.wallet.seed=${TAC_WALLET_SEED}"
JAVA_OPTS="${JAVA_OPTS} -Dtac.data-directory=$WVDATA/data -Dtac.directory=$WVDATA"

logEcho "Node is starting..."
logEcho "TAC_HEAP_SIZE='${TAC_HEAP_SIZE}'"
logEcho "TAC_LOG_LEVEL='${TAC_LOG_LEVEL}'"
logEcho "TAC_NETWORK='${TAC_NETWORK}'"
logEcho "TAC_WALLET_SEED='${TAC_WALLET_SEED}'"
logEcho "TAC_WALLET_PASSWORD='${TAC_WALLET_PASSWORD}'"
logEcho "TAC_CONFIG='${TAC_CONFIG}'"
logEcho "JAVA_OPTS='${JAVA_OPTS}'"

JAVA_OPTS="-Dlogback.stdout.level=${TAC_LOG_LEVEL}
  -XX:+ExitOnOutOfMemoryError
  -Xmx${TAC_HEAP_SIZE}
  -Dlogback.file.directory=$WVLOG
  -Dconfig.override_with_env_vars=true
  ${JAVA_OPTS}
  -cp '/usr/share/tac/lib/plugins/*:/usr/share/tac/lib/*'" exec gosu tac tac "$TAC_CONFIG"
