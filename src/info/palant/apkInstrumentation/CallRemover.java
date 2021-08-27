/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.Map;
import java.util.Properties;

import soot.Body;
import soot.BodyTransformer;
import soot.SootMethod;

public class CallRemover extends BodyTransformer
{
  private MethodConfig filter;
  private MethodConfig methodConfig;

  public CallRemover(Properties config)
  {
    String method = config.getProperty("CallRemover.method");
    if (method == null)
      throw new RuntimeException("Please add CallRemover.method option to config file.");
    this.methodConfig = new MethodConfig(method, "");

    String filterSpec = config.getProperty("CallRemover.filter");
    if (filterSpec != null)
      this.filter = new MethodConfig(filterSpec, "");
    else
      this.filter = null;
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && this.filter.get(body.getMethod()) == null)
      return;

    body.getUnits().removeIf(unit -> {
      SootMethod method = UnitParser.getInvocationMethod(unit);
      return method != null && this.methodConfig.get(method) != null;
    });

    body.validate();
  }
}
