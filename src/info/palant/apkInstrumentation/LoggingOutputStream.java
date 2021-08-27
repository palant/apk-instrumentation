/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;

public class LoggingOutputStream extends FilterOutputStream
{
  private final String tag;
  private final String prefix;
  private boolean reentrance = false;

  public LoggingOutputStream(OutputStream out, String tag, String prefix)
  {
    super(out);
    this.tag = tag;
    this.prefix = prefix;
  }

  private String formatByte(byte b)
  {
    if (b == 0x5C)
      return "\\\\";
    else if (b == 0x22)
      return "\\\"";
    else if (b >= 0x20 && b < 0x7F)
      return String.valueOf((char)b);
    else
      return String.format("\\x%02x", b);
  }

  private void log(String data)
  {
    String output = String.format("%s: sent data \"%s\"", this.prefix, data);
    for (int i = 0; i < output.length(); i += 4000)
    {
      // Calling indirectly to avoid dependency on android.jar during build
      try
      {
        Class<?> logClass = Class.forName("android.util.Log");
        Method logMethod = logClass.getDeclaredMethod("i", String.class, String.class);
        logMethod.invoke(null, this.tag, output.substring(i, Math.min(i + 4000, output.length())));
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void write(int b) throws IOException
  {
    if (this.reentrance)
    {
      super.write(b);
      return;
    }

    this.reentrance = true;
    try
    {
      super.write(b);

      this.log(this.formatByte((byte)b));
    }
    finally
    {
      this.reentrance = false;
    }
  }

  @Override
  public void write(byte[] b) throws IOException
  {
    if (this.reentrance)
    {
      super.write(b);
      return;
    }

    this.reentrance = true;
    try
    {
      super.write(b);

      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < b.length; i++)
        builder.append(this.formatByte(b[i]));
      this.log(builder.toString());
    }
    finally
    {
      this.reentrance = false;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException
  {
    if (this.reentrance)
    {
      super.write(b, off, len);
      return;
    }

    this.reentrance = true;
    try
    {
      super.write(b, off, len);

      StringBuilder builder = new StringBuilder();
      for (int i = off; i < off + len; i++)
        builder.append(this.formatByte(b[i]));
      this.log(builder.toString());
    }
    finally
    {
      this.reentrance = false;
    }
  }
}
