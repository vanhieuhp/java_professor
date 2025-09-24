# Ubuntu 22.04 base image
FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ARG GRPCURL_VERSION=1.9.1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       curl \
       tar \
    && rm -rf /var/lib/apt/lists/*

# Install grpcurl
RUN curl -fsSL -o /tmp/grpcurl.tgz \
      https://github.com/fullstorydev/grpcurl/releases/download/v${GRPCURL_VERSION}/grpcurl_${GRPCURL_VERSION}_linux_x86_64.tar.gz \
    && tar -xzf /tmp/grpcurl.tgz -C /usr/local/bin grpcurl \
    && chmod +x /usr/local/bin/grpcurl \
    && rm -f /tmp/grpcurl.tgz

# Default command keeps the container alive; docker-compose can override
CMD ["sh", "-c", "tail -f /dev/null"]


