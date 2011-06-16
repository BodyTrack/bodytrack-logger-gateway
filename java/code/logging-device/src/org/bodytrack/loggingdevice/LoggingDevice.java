package org.bodytrack.loggingdevice;

import edu.cmu.ri.createlab.device.CreateLabDeviceProxy;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public interface LoggingDevice extends CreateLabDeviceProxy
   {
   /**
    * Retrieves a filename from the logging device.  Returns the filename as a {@link String} upon success, or
    * returns <code>null</code> if the command failed.  If there are no data files available from the logging device,
    * this method will return an empty {@link String}.
    */
   @Nullable
   String getFilename();

   /**
    * Retrieves a {@link DataFile} from the logging device specified by the given <code>filename</code>.  Returns a
    * {@link DataFile} for the file upon success, or returns <code>null</code> if the command failed or the given
    * <code>filename</code> is <code>null</code>.  If there is no such file available from the logging device, this
    * method will throw a {@link NoSuchFileException}.
    *
    * @throws NoSuchFileException if the logging device does not have a file with the given <code>filename</code>
    */
   @Nullable
   DataFile getFile(@Nullable final String filename) throws NoSuchFileException;

   /**
    * Requests that the device erases the file specified by the given <code>filename</code>.  Returns <code>true</code>
    * upon success, <code>false</code> otherwise.
    */
   boolean eraseFile(@Nullable final String filename);

   /** Returns the {@link LoggingDeviceConfig configuration} for this <code>LoggingDevice</code>. */
   @Nullable
   LoggingDeviceConfig getLoggingDeviceConfig();

   /** Returns the {@link DataStoreServerConfig configuration} for this <code>LoggingDevice</code>. */
   @Nullable
   DataStoreServerConfig getDataStoreServerConfig();

   /** Returns the {@link DataStoreConnectionConfig configuration} for this <code>LoggingDevice</code>. */
   @Nullable
   DataStoreConnectionConfig getDataStoreConnectionConfig();
   }