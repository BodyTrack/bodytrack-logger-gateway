package org.bodytrack.applications;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import edu.cmu.ri.createlab.device.CreateLabDevicePingFailureEventListener;
import edu.cmu.ri.createlab.serial.commandline.SerialDeviceCommandLineApplication;
import org.apache.log4j.Logger;
import org.bodytrack.loggingdevice.DataFileDownloader;
import org.bodytrack.loggingdevice.DataFileManager;
import org.bodytrack.loggingdevice.DataFileUploader;
import org.bodytrack.loggingdevice.DataStoreServerConfig;
import org.bodytrack.loggingdevice.LoggingDevice;
import org.bodytrack.loggingdevice.LoggingDeviceConfig;
import org.bodytrack.loggingdevice.LoggingDeviceFactory;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class BodyTrackLoggingDeviceGateway extends SerialDeviceCommandLineApplication
   {
   private static final Logger LOG = Logger.getLogger(BodyTrackLoggingDeviceGateway.class);

   public static void main(final String[] args)
      {
      new BodyTrackLoggingDeviceGateway().run();
      }

   private LoggingDevice device;
   private DataFileManager dataFileManager;

   private final CreateLabDevicePingFailureEventListener pingFailureEventListener =
         new CreateLabDevicePingFailureEventListener()
         {
         public void handlePingFailureEvent()
            {
            LOG.debug("BodyTrackLoggingDeviceGateway.handlePingFailureEvent(): ping failure detected, cleaning up...");

            println("Device ping failure detected.  Cleaning up...");
            disconnect(false);

            LOG.debug("BodyTrackLoggingDeviceGateway.handlePingFailureEvent(): ping failure detected, attempting reconnect...");

            println("Now attempting to reconnect to the device...");
            startup();
            }
         };

   private BodyTrackLoggingDeviceGateway()
      {
      super(new BufferedReader(new InputStreamReader(System.in)));

      registerActions();
      }

   private final Runnable scanAndConnectToDeviceAction =
         new Runnable()
         {
         public void run()
            {
            if (isConnected())
               {
               println("You are already connected to a BodyTrack Logging Device.");
               }
            else
               {
               println("Scanning for a BodyTrack Logging Device...");
               device = LoggingDeviceFactory.create();

               if (device == null)
                  {
                  println("Connection failed.");
                  }
               else
                  {
                  device.addCreateLabDevicePingFailureEventListener(pingFailureEventListener);
                  println("Connection successful, starting up the DataFileDownloader and DataFileUploader...");

                  final DataStoreServerConfig dataStoreServerConfig = device.getDataStoreServerConfig();
                  final LoggingDeviceConfig loggingDeviceConfig = device.getLoggingDeviceConfig();

                  if (dataStoreServerConfig != null && loggingDeviceConfig != null)
                     {
                     final DataFileUploader dataFileUploader = new DataFileUploader(dataStoreServerConfig, loggingDeviceConfig);
                     final DataFileDownloader dataFileDownloader = new DataFileDownloader(device);

                     dataFileManager = new DataFileManager(dataStoreServerConfig,
                                                           loggingDeviceConfig,
                                                           dataFileUploader,
                                                           dataFileDownloader);

                     dataFileManager.startup();
                     }
                  }
               }
            }
         };

   private final Runnable disconnectFromDeviceAction =
         new Runnable()
         {
         public void run()
            {
            disconnect();
            }
         };

   private final Runnable quitAction =
         new Runnable()
         {
         public void run()
            {
            LOG.debug("BodyTrackLoggingDeviceGateway.run(): Quit requested by user.");
            disconnect();
            println("Bye!");
            }
         };

   private void registerActions()
      {
      registerAction("c", scanAndConnectToDeviceAction);
      registerAction("d", disconnectFromDeviceAction);

      registerAction(QUIT_COMMAND, quitAction);
      }

   @Override
   protected void startup()
      {
      // autoconnect on startup
      println("");
      println("Auto-connecting to the device...");
      println("");
      scanAndConnectToDeviceAction.run();
      }

   protected final void menu()
      {
      println("COMMANDS -----------------------------------");
      println("");
      println("c         Scan all serial ports and connect to the first device found");
      println("d         Disconnect from the device");
      println("");
      println("q         Quit");
      println("");
      println("--------------------------------------------");
      }

   protected final boolean isConnected()
      {
      return device != null;
      }

   protected final void disconnect()
      {
      if (isConnected())
         {
         disconnect(true);
         }
      }

   private void disconnect(final boolean willTryToDisconnectFromDevice)
      {
      // shutdown the data file manager
      if (dataFileManager != null)
         {
         dataFileManager.shutdown();
         }

      // disconnect from the device
      if (willTryToDisconnectFromDevice && device != null)
         {
         device.disconnect();
         }

      // set to null
      device = null;
      dataFileManager = null;
      }
   }
