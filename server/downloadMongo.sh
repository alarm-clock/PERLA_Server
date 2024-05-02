#!/bin/bash

curl -fsSL https://www.mongodb.org/static/pgp/server-4.4.asc | sudo apt-key add -
apt update
apt install mongodb-org
systemctl start mongod
systemctl enable mongod
systemctl status mongod