package org.bodytrack.loggingdevice;

import java.io.File;

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
      public static final File LOGGING_DEVICE_DATA_DIRECTORY = new File(BODYTRACK_HOME_DIRECTORY, "Logging Device Data");

      static
         {
         // make sure the logging device data directory exists
         //noinspection ResultOfMethodCallIgnored
         LOGGING_DEVICE_DATA_DIRECTORY.mkdirs();
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
