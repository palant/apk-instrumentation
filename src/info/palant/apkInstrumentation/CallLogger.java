/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.Map;
import java.util.Properties;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.BodyTransformer;

public class CallLogger extends BodyTransformer
{
  private final MethodConfig filter;
  private String tag;
  private MethodConfig methodConfig;

  public CallLogger(Properties config)
  {
    String filterSpec = config.getProperty("CallLogger.filter");
    if (filterSpec != null)
      this.filter = new MethodConfig(filterSpec, "");
    else
      this.filter = null;

    this.tag = config.getProperty("CallLogger.tag");
    if (tag == null)
      this.tag = "CallLogger";

    this.methodConfig = new MethodConfig(config, "CallLogger.");
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && this.filter.get(body.getMethod()) == null)
      return;

    for (Unit unit: body.getUnits().toArray(new Unit[0]))
    {
      SootMethod method = UnitParser.getInvocationMethod(unit);
      if (method == null)
        continue;

      String formatString = this.methodConfig.get(method);
      if (formatString == null)
        continue;

      UnitSequence units = new UnitSequence(body);
      units.log(this.tag, units.extendedFormat(
        formatString,
        UnitParser.getAssignmentTarget(unit),
        UnitParser.getInvocationBase(unit),
        UnitParser.getInvocationArgs(unit)
      ));
      units.insertAfter(unit);
    }
  }
}
