#!/bin/bash

if ! command -v java &> /dev/null; then
    echo "Java is not installed!!"
    exit 1
fi

I=1
INSTALL_MONGO=0
SKIP_MONGO=0

if ! command -v mongod &> /dev/null; then

  while [ $I == 1 ]; do
    echo "MongoDB is not installed!! Do you want to install mongo (if database is running on server you can skip but then you must enter values into config manually)?[yes/no/skip]"
    read -r RESPONSE
    if [ "$RESPONSE" == "yes"  ]; then
        I=0
        INSTALL_MONGO=1
    elif [ "$RESPONSE" == "no" ]; then
        echo "Server can not run without db, exiting..."
        exit 1
    elif [ "$RESPONSE" == "skip" ]; then
        I=0
        SKIP_MONGO=1
    fi
  done

  if [ $INSTALL_MONGO == 1  ]; then
    if ! setup/downloadMongo.sh ; then
       echo "Could not download mongo"
       exit 1
    fi
  fi
fi



if [ $SKIP_MONGO == 0 ]; then

  if ! pgrep mongod &> /dev/null; then
     echo "MongoDB is installed but not running... turning it on"
     if ! systemctl start mongod; then
        echo "Error: Could not start mongod..."
        exit 1
     fi
  fi

    echo "Going to create user in mongo from createDBUser.js file...
    check it, change values, and close it before continuing.
    Press enter to continue"
    read -r A_A
    if ! setup/createUserInMongo.sh; then
       echo "Could not create user in database"
       exit 1
    fi
fi
setup/createSelfSignedCert.sh

echo "Building server, this may take several minutes"
./gradlew
./gradlew build

echo "To build server again just run [./gradlew build] command"