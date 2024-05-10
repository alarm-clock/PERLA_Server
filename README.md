# Battle Management System PERLA

PERLA is open-source battle management system extension for Locus Map application developed by check company Asamm Softvare.
This project was developed as my Bachelor's thesis at Brno University of Technology Faculty of Information Technology.
Main aims of this project is to create free battle management system that is freely available and any group of people can use it.
Whole system is divided into two parts. PERLA client, which is application for Android systems with API 29+, and
PERLA server. In this repository you will find implementation of PERLA server and manual how to build it and run it. 
PERLA client is located in this repository https://github.com/alarm-clock/PERLA_Android_client.

## Features implemented by PERLA

At this point PERLA system implements these features:
- Blue force tracking,
- Dividing users into teams, team management, and team location share,
- Points of interest sharing and management.
- Chat.

What features I plant to implement in the future:
- Enhance existing features,
- Adding ability to send files and points through chat,
- Adding support other file types then photos (video is nearly finished),
- Formatted messages,
- File sharing,
- Live map drawing,
- Full admin client,
- Web client,
- In very far future implement own maps.

If you want to help I will be glad. Right now my bachelor's was submitted so anyone can help with this project.

## How to build PERLA Android client

This project was whole done on Ubuntu but because this is Kotlin (Java++) it can be built also on Windows. Especially
if you use Intellij or Android Studio you can build it anywhere. But because I use Linux this tutorial
will only cover how to build it on Linux.

### Requirements

- Java 17
- Internet connection
- Having project present on system

### Build

1. Run `sudo ./build.sh` script

What does this script do? Firstly, it will check if Java is installed. Then, it will check if Mogno is installed on the system.
If yes, it will skip the Mongo installation part. If you have a running Mongo database on a dedicated DB server, then skip this part.
Otherwise, the Mongo database will be downloaded by `apt`. After that, it will start mongo database and enable it to start
at startup. After that, it will try to create a user for the server to access the database. The command to do that will is
stored in `setup/createUserInMongo.js`. Change user and pwd parameters as you wish. You can change roles, but only if you know
what you are doing. The server uses the database name serverDB. If you want to change this later, use the `mongo` command that will
start the interactive shell. After you are done changing, press enter to let the script continue. The next thing on the list is
the certificate for HTTPS. You can create a self-signed certificate (less safe option, but for testing, it is good) if you want.
If you have a valid HTTPS certificate, store it to `keyStore` using `keytool`. But for testing purposes, I recommend using a self-signed one; it will be easier to do. Finally, the project will be built using the `./gradlew && ./gradlew build` commands. After it is built, you can manually change anything created, like the user or certificate. The last thing that must be
done before running is to fill the configuration file `config/application.conf`. Here, you will fill in the keystore name, key alias,
and their passwords. If the script creates the key, then both keystore and key will have the same password. The last thing that you will fill
in is database information. If Mongo was configured in the script, you can leave the host and port as they are. To check if the host and port
are correct, use the `mongo` command to start the interactive shell. On the second line, there will be a host and port.

### Run

To run the server, just run the `run.sh` script. If the server is running and the client can't connect to a server, then most likely, the firewall is preventing the client from establishing a connection with the server. If you write the command `help` list of available commands
will be printed in the format: `command  [parameter_name] [other_parameter_name] [key value [key value [...]]] [option1 ? option2] [optional_parameter/s ?] `.
Key-values are usually used in editing or updating. Key is the class attribute that is being changed and value is new value.
Options must be written one-to-one with what was written in help.