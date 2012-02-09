package org.bodytrack.loggingdevice.commands;

import java.util.concurrent.TimeUnit;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceReturnValueCommandStrategy;
import edu.cmu.ri.createlab.serial.SerialDeviceCommandResponse;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class DeleteFileCommandStrategy extends CreateLabSerialDeviceReturnValueCommandStrategy<Boolean>
   {
   private static final Logger LOG = Logger.getLogger(DeleteFileCommandStrategy.class);

   /** The command character used to send the delete file command. */
   private static final byte COMMAND_PREFIX = 'E';

   /** The size of the expected response, in bytes */
   private static final int SIZE_IN_BYTES_OF_EXPECTED_RESPONSE = 1;

   private final byte[] command;

   public DeleteFileCommandStrategy(final String filename)
      {
      super(10, TimeUnit.SECONDS);

      final String filenameUpperCase = filename.toUpperCase(); // base station uses all upper case for filenames

      final int lengthOfFilenameAndCRLF = filenameUpperCase.length() + 2;

      // The command consists of the command prefix, one byte containing the length of the filename plus the CRLF, then
      // the filename followed by a CRLF.
      command = new byte[1 + 1 + lengthOfFilenameAndCRLF];

      // build the command
      command[0] = COMMAND_PREFIX;
      command[1] = (byte)lengthOfFilenameAndCRLF;
      for (int i = 0; i < filenameUpperCase.length(); i++)
         {
         command[2 + i] = (byte)filenameUpperCase.charAt(i);
         }
      command[command.length - 2] = '\r';
      command[command.length - 1] = '\n';
      }

   @Override
   protected int getSizeOfExpectedResponse()
      {
      return SIZE_IN_BYTES_OF_EXPECTED_RESPONSE;
      }

   @Override
   protected byte[] getCommand()
      {
      return command.clone();
      }

   @Nullable
   @Override
   public Boolean convertResponse(final SerialDeviceCommandResponse response)
      {
      if (response != null && response.wasSuccessful())
         {
         final byte[] responseData = response.getData();

         if (LOG.isTraceEnabled())
            {
            LOG.trace("DeleteFileCommandStrategy.convertResponse(): got back [" + responseData.length + "] byte(s)");
            }
         return 'T' == (char)responseData[0];
         }
      return null;
      }
   }
