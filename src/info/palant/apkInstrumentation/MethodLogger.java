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
  private String tag;
  private MethodConfig methodConfig;

  public MethodLogger(Properties config)
  {
    this.tag = config.getProperty("MethodLogger.tag");
    if (tag == null)
      this.tag = "MethodLogger";

    this.methodConfig = new MethodConfig(config, "MethodLogger.");
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    String formatString = this.methodConfig.get(body.getMethod());
    if (formatString == null)
      return;

    UnitSequence units = new UnitSequence(body);
    units.log(this.tag, units.extendedFormat(
      formatString,
      null,
      body.getThisLocal(),
      body.getParameterLocals().stream().map(local -> (Value)local).collect(Collectors.toList())
    ));
    units.insertBefore();
  }
}
