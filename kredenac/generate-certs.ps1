# Requires: openssl and keytool

param(
    [Parameter(Mandatory = $true)][System.Security.SecureString]$TruststorePassword,
    [Parameter(Mandatory = $true)][System.Security.SecureString]$KeystorePassword
)

$ErrorActionPreference = "Stop"

function ConvertTo-PlainText($SecureString) {
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    }
    finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$env:KREDENAC_TRUSTSTORE_PW = ConvertTo-PlainText $TruststorePassword
$env:KREDENAC_KEYSTORE_PW = ConvertTo-PlainText $KeystorePassword

Remove-Item Env:\OPENSSL_CONF -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path certs | Out-Null
Set-Location certs

# CA (reused to sign both leaf certs)
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 `
  -out ca.crt -subj "/CN=kredenac-internal-ca"

# Redis leaf cert
openssl genrsa -out redis.key 2048
openssl req -new -key redis.key -out redis.csr -subj "/CN=localhost" `
  -addext "subjectAltName=DNS:localhost,DNS:redis,IP:127.0.0.1"
openssl x509 -req -in redis.csr -CA ca.crt -CAkey ca.key -CAcreateserial `
  -out redis.crt -days 3650 -sha256 -copy_extensions copyall
Remove-Item redis.csr

# Lettuce requires a Java KeyStore, not a raw PEM
keytool -import -trustcacerts -noprompt `
  -alias kredenac-ca -file ca.crt `
  -keystore redis-truststore.jks -storepass:env KREDENAC_TRUSTSTORE_PW

# Backend leaf cert + PKCS12 keystore for Ktor's Netty listener
openssl genrsa -out backend.key 2048
openssl req -new -key backend.key -out backend.csr -subj "/CN=localhost" `
  -addext "subjectAltName=DNS:localhost,DNS:backend,DNS:host.docker.internal,IP:127.0.0.1"
openssl x509 -req -in backend.csr -CA ca.crt -CAkey ca.key -CAcreateserial `
  -out backend.crt -days 3650 -sha256 -copy_extensions copyall
Remove-Item backend.csr

openssl pkcs12 -export -in backend.crt -inkey backend.key `
  -out backend.p12 -name backend -passout env:KREDENAC_KEYSTORE_PW

Remove-Item Env:\KREDENAC_TRUSTSTORE_PW -ErrorAction SilentlyContinue
Remove-Item Env:\KREDENAC_KEYSTORE_PW -ErrorAction SilentlyContinue

Write-Host "Done. Generated in .\certs:"
Write-Host "  ca.crt / ca.key            - the local CA"
Write-Host "  redis.crt / redis.key      - Redis's TLS cert, for the container"
Write-Host "  redis-truststore.jks       - CA cert as a Java truststore, for Lettuce"
Write-Host "  backend.p12                - backend's cert+key bundled for Ktor"