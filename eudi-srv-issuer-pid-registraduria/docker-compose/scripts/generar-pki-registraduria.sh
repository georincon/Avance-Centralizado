#!/usr/bin/env bash
#
# PKI de DESARROLLO del stack del issuer Registraduría (todo nuevo; no reutiliza claves de ningún otro proyecto):
#   - CA local  -> pki-dev/ca.crt (confiar solo en dev: wallet dev, curl --cacert)
#   - TLS del proxy (SAN = IP del host) -> haproxy/certs/registraduria.tls.pem
#   - TLS interno de Keycloak            -> keycloak/certs/keycloak.tls.{crt,key}
#   - Keystore del issuer                -> pid-issuer/issuer-keys.jks (issuance, access-certificate, request/nonce-encryption)
#   - Clave de la lista de estado        -> status-list/certs/{key.pem,cert.der}
# Uso: HOST_IP=192.168.0.240 ./scripts/generar-pki-registraduria.sh [--force]
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
HOST_IP="${HOST_IP:-192.168.0.240}"
P=pki-dev
if [[ -f $P/ca.key && "${1:-}" != "--force" ]]; then echo "Ya existe $P (usa --force)"; exit 1; fi
mkdir -p $P haproxy/certs keycloak/certs pid-issuer status-list/certs
ec() { openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:"${2:-P-256}" -out "$1" 2>/dev/null; }

# --- CA local
ec $P/ca.key
openssl req -new -x509 -key $P/ca.key -sha256 -days 3650 -subj "/C=CO/O=Registraduria Dev/CN=Registraduria Dev Local CA" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" -out $P/ca.crt

issue() { # nombre CN SAN
  ec $P/$1.key
  openssl req -new -key $P/$1.key -subj "/C=CO/O=Registraduria Dev/CN=$2" -out $P/$1.csr
  openssl x509 -req -in $P/$1.csr -CA $P/ca.crt -CAkey $P/ca.key -CAcreateserial -days 825 -sha256 -out $P/$1.crt \
    -extfile <(printf "basicConstraints=CA:FALSE\nkeyUsage=digitalSignature\nextendedKeyUsage=serverAuth\nsubjectAltName=$3") 2>/dev/null
  rm -f $P/$1.csr
}
issue proxy "$HOST_IP" "IP:$HOST_IP,DNS:localhost,DNS:civica-desarrollo.avance.org.co"
cat $P/proxy.crt $P/proxy.key > haproxy/certs/registraduria.tls.pem
issue keycloak "keycloak" "DNS:keycloak,DNS:localhost"
cp $P/keycloak.crt keycloak/certs/keycloak.tls.crt; cp $P/keycloak.key keycloak/certs/keycloak.tls.key; chmod 644 keycloak/certs/keycloak.tls.key

# --- Keystore del issuer: cadena de firma (root > intermediate > issuance) + claves auxiliares
mkdir -p $P/iss
ec $P/iss/root.key; openssl req -new -x509 -key $P/iss/root.key -sha256 -days 3650 -subj "/CN=REGISTRADURIA ISSUANCE ROOT" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" -out $P/iss/root.crt
ec $P/iss/int.key; openssl req -new -key $P/iss/int.key -subj "/CN=REGISTRADURIA ISSUANCE INTERMEDIATE" -out $P/iss/int.csr
openssl x509 -req -in $P/iss/int.csr -CA $P/iss/root.crt -CAkey $P/iss/root.key -CAcreateserial -days 3650 -sha256 -out $P/iss/int.crt \
  -extfile <(printf "basicConstraints=critical,CA:TRUE,pathlen:0\nkeyUsage=critical,keyCertSign,cRLSign") 2>/dev/null
ec $P/iss/issuance.key; openssl req -new -key $P/iss/issuance.key -subj "/C=CO/O=Registraduria Dev/CN=REGISTRADURIA PID ISSUANCE" -out $P/iss/issuance.csr
openssl x509 -req -in $P/iss/issuance.csr -CA $P/iss/int.crt -CAkey $P/iss/int.key -CAcreateserial -days 730 -sha256 -out $P/iss/issuance.crt \
  -extfile <(printf "basicConstraints=CA:FALSE\nkeyUsage=critical,digitalSignature\nsubjectAltName=DNS:pid-issuer,IP:$HOST_IP") 2>/dev/null
cat $P/iss/issuance.crt $P/iss/int.crt $P/iss/root.crt > $P/iss/issuance-chain.crt
selfsigned() { # alias curva [keyUsage]
  ec $P/iss/$1.key "$2"
  local extra=()
  [[ -n "${3:-}" ]] && extra=(-addext "keyUsage=critical,$3")
  openssl req -new -x509 -key $P/iss/$1.key -sha256 -days 730 -subj "/C=CO/CN=pid-issuer" -addext "subjectAltName=DNS:pid-issuer" "${extra[@]}" -out $P/iss/$1.crt
}
# request-encryption/nonce-encryption son claves EC para ECDH-ES (cifrado): necesitan
# keyUsage=keyAgreement para que Nimbus derive JWK `use=enc` al leer el certificado
# (sin keyUsage, `use` queda sin poder derivarse y el issuer falla al iniciar con
# "encryptionKeys must have key use 'enc'"). access-certificate no se valida así.
selfsigned access-certificate P-384
selfsigned request-encryption P-256 keyAgreement
selfsigned nonce-encryption P-256 keyAgreement
rm -f pid-issuer/issuer-keys.jks
add() { # alias key chain
  openssl pkcs12 -export -inkey "$2" -in "$3" -name "$1" -out $P/iss/$1.p12 -passout "pass:$1"
  keytool -importkeystore -srckeystore $P/iss/$1.p12 -srcstoretype PKCS12 -srcstorepass "$1" -srcalias "$1" \
    -destkeystore pid-issuer/issuer-keys.jks -deststoretype JKS -deststorepass regkeys -destalias "$1" -destkeypass "$1" -noprompt 2>/dev/null
}
add issuance $P/iss/issuance.key $P/iss/issuance-chain.crt
for a in access-certificate request-encryption nonce-encryption; do add $a $P/iss/$a.key $P/iss/$a.crt; done

# --- Lista de estado
ec status-list/certs/key.pem
openssl req -new -x509 -key status-list/certs/key.pem -sha256 -days 730 -subj "/C=CO/O=Registraduria Dev/CN=Registraduria Status List" -out status-list/certs/cert.crt
openssl x509 -in status-list/certs/cert.crt -outform der -out status-list/certs/cert.der

# --- WRPRC propio del issuer (JWT rc-wrp+jwt firmado ES256 con la clave de emisión; el issuer solo lo publica en sus metadatos)
python3 -I - "$P/iss/issuance.key" "$P/iss/issuance.crt" "$HOST_IP" > $P/wrprc.jwt <<'PY'
import sys,json,base64,time
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import ec, utils
from cryptography import x509
key=serialization.load_pem_private_key(open(sys.argv[1],'rb').read(),None)
cert=x509.load_pem_x509_certificate(open(sys.argv[2],'rb').read())
ip=sys.argv[3]; iss=f"https://{ip}:9543/pid-registraduria"
b=lambda d: base64.urlsafe_b64encode(d).rstrip(b'=').decode()
hdr={"x5c":[base64.b64encode(cert.public_bytes(serialization.Encoding.DER)).decode()],"typ":"rc-wrp+jwt","alg":"ES256"}
pl={"name":"Registraduría (emulador, desarrollo)","sub_ln":"Registraduria Dev","sub":"CO-DEV-REGISTRADURIA-EMULADOR","country":"CO",
 "registry_uri":iss,"srv_description":[{"lang":"es","value":"Emulador de la Registraduría Nacional: emisor de PID (mso_mdoc y dc+sd-jwt) vía OpenID4VCI. Solo desarrollo."}],
 "entitlements":["https://uri.etsi.org/19475/Entitlement/PID_Provider"],"privacy_policy":iss+"/privacy","info_uri":iss,"support_uri":iss+"/support",
 "supervisory_authority":{"email":"dev@invalid.example","phone":"+570000000","uri":iss},"policy_id":["0.4.0.19475.3.1"],"certificate_policy":iss+"/certificate-policy","iat":int(time.time()),
 "provides_attestations":[{"format":"dc+sd-jwt","meta":{"vct_values":["urn:eudi:pid:1"]}},{"format":"mso_mdoc","meta":{"doctype_value":"eu.europa.ec.eudi.pid.1"}}]}
si=b(json.dumps(hdr,separators=(',',':')).encode())+'.'+b(json.dumps(pl,separators=(',',':'),ensure_ascii=False).encode())
r,s_=utils.decode_dss_signature(key.sign(si.encode(),ec.ECDSA(hashes.SHA256())))
print(si+'.'+b(r.to_bytes(32,'big')+s_.to_bytes(32,'big')))
PY
touch .env; grep -v '^ISSUER_WRPRC=' .env > .env.tmp || true; echo "ISSUER_WRPRC=$(cat $P/wrprc.jwt)" >> .env.tmp; mv .env.tmp .env
rm -f $P/*.srl $P/iss/*.csr $P/iss/*.srl $P/iss/*.p12
echo "PKI lista. CA para confiar en dev: $PWD/$P/ca.crt"
