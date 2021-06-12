FROM debian:stable-slim
ARG TAC_LOG_LEVEL="INFO"
ARG TAC_HEAP_SIZE="2g"
ARG TAC_NETWORK="mainnet"
ARG ENABLE_GRPC="true"

ENV TAC_LOG_LEVEL=$TAC_LOG_LEVEL
ENV TAC_HEAP_SIZE=$TAC_HEAP_SIZE
ENV TAC_NETWORK=$TAC_NETWORK
ENV ENABLE_GRPC=$ENABLE_GRPC

COPY build-scripts /tmp/

# Additional dependencies
RUN /bin/bash /tmp/setup-deps.sh

# Node DEB files
ENV WVDATA=/var/lib/tac
ENV WVLOG=/var/log/tac
COPY target /tmp/
RUN /bin/bash /tmp/setup-node.sh

EXPOSE 6869 6868 6863 6862 6870
VOLUME /var/lib/tac
VOLUME /var/log/tac
VOLUME /usr/share/tac/lib/plugins
WORKDIR /var/lib/tac

STOPSIGNAL SIGINT
ENTRYPOINT ["/usr/share/tac/bin/entrypoint.sh"]
