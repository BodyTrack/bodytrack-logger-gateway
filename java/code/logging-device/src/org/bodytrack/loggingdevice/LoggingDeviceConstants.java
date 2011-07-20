package org.bodytrack.loggingdevice;

import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * <code>LoggingDeviceConstants</code> defines various constants for BodyTrack Logging Devices.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class LoggingDeviceConstants
   {
   public static final class FilePaths
      {
      public static final File BODYTRACK_HOME_DIRECTORY = new File(System.getProperty("user.home") + File.separator + "BodyTrack" + File.separator);
      public static final File LOGGING_DEVICE_ROOT_DATA_DIRECTORY = new File(BODYTRACK_HOME_DIRECTORY, "LoggingDeviceData");

      static
         {
         // make sure the logging device data directory exists
         //noinspection ResultOfMethodCallIgnored
         LOGGING_DEVICE_ROOT_DATA_DIRECTORY.mkdirs();
         }

      /**
       * Creates (if necessary) and returns the directory into which data files for the given
       * {@link DataStoreServerConfig} and {@link LoggingDeviceConfig} should be stored.
       */
      @NotNull
      public static File getDeviceDataDirectory(@NotNull final DataStoreServerConfig dataStoreServerConfig, @NotNull final LoggingDeviceConfig loggingDeviceConfig)
         {
         final File serverDirectory = new File(LOGGING_DEVICE_ROOT_DATA_DIRECTORY, dataStoreServerConfig.getServerName() + "_" + dataStoreServerConfig.getServerPort());
         final File deviceDataFileDirectory = new File(serverDirectory, "User" + loggingDeviceConfig.getUsername() + File.separator + loggingDeviceConfig.getDeviceNickname());

         // make sure the directory exists
         //noinspection ResultOfMethodCallIgnored
         deviceDataFileDirectory.mkdirs();

         return deviceDataFileDirectory;
         }

      private FilePaths()
         {
         // private to prevent instantiation
         }
      }

   private LoggingDeviceConstants()
      {
      // private to prevent instantiation
      }
   }
