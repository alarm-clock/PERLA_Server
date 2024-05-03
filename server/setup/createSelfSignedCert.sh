#!/bin/bash

#script for generating self signed certificate so that server can use HTTPS even though you don't have certificate

KEY_STORE_NAME=""
PWD=""
ALIAS=""
SAN=""

LOOP=1
while [ $LOOP == 1 ]; do

  echo "Do you want to generate self-signed certificate?[yes/no]"
  read -r GENERATE

  if [ "$GENERATE" == "yes" ] || [ "$GENERATE" == "no" ]; then
    LOOP=0
  fi
done
if [ "$GENERATE" == "yes" ]; then
    LOOP=1
    while [ $LOOP == 1 ]; do
        echo "Enter key store name with no spaces:"
        read -r KEY_STORE_NAME
        echo "Enter keystore password with no spaces:"
        read -r PWD
        echo "Enter alias with no spaces:"
        read -r ALIAS
        echo "Enter SAN entries in one string (for example IP:192.168.0.2, other options can be found on the internet): "
        read -r SAN

        CYCLE=1

        while [ $CYCLE == 1  ]; do

          echo "Are entered values correct?[yes/no]"
          echo "Key store name: $KEY_STORE_NAME, Password: $PWD, Alias: $ALIAS, SAN: $SAN"
          read -r RESP

          if [ "$RESP" == "yes" ]; then
            CYCLE=0
            LOOP=0
          elif [ "$RESP" == "no" ]; then
            CYCLE=0
          else
            echo "Unknown response"
          fi
        done
    done

    KEY_STORE_NAME="${KEY_STORE_NAME}.jks"
    SAN="SAN=${SAN}"

    keytool  -keystore "$KEY_STORE_NAME" -storepass "$PWD" -alias "$ALIAS" -deststoretype pkcs12  -genkeypair -keyalg RSA -validity 395 -keysize 2048 -sigalg SHA256withRSA  -ext "SAN=$SAN"

    echo "Key store name: $KEY_STORE_NAME, Password: $PWD, Alias: $ALIAS, SAN: $SAN"
fi
exit 0

