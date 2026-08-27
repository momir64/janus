#!/usr/bin/env bash
set -euo pipefail

# Requires: openssl and keytool

if [ $# -ne 2 ]; then
  echo "Usage: $0 <truststore-password> <keystore-password>" >&2
  exit 1
fi

export KREDENAC_TRUSTSTORE_PW="$1"
export KREDENAC_KEYSTORE_PW="$2"
trap 'unset KREDENAC_TRUSTSTORE_PW KREDENAC_KEYSTORE_PW' EXIT

mkdir -p certs && cd certs

# CA (reused to sign both leaf certs)
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -out ca.crt -subj "/CN=kredenac-internal-ca"

# Redis leaf cert
openssl genrsa -out redis.key 2048
openssl req -new -key redis.key -out redis.csr -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
openssl x509 -req -in redis.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out redis.crt -days 3650 -sha256 -copy_extensions copyall
rm redis.csr

# Lettuce requires a Java KeyStore, not a raw PEM
keytool -import -trustcacerts -noprompt \
  -alias kredenac-ca -file ca.crt \
  -keystore redis-truststore.jks -storepass:env KREDENAC_TRUSTSTORE_PW

# Backend leaf cert + PKCS12 keystore for Ktor's Netty listener
openssl genrsa -out backend.key 2048
openssl req -new -key backend.key -out backend.csr -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:host.docker.internal,IP:127.0.0.1"
openssl x509 -req -in backend.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out backend.crt -days 3650 -sha256 -copy_extensions copyall
rm backend.csr

openssl pkcs12 -export -in backend.crt -inkey backend.key \
  -out backend.p12 -name backend -passout env:KREDENAC_KEYSTORE_PW

echo "Done. Generated in ./certs:"
echo "  ca.crt / ca.key            — the local CA"
echo "  redis.crt / redis.key      — Redis's TLS cert, for the container"
echo "  redis-truststore.jks       — CA cert as a Java truststore, for Lettuce"
echo "  backend.p12                — backend's cert+key bundled for Ktor"