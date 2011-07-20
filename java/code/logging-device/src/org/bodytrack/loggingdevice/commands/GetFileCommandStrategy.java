package org.bodytrack.loggingdevice.commands;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceVariableLengthReturnValueCommandStrategy;
import edu.cmu.ri.createlab.serial.SerialDeviceCommandResponse;
import org.apache.log4j.Logger;
import org.bodytrack.loggingdevice.DataFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class GetFileCommandStrategy extends CreateLabSerialDeviceVariableLengthReturnValueCommandStrategy<DataFile>
   {
   private static final Logger LOG = Logger.getLogger(GetFileCommandStrategy.class);

   public static final int READ_TIMEOUT = 15;
   public static final TimeUnit READ_TIMEOUT_UNITS = TimeUnit.MINUTES;

   /** The command character used to send the erase file command. */
   private static final byte COMMAND_PREFIX = 'D';

   /** The size of the expected response, in bytes */
   private static final int SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER = 4;

   /** The size of the checksum, in bytes */
   private static final int SIZE_IN_BYTES_OF_CHECKSUM = 4;

   private final byte[] command;
   private final String filename;

   public GetFileCommandStrategy(@NotNull final String filename)
      {
      super(READ_TIMEOUT, READ_TIMEOUT_UNITS);
      this.filename = filename.toUpperCase();   // base station uses all upper case for filenames

      final int lengthOfFilenameAndCRLF = this.filename.length() + 2;

      // The command consists of the command prefix, one byte containing the length of the filename plus the CRLF, then
      // the filename followed by a CRLF.
      command = new byte[1 + 1 + lengthOfFilenameAndCRLF];

      // build the command
      command[0] = COMMAND_PREFIX;
      command[1] = (byte)lengthOfFilenameAndCRLF;
      for (int i = 0; i < this.filename.length(); i++)
         {
         command[2 + i] = (byte)this.filename.charAt(i);
         }
      command[command.length - 2] = '\r';
      command[command.length - 1] = '\n';
      }

   protected byte[] getCommand()
      {
      return command.clone();
      }

   @Override
   protected int getSizeOfExpectedResponseHeader()
      {
      return SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER;
      }

   @Override
   protected int getSizeOfVariableLengthResponse(final byte[] header)
      {
      // convert 4 bytes into an int
      final int size = ByteBuffer.wrap(header, 0, SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER).getInt();

      if (LOG.isDebugEnabled())
         {
         LOG.debug("GetFileCommandStrategy.getSizeOfVariableLengthResponse(): size [" + size + "] extracted from header");
         }

      return size;
      }

   /**
    * Returns a {@link DataFile} (which might be {@link DataFile#isEmpty() empty}), or <code>null</code> if the command
    * failed.
    */
   @Nullable
   @Override
   public DataFile convertResponse(@Nullable final SerialDeviceCommandResponse response)
      {
      if (response != null && response.wasSuccessful())
         {
         // get the data
         final byte[] responseData = response.getData();

         // get the length of the file + the 4 bytes for the CRC32 checksum
         final int lengthOfFileAndChecksum = getSizeOfVariableLengthResponse(responseData);
         if (lengthOfFileAndChecksum == 0)
            {
            LOG.info("GetFileCommandStrategy.convertResponse(): No data available, returning empty DataFile.");
            return new DataFileImpl();
            }
         else if (lengthOfFileAndChecksum <= SIZE_IN_BYTES_OF_CHECKSUM)
            {
            LOG.info("GetFileCommandStrategy.convertResponse(): Missing file data and/or checksum, returning empty DataFile.");
            return new DataFileImpl();
            }
         else
            {
            if (LOG.isTraceEnabled())
               {
               LOG.trace("GetFileCommandStrategy.convertResponse(): length of file + checksum is [" + lengthOfFileAndChecksum + "]");
               }

            try
               {
               final DataFileImpl dataFile = new DataFileImpl(filename, responseData, SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER, lengthOfFileAndChecksum);

               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("GetFileCommandStrategy.convertResponse(): file download succeeded [" + dataFile + "]");
                  }
               return dataFile;
               }
            catch (IllegalArgumentException ignored)
               {
               LOG.error("GetFileCommandStrategy.convertResponse(): IllegalArgumentException while trying to construct the DataFileImpl");
               }
            }
         }
      return null;
      }

   private static final class DataFileImpl implements DataFile
      {
      private static final String EMPTY_FILENAME = "";
      private static final byte[] EMPTY_DATA = new byte[0];
      private static final int EMPTY_OFFSET = 0;
      private static final long EMPTY_TIMESTAMP = 0;
      private static final int EMPTY_LENGTH = 0;

      private final boolean isEmpty;
      private final String baseFilename;
      private final String filename;
      private final byte[] data;
      private final int offset;
      private final int fileLength;
      private final long timestampInMillis;
      private final boolean isChecksumCorrect;

      private DataFileImpl()
         {
         this(EMPTY_FILENAME, EMPTY_DATA, EMPTY_OFFSET, EMPTY_LENGTH);
         }

      private DataFileImpl(@NotNull final String filename, @NotNull final byte[] data, final int offset, final int lengthOfFileAndChecksum) throws IllegalArgumentException
         {
         this.filename = filename;
         this.data = data;
         this.offset = offset;
         this.fileLength = (lengthOfFileAndChecksum == 0) ? 0 : lengthOfFileAndChecksum - SIZE_IN_BYTES_OF_CHECKSUM;
         this.isEmpty = (EMPTY_FILENAME.equals(filename) && Arrays.equals(EMPTY_DATA, data) && offset == EMPTY_OFFSET && fileLength == EMPTY_LENGTH);

         // check whether we're trying to create an empty file
         if (this.isEmpty)
            {
            this.timestampInMillis = EMPTY_TIMESTAMP;
            this.baseFilename = filename;
            this.isChecksumCorrect = false;
            }
         else
            {
            if (!DataFile.FILENAME_PATTERN.matcher(filename).matches())
               {
               final String message = "Invalid filename: filename [" + filename + "] does not match pattern " + DataFile.FILENAME_PATTERN;
               LOG.error("GetFileCommandStrategy$DataFileImpl.DataFileImpl(): " + message);
               throw new IllegalArgumentException(message);
               }

            this.baseFilename = filename.substring(0, filename.length() - FILENAME_EXTENSION.length());

            final String hexTimestamp = filename.substring(0, filename.length() - DataFile.FILENAME_EXTENSION.length());

            // the filename is also the timestamp, in hex
            final long seconds = Integer.parseInt(hexTimestamp, 16);
            timestampInMillis = seconds * 1000;

            // Read the expected checksum.  In Java, a checksum is a long and a long is 8 bytes, but the BodyTrack
            // device only returns 4 bytes to us. In order to use a ByteBuffer to easily convert the bytes to a long, we
            // need to give the ByteBuffer an array of 8 bytes.  We simply put the checksum data from the device in the
            // lower 4 bytes of the 8-byte array, and then give it to ByteBuffer for conversion to a long.
            final byte[] checksumBytes = new byte[8];
            System.arraycopy(data, SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER + fileLength, checksumBytes, 4, SIZE_IN_BYTES_OF_CHECKSUM);
            final long expectedChecksum = ByteBuffer.wrap(checksumBytes).getLong();

            // calculate the actual checksum
            final Checksum checksum = new CRC32();
            checksum.update(data, SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER, fileLength);
            final long actualChecksum = checksum.getValue();

            // compare expected checksum to actual
            isChecksumCorrect = (expectedChecksum == actualChecksum);
            if (LOG.isDebugEnabled())
               {
               if (!isChecksumCorrect)
                  {
                  LOG.debug("GetFileCommandStrategy.convertResponse(): checksum verification failed: expected [" + expectedChecksum + "] actual [" + actualChecksum + "].  Returning null.");
                  }
               }
            }
         }

      @Override
      public boolean isEmpty()
         {
         return isEmpty;
         }

      @NotNull
      @Override
      public String getBaseFilename()
         {
         return baseFilename;
         }

      @Override
      @NotNull
      public String getFilename()
         {
         return filename;
         }

      @Override
      @NotNull
      public Date getTimestamp()
         {
         return new Date(timestampInMillis);
         }

      @Override
      public void writeToOutputStream(@Nullable final DataOutputStream outputStream) throws IOException
         {
         if (outputStream != null)
            {
            outputStream.write(data, offset, fileLength);
            }
         }

      @Override
      public int getLength()
         {
         return fileLength;
         }

      public boolean isChecksumCorrect()
         {
         return isChecksumCorrect;
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

         final DataFileImpl dataFile = (DataFileImpl)o;

         if (fileLength != dataFile.fileLength)
            {
            return false;
            }
         if (isChecksumCorrect != dataFile.isChecksumCorrect)
            {
            return false;
            }
         if (isEmpty != dataFile.isEmpty)
            {
            return false;
            }
         if (offset != dataFile.offset)
            {
            return false;
            }
         if (timestampInMillis != dataFile.timestampInMillis)
            {
            return false;
            }
         if (baseFilename != null ? !baseFilename.equals(dataFile.baseFilename) : dataFile.baseFilename != null)
            {
            return false;
            }
         if (!Arrays.equals(data, dataFile.data))
            {
            return false;
            }
         if (filename != null ? !filename.equals(dataFile.filename) : dataFile.filename != null)
            {
            return false;
            }

         return true;
         }

      @Override
      public int hashCode()
         {
         int result = (isEmpty ? 1 : 0);
         result = 31 * result + (baseFilename != null ? baseFilename.hashCode() : 0);
         result = 31 * result + (filename != null ? filename.hashCode() : 0);
         result = 31 * result + (data != null ? Arrays.hashCode(data) : 0);
         result = 31 * result + offset;
         result = 31 * result + fileLength;
         result = 31 * result + (int)(timestampInMillis ^ (timestampInMillis >>> 32));
         result = 31 * result + (isChecksumCorrect ? 1 : 0);
         return result;
         }

      @Override
      public String toString()
         {
         final StringBuilder sb = new StringBuilder();
         sb.append("DataFile");
         if (isEmpty)
            {
            sb.append("{isEmpty=").append(isEmpty).append('}');
            }
         else
            {
            sb.append("{baseFilename='").append(baseFilename).append('\'');
            sb.append(", filename='").append(filename).append('\'');
            sb.append(", length=").append(fileLength);
            sb.append(", timestampInMillis=").append(timestampInMillis);
            sb.append(", checksumCorrect=").append(isChecksumCorrect);
            sb.append('}');
            }
         return sb.toString();
         }
      }
   }