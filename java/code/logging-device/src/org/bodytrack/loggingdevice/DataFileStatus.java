package org.bodytrack.loggingdevice;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * <code>DataFileStatus</code> represents the various states a {@link DataFile} can be in, from the perspective of
 * the {@link DataFileManager}.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public enum DataFileStatus
   {
      WRITING(".WRITING"),
      DOWNLOADED(".BT"),
      UPLOADING(".UPLOADING"),
      UPLOADED(".BTU"),
      CORRUPT_DATA(".BTX"),
      INCORRECT_CHECKSUM(".BTC");

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

   DataFileStatus(@NotNull final String filenameExtension)
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
