package org.bodytrack.loggingdevice;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import edu.cmu.ri.createlab.util.thread.DaemonThreadFactory;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class DataFileDownloader
   {
   private static final Logger LOG = Logger.getLogger(DataFileDownloader.class);

   private final LoggingDevice device;
   private final DataFileManager dataFileManager;
   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory(this.getClass() + ".executor"));
   private final Runnable downloadFileCommand = new DownloadFileCommand();

   private boolean isRunning = false;
   private boolean hasBeenShutdown = false;
   private final Lock lock = new ReentrantLock();

   public DataFileDownloader(@NotNull final LoggingDevice device, @NotNull final DataFileManager dataFileManager)
      {
      this.device = device;
      this.dataFileManager = dataFileManager;
      }

   /**
    * Starts the <code>DataFileDownloader</code>.  Does nothing if it has already been started or shutdown.
    *
    * @see #shutdown()
    */
   public void startup() throws IllegalStateException
      {
      lock.lock();  // block until condition holds
      try
         {
         if (!isRunning && !hasBeenShutdown)
            {
            isRunning = true;

            // schedule the download file command, which will reschedule itself upon completion
            scheduleNextFileDownload(0, TimeUnit.SECONDS);
            }
         else
            {
            LOG.debug("DataFileDownloader.startup(): Cannot startup since it's already running or has been shutdown.");
            }
         }
      finally
         {
         lock.unlock();
         }
      }

   /**
    * Shuts down the <code>DataFileDownloader</code>.  Once it is shut down, it cannot be started up again.
    *
    * @see #startup()
    */
   public void shutdown()
      {
      LOG.debug("DataFileDownloader.shutdown()");

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
               LOG.debug("DataFileDownloader.shutdown(): Shutting down the executor");
               final List<Runnable> unexecutedTasks = executor.shutdownNow();
               LOG.debug("DataFileDownloader.shutdown(): Unexecuted tasks: " + (unexecutedTasks == null ? 0 : unexecutedTasks.size()));
               LOG.debug("DataFileDownloader.shutdown(): Waiting up to 30 seconds for the executor to shutdown...");
               final boolean terminatedNormally = executor.awaitTermination(30, TimeUnit.SECONDS);
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileDownloader.shutdown(): Executor successfully shutdown (timed out = " + !terminatedNormally + ")");
                  }
               }
            catch (Exception e)
               {
               LOG.error("DataFileDownloader.shutdown(): Exception while trying to shut down the executor", e);
               }
            }
         }
      finally
         {
         lock.unlock();
         }
      }

   private void scheduleNextFileDownload(final int delay, final TimeUnit timeUnit)
      {
      executor.schedule(downloadFileCommand, delay, timeUnit);
      }

   private final class DownloadFileCommand implements Runnable
      {
      @Override
      public void run()
         {
         LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Calling LoggingDevice.getAvailableFilenames()...");

         int delayUntilNextDownload = 5;
         TimeUnit timeUnit = TimeUnit.SECONDS;

         // get the Set of available filenames from the device.  We'll iterate over each one and take appropriate action
         final SortedSet<String> availableFilenames = device.getAvailableFilenames();

         if (availableFilenames == null)
            {
            // the command failed, so try again in a bit
            LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Failed to get the list of available files from the device.");
            delayUntilNextDownload = 1;
            timeUnit = TimeUnit.MINUTES;
            }
         else if (availableFilenames.isEmpty())
            {
            // no files available
            LOG.debug("DataFileDownloader$DownloadFileCommand.run(): No data files are available.");
            delayUntilNextDownload = 1;
            timeUnit = TimeUnit.MINUTES;
            }
         else
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Device has [" + availableFilenames.size() + "] available file(s)");
               }

            // we got some filenames, so process them...
            for (final String filename : availableFilenames)
               {
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Procesing file [" + filename + "]");
                  }

               final DataFileManager.ActionToPerformOnDeviceDataFile actionToPerformOnDeviceDataFile = dataFileManager.getActionToPerformOnDeviceDataFile(filename);

               switch (actionToPerformOnDeviceDataFile)
                  {
                  case NO_ACTION:
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileDownloader$DownloadFileCommand.run(): No action necessary for file [" + filename + "].");
                        }
                     break;
                  case DELETE_FROM_DEVICE:
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Attempting to delete file [" + filename + "].");
                        }
                     deleteFileFromDevice(filename);
                     break;
                  case DOWNLOAD_FROM_DEVICE:
                     // the file doesn't already exist on disk, so try to download it from the device
                     if (!downloadFileFromDevice(filename))
                        {
                        delayUntilNextDownload = 1;
                        timeUnit = TimeUnit.MINUTES;
                        }
                     break;

                  default:
                     LOG.error("DataFileDownloader$DownloadFileCommand.run(): Unexpected ActionToPerformOnDeviceDataFile [" + actionToPerformOnDeviceDataFile + "].  Ignoring.");
                  }
               }
            }

         if (LOG.isDebugEnabled())
            {
            LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Scheduling the next download for " + delayUntilNextDownload + " " + timeUnit + " from now...");
            }
         scheduleNextFileDownload(delayUntilNextDownload, timeUnit);
         }

      private boolean downloadFileFromDevice(@NotNull final String filename)
         {
         boolean success = false;

         try
            {
            final DataFile dataFile = device.getFile(filename);

            if (dataFile == null)
               {
               // the command failed, so try again in a bit
               LOG.debug("DataFileDownloader$DownloadFileCommand.run(): File download failed.");
               }
            else if (dataFile.isEmpty())
               {
               // there's no data available, so try again in after a while
               LOG.debug("DataFileDownloader$DownloadFileCommand.run(): No data available.");
               }
            else
               {
               // success, so ask the DataFileManager to save the file
               try
                  {
                  success = dataFileManager.save(dataFile) != null;
                  if (LOG.isDebugEnabled())
                     {
                     LOG.debug("DataFileDownloader$DownloadFileCommand.run(): File download " + (success ? "succeeded" : "failed") + ")!");
                     }
                  }
               catch (IOException e)
                  {
                  LOG.error("DataFileDownloader$DownloadFileCommand.run(): IOException while trying to save the DataFile [" + dataFile + "]", e);
                  }
               }
            }
         catch (NoSuchFileException ignored)
            {
            LOG.error("DataFileDownloader$DownloadFileCommand.run(): NoSuchFileException while trying to download file [" + filename + "] from the device.  Ignoring.");
            }

         return success;
         }

      private boolean deleteFileFromDevice(final String filename)
         {
         final boolean wasDeleteSuccessful = device.deleteFile(filename);

         if (wasDeleteSuccessful)
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileDownloader$DownloadFileCommand.deleteFileFromDevice(): file [" + filename + "] successfully deleted from device.");
               }
            }
         else
            {
            LOG.error("DataFileDownloader$DownloadFileCommand.deleteFileFromDevice(): failed to delete file [" + filename + "] from device.");
            }

         return wasDeleteSuccessful;
         }
      }
   }
