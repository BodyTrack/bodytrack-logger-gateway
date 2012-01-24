package org.bodytrack.loggingdevice.commands;

import java.util.concurrent.TimeUnit;
import edu.cmu.ri.createlab.serial.SerialDeviceCommandResponse;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * <code>GetAvailableFilenamesCommandStrategy</code> gets the list of available data files from the logging device.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class GetAvailableFilenamesCommandStrategy extends UnknownLengthStringResponseCommandStrategy
   {
   /** The command character used to request the list of available data files */
   private static final byte[] COMMAND = {'F'};

   private static final String END_OF_STRING_DELIMITER = "\r\n";

   public GetAvailableFilenamesCommandStrategy()
      {
      super(10, TimeUnit.MINUTES);
      }

   @Override
   protected byte[] getCommand()
      {
      return COMMAND.clone();
      }

   @Override
   protected String getEndOfResponseDelimiterPattern()
      {
      return END_OF_STRING_DELIMITER;
      }

   @Override
   @Nullable
   public String convertResponse(final SerialDeviceCommandResponse response)
      {
      if (response != null && response.wasSuccessful())
         {
         return new String(response.getData());
         }
      return null;
      }
   }
