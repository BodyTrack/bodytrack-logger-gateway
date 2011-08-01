package org.bodytrack.loggingdevice.commands;

import java.util.concurrent.TimeUnit;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceCommandStrategy;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceVariableLengthReturnValueCommandStrategy;
import edu.cmu.ri.createlab.serial.SerialDeviceCommandResponse;
import edu.cmu.ri.createlab.util.ByteUtils;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class VariableLengthStringResponseCommandStrategy extends CreateLabSerialDeviceVariableLengthReturnValueCommandStrategy<String>
   {
   /** The size of the expected response header, in bytes */
   private static final int SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER = 1;

   private final byte[] command;
   private final char commandCharacter;

   public VariableLengthStringResponseCommandStrategy(final char commandCharacter)
      {
      this(commandCharacter,
           CreateLabSerialDeviceCommandStrategy.DEFAULT_READ_TIMEOUT_MILLIS,
           TimeUnit.MILLISECONDS,
           CreateLabSerialDeviceCommandStrategy.DEFAULT_SLURP_TIMEOUT_MILLIS,
           TimeUnit.MILLISECONDS,
           CreateLabSerialDeviceCommandStrategy.DEFAULT_MAX_NUMBER_OF_RETRIES);
      }

   protected VariableLengthStringResponseCommandStrategy(final char commandCharacter,
                                                         final long readTimeout,
                                                         final TimeUnit readTimeoutTimeUnit,
                                                         final long slurpTimeout,
                                                         final TimeUnit slurpTimeoutTimeUnit,
                                                         final int maxNumberOfRetries)
      {
      super(readTimeout, readTimeoutTimeUnit, slurpTimeout, slurpTimeoutTimeUnit, maxNumberOfRetries);
      command = new byte[]{(byte)commandCharacter};
      this.commandCharacter = commandCharacter;
      }

   @Override
   protected final byte[] getCommand()
      {
      return command.clone();
      }

   @Override
   protected final int getSizeOfExpectedResponseHeader()
      {
      return SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER;
      }

   @Override
   protected final int getSizeOfVariableLengthResponse(final byte[] header)
      {
      return ByteUtils.unsignedByteToInt(header[0]);
      }

   @Nullable
   @Override
   public final String convertResponse(final SerialDeviceCommandResponse response)
      {
      return convertResponseToString(response);
      }

   @Nullable
   public static String convertResponseToString(final SerialDeviceCommandResponse response)
      {
      if (response != null && response.wasSuccessful())
         {
         final byte[] responseData = response.getData();
         final int numChars = Math.max(0, responseData.length - SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER - 2); // subtract 2 to ignore the CR and LF, then make sure the result is non-negative
         final char[] chars = new char[numChars];
         for (int i = 0; i < chars.length; i++)
            {
            chars[i] = (char)responseData[i + SIZE_IN_BYTES_OF_EXPECTED_RESPONSE_HEADER];
            }
         return new String(chars);
         }
      return null;
      }

   @Override
   public String toString()
      {
      final StringBuilder sb = new StringBuilder();
      sb.append("VariableLengthStringResponseCommandStrategy");
      sb.append("{").append(commandCharacter);
      sb.append('}');
      return sb.toString();
      }
   }