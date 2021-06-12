#!/bin/bash

# Create user
groupadd -r tac --gid=999
useradd -r -g tac --uid=999 --home-dir=$WVDATA --shell=/bin/bash tac

# Install DEB packages
dpkg -i /tmp/tac.deb || exit 1
if [[ $ENABLE_GRPC == "true" ]]; then
  echo "Installing gRPC server"
  dpkg -i /tmp/grpc-server.deb || exit 1
fi

# Set permissions
chown -R tac:"tac $WVDATA $WVLOG && chmod 777 $WVDATA $WVLOG

rm /etc/tac/tac.conf # Remove example config
cp /tmp/entrypoint.sh /usr/share/tac/bin/entrypoint.sh
chmod +x /usr/share/tac/bin/entrypoint.sh

# Cleanup
rm -rf /tmp/*
