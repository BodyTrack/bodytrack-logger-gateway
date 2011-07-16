package org.bodytrack.loggingdevice;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public interface DataFile
   {
   /** The standard <code>DataFile</code> extension */
   String FILENAME_EXTENSION = ".BT";

   Pattern FILENAME_PATTERN = Pattern.compile("[A-F0-9]{8}\\.BT");

   /**
    * Returns <code>true</code> if the file is empty, <code>false</code> otherwise.  An empty file is used to signify
    * that no more data is currently available.
    */
   boolean isEmpty();

   /**
    * Returns the filename, without the extension.
    *
    * @see #FILENAME_EXTENSION
    */
   @NotNull
   String getBaseFilename();

   /**
    * Returns the filename, including the extension.
    *
    * @see #FILENAME_EXTENSION
    */
   @NotNull
   String getFilename();

   /** Returns a copy of this file's timestamp */
   @NotNull
   Date getTimestamp();

   /**
    * Writes the file's data to the given {@link DataOutputStream}.  Does nothing if the given <code>outputStream</code>
    * is <code>null</code>.
    *
    *  @throws IOException if the write failed.
    */
   void writeToOutputStream(@Nullable final DataOutputStream outputStream) throws IOException;

   /** Returns the number of bytes in the data */
   int getLength();

   /** Returns whether the actual checksum equals what was expected. */
   boolean isChecksumCorrect();
   }