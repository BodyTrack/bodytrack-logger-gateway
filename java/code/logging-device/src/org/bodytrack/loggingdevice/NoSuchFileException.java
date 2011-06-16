package org.bodytrack.loggingdevice;

/**
 * <p>
 * <code>NoSuchFileException</code> is thrown to signify that a specified file does not exist.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class NoSuchFileException extends Exception
   {
   public NoSuchFileException()
      {
      }

   public NoSuchFileException(final String s)
      {
      super(s);
      }

   public NoSuchFileException(final String s, final Throwable throwable)
      {
      super(s, throwable);
      }

   public NoSuchFileException(final Throwable throwable)
      {
      super(throwable);
      }
   }
