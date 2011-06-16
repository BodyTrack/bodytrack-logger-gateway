========================================================================================================================

                                            BodyTrack Logging Device Gateway

========================================================================================================================

ABOUT
-----

The BodyTrack Logging Device Gateway is an application that acts as a gateway between BodyTrack logging devices and the
bodytrack.org server.

========================================================================================================================

PREREQUISITES
-------------

You must have the following installed in order to build the application:

   * Java JDK 1.6+ (http://www.oracle.com/technetwork/java/javase/downloads/index.html)
   * Ant (http://ant.apache.org/)

You must have the following installed in order to run the application:

   * Java JDK 1.6+ (http://www.oracle.com/technetwork/java/javase/downloads/index.html)
   * FTDI VCP driver (http://www.ftdichip.com/Drivers/VCP.htm)

========================================================================================================================

BUILDING THE CODE
-----------------

To build, cd to the java directory and run "ant".

========================================================================================================================

RUNNING THE APPLICATIONS
------------------------

There are actually two applications contained in this distribution:

1) a simple, command-line client for testing connectivity and basic interaction with a BodyTrack logging device
2) the gateway application which auto-connects to the first BodyTrack logging device it finds, and continually downloads
   data files from the device, caches them locally, and then uploads them to the bodytrack.org server.

To run either client, you must have already built the binaries.  See "BUILDING THE CODE" above.

RUNNING THE COMMAND LINE CLIENT
-------------------------------

To run the command line client, cd to the java directory and run the following:

   java -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.CommandLineLoggingDevice;

RUNNING THE BODYTRACK LOGGING DEVICE GATEWAY
--------------------------------------------

To run the BodyTrack Logging Device Gateway, cd to the java directory and run the following:

   java -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

SPECIFIYING THE SERIAL PORT(S)
------------------------------

If you know the name of the serial port to which your BodyTrack logging device is attached, you can optionally specify
the port as follows:

   java -Dgnu.io.rxtx.SerialPorts=SERIAL_PORT_NAME_HERE -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Simply replace SERIAL_PORT_NAME_HERE in the command above with the absolute path to the port.  For example:

   java -Dgnu.io.rxtx.SerialPorts=/dev/tty.usbserial-A600dGXD -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Note that you can specify multiple serial ports in the command line switch.  When doing so, ports must be delimited by
the path separator character for whatever platform you're running under.  For example, on my Mac:

   java -Dgnu.io.rxtx.SerialPorts=/dev/tty.usbserial-A600dGXD:/dev/tty.usbserial-A60043uP -Djava.library.path=./code/applications/dist -cp ./code/applications/dist/bodytrack-applications.jar org.bodytrack.applications.BodyTrackLoggingDeviceGateway;

Specifying the serial port should greatly decrease the time it takes to establish a connection with the device.

========================================================================================================================
