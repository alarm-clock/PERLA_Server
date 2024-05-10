## Contents of this storage medium

- /src/client/ - source code for client application
- /src/server/ - source code for server
- /src/executables/client/ - .apk file ready to be installed
- /src/executable/server/ - runnable .jar archive with configuration file
- /document_sources/ - Latex source codes for this document
- README.md - Markdown file with contents description and tutorial how to build both server and client
- Manual.pdf - PDF file with user manual how to use client
- Jozef_Michal_Bukas_BP_Locus.pdf - Thesis text

# Important note

During development, I called this system Jmb_bms (Jozef Michal Bukas Battle Management System) but in later stages
I started to refer to it as PERLA system. So in source files I refer to this system as Jmb_bms because I did not
want to rename it last minute and cause any problems. Installed application will have name PERLA. Thanks for 
understanding :-)

# How to build this project

This project was whole done on Ubuntu but because this is Kotlin (Java++) it can be built also on Windows. Especially
if you use Intellij or Android Studio you can build it anywhere. But because I use Linux this tutorial
will only cover how to build it on Linux.

## Application

Installable `.apk` file is stored in `src/executables/client`. Source files that can be built are in `src/client`.

### Requirements Intellij

- Java 17
- Internet connection
- Intellij Idea Ultimate or Android Studio ( rest of this document will be about intellij)
- Having project present on system

### Build Intellij (Preferred/Easier option)

Note that this option will always work.

1. Open Intellij and download android plugin. You can find it and install it in `File > Settings > Plugins`
2. After that, in `File > Settings > Languages & Frameworks > Android SDK` you download SDK with name `Android API 34`
3. Copy Android SDK location and create file `local.properties`, in folder where is gradlew script, and you will add this line `sdk.dir=PATH_TO_ANDROID_SDK` for example `sdk.dir=/home/jozef/Android/Sdk` into `local.properties` file. You may skip this step if intellij generated this file for you.
4. Now what you just need to build project in `Build > Build Project`
5. If everything was done correctly, in `app/build/outputs/apk/debug` you should find `app-debug.apk`


If you get an error that gradle AGP version is incompatible with intelli it means that you have old version of intellij and must update it or download the newest version.
In the error you will find which version is compatible with used gradle version.

### Requirements Linux terminal

- Java 17
- Internet connection
- Having project present on system

### Build Linux terminal

1. Open terminal
2. Follow steps on this official Android developers to download `sdkmanager` https://developer.android.com/tools/sdkmanager. This webpage also shows how to use this command.
3. After that install `"platforms;android-34" "build-tools;34.0.0" "sources;android-34"`
4. If sdkmanger will cry that there is no root directory, or it isn't expected structure `export ANDROID_SDK_ROOT=/path/to/sdk`. If that doesn't help, then manually set that part when running that command with option `--sdk_root=$ANDROID_SDK_ROOT`.
5. Check if everything is installed by running `sdkmanager --list`
6. Store its location in ANDROID_HOME variable `export ANDROID_HOME=/path/to/sdk` (for example for me, it is export ANDROID_HOME=/home/jozef/Android/Sdk, or ANDROID_HOME=/usr/lib/android-sdk). Basically it will be folder where you unzipped cmdline-tools. if you plan to do it multiple times store it in `.bashrc` or anywhere where it will be permanent
7. Move to folder with source code where is `gradlew` script
8. Execute this command `./gradlew && ./gradlew build`, it will take time to build, so you have time to do something else (it can be more than 16 minutes)
9. If everything was done correctly, in `app/build/outputs/apk/debug` you should find `app-debug.apk`

### Installation
The app can be installed onto the device in many ways. The simplest way to do it is by uploading the add-debug.apk file to Google Drive, where you can download it to any device. Another possible way is to upload it to the target device by using `adb`. When the file
will be present on device storage; just open it in the Storage application, and then you will be prompted to install the PERLA client.
By default, you can not install apps from the storage application, so you will be prompted to change it in settings. You must do that.
After that, the application should be installed and ready to run.

## Server

Runnable '.jar' archive is located in `src/executables/server`. Note that you must install mongo database on system, create
user that server will use to connect to DB. Then you must add existing certificate through keytool to keystore from which server 
can access it or generate self-signed certificate using keytool. You can use script located at `src/server/setup/createSelfSignedCert.sh`
and move generated `.jks` file to same directory as runnable `.jar` archive. 

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