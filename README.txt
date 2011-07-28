=======================================================================================================================

                                            BodyTrack Logging Device Gateway

=======================================================================================================================

ABOUT
-----

The BodyTrack Logging Device Gateway is an application which downloads data files from a BodyTrack Custom Sensor
(e.g. Base Station, Chest Strap, etc) to the user's computer (keeping a local copy) and uploads the data files to the
bodytrack.org web site.

Data files are stored in a BodyTrack directory under the user's home directory. The exact path for the data file
directory depends on the device nickname, your user ID, and the server to which you're uploading.  These variables
are specified in the config.txt file on the device's SD card.  So, for example, if user Bubba has a bodytrack.org user
ID of 42 and runs the gateway and connects to a base station named "Bubba_Basestation", data files will be stored in
~/BodyTrack/LoggingDeviceData/bodytrack.org_80/User42/Bubba_Basestation.

Data files saved to your computer will have one of a variety of file extensions.  The file extension tells you the
status of the file.  The current set of possible extensions is:

   .WRITING   - The file is downloading from the device and being written to disk
   .BT        - The file has been downloaded from the device and is awaiting upload.  The checksum is correct.
   .BTC       - The was downloaded from the device, but the checksum is incorrect.  Files with an incorrect checksum
                will be re-downloaded some number of times before the gateway gives up and simply tells the device to
                delete the file.  The gateway will not attempt to upload files with an incorrect checksum.
   .UPLOADING - The file is currently being uploaded to the server
   .BTU       - The file was successfully uploaded to the server and accepted by the server as having no errors.
   .BTX       - The gateway tried to upload the file to the server, but the server responded that the file has errors
                and/or failed bin recs.

The gateway application is not yet distributed in binary form, so you'll need to download the source from GitHub and
build it on your machine.  Please see the instructions below.

=======================================================================================================================

PREREQUISITES
-------------

You must have the following installed in order to build the application:

   * Java JDK 1.6+ (http://www.oracle.com/technetwork/java/javase/downloads/index.html)
   * Ant (http://ant.apache.org/)

You must have the following installed in order to run the application:

   * Java JDK 1.6+ (http://www.oracle.com/technetwork/java/javase/downloads/index.html)
   * FTDI VCP driver (http://www.ftdichip.com/Drivers/VCP.htm)

=======================================================================================================================

DOWNLOADING THE GATEWAY APPLICATION
-----------------------------------

The gateway source code is stored in a GitHub code repository at:

   https://github.com/BodyTrack/bodytrack-logger-gateway.

To download the gateway source code, you can either download a snapshot of the code, or use Git to fetch a (read-only)
copy of the repository.  Instructions for each follow.


Download a Snapshot of the Source Code
--------------------------------------

If you don't care about easily updating to new versions of the gateway and/or you don't have (or want) Git installed,
then it's easiest to just download a snapshot of the source code from GitHub.  To do so, simply do the following:

1) Go to the Commits page for the repository and find the version you want to use.
2) On the right, click the link next to the word "commit".
3) On the resulting page, click the "Downloads" button.  A dialog window should appear, with buttons to download the
   source either as a .tar.gz or a .zip.
4) Choose your favorite and then decompress the archive.

You can now skip to the "Building the Gateway Application" section below.


Download the Source Code Using Git
----------------------------------

If you want to be able to easily update to a newer/different version of the gateway, then you're better off downloading
the source with Git.  Binaries and instructions for installing Git are available from the Git home page.

To get the code with Git, first open a command prompt and change the current directory to wherever you want the source
code to live.  Then do the following:

   $ git clone git://github.com/BodyTrack/bodytrack-logger-gateway.git
   Cloning into bodytrack-logger-gateway...
   remote: Counting objects: 528, done.
   remote: Compressing objects: 100% (299/299), done.
   remote: Total 528 (delta 194), reused 486 (delta 155)
   Receiving objects: 100% (528/528), 18.53 MiB | 4.95 MiB/s, done.
   Resolving deltas: 100% (194/194), done.
   $ ls -l
   total 0
   drwxr-xr-x  8 chris  staff  272 Jul 26 10:40 bodytrack-logger-gateway
   $

If you have already done a "git clone" before, and simply want to update to the latest revision, change to the
bodytrack-logger-gateway directory and run "git pull origin master".  Please refer to the Git documentation if you
want to switch to a specific commit.

=======================================================================================================================

BUILDING THE GATEWAY APPLICATION
--------------------------------

To build the application, open a command prompt and change to the root directory of the source code.  Then change to
the java subdirectory and then run Ant.  It should look similar to the following:

   $ cd bodytrack-logger-gateway
   $ cd java
   $ ant
   Buildfile: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/build.xml

   clean-bodytrack-logging-device:

   clean-bodytrack-applications:

   clean:

   build-bodytrack-logging-device:
       [mkdir] Created dir: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/logging-device/build
       [javac] Compiling 25 source files to /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/logging-device/build

   dist-bodytrack-logging-device:
       [mkdir] Created dir: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/logging-device/dist
        [copy] Copying 20 files to /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/logging-device/dist
         [jar] Building jar: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/logging-device/dist/bodytrack-logging-device.jar

   build-bodytrack-applications:
       [mkdir] Created dir: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/applications/build
       [javac] Compiling 2 source files to /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/applications/build
        [copy] Copying 1 file to /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/applications/build

   dist-bodytrack-applications:
       [mkdir] Created dir: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/applications/dist
        [copy] Copying 21 files to /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/applications/dist
         [jar] Building jar: /Users/chris/projects/BodyTrack/bodytrack-logger-gateway/java/code/applications/dist/bodytrack-applications.jar

   dist:

   all:

   BUILD SUCCESSFUL
   Total time: 4 seconds
   $

The binaries are now built and you're ready to run the application.

=======================================================================================================================

RUNNING THE GATEWAY APPLICATION
-------------------------------

There are actually two applications created when you build the binaries:

1) The gateway application which auto-connects to the first BodyTrack logging device it finds, and continually 
   downloads data files from the device, caches them locally, and then uploads them to the bodytrack.org server.
2) A simple, command-line client for testing connectivity and basic interaction with a BodyTrack logging device.  
   The command line client is good for testing connectivity and basic interaction (including downloading data files), 
   but does not provide upload support.
   
Most users will want to run the gateway application.  To run either application, you must have already built the 
binaries.  See the "Building the Gateway Application" section above.


Running the BodyTrack Logging Device Gateway
--------------------------------------------

To run the BodyTrack Logging Device Gateway, cd to the java directory and run the following:

   $ java -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Users running a UNIX-based system can instead simply run the shell script:

   $ ./bodytrack-logging-device-gateway.sh

Once the gateway is running, you shouldn't need to do anything.  It will connect to the first BodyTrack logging device
it finds and begin downloading data files.  Once downloaded, data files will be uploaded to bodytrack.org (or whatever
server/port you specified in the config.txt file on the device's SD card).

To quit the gateway application, type q and then the ENTER key.


Running the Command Line Client
-------------------------------

To run the command line client, cd to the java directory and run the following:

   $ java -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.CommandLineLoggingDevice;

Users running a UNIX-based system can instead simply run the shell script:

   $ ./command-line-logging-device.sh

The command line client has a menu which lists the various commands you can run to interact with the device.

=======================================================================================================================

TIPS AND TRICKS
---------------

This section discusses some useful tips for running the gateway.


Specifying the Serial Port(s)
-----------------------------

If you know the name of the serial port to which your BodyTrack logging device is attached, you can optionally specify
the port as follows:

   $ java -Dgnu.io.rxtx.SerialPorts=SERIAL_PORT_NAME_HERE -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Simply replace SERIAL_PORT_NAME_HERE in the command above with the absolute path to the port.  For example:

   $ java -Dgnu.io.rxtx.SerialPorts=/dev/tty.usbserial-A600dGXD -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Note that you can specify multiple serial ports in the command line switch.  When doing so, ports must be delimited by
the path separator character for whatever platform you're running under.  For example, on my Mac:

   $ java -Dgnu.io.rxtx.SerialPorts=/dev/tty.usbserial-A600dGXD:/dev/tty.usbserial-A60043uP -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Specifying the serial port should greatly decrease the time it takes to establish a connection with the device.


More Detailed Logging
---------------------

By default, the gateway application uses a logging level of "Debug".  Sometimes it's helpful to drop to the lowest 
level ("Trace") in order to track down problems with serial communication, carefully track the flow of program 
execution, etc.  To change the logging level, do the following:

1) Open the /bodytrack-logger-gateway/java/code/applications/src/log4j.xml file in your favorite text editor.
2) Find this block at the bottom of the XML file:

      <root>
         <priority value="debug"/>
         <appender-ref ref="ConsoleAppender"/>
         <appender-ref ref="RollingFileAppender"/>
      </root>
      Change the word "debug" to "trace", like this:
      <root>
         <priority value="trace"/>
         <appender-ref ref="ConsoleAppender"/>
         <appender-ref ref="RollingFileAppender"/>
      </root>

3) Save the file and close it.
4) Now open a command prompt and rebuild the binaries (see the "Building the Gateway Application" section above).  Now 
   when you run the application you should see much more detailed logging information.

=======================================================================================================================
