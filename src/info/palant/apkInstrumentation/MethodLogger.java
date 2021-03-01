/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.jimple.StringConstant;

public class MethodLogger extends BodyTransformer
{
  private final Filter filter;
  private String tag;

  public MethodLogger(Properties config)
  {
    String filterSpec = config.getProperty("MethodLogger.filter");
    if (filterSpec != null)
      this.filter = new Filter(filterSpec);
    else
      this.filter = null;

    this.tag = config.getProperty("MethodLogger.tag");
    if (tag == null)
      this.tag = "MethodLogger";
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && !this.filter.matches(body))
      return;

    UnitSequence units = new UnitSequence(body);

    List<Local> parameters = body.getParameterLocals();
    if (parameters.size() > 0)
    {
      Local message = units.newObject(
        "java.lang.StringBuilder",
        StringConstant.v("Entered method " + body.getMethod().getSignature() + " with parameters: ")
      );

      boolean first = true;
      for (Local parameter: parameters)
      {
        if (first)
          first = false;
        else
          units.call(message, "append", StringConstant.v(", "));

        units.call(message, "append", units.stringify(parameter));
      }

      units.log(this.tag, units.stringify(message));
    }
    else
      units.log(this.tag, StringConstant.v("Entered method " + body.getMethod().getSignature()));

    units.insertBefore();
  }
}
