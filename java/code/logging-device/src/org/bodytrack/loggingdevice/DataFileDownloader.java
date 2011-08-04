package org.bodytrack.loggingdevice;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import edu.cmu.ri.createlab.util.thread.DaemonThreadFactory;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class DataFileDownloader
   {
   private static final Logger LOG = Logger.getLogger(DataFileDownloader.class);
   private static final Logger CONSOLE_LOG = Logger.getLogger("ConsoleLog");

   public static enum FailedDataFileDownloadCause
      {
         NO_SUCH_FILE,
         EMPTY_DATA_FILE,
         DOWNLOAD_FAILED;

      @Override
      public String toString()
         {
         return this.getClass().getSimpleName() + "." + this.name();
         }
      }

   public interface EventListener
      {
      /** Provides a non-<code>null</code>, unmodifiable {@link SortedSet} of the available filenames on the device. */
      void handleDataFileListEvent(@NotNull final SortedSet<String> availableFilenames);

      void handleSuccessfulDataFileDownloadEvent(@Nullable final DataFile dataFile);

      void handleFailedDataFileDownloadEvent(@NotNull final String filename, @NotNull final FailedDataFileDownloadCause cause);

      void handleDeleteDataFileFromDeviceEvent(@NotNull final String filename, final boolean wasDeleteSuccessful);
      }

   private final LoggingDevice device;
   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory(this.getClass() + ".executor"));
   private final Set<EventListener> eventListeners = new HashSet<EventListener>();
   private final Runnable dataFileListRequestRunnable =
         new Runnable()
         {
         @Override
         public void run()
            {
            LOG.debug("DataFileDownloader.dataFileListRequestRunnable.run()");

            final SortedSet<String> availableFilenames = device.getAvailableFilenames();
            final SortedSet<String> nonNullAvailableFilenames = (availableFilenames == null) ? new TreeSet<String>() : availableFilenames;
            final SortedSet<String> unmodifiableAvailableFilenames = Collections.unmodifiableSortedSet(nonNullAvailableFilenames);

            // notify listeners
            for (final EventListener listener : eventListeners)
               {
               try
                  {
                  listener.handleDataFileListEvent(unmodifiableAvailableFilenames);
                  }
               catch (Exception e)
                  {
                  LOG.error("DataFileDownloader.dataFileListRequestRunnable.run(): Exception while notifying listener [" + listener + "]", e);
                  }
               }
            }
         };

   public void addEventListener(@Nullable final EventListener listener)
      {
      if (listener != null)
         {
         eventListeners.add(listener);
         }
      }

   public void removeEventListener(@Nullable final EventListener listener)
      {
      if (listener != null)
         {
         eventListeners.remove(listener);
         }
      }

   public DataFileDownloader(@NotNull final LoggingDevice device)
      {
      this.device = device;
      }

   public void submitDataFileListRequestTask()
      {
      LOG.debug("DataFileDownloader.submitDataFileListRequestTask()");

      executor.execute(dataFileListRequestRunnable);
      }

   public void submitDownloadDataFileTask(@Nullable final String filename)
      {
      if (LOG.isDebugEnabled())
         {
         LOG.debug("DataFileDownloader.submitDownloadDataFileTask(" + filename + ")");
         }

      if (filename != null)
         {
         executor.execute(
               new Runnable()
               {
               @Override
               public void run()
                  {
                  if (LOG.isInfoEnabled())
                     {
                     CONSOLE_LOG.info("Downloading file " + filename + " from device...");
                     }
                  FailedDataFileDownloadCause failureCause;
                  try
                     {
                     final DataFile dataFile = device.getFile(filename);

                     if (dataFile == null)
                        {
                        // the command failed
                        LOG.debug("DataFileDownloader.submitDownloadDataFileTask.run(): File download failed for file [" + filename + "].");
                        failureCause = FailedDataFileDownloadCause.DOWNLOAD_FAILED;
                        }
                     else if (dataFile.isEmpty())
                        {
                        // there's no data available
                        LOG.debug("DataFileDownloader.submitDownloadDataFileTask.run(): Empty data file [" + filename + "].");
                        failureCause = FailedDataFileDownloadCause.EMPTY_DATA_FILE;
                        }
                     else
                        {
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileDownloader.submitDownloadDataFileTask.run(): Notifying listeners of download of file [" + filename + "]");
                           }

                        // success, so notify listeners
                        for (final EventListener listener : eventListeners)
                           {
                           try
                              {
                              listener.handleSuccessfulDataFileDownloadEvent(dataFile);
                              }
                           catch (Exception e)
                              {
                              LOG.error("DataFileDownloader.submitDownloadDataFileTask.run(): Exception while notifying listener [" + listener + "] of file [" + filename + "] download success", e);
                              }
                           }

                        return;
                        }
                     }
                  catch (NoSuchFileException ignored)
                     {
                     LOG.error("DataFileDownloader.submitDownloadDataFileTask.run(): NoSuchFileException while trying to download file [" + filename + "] from the device.");
                     failureCause = FailedDataFileDownloadCause.NO_SUCH_FILE;
                     }

                  // failure, so notify listeners
                  for (final EventListener listener : eventListeners)
                     {
                     try
                        {
                        listener.handleFailedDataFileDownloadEvent(filename, failureCause);
                        }
                     catch (Exception e)
                        {
                        LOG.error("DataFileDownloader.submitDownloadDataFileTask.run(): Exception while notifying listener [" + listener + "] of file [" + filename + "] download failure", e);
                        }
                     }
                  }
               });
         }
      }

   public void submitDeleteDataFileFromDeviceTask(@Nullable final String filename)
      {
      if (LOG.isDebugEnabled())
         {
         LOG.debug("DataFileDownloader.submitDeleteDataFileFromDeviceTask(" + filename + ")");
         }

      if (filename != null)
         {
         executor.execute(
               new Runnable()
               {
               @Override
               public void run()
                  {
                  if (LOG.isInfoEnabled())
                     {
                     CONSOLE_LOG.info("Deleting file " + filename + " from device...");
                     }
                  final boolean wasDeleteSuccessful = device.deleteFile(filename);

                  if (wasDeleteSuccessful)
                     {
                     if (LOG.isDebugEnabled())
                        {
                        LOG.debug("DataFileDownloader.submitDeleteDataFileFromDeviceTask.run(): file [" + filename + "] successfully deleted from device.");
                        }
                     }
                  else
                     {
                     LOG.error("DataFileDownloader.submitDeleteDataFileFromDeviceTask.run(): failed to delete file [" + filename + "] from device.");
                     }

                  // notify listeners
                  for (final EventListener listener : eventListeners)
                     {
                     try
                        {
                        listener.handleDeleteDataFileFromDeviceEvent(filename, wasDeleteSuccessful);
                        }
                     catch (Exception e)
                        {
                        LOG.error("DataFileDownloader.submitDeleteDataFileFromDeviceTask.run(): Exception while notifying listener [" + listener + "]", e);
                        }
                     }
                  }
               });
         }
      }
   }
