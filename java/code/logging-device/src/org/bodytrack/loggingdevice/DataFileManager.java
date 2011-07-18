package org.bodytrack.loggingdevice;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * <code>DataFileManager</code> helps manage {@link DataFile}s for a particular device.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class DataFileManager
   {
   private static final Logger LOG = Logger.getLogger(DataFileManager.class);

   /**
    * <p>
    * <code>DataFileStatus</code> represents the various states a {@link DataFile} can be in, from the perspective of
    * the {@link DataFileManager}.
    * </p>
    *
    * @author Chris Bartley (bartley@cmu.edu)
    */
   public static enum DataFileStatus
      {
         DOWNLOADED(".BT"),
         UPLOADING(".UPLOADING"),
         UPLOADED(".BTU");

      private final String filenameExtension;

      /**
       * Returns the <code>DataFileStatus</code> for the given filename, or <code>null</code> if no match was found or
       * if the given <code>filename</code> was <code>null</code>.
       */
      @Nullable
      public static DataFileStatus getStatusForFilename(@Nullable final String filename)
         {
         if (filename != null)
            {
            for (final DataFileStatus status : DataFileStatus.values())
               {
               if (filename.endsWith(status.getFilenameExtension()))
                  {
                  return status;
                  }
               }
            }
         return null;
         }

      private DataFileStatus(@NotNull final String filenameExtension)
         {
         this.filenameExtension = filenameExtension;
         }

      @NotNull
      public String getFilenameExtension()
         {
         return filenameExtension;
         }

      /** Returns whether the given {@link File} has this status. */
      public boolean hasStatus(final File file)
         {
         return file != null &&
                file.getName().toUpperCase().endsWith(filenameExtension);
         }

      /**
       * Simply returns the filename extension.
       *
       * @see #getFilenameExtension()
       */
      @Override
      public String toString()
         {
         return filenameExtension;
         }
      }

   public static final String WRITING_FILENAME_EXTENSION = ".WRITING";

   private static final class Config
      {
      private final LoggingDeviceConfig loggingDeviceConfig;
      private final DataStoreServerConfig dataStoreServerConfig;

      private Config(final LoggingDeviceConfig loggingDeviceConfig, final DataStoreServerConfig dataStoreServerConfig)
         {
         this.loggingDeviceConfig = loggingDeviceConfig;
         this.dataStoreServerConfig = dataStoreServerConfig;
         }

      public LoggingDeviceConfig getLoggingDeviceConfig()
         {
         return loggingDeviceConfig;
         }

      public DataStoreServerConfig getDataStoreServerConfig()
         {
         return dataStoreServerConfig;
         }

      @Override
      public boolean equals(final Object o)
         {
         if (this == o)
            {
            return true;
            }
         if (o == null || getClass() != o.getClass())
            {
            return false;
            }

         final Config that = (Config)o;

         if (dataStoreServerConfig != null ? !dataStoreServerConfig.equals(that.dataStoreServerConfig) : that.dataStoreServerConfig != null)
            {
            return false;
            }
         if (loggingDeviceConfig != null ? !loggingDeviceConfig.equals(that.loggingDeviceConfig) : that.loggingDeviceConfig != null)
            {
            return false;
            }

         return true;
         }

      @Override
      public int hashCode()
         {
         int result = loggingDeviceConfig != null ? loggingDeviceConfig.hashCode() : 0;
         result = 31 * result + (dataStoreServerConfig != null ? dataStoreServerConfig.hashCode() : 0);
         return result;
         }
      }

   private static final Map<Config, DataFileManager> INSTANCES = new HashMap<Config, DataFileManager>();
   private static final Lock INSTANCE_LOCK = new ReentrantLock();

   /**
    * Returns a <code>DataFileManager</code> for the device specified by the given {@link LoggingDevice}.  Returns
    * <code>null</code> if the given {@link LoggingDevice} is <code>null</code>.
    *
    * @throws IllegalStateException if the {@link LoggingDeviceConfig} returned by the given {@link LoggingDevice} is <code>null</code>.
    * @throws IllegalStateException if the {@link DataStoreServerConfig} returned by the given {@link LoggingDevice} is <code>null</code>.
    */
   @Nullable
   public static DataFileManager getInstance(@Nullable final LoggingDevice loggingDevice) throws IllegalStateException
      {
      DataFileManager dataFileManager = null;

      if (loggingDevice != null)
         {
         final LoggingDeviceConfig loggingDeviceConfig = loggingDevice.getLoggingDeviceConfig();
         if (loggingDeviceConfig == null)
            {
            throw new IllegalStateException("Cannot create the DataFileManager because the LoggingDeviceConfig is null.");
            }

         final DataStoreServerConfig dataStoreServerConfig = loggingDevice.getDataStoreServerConfig();
         if (dataStoreServerConfig == null)
            {
            throw new IllegalStateException("Cannot create the DataFileManager because the DataStoreServerConfig is null.");
            }

         INSTANCE_LOCK.lock();  // block until condition holds
         try
            {
            dataFileManager = INSTANCES.get(loggingDeviceConfig);
            if (dataFileManager == null)
               {
               final Config config = new Config(loggingDeviceConfig, dataStoreServerConfig);
               dataFileManager = new DataFileManager(config);
               INSTANCES.put(config, dataFileManager);
               }
            }
         finally
            {
            INSTANCE_LOCK.unlock();
            }
         }

      return dataFileManager;
      }

   private final File dataFileDirectory;
   private final Lock filesystemLock = new ReentrantLock();
   private final FilenameFilter uploadableFileFilenameFilter =
         new FilenameFilter()
         {
         @Override
         public boolean accept(final File file, final String filename)
            {
            return file != null && filename != null && filename.toUpperCase().endsWith(DataFile.FILENAME_EXTENSION);
            }
         };

   /** Creates a <code>DataFileManager</code> for the device specified by the given {@link LoggingDeviceConfig}. */
   private DataFileManager(@NotNull final Config config)
      {
      final File serverDirectory = new File(LoggingDeviceConstants.FilePaths.LOGGING_DEVICE_DATA_DIRECTORY, config.getDataStoreServerConfig().getServerName() + "_" + config.getDataStoreServerConfig().getServerPort());
      dataFileDirectory = new File(serverDirectory, "User" + config.getLoggingDeviceConfig().getUsername() + File.separator + config.getLoggingDeviceConfig().getDeviceNickname());

      // make sure the directory exists
      //noinspection ResultOfMethodCallIgnored
      dataFileDirectory.mkdirs();
      }

   /**
    * Saves the given {@link DataFile} to the given directory.  Returns a {@link File} for the saved file upon success,
    * or returns <code>null</code> if the given <code>DataFile</code> is <code>null</code>,
    * {@link DataFile#isEmpty() empty}, or another file with the same base
    * {@link DataFile#getBaseFilename() base filename} already exists on disk.
    *
    * @throws IOException if the file cannot be written
    */
   @Nullable
   public File save(@Nullable final DataFile dataFile) throws IOException
      {
      if (dataFile != null && !dataFile.isEmpty())
         {
         if (LOG.isDebugEnabled())
            {
            LOG.debug("DataFileManager.save(): Request to save DataFile [" + dataFile.getFilename() + "]");
            }

         filesystemLock.lock();  // block until condition holds
         try
            {
            // see whether the file already exists (in some form)
            if (doesFileExist(dataFile))
               {
               // simply log that the file exists and is being skipped
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileManager.save(): A datafile with the base filename [" + dataFile.getBaseFilename() + "] already exists, so this one will be ignored.");
                  }
               }
            else
               {
               // try writing the file
               DataOutputStream os = null;
               try
                  {
                  // write the file, but use a filename with a special extension to signify the file is being written
                  final File tempFile = new File(dataFileDirectory, dataFile.getFilename() + WRITING_FILENAME_EXTENSION);
                  os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
                  dataFile.writeToOutputStream(os);

                  // rename the file
                  final File file = new File(dataFileDirectory, dataFile.getFilename());
                  final boolean wasRenameSuccessful = tempFile.renameTo(file);
                  if (wasRenameSuccessful)
                     {
                     // success, so return the file
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileManager.save(): DataFile [" + file + "] saved successfully");
                        }
                     return file;
                     }
                  else
                     {
                     LOG.error("DataFileManager.save(): Failed to rename file [" + tempFile + "] to [" + file + "].  Attempting to delete temp file...");
                     if (tempFile.delete())
                        {
                        if (LOG.isInfoEnabled())
                           {
                           LOG.info("DataFileManager.save(): deleted temp file [" + tempFile + "]");
                           }
                        }
                     else
                        {
                        LOG.error("DataFileManager.save(): Failed to delete temp file [" + tempFile + "]");
                        }
                     }
                  }
               finally
                  {
                  if (os != null)
                     {
                     try
                        {
                        os.close();
                        }
                     catch (IOException ignored)
                        {
                        LOG.error("DataFileManager.save(): IOException while trying to close the DataOutputStream.  Oh well.");
                        }
                     }
                  }
               }
            }
         finally
            {
            filesystemLock.unlock();
            }
         }
      return null;
      }

   /**
    * Finds a file in need of uploading, renames it to signify it's being uploaded, and returns it to the caller.
    * Returns <code>null</code> if no files are ready for uploading.
    */
   @Nullable
   public File getFileToUpload()
      {
      filesystemLock.lock();  // block until condition holds
      try
         {
         // get all files ready for uploading
         final File[] uploadableFiles = dataFileDirectory.listFiles(uploadableFileFilenameFilter);
         if (uploadableFiles != null && uploadableFiles.length > 0)
            {
            // just choose the first valid one
            final File file = uploadableFiles[0];

            // mark a file as being uploaded simply by changing its extension
            final File fileToUpload = new File(dataFileDirectory, file.getName() + DataFileStatus.UPLOADING.getFilenameExtension());

            // make sure the file doesn't already exist before renaming it (it shouldn't ever happen, but...)
            if (fileToUpload.exists())
               {
               LOG.error("DataFileManager.getFileToUpload(): File [" + fileToUpload + "] already exists, so another thread must be uploading it.  Aborting and returning null.");
               }
            else
               {
               final boolean wasRenameSuccessful = file.renameTo(fileToUpload);
               if (wasRenameSuccessful)
                  {
                  // success, so return the file
                  if (LOG.isDebugEnabled())
                     {
                     LOG.debug("DataFileManager.getFileToUpload(): Returning file [" + fileToUpload + "]");
                     }
                  return fileToUpload;
                  }
               else
                  {
                  LOG.error("DataFileManager.getFileToUpload(): Failed to rename file [" + file + "] to [" + fileToUpload + "].  Aborting and returning null.");
                  }
               }
            }
         else
            {
            LOG.debug("DataFileManager.getFileToUpload(): No uploadable files found, returning null");
            }
         }
      finally
         {
         filesystemLock.unlock();
         }

      return null;
      }

   public void uploadComplete(@Nullable final File uploadedFile, @Nullable final DataFileUploadResponse uploadResponse)
      {
      if (DataFileStatus.UPLOADING.hasStatus(uploadedFile))
         {
         if (LOG.isDebugEnabled())
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileManager.uploadComplete(): file [" + uploadedFile + "], response = [" + uploadResponse + "]");
               }
            }

         if (uploadResponse == null)
            {
            // if the response was null, then a problem occurred during upload, so just rename the file and return it
            // back to the pool of uploadable files.

            filesystemLock.lock();  // block until condition holds
            try
               {
               // change the extension back to the default
               final String newFilename = uploadedFile.getName().substring(0, uploadedFile.getName().length() - DataFileStatus.UPLOADING.getFilenameExtension().length());
               final File file = new File(dataFileDirectory, newFilename);

               if (file.exists())
                  {
                  // this shouldn't happen, but...
                  LOG.error("DataFileManager.uploadComplete(): File [" + file + "] already exists--this shouldn't happen!  Aborting.");
                  }
               else
                  {
                  final boolean wasRenameSuccessful = uploadedFile.renameTo(file);
                  if (wasRenameSuccessful)
                     {
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileManager.getFileToUpload(): Renamed file [" + uploadedFile + "] to [" + file + "]");
                        }
                     }
                  else
                     {
                     LOG.error("DataFileManager.getFileToUpload(): Failed to rename file [" + uploadedFile + "] to [" + file + "].  Aborting.");
                     }
                  }
               }
            finally
               {
               filesystemLock.unlock();
               }
            }
         else
            {
            final Integer numFailedBinRecs = uploadResponse.getFailedBinRecs();
            final List<String> errors = uploadResponse.getErrors();
            if (numFailedBinRecs == null || numFailedBinRecs > 0 || (errors != null && errors.size() > 0))
               {
               // TODO: what if the file is corrupt on the device?  This logic will cause repeated upload attempts
               // to the server, which will always fail, and the file will never get removed from the device.  If
               // enough of these corrupt files collect on the device, it may eventually fill up.

               // we had a failure, so just delete the local file so that we'll eventually get it again from the device
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileManager.uploadComplete(): num failed binrecs is [" + numFailedBinRecs + "] and errors is [" + errors + "], so just delete local copy to allow for a future retry");
                  }
               filesystemLock.lock();  // block until condition holds
               try
                  {
                  if (uploadedFile.delete())
                     {
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileManager.uploadComplete(): deleted file [" + uploadedFile + "]");
                        }
                     }
                  else
                     {
                     LOG.error("DataFileManager.uploadComplete(): failed to delete file [" + uploadedFile + "]");
                     }
                  }
               finally
                  {
                  filesystemLock.unlock();
                  }
               }
            else
               {
               // no failures!  rename the file to signify that the upload was successful...

               filesystemLock.lock();  // block until condition holds
               try
                  {
                  // change the extension to the one used for uploaded files
                  final String newFilename = uploadedFile.getName().substring(0, uploadedFile.getName().indexOf(DataFile.FILENAME_EXTENSION)) + DataFileStatus.UPLOADED.getFilenameExtension();
                  final File file = new File(dataFileDirectory, newFilename);

                  if (file.exists())
                     {
                     // this shouldn't happen, but...
                     LOG.error("DataFileManager.uploadComplete(): File [" + file + "] already exists--this shouldn't happen!  Aborting.");
                     // TODO: maybe a better thing to do here would be to diff the two files, make sure they're the same and, if so, then just
                     // delete the .UPLOADING one.  Or, better yet, don't even try to upload a .BT if a corresponding and identical .BTU exists.
                     // Not sure yet what to do if the diff fails.
                     }
                  else
                     {
                     final boolean wasRenameSuccessful = uploadedFile.renameTo(file);
                     if (wasRenameSuccessful)
                        {
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileManager.getFileToUpload(): Renamed file [" + uploadedFile + "] to [" + file + "]");
                           }
                        }
                     else
                        {
                        LOG.error("DataFileManager.getFileToUpload(): Failed to rename file [" + uploadedFile + "] to [" + file + "].  Aborting.");
                        }
                     }
                  }
               finally
                  {
                  filesystemLock.unlock();
                  }

               // Don't worry about telling the device that the file can be deleted here--let the DataFileDownloader
               // handle that the next time that it tries to fetch the file and sees that it's already been uploaded.
               }
            }
         }
      }

   /**
    * Returns true if a file already exists with the same {@link DataFile#getBaseFilename() base filename} as the given
    * {@link DataFile}.
    */
   private boolean doesFileExist(@NotNull final DataFile dataFile)
      {
      final File[] files = getFilesWithSameBaseFilename(dataFile.getBaseFilename());

      return !dataFile.isEmpty() && files != null && files.length > 0;
      }

   /**
    * If a file already exists with the same {@link DataFile#getBaseFilename() base filename} as the given
    * <code>filename</code>, then this method returns the {@link DataFileStatus} of that file; otherwise it returns
    * <code>null</code>.  This method also returns <code>null</code> if the given <code>filename</code> is
    * <code>null</code>.
    */
   @Nullable
   public DataFileStatus getDataFileStatus(@Nullable final String filename)
      {
      if (filename != null)
         {
         final int dotPosition = filename.indexOf('.');
         final String baseFilename;
         if (dotPosition >= 0)
            {
            baseFilename = filename.substring(0, dotPosition);
            }
         else
            {
            baseFilename = filename;
            }

         final File[] files = getFilesWithSameBaseFilename(baseFilename);
         if (files != null && files.length > 0 && files[0] != null)
            {
            return DataFileStatus.getStatusForFilename(files[0].getName());
            }
         }
      return null;
      }

   private File[] getFilesWithSameBaseFilename(final String baseFilename)
      {
      return dataFileDirectory.listFiles(
            new FilenameFilter()
            {
            private final String baseFilenameUppercase = baseFilename.toUpperCase();

            @Override
            public boolean accept(final File file, final String filename)
               {
               return file != null && filename != null && filename.toUpperCase().startsWith(baseFilenameUppercase);
               }
            });
      }
   }
