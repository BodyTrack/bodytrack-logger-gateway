package org.bodytrack.applications;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.SortedMap;
import java.util.SortedSet;
import edu.cmu.ri.createlab.device.CreateLabDevicePingFailureEventListener;
import edu.cmu.ri.createlab.serial.commandline.SerialDeviceCommandLineApplication;
import org.bodytrack.loggingdevice.DataFile;
import org.bodytrack.loggingdevice.DataFileManager;
import org.bodytrack.loggingdevice.DataStoreConnectionConfig;
import org.bodytrack.loggingdevice.DataStoreServerConfig;
import org.bodytrack.loggingdevice.LoggingDevice;
import org.bodytrack.loggingdevice.LoggingDeviceConfig;
import org.bodytrack.loggingdevice.LoggingDeviceFactory;
import org.bodytrack.loggingdevice.NoSuchFileException;
import org.bodytrack.loggingdevice.WirelessAuthorizationType;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class CommandLineLoggingDevice extends SerialDeviceCommandLineApplication
   {
   public static void main(final String[] args)
      {
      new CommandLineLoggingDevice().run();
      }

   private LoggingDevice device;
   private DataFileManager dataFileManager;

   private final CreateLabDevicePingFailureEventListener pingFailureEventListener =
         new CreateLabDevicePingFailureEventListener()
         {
         public void handlePingFailureEvent()
            {
            println("Device ping failure detected.  You will need to reconnect.");
            device = null;
            dataFileManager = null;
            }
         };

   private CommandLineLoggingDevice()
      {
      super(new BufferedReader(new InputStreamReader(System.in)));

      registerActions();
      }

   private final Runnable enumeratePortsAction =
         new Runnable()
         {
         public void run()
            {
            enumeratePorts();
            }
         };

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
                  final LoggingDeviceConfig loggingDeviceConfig = device.getLoggingDeviceConfig();

                  if (loggingDeviceConfig != null)
                     {
                     dataFileManager = DataFileManager.getInstance(device);
                     println("Connection successful!");
                     }
                  else
                     {
                     println("Failed to obtain the logging device config.  You will need to reconnect.!");
                     disconnect();
                     }
                  }
               }
            }
         };

   private final Runnable connectToDeviceAction =
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
               final SortedMap<Integer, String> portMap = enumeratePorts();

               if (!portMap.isEmpty())
                  {
                  final Integer index = readInteger("Connect to port number: ");

                  if (index == null)
                     {
                     println("Invalid port");
                     }
                  else
                     {
                     final String serialPortName = portMap.get(index);

                     if (serialPortName != null)
                        {
                        device = LoggingDeviceFactory.create(serialPortName);

                        if (device == null)
                           {
                           println("Connection failed!");
                           }
                        else
                           {
                           device.addCreateLabDevicePingFailureEventListener(pingFailureEventListener);
                           final LoggingDeviceConfig loggingDeviceConfig = device.getLoggingDeviceConfig();

                           if (loggingDeviceConfig != null)
                              {
                              dataFileManager = DataFileManager.getInstance(device);
                              println("Connection successful!");
                              }
                           else
                              {
                              println("Failed to obtain the logging device config.  You will need to reconnect.!");
                              disconnect();
                              }
                           }
                        }
                     else
                        {
                        println("Invalid port");
                        }
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

   private final Runnable downloadFileAction =
         new Runnable()
         {
         public void run()
            {
            if (isConnected())
               {
               final String filename = readString("Name of file to download: ");
               if (filename == null || filename.length() <= 0)
                  {
                  println("Invalid file name");
                  return;
                  }

               try
                  {
                  final DataFile dataFile = device.getFile(filename);
                  if (dataFile == null)
                     {
                     println("Failed to transfer the file from the device.");
                     }
                  else if (dataFile.isEmpty())
                     {
                     println("No data available.");
                     }
                  else
                     {
                     println("File transferred successfully! [" + dataFile + "]");

                     try
                        {
                        final File file = dataFileManager.save(dataFile);
                        if (file == null)
                           {
                           println("Failed to save file.");
                           }
                        else
                           {
                           println("File saved successfully [" + file + "]");
                           }
                        }
                     catch (IOException ignored)
                        {
                        println("IOException while trying to download a file.");
                        }
                     }
                  }
               catch (NoSuchFileException ignored)
                  {
                  println("The file '" + filename + "' does not exist on the device.");
                  }
               }
            else
               {
               println("You must be connected to the BodyTrack Logging Device first.");
               }
            }
         };

   private final Runnable eraseFileAction =
         new Runnable()
         {
         public void run()
            {
            if (isConnected())
               {
               final String filename = readString("Name of file to erase: ");
               if (filename == null || filename.length() <= 0)
                  {
                  println("Invalid file name");
                  return;
                  }

               final boolean wasSuccessful = device.eraseFile(filename);
               if (wasSuccessful)
                  {
                  println("File '" + filename + "' erased successfully.");
                  }
               else
                  {
                  println("Failed to erase file '" + filename + "'.");
                  }
               }
            else
               {
               println("You must be connected to the BodyTrack Logging Device first.");
               }
            }
         };

   private final Runnable getAvailableFilesAction =
         new Runnable()
         {
         public void run()
            {
            if (isConnected())
               {
               final SortedSet<String> filenames = device.getAvailableFilenames();

               if (filenames == null)
                  {
                  println("Failed to get the list of available files.");
                  }
               else if (filenames.isEmpty())
                  {
                  println("No files available.");
                  }
               else
                  {
                  println("Found " + filenames.size() + " available file" + (filenames.size() == 1 ? "" : "s") + ":");

                  for (final String filename : filenames)
                     {
                     println("      " + filename);
                     }
                  }
               }
            else
               {
               println("You must be connected to the BodyTrack Logging Device first.");
               }
            }
         };

   private final Runnable quitAction =
         new Runnable()
         {
         public void run()
            {
            disconnect();
            println("Bye!");
            }
         };

   private abstract class GetStringAction implements Runnable
      {
      private final String label;

      protected GetStringAction(final String label)
         {
         this.label = label;
         }

      @Override
      public void run()
         {
         if (isConnected())
            {
            println(label + ": " + getString());
            }
         else
            {
            println("You must be connected to the BodyTrack Logging Device first.");
            }
         }

      @Nullable
      protected abstract String getString();
      }

   private void registerActions()
      {
      registerAction("?", enumeratePortsAction);
      registerAction("C", scanAndConnectToDeviceAction);
      registerAction("c", connectToDeviceAction);
      registerAction("d", disconnectFromDeviceAction);

      registerAction("u",
                     new GetStringAction("Username")
                     {
                     @Override
                     protected String getString()
                        {
                        final LoggingDeviceConfig config = device.getLoggingDeviceConfig();
                        return (config == null) ? null : config.getUsername();
                        }
                     });
      registerAction("s",
                     new GetStringAction("Wireless SSID")
                     {
                     @Override
                     protected String getString()
                        {
                        final DataStoreConnectionConfig config = device.getDataStoreConnectionConfig();
                        return (config == null) ? null : config.getWirelessSsid();
                        }
                     });
      registerAction("a",
                     new GetStringAction("Wireless Authorization Type")
                     {
                     @Override
                     protected String getString()
                        {
                        final DataStoreConnectionConfig config = device.getDataStoreConnectionConfig();
                        final WirelessAuthorizationType wirelessAuthorizationType = (config == null) ? null : config.getWirelessAuthorizationType();
                        return (wirelessAuthorizationType == null) ? "Unknown" : wirelessAuthorizationType.toString();
                        }
                     });
      registerAction("k",
                     new GetStringAction("Wireless Authorization Key")
                     {
                     @Override
                     protected String getString()
                        {
                        final DataStoreConnectionConfig config = device.getDataStoreConnectionConfig();
                        return (config == null) ? null : config.getWirelessAuthorizationKey();
                        }
                     });
      registerAction("n",
                     new GetStringAction("Device Nickname")
                     {
                     @Override
                     protected String getString()
                        {
                        final LoggingDeviceConfig config = device.getLoggingDeviceConfig();
                        return (config == null) ? null : config.getDeviceNickname();
                        }
                     });
      registerAction("v",
                     new GetStringAction("Server Name")
                     {
                     @Override
                     protected String getString()
                        {
                        final DataStoreServerConfig config = device.getDataStoreServerConfig();
                        return (config == null) ? null : config.getServerName();
                        }
                     });
      registerAction("p",
                     new GetStringAction("Server Port")
                     {
                     @Override
                     protected String getString()
                        {
                        final DataStoreServerConfig config = device.getDataStoreServerConfig();
                        return (config == null) ? null : config.getServerPort();
                        }
                     });

      registerAction("g", getAvailableFilesAction);
      registerAction("f", downloadFileAction);
      registerAction("e", eraseFileAction);

      registerAction(QUIT_COMMAND, quitAction);
      }

   protected final void menu()
      {
      println("COMMANDS -----------------------------------");
      println("");
      println("?         List all available serial ports");
      println("");
      println("C         Scan all serial ports and connect to the first device found");
      println("c         Connect to the device");
      println("d         Disconnect from the device");
      println("");
      println("u         Gets the username associated with this device");
      println("n         Gets this device's nickname");
      println("");
      println("s         Gets the wireless SSID which this device is configured to use");
      println("a         Gets the wireless authorization type which this device is configured to use");
      println("k         Gets the wireless authorization key which this device is configured to use");
      println("");
      println("v         Gets the server name to which this device is configured to upload");
      println("p         Gets the server port to which this device is configured to upload");
      println("");
      println("g         Gets the set of available files from the device");
      println("f         Downloads a file from the device");
      println("e         Erases the specified file from the device");
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
         device.disconnect();
         device = null;
         dataFileManager = null;
         }
      }
   }
