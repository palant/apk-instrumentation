/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public class LoggingInputStream extends FilterInputStream
{
  private final String tag;
  private final String prefix;
  private boolean reentrance = false;

  public LoggingInputStream(InputStream in, String tag, String prefix)
  {
    super(in);
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
    String output = String.format("%s: received data \"%s\"", this.prefix, data);
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
  public int read() throws IOException
  {
    if (this.reentrance)
      return super.read();

    this.reentrance = true;
    try
    {
      int result = super.read();
      if (result >= 0)
        this.log(this.formatByte((byte)result));
      return result;
    }
    finally
    {
      this.reentrance = false;
    }
  }

  @Override
  public int read(byte[] b) throws IOException
  {
    if (this.reentrance)
      return super.read(b);

    this.reentrance = true;
    try
    {
      int result = super.read(b);
      if (result > 0)
      {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < result; i++)
          builder.append(this.formatByte(b[i]));
        this.log(builder.toString());
      }
      return result;
    }
    finally
    {
      this.reentrance = false;
    }
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException
  {
    if (this.reentrance)
      return super.read(b, off, len);

    this.reentrance = true;
    try
    {
      int result = super.read(b, off, len);
      if (result >= 0)
      {
        StringBuilder builder = new StringBuilder();
        for (int i = off; i < off + result; i++)
          builder.append(this.formatByte(b[i]));
        this.log(builder.toString());
      }
      return result;
    }
    finally
    {
      this.reentrance = false;
    }
  }
}
