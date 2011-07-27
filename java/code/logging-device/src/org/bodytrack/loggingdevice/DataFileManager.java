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
import java.util.SortedSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import edu.cmu.ri.createlab.util.thread.DaemonThreadFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class DataFileManager implements DataFileUploader.EventListener, DataFileDownloader.EventListener
   {
   private static final Logger LOG = Logger.getLogger(DataFileManager.class);
   private static final int NUM_DOWNLOAD_RETRIES_FOR_FAILED_CHECKSUM = 5;

   @NotNull
   private final File dataFileDirectory;

   @Nullable
   private DataFileUploader dataFileUploader = null;

   @Nullable
   private DataFileDownloader dataFileDownloader = null;

   private boolean isRunning = false;
   private boolean hasBeenShutdown = false;

   private final Lock lock = new ReentrantLock();
   private final Map<String, Integer> retryDownloadCountMap = new HashMap<String, Integer>();
   private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10, new DaemonThreadFactory(this.getClass() + ".executor"));

   private final Runnable submitFileListDownloadRunnable =
         new Runnable()
         {
         @Override
         public void run()
            {
            if (dataFileDownloader != null)
               {
               dataFileDownloader.submitDataFileListRequestTask();
               }
            }
         };

   public DataFileManager(@NotNull final DataStoreServerConfig dataStoreServerConfig,
                          @NotNull final LoggingDeviceConfig loggingDeviceConfig)
      {
      this(dataStoreServerConfig, loggingDeviceConfig, null, null);
      }

   public DataFileManager(@NotNull final DataStoreServerConfig dataStoreServerConfig,
                          @NotNull final LoggingDeviceConfig loggingDeviceConfig,
                          @Nullable final DataFileUploader dataFileUploader,
                          @Nullable final DataFileDownloader dataFileDownloader)
      {
      this.dataFileUploader = dataFileUploader;
      this.dataFileDownloader = dataFileDownloader;
      this.dataFileDirectory = LoggingDeviceGatewayConstants.FilePaths.getDeviceDataDirectory(dataStoreServerConfig, loggingDeviceConfig);

      // register self as a listener to the uploader so we can get notified when uploads are complete
      if (dataFileUploader != null)
         {
         dataFileUploader.addEventListener(this);
         }

      // register self as a listener to the downloader so we can get notified when downloads are complete
      if (dataFileDownloader != null)
         {
         dataFileDownloader.addEventListener(this);
         }
      }

   public void startup()
      {
      lock.lock();  // block until condition holds
      try
         {
         if (!isRunning && !hasBeenShutdown)
            {
            isRunning = true;

            // Clean up files in data file directory, in case the program was terminated before while an upload was in
            // progress We'll simply rename any files with the {@link DataFileStatus#UPLOADING} extension so that they
            // have the default extension.
            final File[] filesInUploadingState = dataFileDirectory.listFiles(new DataFileStatusFilenameFilter(DataFileStatus.UPLOADING));

            if (filesInUploadingState != null && filesInUploadingState.length > 0)
               {
               if (LOG.isInfoEnabled())
                  {
                  LOG.info("DataFileManager.startup(): Found [" + filesInUploadingState.length + "] file(s) which were being uploaded when the program was last killed.  Renaming them so that they will get uploaded again...");
                  }
               for (final File file : filesInUploadingState)
                  {
                  changeFileExtension(file, DataFileStatus.UPLOADING.getFilenameExtension(), DataFileStatus.DOWNLOADED.getFilenameExtension());
                  }
               }

            //  If the uploader is non-null, then run through all existing downloaded files and kick off an upload job for each one
            if (dataFileUploader != null)
               {
               // get the list of all downloaded files
               final File[] filesReadyForUpload = dataFileDirectory.listFiles(new DataFileStatusFilenameFilter(DataFileStatus.DOWNLOADED));

               if (filesReadyForUpload != null && filesReadyForUpload.length > 0)
                  {
                  if (LOG.isInfoEnabled())
                     {
                     LOG.info("DataFileManager.startup(): Found [" + filesReadyForUpload.length + "] file(s) to upload");
                     }
                  for (final File file : filesReadyForUpload)
                     {
                     submitUploadFileTask(file);
                     }
                  }
               }

            // schedule the command to get the list of files from the device, which will reschedule itself upon completion
            scheduleNextFileListDownload(0, TimeUnit.SECONDS);
            }
         else
            {
            LOG.debug("DataFileManager.startup(): Cannot startup since it's already running or has been shutdown.");
            }
         }
      finally
         {
         lock.unlock();
         }
      }

   private void scheduleNextFileListDownload(final int delay, final TimeUnit timeUnit)
      {
      if (dataFileDownloader != null)
         {
         executor.schedule(submitFileListDownloadRunnable, delay, timeUnit);
         }
      }

   /**
    * Shuts down the <code>DataFileManager</code>.  Once it is shut down, it cannot be started up again.
    *
    * @see #startup()
    */
   public void shutdown()
      {
      LOG.debug("DataFileManager.shutdown()");

      lock.lock();  // block until condition holds
      try
         {
         if (isRunning)
            {
            isRunning = false;
            hasBeenShutdown = true;

            // shut down the executor
            try
               {
               LOG.debug("DataFileManager.shutdown(): Shutting down the executor");
               final List<Runnable> unexecutedTasks = executor.shutdownNow();
               LOG.debug("DataFileManager.shutdown(): Unexecuted tasks: " + (unexecutedTasks == null ? 0 : unexecutedTasks.size()));
               LOG.debug("DataFileManager.shutdown(): Waiting up to 30 seconds for the executor to shutdown...");
               final boolean terminatedNormally = executor.awaitTermination(30, TimeUnit.SECONDS);
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileManager.shutdown(): Executor successfully shutdown (timed out = " + !terminatedNormally + ")");
                  }
               }
            catch (Exception e)
               {
               LOG.error("DataFileManager.shutdown(): Exception while trying to shut down the executor", e);
               }
            }
         }
      finally
         {
         lock.unlock();
         }
      }

   private void submitUploadFileTask(@NotNull final File file)
      {
      if (dataFileUploader != null)
         {
         final File fileToUpload = changeFileExtension(file, DataFileStatus.DOWNLOADED.getFilenameExtension(), DataFileStatus.UPLOADING.getFilenameExtension());
         if (fileToUpload != null)
            {
            LOG.debug("DataFileManager.submitUploadFileTask(): Submitting file [" + fileToUpload.getName() + "] for uploading...");
            dataFileUploader.submitUploadFileTask(fileToUpload, file.getName());
            }
         else
            {
            LOG.error("DataFileManager.submitUploadFileTask(): Failed to rename file [" + file.getName() + "] in preparation for uploading it.  Skipping.");
            }
         }
      }

   @Override
   public void handleFileUploadedEvent(@NotNull final File uploadedFile, @Nullable final DataFileUploadResponse uploadResponse)
      {
      LOG.debug("DataFileManager.handleFileUploadedEvent(" + uploadedFile + ", " + uploadResponse + ")");

      if (DataFileStatus.UPLOADING.hasStatus(uploadedFile))
         {
         if (LOG.isDebugEnabled())
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileManager.handleFileUploadedEvent(): file [" + uploadedFile + "], response = [" + uploadResponse + "]");
               }
            }

         if (uploadResponse == null)
            {
            // If the response was null, then a problem occurred during upload, so just rename the file and return it
            // back to the pool of uploadable files.  Also submit a new upload job for it.

            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileManager.handleFileUploadedEvent(): Upload failure for file [" + uploadedFile.getName() + "], so just rename it back to the default and try again later.");
               }

            lock.lock();  // block until condition holds
            try
               {
               // change the extension back to the default
               final File defaultFilename = changeFileExtension(uploadedFile, DataFileStatus.UPLOADING.getFilenameExtension(), DataFileStatus.DOWNLOADED.getFilenameExtension());
               if (defaultFilename == null)
                  {
                  LOG.error("DataFileManager.handleFileUploadedEvent(): Failed to rename file [" + uploadedFile + "] back to the default name.  Aborting.");
                  }
               else
                  {
                  if (LOG.isDebugEnabled())
                     {
                     LOG.debug("DataFileManager.handleFileUploadedEvent(): Renamed file [" + uploadedFile + "] to [" + defaultFilename + "].  Will retry upload in 1 minute.");
                     }

                  // schedule the upload again
                  executor.schedule(
                        new Runnable()
                        {
                        @Override
                        public void run()
                           {
                           submitUploadFileTask(defaultFilename);
                           }
                        },
                        1,
                        TimeUnit.MINUTES);
                  }
               }
            finally
               {
               lock.unlock();
               }
            }
         else
            {
            // see if there were any errors or failed bin recs
            final Integer numFailedBinRecs = uploadResponse.getFailedBinRecs();
            final List<String> errors = uploadResponse.getErrors();
            if (numFailedBinRecs == null || numFailedBinRecs > 0 || (errors != null && errors.size() > 0))
               {
               // we had a failure, so just rename the local file to mark it as having corrupt data
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileManager.handleFileUploadedEvent(): num failed binrecs is [" + numFailedBinRecs + "] and errors is [" + errors + "], so mark the file as having corrupt data");
                  }
               lock.lock();  // block until condition holds
               try
                  {
                  final File corruptFile = changeFileExtension(uploadedFile, DataFileStatus.UPLOADING.getFilenameExtension(), DataFileStatus.CORRUPT_DATA.getFilenameExtension());
                  if (corruptFile == null)
                     {
                     LOG.error("DataFileManager.handleFileUploadedEvent(): failed to mark file [" + uploadedFile + "] as having corrupt data!  No further action will be taken on this file.");
                     }
                  else
                     {
                     LOG.info("DataFileManager.handleFileUploadedEvent(): renamed file [" + uploadedFile + "] to [" + corruptFile + "]  to mark it as having corrupt data");
                     }
                  }
               finally
                  {
                  lock.unlock();
                  }
               }
            else
               {
               // no failures!  rename the file to signify that the upload was successful...
               lock.lock();  // block until condition holds
               try
                  {
                  // change the extension to the one used for uploaded files
                  final File newFile = changeFileExtension(uploadedFile, DataFileStatus.UPLOADING.getFilenameExtension(), DataFileStatus.UPLOADED.getFilenameExtension());
                  if (newFile == null)
                     {
                     LOG.error("DataFileManager.handleFileUploadedEvent(): Failed to renamed successfully uploaded file [" + uploadedFile.getName() + "]");
                     }
                  else
                     {
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileManager.handleFileUploadedEvent(): Renamed file [" + uploadedFile + "] to [" + newFile + "]");
                        }
                     }
                  }
               finally
                  {
                  lock.unlock();
                  }

               // Don't worry about telling the downloader that the file can be deleted here--that'll be handled elsewhere
               }
            }
         }
      }

   @Override
   public void handleDataFileListEvent(@NotNull final SortedSet<String> availableFilenames)
      {
      LOG.debug("DataFileManager.handleFileListEvent()");

      int delayUntilNextFileListRequest = 1;
      TimeUnit timeUnit = TimeUnit.SECONDS;

      if (availableFilenames.isEmpty())
         {
         // no files available
         LOG.debug("DataFileManager.handleFileListEvent(): No data files are available.");
         delayUntilNextFileListRequest = 1;
         timeUnit = TimeUnit.MINUTES;
         }
      else
         {
         if (LOG.isDebugEnabled())
            {
            LOG.debug("DataFileManager.handleFileListEvent(): Processing [" + availableFilenames.size() + "] file(s)...");
            }

         for (final String filename : availableFilenames)
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileManager.handleFileListEvent(): Procesing file [" + filename + "]");
               }

            // determine what action to take for this file
            lock.lock();  // block until condition holds
            try
               {
               // check whether this file is one we already have and, if so, get its status
               final DataFileStatus fileStatus = getDataFileStatusForBaseFilename(computeBaseFilename(filename));

               if (fileStatus == null)
                  {
                  if (LOG.isDebugEnabled())
                     {
                     LOG.debug("DataFileManager.handleFileListEvent(): DataFileStatus for file [" + filename + "] is null, so submit a task to download it.");
                     }
                  dataFileDownloader.submitDownloadDataFileTask(filename);
                  }
               else
                  {
                  switch (fileStatus)
                     {
                     case WRITING:
                        // if the file is currently being written (this shouldn't happen!) then don't do anything
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileManager.handleFileListEvent(): file [" + filename + "] is currently being written, so no further action is required at this time.");
                           }
                        break;

                     case DOWNLOADED:
                        // if the file has already been downloaded and has the DOWNLOADED status, then its checksum is OK so it can be deleted from the device.
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileManager.handleFileListEvent(): file [" + filename + "] has already been downloaded successfully, so submit a task for it to be deleted from the device.");
                           }
                        dataFileDownloader.submitDeleteDataFileFromDeviceTask(filename);

                        break;

                     case UPLOADING:
                        // if the file is currently uploading, then the checksum must be correct, so we can safely delete the file from the device
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileManager.handleFileListEvent(): file [" + filename + "] is currently being uploaded, so submit a task for it to be deleted from the device.");
                           }
                        dataFileDownloader.submitDeleteDataFileFromDeviceTask(filename);

                        break;

                     case UPLOADED:
                        // if the file has already been uploaded, then we can safely delete the file from the device
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileManager.handleFileListEvent(): file [" + filename + "] has already been uploaded, so submit a task for it to be deleted from the device.");
                           }
                        dataFileDownloader.submitDeleteDataFileFromDeviceTask(filename);

                        break;

                     case CORRUPT_DATA:
                        // if the file has already been saved to disk and the checksum is correct but the data is correct, then just delete the file from the device
                        if (LOG.isInfoEnabled())
                           {
                           LOG.info("DataFileManager.handleFileListEvent(): file [" + filename + "] has valid checksum but invalid data, so submit a task for it to be deleted from the device.");
                           }
                        dataFileDownloader.submitDeleteDataFileFromDeviceTask(filename);

                        break;

                     case INCORRECT_CHECKSUM:
                        // If the file has already been saved to disk, but the checksum is incorrect, then we should try to
                        // re-download up to NUM_DOWNLOAD_RETRIES_FOR_FAILED_CHECKSUM times.
                        if (incrementAndGetRetryDownloadCount(filename) < NUM_DOWNLOAD_RETRIES_FOR_FAILED_CHECKSUM)
                           {
                           if (LOG.isInfoEnabled())
                              {
                              LOG.debug("DataFileManager.handleFileListEvent(): file [" + filename + "] has already been downloaded but had an incorrect checksum.  Submitting a task to retry the download...");
                              }

                           dataFileDownloader.submitDownloadDataFileTask(filename);
                           }
                        else
                           {
                           if (LOG.isInfoEnabled())
                              {
                              LOG.debug("DataFileManager.handleFileListEvent(): file [" + filename + "] has already been downloaded but had an incorrect checksum.  Will submit a task to delete the file from the device.");
                              }

                           dataFileDownloader.submitDeleteDataFileFromDeviceTask(filename);
                           }
                        break;

                     default:
                        LOG.error("DataFileManager.handleFileListEvent(): Unexpected DataFileStatus [" + fileStatus + "].  Ignoring.");
                     }
                  }
               }
            finally
               {
               lock.unlock();
               }
            }
         }

      if (LOG.isDebugEnabled())
         {
         LOG.debug("DataFileManager.handleFileListEvent(): Scheduling the next file list request with a delay of " + delayUntilNextFileListRequest + " " + timeUnit);
         }
      scheduleNextFileListDownload(delayUntilNextFileListRequest, timeUnit);
      }

   @Override
   public void handleSuccessfulDataFileDownloadEvent(@Nullable final DataFile dataFile)
      {
      if (dataFile != null)
         {
         if (LOG.isDebugEnabled())
            {
            LOG.debug("DataFileManager.handleFileDownloadedEvent(" + dataFile.getBaseFilename() + ")");
            }
         try
            {
            save(dataFile);
            }
         catch (IOException e)
            {
            LOG.error("DataFileManager.handleSuccessfulDataFileDownloadEvent(): IOException while trying to save data file [" + dataFile + "]", e);
            }
         }
      }

   @Override
   public void handleFailedDataFileDownloadEvent(@NotNull final String filename, @NotNull final DataFileDownloader.FailedDataFileDownloadCause cause)
      {
      if (LOG.isDebugEnabled())
         {
         LOG.debug("DataFileManager.handleFailedDataFileDownloadEvent(" + filename + "," + cause + ")");
         }
      }

   @Override
   public void handleDeleteDataFileFromDeviceEvent(@NotNull final String filename, final boolean wasDeleteSuccessful)
      {
      if (LOG.isDebugEnabled())
         {
         LOG.debug("DataFileManager.handleDeleteDataFileFromDeviceEvent(" + filename + "," + wasDeleteSuccessful + ")");
         }

      retryDownloadCountMap.remove(filename);
      }

   /**
    * Changes the file extension on the given <code>file</code> from the <code>existingFilenameExtension</code> to
    * the <code>newFilenameExtension</code>.  Returns the new {@link File} upon success, <code>null</code> on failure.
    */
   @Nullable
   private File changeFileExtension(@NotNull final File file, @NotNull final String existingFilenameExtension, @NotNull final String newFilenameExtension)
      {
      lock.lock();  // block until condition holds
      try
         {
         final int extensionPosition = file.getName().indexOf(existingFilenameExtension);
         if (extensionPosition >= 0)
            {
            final String filenameWithoutOldExtension = file.getName().substring(0, extensionPosition);
            final File newFilename = new File(file.getParentFile(), filenameWithoutOldExtension + newFilenameExtension);
            if (file.renameTo(newFilename))
               {
               if (LOG.isTraceEnabled())
                  {
                  LOG.trace("DataFileManager.changeFileExtension(): renamed file [" + file.getName() + "] to [" + newFilename.getName() + "]");
                  }
               return newFilename;
               }
            else
               {
               if (LOG.isEnabledFor(Level.ERROR))
                  {
                  LOG.error("DataFileManager.changeFileExtension(): failed to rename file [" + file.getName() + "] to [" + newFilename.getName() + "]");
                  }
               }
            }
         }
      finally
         {
         lock.unlock();
         }
      return null;
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

         lock.lock();  // block until condition holds
         try
            {
            // see whether the file already exists (in some form)
            final DataFileStatus dataFileStatus = getDataFileStatusForBaseFilename(dataFile.getBaseFilename());

            // if the file doesn't exist, or if it exists but has an incorrect checksum, then save it
            if (dataFileStatus == null || DataFileStatus.INCORRECT_CHECKSUM.equals(dataFileStatus))
               {
               // if the file already exists with an incorrect checksum, then we first need to delete the existing one
               // so we can download the new one
               if (DataFileStatus.INCORRECT_CHECKSUM.equals(dataFileStatus))
                  {
                  final String nameOfFileToDelete = dataFile.getBaseFilename() + DataFileStatus.INCORRECT_CHECKSUM.getFilenameExtension();
                  final File fileToDelete = new File(dataFileDirectory, nameOfFileToDelete);
                  if (fileToDelete.delete())
                     {
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileManager.save(): Deleted incorrect checksum file [" + nameOfFileToDelete + "]");
                        }
                     }
                  else
                     {
                     LOG.error("DataFileManager.save(): Failed to delete incorrect checksum file [" + nameOfFileToDelete + "]");
                     }
                  }

               // try writing the file
               DataOutputStream os = null;
               try
                  {
                  // write the file, but use a filename with a special extension to signify the file is being written
                  final File tempFile = new File(dataFileDirectory, dataFile.getBaseFilename() + DataFileStatus.WRITING.getFilenameExtension());
                  os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
                  dataFile.writeToOutputStream(os);

                  // try to close the file, so we can rename it
                  boolean wasClosedSuccessfully = false;
                  try
                     {
                     os.close();
                     os = null;
                     wasClosedSuccessfully = true;
                     }
                  catch (IOException ignored)
                     {
                     LOG.error("DataFileManager.save(): IOException while trying to close the DataOutputStream for file [" + tempFile + "].  Oh well.");
                     }

                  if (wasClosedSuccessfully)
                     {
                     // check the checksum, and use it to determine which file extension our new file should have.
                     final DataFileStatus status = dataFile.isChecksumCorrect() ? DataFileStatus.DOWNLOADED : DataFileStatus.INCORRECT_CHECKSUM;

                     if (!dataFile.isChecksumCorrect())
                        {
                        LOG.info("DataFileManager.save(): checksum failed for data file [" + dataFile.getFilename() + "]");
                        }

                     // rename the file
                     final File file = changeFileExtension(tempFile, DataFileStatus.WRITING.getFilenameExtension(), status.getFilenameExtension());
                     if (file != null)
                        {
                        // success, so return the file
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileManager.save(): DataFile [" + file + "] saved successfully");
                           }

                        if (dataFile.isChecksumCorrect())
                           {
                           // submit an upload task
                           submitUploadFileTask(file);
                           }
                        else
                           {
                           LOG.error("DataFileManager.save(): Upload task not submitted for data file [" + file + "] since the checksum is incorrect.");
                           }

                        return file;
                        }
                     else
                        {
                        LOG.error("DataFileManager.save(): Failed to rename file [" + tempFile + "] to have an extension of [" + status.getFilenameExtension() + "].  Attempting to delete temp file...");
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
                  else
                     {
                     LOG.error("DataFileManager.save(): Failed to close the file [" + tempFile + "]");
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
            else
               {
               // simply log that the file exists and is being skipped
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileManager.save(): A datafile with the base filename [" + dataFile.getBaseFilename() + "] already exists with DataFileStatus [" + dataFileStatus + "], so this one will be ignored.");
                  }
               }
            }
         finally
            {
            lock.unlock();
            }
         }
      return null;
      }

   /**
    * If a file already exists with the same {@link DataFile#getBaseFilename() base filename} as the given
    * <code>baseFilename</code>, then this method returns the {@link DataFileStatus} of that file; otherwise it returns
    * <code>null</code>.
    */
   @Nullable
   private DataFileStatus getDataFileStatusForBaseFilename(@NotNull final String baseFilename)
      {
      lock.lock();  // block until condition holds
      try
         {
         final File[] files = getFilesWithSameBaseFilename(baseFilename);
         if (files != null && files.length > 0 && files[0] != null)
            {
            return DataFileStatus.getStatusForFilename(files[0].getName());
            }

         return null;
         }
      finally
         {
         lock.unlock();
         }
      }

   private String computeBaseFilename(@NotNull final String filename)
      {
      // get the base filename
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
      return baseFilename;
      }

   private int incrementAndGetRetryDownloadCount(@NotNull final String filename)
      {
      lock.lock();  // block until condition holds
      try
         {
         Integer count = retryDownloadCountMap.get(filename);
         if (count == null)
            {
            count = 0;
            }
         retryDownloadCountMap.put(filename, ++count);
         return count;
         }
      finally
         {
         lock.unlock();
         }
      }

   private File[] getFilesWithSameBaseFilename(final String baseFilename)
      {
      lock.lock();  // block until condition holds
      try
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
      finally
         {
         lock.unlock();
         }
      }

   /** Filters {@link DataFile}s based on their {@link DataFileStatus}. */
   private static class DataFileStatusFilenameFilter implements FilenameFilter
      {
      private final DataFileStatus dataFileStatus;

      private DataFileStatusFilenameFilter(@NotNull final DataFileStatus dataFileStatus)
         {
         this.dataFileStatus = dataFileStatus;
         }

      @Override
      public boolean accept(final File file, final String filename)
         {
         return file != null && filename != null && filename.toUpperCase().endsWith(dataFileStatus.getFilenameExtension());
         }
      }
   }
