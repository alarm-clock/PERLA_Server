#!/bin/bash

if ! curl -fsSL https://www.mongodb.org/static/pgp/server-4.4.asc | sudo apt-key add -; then
  echo "Error: Failed to fetch key for mongo download..."
  exit 1
fi
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/4.4 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-4.4.list
if ! apt update; then
  echo "Error"
  exit 1
fi
if ! apt install mongodb-org; then
   echo "Error"
   exit 1
fi
if ! systemctl start mongod; then
    echo "Error"
    exit 1
fi
if ! systemctl enable mongod; then
    echo "Error"
    exit 1
fi
if 1 systemctl status mongod; then
    echo "Error"
    exit 1
fi

exit 0