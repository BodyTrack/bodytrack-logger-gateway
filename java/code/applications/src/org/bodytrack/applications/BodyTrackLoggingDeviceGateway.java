package org.bodytrack.applications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import edu.cmu.ri.createlab.device.CreateLabDevicePingFailureEventListener;
import edu.cmu.ri.createlab.serial.commandline.SerialDeviceCommandLineApplication;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bodytrack.loggingdevice.DataFile;
import org.bodytrack.loggingdevice.DataFileDownloader;
import org.bodytrack.loggingdevice.DataFileManager;
import org.bodytrack.loggingdevice.DataFileUploader;
import org.bodytrack.loggingdevice.DataStoreConnectionConfig;
import org.bodytrack.loggingdevice.DataStoreServerConfig;
import org.bodytrack.loggingdevice.LoggingDevice;
import org.bodytrack.loggingdevice.LoggingDeviceConfig;
import org.bodytrack.loggingdevice.LoggingDeviceFactory;
import org.bodytrack.loggingdevice.NoSuchFileException;
import org.bodytrack.loggingdevice.WirelessAuthorizationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class BodyTrackLoggingDeviceGateway extends SerialDeviceCommandLineApplication
   {
   private static final Logger LOG = Logger.getLogger(BodyTrackLoggingDeviceGateway.class);
   private static final Logger CONSOLE_LOG = Logger.getLogger("ConsoleLog");

   private static final String HELP_COMMAND_LINE_SWITCH = "--help";
   private static final String NO_UPLOAD_COMMAND_LINE_SWITCH = "--no-upload";
   private static final String CONFIG_COMMAND_LINE_SWITCH = "--config";
   private static final String LOGGING_LEVEL_COMMAND_LINE_SWITCH = "--logging-level";
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
         s.append("   ").append(NO_UPLOAD_COMMAND_LINE_SWITCH).append("               ").append("Files will not be uploaded").append(LINE_SEPARATOR);
         s.append("   ").append(LOGGING_LEVEL_COMMAND_LINE_SWITCH).append("=<level>")
               .append("   Sets the logging level for the log file.  Has no effect on the").append(LINE_SEPARATOR)
               .append("                             console logging.  Valid values are 'trace', 'debug', and 'info'.").append(LINE_SEPARATOR);
         s.append("   ").append(CONFIG_COMMAND_LINE_SWITCH).append("=<path>").append("           ")
               .append("Specify a path to a local config file.  No connection to a device").append(LINE_SEPARATOR)
               .append("                             will be attempted (and thus no files will be downloaded).  Instead,").append(LINE_SEPARATOR)
               .append("                             the gateway will obtain upload server/port, user info, and device").append(LINE_SEPARATOR)
               .append("                             info from this config file.  The gateway will process all BodyTrack").append(LINE_SEPARATOR)
               .append("                             data files in the data file directory denoted by the upload server/port,").append(LINE_SEPARATOR)
               .append("                             user info, and device found in the config file.").append(LINE_SEPARATOR);
         s.append("   ").append(HELP_COMMAND_LINE_SWITCH).append("                    ").append("Displays this help message").append(LINE_SEPARATOR);
         println(s);
         }
      else
         {
         Level loggingLevel = LogManager.getRootLogger().getLevel();
         if (arguments.containsKey(LOGGING_LEVEL_COMMAND_LINE_SWITCH))
            {
            String desiredLoggingLevel = arguments.get(LOGGING_LEVEL_COMMAND_LINE_SWITCH);
            if (desiredLoggingLevel != null)
               {
               desiredLoggingLevel = desiredLoggingLevel.toLowerCase();
               }
            if ("trace".equals(desiredLoggingLevel))
               {
               loggingLevel = Level.TRACE;
               }
            else if ("debug".equals(desiredLoggingLevel))
               {
               loggingLevel = Level.DEBUG;
               }
            else if ("info".equals(desiredLoggingLevel))
               {
               loggingLevel = Level.INFO;
               }
            }
         LogManager.getRootLogger().setLevel(loggingLevel);
         final String message = "Log file logging level is '" + loggingLevel + "'";
         LOG.info(message);
         CONSOLE_LOG.info(message);

         arguments.remove(HELP_COMMAND_LINE_SWITCH);
         arguments.remove(LOGGING_LEVEL_COMMAND_LINE_SWITCH);
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

            CONSOLE_LOG.error("Device ping failure detected.  Cleaning up...");
            disconnect(false);

            LOG.debug("BodyTrackLoggingDeviceGateway.handlePingFailureEvent(): ping failure detected, attempting reconnect...");

            CONSOLE_LOG.info("Now attempting to reconnect to the device...");
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
               CONSOLE_LOG.info("You are already connected to a BodyTrack Logging Device.");
               }
            else
               {
               final boolean isDownloadDisabled = arguments.containsKey(CONFIG_COMMAND_LINE_SWITCH);
               if (isDownloadDisabled)
                  {
                  CONSOLE_LOG.info("Loading config file...");
                  device = createFakeLoggingDevice(arguments.get(CONFIG_COMMAND_LINE_SWITCH));
                  }
               else
                  {
                  CONSOLE_LOG.info("Scanning for a BodyTrack Logging Device...");
                  device = LoggingDeviceFactory.create();
                  }

               if (device == null)
                  {
                  CONSOLE_LOG.error("Connection failed.");
                  }
               else
                  {
                  device.addCreateLabDevicePingFailureEventListener(pingFailureEventListener);
                  final DataStoreServerConfig dataStoreServerConfig = device.getDataStoreServerConfig();
                  final LoggingDeviceConfig loggingDeviceConfig = device.getLoggingDeviceConfig();

                  if (dataStoreServerConfig != null && loggingDeviceConfig != null)
                     {
                     final boolean isUploadDisabled = arguments.containsKey(NO_UPLOAD_COMMAND_LINE_SWITCH);

                     final DataFileDownloader dataFileDownloader;
                     if (isDownloadDisabled)
                        {
                        CONSOLE_LOG.info("Data files will not be downloaded from a device since you specified a config file for device [" + loggingDeviceConfig.getDeviceNickname() + "] and user [" + loggingDeviceConfig.getUsername() + "]");
                        dataFileDownloader = null;
                        }
                     else
                        {
                        CONSOLE_LOG.info("Connection successful to device [" + loggingDeviceConfig.getDeviceNickname() + "] for user [" + loggingDeviceConfig.getUsername() + "] on serial port [" + device.getPortName() + "].");
                        dataFileDownloader = new DataFileDownloader(device);
                        }

                     final DataFileUploader dataFileUploader;
                     if (isUploadDisabled)
                        {
                        CONSOLE_LOG.info("Data files will not be uploaded since you specified the " + NO_UPLOAD_COMMAND_LINE_SWITCH + " option.");
                        dataFileUploader = null;
                        }
                     else
                        {
                        CONSOLE_LOG.info("Data files will be uploaded to " + dataStoreServerConfig.getServerName() + ":" + dataStoreServerConfig.getServerPort());
                        dataFileUploader = new DataFileUploader(dataStoreServerConfig, loggingDeviceConfig);
                        }

                     if (dataFileDownloader == null && dataFileUploader == null)
                        {
                        final String msg = "You have disabled both download and upload.  There's nothing for me to do, so I'm quitting.";
                        LOG.info(msg);
                        CONSOLE_LOG.info(msg);
                        device.disconnect();
                        System.exit(0);
                        }
                     else
                        {
                        CONSOLE_LOG.info("Starting up the Gateway...");
                        dataFileManager = new DataFileManager(dataStoreServerConfig,
                                                              loggingDeviceConfig,
                                                              dataFileUploader,
                                                              dataFileDownloader);
                        dataFileManager.startup();
                        }
                     }
                  else
                     {
                     final String message = "Connection Failed:  Could not obtain the DataStoreServerConfig and/or LoggingDeviceConfig from the device.  The Gateway will now shutdown.";
                     LOG.error(message);
                     CONSOLE_LOG.error(message);
                     device.disconnect();
                     System.exit(1);
                     }
                  }
               }
            }

         @Nullable
         private LoggingDevice createFakeLoggingDevice(@Nullable final String pathToConfigFile)
            {
            if (pathToConfigFile == null || pathToConfigFile.length() < 1)
               {
               CONSOLE_LOG.error("The specified config file path must not be empty.");
               }
            else
               {
               final File configFile = new File(pathToConfigFile);
               if (configFile.isFile())
                  {
                  final Properties properties = new Properties();
                  try
                     {
                     properties.load(new FileReader(configFile));
                     if (LOG.isDebugEnabled())
                        {
                        final StringBuilder s = new StringBuilder("\nProperties found in config file '" + pathToConfigFile + "':\n");
                        for (final Object key : new TreeSet<Object>(properties.keySet()))
                           {
                           final String val = properties.getProperty((String)key);
                           s.append("   [").append(key).append("]=[").append(val).append("]").append(System.getProperty("line.separator", "\n"));
                           }
                        LOG.debug("BodyTrackLoggingDeviceGateway.createFakeLoggingDevice(): " + s);
                        }

                     return new FakeLoggingDevice(properties);
                     }
                  catch (Exception e)
                     {
                     LOG.error("BodyTrackLoggingDeviceGateway.createFakeLoggingDevice(): Exception while trying to read the config file [" + pathToConfigFile + "]", e);
                     CONSOLE_LOG.error("Failed to read the config file '" + pathToConfigFile + "'");
                     }
                  }
               else
                  {
                  final String msg = "The specified config file path '" + pathToConfigFile + "' does not denote a valid config file.";
                  LOG.error("BodyTrackLoggingDeviceGateway.createFakeLoggingDevice(): " + msg);
                  CONSOLE_LOG.error(msg);
                  }
               }
            return null;
            }
         };

   private final Runnable printStatisticsAction =
         new Runnable()
         {
         public void run()
            {
            if (isConnected())
               {
               CONSOLE_LOG.info(dataFileManager.getStatistics());
               }
            else
               {
               CONSOLE_LOG.info("You are not connected to a BodyTrack Logging Device.");
               }
            }
         };

   private final Runnable setLoggingLevelAction =
         new Runnable()
         {
         public void run()
            {
            println("Choose the logging level for the log file:");
            println("   1: TRACE");
            println("   2: DEBUG (default)");
            println("   3: INFO");
            final Integer loggingLevelChoice = readInteger("Logging level (1-3): ");
            final Level chosenLevel;
            switch (loggingLevelChoice)
               {
               case (1):
                  chosenLevel = Level.TRACE;
                  break;

               case (2):
                  chosenLevel = Level.DEBUG;
                  break;

               case (3):
                  chosenLevel = Level.INFO;
                  break;

               default:
                  chosenLevel = null;
               }

            if (chosenLevel == null)
               {
               println("Invalid choice.");
               }
            else
               {
               LogManager.getRootLogger().setLevel(chosenLevel);
               final String message = "Logging level now set to '" + chosenLevel + "'.";
               LOG.info(message);
               println(message);
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
            CONSOLE_LOG.info("Bye!");
            }
         };

   private void registerActions()
      {
      registerAction("c", scanAndConnectToDeviceAction);
      registerAction("s", printStatisticsAction);
      registerAction("l", setLoggingLevelAction);
      registerAction("d", disconnectFromDeviceAction);

      registerAction(QUIT_COMMAND, quitAction);
      }

   @Override
   protected void startup()
      {
      // autoconnect on startup
      scanAndConnectToDeviceAction.run();
      }

   protected final void menu()
      {
      println("COMMANDS -----------------------------------");
      println("");
      println("c         Scan all serial ports and connect to the first device found");
      println("s         Print statistics for files downloaded, uploaded, and deleted");
      println("l         Set the logging level for the log file (has no effect on console logging)");
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

   private class FakeLoggingDevice implements LoggingDevice
      {
      private final LoggingDeviceConfig loggingDeviceConfig;
      private final DataStoreServerConfig dataStoreServerConfig;
      private final DataStoreConnectionConfig dataStoreConnectionConfig;

      private FakeLoggingDevice(@NotNull final Properties properties)
         {
         loggingDeviceConfig =
               new LoggingDeviceConfig()
               {
               @NotNull
               @Override
               public String getUsername()
                  {
                  return properties.getProperty("user", "");
                  }

               @NotNull
               @Override
               public String getDeviceNickname()
                  {
                  return properties.getProperty("nickname", "");
                  }
               };

         dataStoreServerConfig =
               new DataStoreServerConfig()
               {
               @NotNull
               @Override
               public String getServerName()
                  {
                  return properties.getProperty("server", "");
                  }

               @NotNull
               @Override
               public String getServerPort()
                  {
                  return properties.getProperty("port", "");
                  }
               };

         final WirelessAuthorizationType authType = WirelessAuthorizationType.findById(properties.getProperty("auth", ""));
         if (authType == null)
            {
            dataStoreConnectionConfig = null;
            }
         else
            {
            dataStoreConnectionConfig =
                  new DataStoreConnectionConfig()
                  {
                  @NotNull
                  @Override
                  public String getWirelessSsid()
                     {
                     return properties.getProperty("ssid", "");
                     }

                  @NotNull
                  @Override
                  public WirelessAuthorizationType getWirelessAuthorizationType()
                     {

                     return authType;
                     }

                  @NotNull
                  @Override
                  public String getWirelessAuthorizationKey()
                     {
                     return properties.getProperty("phrase", "");
                     }
                  };
            }
         }

      @Override
      public SortedSet<String> getAvailableFilenames()
         {
         return new TreeSet<String>();
         }

      @Override
      public DataFile getFile(@Nullable final String filename) throws NoSuchFileException
         {
         throw new NoSuchFileException("This fake logging device doesn't support file retrieval");
         }

      @Override
      public boolean deleteFile(@Nullable final String filename)
         {
         return false;
         }

      @Override
      public LoggingDeviceConfig getLoggingDeviceConfig()
         {
         return loggingDeviceConfig;
         }

      @Override
      public DataStoreServerConfig getDataStoreServerConfig()
         {
         return dataStoreServerConfig;
         }

      @Override
      public DataStoreConnectionConfig getDataStoreConnectionConfig()
         {
         return dataStoreConnectionConfig;
         }

      @Override
      public String getPortName()
         {
         return "FakePort";
         }

      @Override
      public void disconnect()
         {
         // do nothing
         }

      @Override
      public void addCreateLabDevicePingFailureEventListener(final CreateLabDevicePingFailureEventListener listener)
         {
         // do nothing
         }

      @Override
      public void removeCreateLabDevicePingFailureEventListener(final CreateLabDevicePingFailureEventListener listener)
         {
         // do nothing
         }
      }
   }
