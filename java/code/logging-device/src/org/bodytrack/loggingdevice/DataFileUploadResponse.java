package org.bodytrack.loggingdevice;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * <code>DataFileUploadResponse</code> contains the response details from the server after uploading a {@link DataFile}.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
interface DataFileUploadResponse
   {
   /** Returns the number of successful datasets. */
   @Nullable
   Integer getSuccessfulDatasets();

   /** Returns the number of duplicate datasets. */
   @Nullable
   Integer getDuplicateDatasets();

   /** Returns the number of successful datasets. */
   @Nullable
   Integer getSuccessfulBinRecs();

   /** Returns the number of failed datasets. */
   @Nullable
   Integer getFailedBinRecs();

   @Nullable
   Double getMinTime();

   @Nullable
   Double getMaxTime();

   /** Returns an unmodifiable {@link List} of any errors that occurred in the upload. */
   @Nullable
   List<String> getErrors();

   /** Returns an unmodifiable {@link Map} of any unknown properties. */
   @NotNull
   Map<String, Object> getUnknownProperties();
   }