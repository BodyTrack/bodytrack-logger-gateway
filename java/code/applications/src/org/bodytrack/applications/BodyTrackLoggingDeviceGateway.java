package org.bodytrack.applications;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
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

   private static final String HELP_COMMAND_LINE_SWITCH = "--help";
   private static final String NO_UPLOAD_COMMAND_LINE_SWITCH = "--no-upload";
   private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

   public static void main(final String[] args)
      {
      final Map<String, String> arguments = new HashMap<String, String>(args.length);
      for (final String arg : args)
         {
         final int equalsPosition = arg.indexOf('=');
         final String key;
         final String val;
         if (equalsPosition < 0)
            {
            key = arg;
            val = "";
            }
         else
            {
            key = arg.substring(0, equalsPosition);
            val = arg.substring(equalsPosition + 1);
            }
         arguments.put(key, val);
         }

      if (arguments.containsKey(HELP_COMMAND_LINE_SWITCH))
         {
         final StringBuilder s = new StringBuilder("Options:").append(LINE_SEPARATOR);
         s.append("   ").append(NO_UPLOAD_COMMAND_LINE_SWITCH).append("           ").append("Files will not be uploaded").append(LINE_SEPARATOR);
         s.append("   ").append(HELP_COMMAND_LINE_SWITCH).append("                ").append("Displays this help message").append(LINE_SEPARATOR);
         println(s);
         }
      else
         {
         arguments.remove(HELP_COMMAND_LINE_SWITCH);
         new BodyTrackLoggingDeviceGateway(arguments).run();
         }
      }

   private LoggingDevice device;
   private DataFileManager dataFileManager;
   private final Map<String, String> arguments;

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

   private BodyTrackLoggingDeviceGateway(final Map<String, String> arguments)
      {
      super(new BufferedReader(new InputStreamReader(System.in)));
      this.arguments = arguments;

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
                  final DataStoreServerConfig dataStoreServerConfig = device.getDataStoreServerConfig();
                  final LoggingDeviceConfig loggingDeviceConfig = device.getLoggingDeviceConfig();

                  if (dataStoreServerConfig != null && loggingDeviceConfig != null)
                     {
                     println("Connection successful to device [" + loggingDeviceConfig.getDeviceNickname() + "] for user [" + loggingDeviceConfig.getUsername() + "].");
                     final boolean isUploadDisabled = arguments.containsKey(NO_UPLOAD_COMMAND_LINE_SWITCH);
                     if (isUploadDisabled)
                        {
                        println("Data files will not be uploaded since you specified the " + NO_UPLOAD_COMMAND_LINE_SWITCH + " option.");
                        }
                     else
                        {
                        println("Data files will be uploaded to " + dataStoreServerConfig.getServerName() + ":" + dataStoreServerConfig.getServerPort());
                        }

                     final DataFileUploader dataFileUploader = isUploadDisabled ? null : new DataFileUploader(dataStoreServerConfig, loggingDeviceConfig);
                     final DataFileDownloader dataFileDownloader = new DataFileDownloader(device);

                     println("Starting up the Gateway...");
                     dataFileManager = new DataFileManager(dataStoreServerConfig,
                                                           loggingDeviceConfig,
                                                           dataFileUploader,
                                                           dataFileDownloader);
                     dataFileManager.startup();
                     }
                  else
                     {
                     println("Connection Failed:  Could not obtain the DataStoreServerConfig and/or LoggingDeviceConfig from the device.");
                     }
                  }
               }
            }
         };

   private final Runnable printStatisticsAction =
         new Runnable()
         {
         public void run()
            {
            if (isConnected())
               {
               println(dataFileManager.getStatistics());
               }
            else
               {
               println("You are not connected to a BodyTrack Logging Device.");
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
      registerAction("s", printStatisticsAction);
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
      println("s         Print statistics for files downloaded, uploaded, and deleted");
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
