/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import soot.Body;
import soot.BodyTransformer;
import soot.Value;

public class MethodLogger extends BodyTransformer
{
  private final MethodConfig filter;
  private String tag;
  private String format;

  public MethodLogger(Properties config)
  {
    String filterSpec = config.getProperty("MethodLogger.filter");
    if (filterSpec != null)
      this.filter = new MethodConfig(filterSpec, "");
    else
      this.filter = null;

    this.tag = config.getProperty("MethodLogger.tag");
    if (tag == null)
      this.tag = "MethodLogger";

    this.format = config.getProperty("MethodLogger.format");
    if (this.format == null)
      this.format = "Entered method {method:%s} ({args:%s})";
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && this.filter.get(body.getMethod()) == null)
      return;

    UnitSequence units = new UnitSequence(body);
    units.log(this.tag, units.extendedFormat(
      this.format,
      null,
      body.getThisLocal(),
      body.getParameterLocals().stream().map(local -> (Value)local).collect(Collectors.toList())
    ));
    units.insertBefore();
  }
}
