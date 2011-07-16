package org.bodytrack.loggingdevice.commands;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceCommandStrategy;
import edu.cmu.ri.createlab.serial.SerialDeviceCommandResponse;
import edu.cmu.ri.createlab.serial.SerialDeviceIOHelper;
import edu.cmu.ri.createlab.serial.SerialDeviceReturnValueCommandStrategy;
import org.apache.log4j.Logger;

/**
 * The <code>UnknownLengthStringResponseCommandStrategy</code> class is a
 * {@link CreateLabSerialDeviceCommandStrategy} which allows for unknown-length responses which are terminated by a
 * predefined {@link String} delimiter.
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public abstract class UnknownLengthStringResponseCommandStrategy extends CreateLabSerialDeviceCommandStrategy implements SerialDeviceReturnValueCommandStrategy<String>
   {
   private static final Logger LOG = Logger.getLogger(UnknownLengthStringResponseCommandStrategy.class);

   /**
    * Creates a <code>UnknownLengthStringResponseCommandStrategy</code> using the default values for read timeout, slurp
    * timeout, and max retries.
    *
    * @see #DEFAULT_READ_TIMEOUT_MILLIS
    * @see #DEFAULT_SLURP_TIMEOUT_MILLIS
    * @see #DEFAULT_MAX_NUMBER_OF_RETRIES
    */
   protected UnknownLengthStringResponseCommandStrategy()
      {
      }

   /**
    * Creates a <code>UnknownLengthStringResponseCommandStrategy</code> using the given value for read timeout and the default
    * values for slurp timeout and max retries.
    *
    * @see #DEFAULT_SLURP_TIMEOUT_MILLIS
    * @see #DEFAULT_MAX_NUMBER_OF_RETRIES
    */
   protected UnknownLengthStringResponseCommandStrategy(final long readTimeout, final TimeUnit readTimeoutTimeUnit)
      {
      super(readTimeout, readTimeoutTimeUnit);
      }

   /**
    * Creates a <code>UnknownLengthStringResponseCommandStrategy</code> using the given values for read timeout and slurp
    * timeout and the default value for max retries.
    *
    * @see #DEFAULT_MAX_NUMBER_OF_RETRIES
    */
   protected UnknownLengthStringResponseCommandStrategy(final long readTimeout, final TimeUnit readTimeoutTimeUnit, final long slurpTimeout, final TimeUnit slurpTimeoutTimeUnit)
      {
      super(readTimeout, readTimeoutTimeUnit, slurpTimeout, slurpTimeoutTimeUnit);
      }

   /**
    * Creates a <code>UnknownLengthStringResponseCommandStrategy</code> using the given values for read timeout, slurp
    * timeout, and max retries.
    */
   protected UnknownLengthStringResponseCommandStrategy(final long readTimeout, final TimeUnit readTimeoutTimeUnit, final long slurpTimeout, final TimeUnit slurpTimeoutTimeUnit, final int maxNumberOfRetries)
      {
      super(readTimeout, readTimeoutTimeUnit, slurpTimeout, slurpTimeoutTimeUnit, maxNumberOfRetries);
      }

   /**
    * Executes the strategy, and returns the {@link String} as a byte array in the {@link SerialDeviceCommandResponse}.
    * The delimiter is included at the end of the {@link String}.
    */
   public final SerialDeviceCommandResponse execute(final SerialDeviceIOHelper ioHelper)
      {
      LOG.trace("UnknownLengthStringResponseCommandStrategy.execute()");

      final String delimiterPattern = getEndOfResponseDelimiterPattern();

      if ((delimiterPattern != null) && (delimiterPattern.length() > 0))
         {
         // get the command to be written
         final byte[] command = getCommand();

         // write the command and check for the command echo
         final boolean wasWriteSuccessful = writeCommand(ioHelper, command);

         if (wasWriteSuccessful)
            {

            LOG.trace("UnknownLengthStringResponseCommandStrategy.execute(): Reading command return value, looking for delimiter [" + delimiterPattern + "]...");

            // get the last character of the pattern so we can search for it
            final char lastCharacterOfDelimiterPattern = delimiterPattern.charAt(delimiterPattern.length() - 1);

            // create a StringBuilder to read into
            final StringBuilder stringBuilder = new StringBuilder();

            // define the ending time
            final long readEndTime = getReadTimeoutMillis() + System.currentTimeMillis();

            // repeatedly read until we run out of time, or we find the first character in the pattern.  If the last
            // character of the delimiter pattern is found, compare the end of what was read with the
            // delimiterPattern to see whether there's a match.  If so, then we're done.  If not, then repeat the
            // above.
            boolean foundPattern = false;
            try
               {
               do
                  {
                  // read into the StringBuilder
                  readIntoStringBuilderUntilCharFoundOrTimeout(ioHelper, stringBuilder, lastCharacterOfDelimiterPattern, readEndTime);

                  // now compare the delimiter pattern with the end of what's in the StringBuilder to see whether we've
                  // found a match
                  if (stringBuilder.length() >= delimiterPattern.length())
                     {
                     // see whether we found the delimiter pattern
                     final String pieceToCompareWithDelimiter = stringBuilder.substring(stringBuilder.length() - delimiterPattern.length());
                     if (delimiterPattern.equals(pieceToCompareWithDelimiter))
                        {
                        foundPattern = true;
                        }
                     }
                  }
               while (!foundPattern && System.currentTimeMillis() <= readEndTime);

               if (foundPattern)
                  {
                  return new SerialDeviceCommandResponse(stringBuilder.toString().getBytes());
                  }
               }
            catch (IOException e)
               {
               LOG.error("IOException while trying to read the response", e);
               }
            }
         }
      else
         {
         LOG.error("UnknownLengthStringResponseCommandStrategy.execute(): The end of response delimiter cannot be null or empty!  No write will be attempted, and a SerialDeviceCommandResponse with a false success will be returned");
         }

      return new SerialDeviceCommandResponse(false);
      }

   private void readIntoStringBuilderUntilCharFoundOrTimeout(final SerialDeviceIOHelper ioHelper,
                                                             final StringBuilder stringBuilder,
                                                             final char charToFind,
                                                             final long readEndTime) throws IOException
      {
      if (ioHelper != null && stringBuilder != null)
         {
         // read until we run out of time, or we find the first character in the pattern
         boolean foundCharacter = false;
         while (!foundCharacter && (System.currentTimeMillis() <= readEndTime))
            {
            if (ioHelper.isDataAvailable())
               {
               try
                  {
                  final int c = ioHelper.read();
                  if (c >= 0)
                     {
                     stringBuilder.append((char)c);
                     if (LOG.isTraceEnabled())
                        {
                        LOG.trace("CreateLabSerialDeviceCommandStrategy.slurpAndMatchPattern():    read [" + (char)c + "|" + c + "]");
                        }
                     foundCharacter = (c == charToFind);
                     }
                  else
                     {
                     LOG.error("CreateLabSerialDeviceCommandStrategy.slurpAndMatchPattern(): End of stream reached while trying to read the pattern");
                     break;
                     }
                  }
               catch (IOException e)
                  {
                  LOG.error("CreateLabSerialDeviceCommandStrategy.slurpAndMatchPattern(): IOException while trying to read the pattern", e);
                  break;
                  }
               }
            }
         }
      }

   /** The command to be written, including any arguments. */
   protected abstract byte[] getCommand();

   /** Returns the {@link String} which marks the end of the response. */
   protected abstract String getEndOfResponseDelimiterPattern();
   }