package org.bodytrack.loggingdevice;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import edu.cmu.ri.createlab.util.thread.DaemonThreadFactory;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class DataFileUploader
   {
   private static final Logger LOG = Logger.getLogger(DataFileUploader.class);
   private static final Logger CONSOLE_LOG = Logger.getLogger("ConsoleLog");

   private static final int MAX_NUM_UPLOAD_THREADS = 4;

   public interface EventListener
      {
      void handleFileUploadedEvent(@NotNull final File uploadedFile, @Nullable final DataFileUploadResponse uploadResponse);
      }

   private final ExecutorService executor = Executors.newFixedThreadPool(MAX_NUM_UPLOAD_THREADS, new DaemonThreadFactory(this.getClass() + ".executor"));
   private final String uploadUrlPrefix;
   private final Set<EventListener> eventListeners = new HashSet<EventListener>();

   /**
    * Constructs a <code>DataFileUploader</code> for the given {@link DataStoreServerConfig} and {@link DataStoreServerConfig}.
    */
   public DataFileUploader(@NotNull final DataStoreServerConfig serverConfig,
                           @NotNull final LoggingDeviceConfig loggingDeviceConfig)
      {
      // build the upload URL prefix
      uploadUrlPrefix = "http://" + serverConfig.getServerName() + ":" + serverConfig.getServerPort() + "/users/" + loggingDeviceConfig.getUsername() + "/binupload?dev_nickname=" + loggingDeviceConfig.getDeviceNickname();

      if (LOG.isInfoEnabled())
         {
         final String msg = "URL prefix for uploading: [" + uploadUrlPrefix + "]";
         LOG.info("DataFileUploader.DataFileUploader(): " + msg);
         CONSOLE_LOG.info(msg);
         }
      }

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

   public void submitUploadFileTask(@Nullable final File fileToUpload, @Nullable final String originalFilename)
      {
      if (LOG.isDebugEnabled())
         {
         LOG.debug("DataFileUploader.submitUploadFileTask(" + fileToUpload + ", " + originalFilename + ")");
         }

      if (fileToUpload != null && originalFilename != null)
         {
         executor.execute(new UploadFileTask(fileToUpload, originalFilename));
         }
      }

   private final class UploadFileTask implements Runnable
      {
      private final File fileToUpload;
      private final String originalFilename;

      private UploadFileTask(@NotNull final File fileToUpload, @NotNull final String originalFilename)
         {
         this.fileToUpload = fileToUpload;
         this.originalFilename = originalFilename;
         }

      @Override
      public void run()
         {
         DataFileUploadResponse dataFileUploadResponse = null;
         final HttpClient httpClient = new DefaultHttpClient();
         try
            {
            final HttpPost httpPost = new HttpPost(uploadUrlPrefix + "&filename=" + originalFilename);
            final FileEntity entity = new FileEntity(fileToUpload, "application/octet-stream");
            httpPost.setEntity(entity);

            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileUploader$UploadFileTask.run(): uploading file [" + fileToUpload + "] to [" + httpPost.getURI() + "]...");
               }
            if (LOG.isInfoEnabled())
               {
               CONSOLE_LOG.info("Uploading file " + originalFilename + " to server...");
               }
            final HttpResponse response = httpClient.execute(httpPost);
            final HttpEntity responseEntity = response.getEntity();
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileUploader$UploadFileTask.run(): response status [" + response.getStatusLine() + "]");
               }

            if (responseEntity != null)
               {
               try
                  {
                  // read the response into a String
                  final StringWriter writer = new StringWriter();
                  IOUtils.copy(responseEntity.getContent(), writer, "UTF-8");
                  final String responseStr = writer.toString();

                  // Early versions of the server's JSON response don't start with a curly brace, so just look for it
                  // and start there.  TODO: remove this once the server is returning a proper JSON response.
                  String json = null;
                  try
                     {
                     json = responseStr.substring(responseStr.indexOf("{"));
                     }
                  catch (Exception e)
                     {
                     LOG.error("DataFileUploader$UploadFileTask.run(): Exception while parsing the JSON response", e);
                     }

                  if (LOG.isDebugEnabled())
                     {
                     LOG.debug("DataFileUploader$UploadFileTask.run(): response [" + json + "]");
                     }

                  if (json != null)
                     {
                     // now parse the response, converting the JSON into a DataFileUploadResponse
                     final ObjectMapper mapper = new ObjectMapper();
                     dataFileUploadResponse = mapper.readValue(json, DataFileUploadResponseImpl.class);
                     }
                  }
               catch (IOException e)
                  {
                  LOG.error("DataFileUploader$UploadFileTask.run(): IOException while reading or parsing the response", e);
                  }
               catch (IllegalStateException e)
                  {
                  LOG.error("DataFileUploader$UploadFileTask.run(): IllegalStateException while reading the response", e);
                  }
               }
            EntityUtils.consume(responseEntity);
            }
         catch (ClientProtocolException e)
            {
            LOG.error("DataFileUploader$UploadFileTask.run(): ClientProtocolException while trying to upload data file [" + fileToUpload + "]", e);
            }
         catch (IOException e)
            {
            LOG.error("DataFileUploader$UploadFileTask.run(): IOException while trying to upload data file [" + fileToUpload + "]", e);
            }
         finally
            {
            // When the HttpClient instance is no longer needed, shut down the connection manager to ensure immediate
            // deallocation of all system resources
            httpClient.getConnectionManager().shutdown();
            }

         // notify listeners
         for (final EventListener listener : eventListeners)
            {
            listener.handleFileUploadedEvent(fileToUpload, dataFileUploadResponse);
            }
         }
      }

   /**
    * @author Chris Bartley (bartley@cmu.edu)
    */
   private static final class DataFileUploadResponseImpl implements DataFileUploadResponse
      {
      private Integer successfulDatasets = null;
      private Integer duplicateDatasets = null;
      private Integer successfulBinRecs = null;
      private Integer failedBinRecs = null;
      private Double minTime = null;
      private Double maxTime = null;
      private List<String> errors = null;
      private final Map<String, Object> unknownProperties = new HashMap<String, Object>();

      @Override
      @Nullable
      @JsonProperty("successful_datasets")
      public Integer getSuccessfulDatasets()
         {
         return successfulDatasets;
         }

      @JsonProperty("successful_datasets")
      private void setSuccessfulDatasets(@Nullable final Integer successfulDatasets)
         {
         this.successfulDatasets = successfulDatasets;
         }

      @Override
      @Nullable
      @JsonProperty("duplicate_datasets")
      public Integer getDuplicateDatasets()
         {
         return duplicateDatasets;
         }

      @JsonProperty("duplicate_datasets")
      private void setDuplicateDatasets(@Nullable final Integer duplicateDatasets)
         {
         this.duplicateDatasets = duplicateDatasets;
         }

      @Override
      @Nullable
      @JsonProperty("successful_binrecs")
      public Integer getSuccessfulBinRecs()
         {
         return successfulBinRecs;
         }

      @JsonProperty("successful_binrecs")
      private void setSuccessfulBinRecs(@Nullable final Integer successfulBinRecs)
         {
         this.successfulBinRecs = successfulBinRecs;
         }

      @Override
      @Nullable
      @JsonProperty("failed_binrecs")
      public Integer getFailedBinRecs()
         {
         return failedBinRecs;
         }

      @JsonProperty("failed_binrecs")
      private void setFailedBinrecs(@Nullable final Integer failedBinRecs)
         {
         this.failedBinRecs = failedBinRecs;
         }

      @Override
      @Nullable
      @JsonProperty("min_time")
      public Double getMinTime()
         {
         return minTime;
         }

      @JsonProperty("min_time")
      private void setMinTime(@Nullable final Double minTime)
         {
         this.minTime = minTime;
         }

      @Override
      @Nullable
      @JsonProperty("max_time")
      public Double getMaxTime()
         {
         return maxTime;
         }

      @JsonProperty("max_time")
      private void setMaxTime(@Nullable final Double maxTime)
         {
         this.maxTime = maxTime;
         }

      @Override
      @Nullable
      @JsonProperty("error_arr")
      public List<String> getErrors()
         {
         return errors;
         }

      @JsonProperty("error_arr")
      private void setErrors(@Nullable final List<String> errors)
         {
         this.errors = new ArrayList<String>(errors);
         }

      @JsonAnySetter
      private void handleUnknown(@Nullable final String key, @Nullable final Object value)
         {
         if (key != null)
            {
            unknownProperties.put(key, value);
            }
         }

      /** Returns an unmodifiable {@link Map} of any unknown properties. */
      @Override
      @NotNull
      public Map<String, Object> getUnknownProperties()
         {
         return Collections.unmodifiableMap(unknownProperties);
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

         final DataFileUploadResponseImpl that = (DataFileUploadResponseImpl)o;

         if (duplicateDatasets != null ? !duplicateDatasets.equals(that.duplicateDatasets) : that.duplicateDatasets != null)
            {
            return false;
            }
         if (errors != null ? !errors.equals(that.errors) : that.errors != null)
            {
            return false;
            }
         if (failedBinRecs != null ? !failedBinRecs.equals(that.failedBinRecs) : that.failedBinRecs != null)
            {
            return false;
            }
         if (maxTime != null ? !maxTime.equals(that.maxTime) : that.maxTime != null)
            {
            return false;
            }
         if (minTime != null ? !minTime.equals(that.minTime) : that.minTime != null)
            {
            return false;
            }
         if (successfulBinRecs != null ? !successfulBinRecs.equals(that.successfulBinRecs) : that.successfulBinRecs != null)
            {
            return false;
            }
         if (successfulDatasets != null ? !successfulDatasets.equals(that.successfulDatasets) : that.successfulDatasets != null)
            {
            return false;
            }
         if (unknownProperties != null ? !unknownProperties.equals(that.unknownProperties) : that.unknownProperties != null)
            {
            return false;
            }

         return true;
         }

      @Override
      public int hashCode()
         {
         int result = successfulDatasets != null ? successfulDatasets.hashCode() : 0;
         result = 31 * result + (duplicateDatasets != null ? duplicateDatasets.hashCode() : 0);
         result = 31 * result + (successfulBinRecs != null ? successfulBinRecs.hashCode() : 0);
         result = 31 * result + (failedBinRecs != null ? failedBinRecs.hashCode() : 0);
         result = 31 * result + (minTime != null ? minTime.hashCode() : 0);
         result = 31 * result + (maxTime != null ? maxTime.hashCode() : 0);
         result = 31 * result + (errors != null ? errors.hashCode() : 0);
         result = 31 * result + (unknownProperties != null ? unknownProperties.hashCode() : 0);
         return result;
         }

      @Override
      public String toString()
         {
         final StringBuilder sb = new StringBuilder();
         sb.append("DataFileUploadResponseImpl");
         sb.append("{successfulDatasets=").append(successfulDatasets);
         sb.append(", duplicateDatasets=").append(duplicateDatasets);
         sb.append(", successfulBinRecs=").append(successfulBinRecs);
         sb.append(", failedBinRecs=").append(failedBinRecs);
         sb.append(", minTime=").append(minTime);
         sb.append(", maxTime=").append(maxTime);
         sb.append(", errors=").append(errors);
         sb.append(", unknownProperties=").append(unknownProperties);
         sb.append('}');
         return sb.toString();
         }
      }
   }
