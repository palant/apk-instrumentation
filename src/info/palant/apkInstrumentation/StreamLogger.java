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
import soot.Value;
import soot.BodyTransformer;
import soot.jimple.StringConstant;

public class StreamLogger extends BodyTransformer
{
  final static String OUTPUT_STREAM_CLASS = "info.palant.apkInstrumentation.LoggingOutputStream";
  final static String INPUT_STREAM_CLASS = "info.palant.apkInstrumentation.LoggingInputStream";
  private final MethodConfig filter;
  private String tag;
  private MethodConfig methodConfig;

  public StreamLogger(Properties config)
  {
    String filterSpec = config.getProperty("StreamLogger.filter");
    if (filterSpec != null)
      this.filter = new MethodConfig(filterSpec, "");
    else
      this.filter = null;

    this.tag = config.getProperty("StreamLogger.tag");
    if (tag == null)
      this.tag = "StreamLogger";

    this.methodConfig = new MethodConfig(config, "StreamLogger.");

    ClassInjector.injectClass(OUTPUT_STREAM_CLASS);
    ClassInjector.injectClass(INPUT_STREAM_CLASS);
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && this.filter.get(body.getMethod()) == null)
      return;

    for (Unit unit: body.getUnits().toArray(new Unit[0]))
    {
      if (!UnitParser.isAssignment(unit))
        continue;

      SootMethod method = UnitParser.getInvocationMethod(unit);
      if (method == null)
        continue;

      String formatString = this.methodConfig.get(method);
      if (formatString == null)
        continue;

      Value result = UnitParser.getAssignmentTarget(unit);
      String type = result.getType().toString();
      String cls;
      if (type.equals("java.io.InputStream"))
        cls = INPUT_STREAM_CLASS;
      else if (type.equals("java.io.OutputStream"))
        cls = OUTPUT_STREAM_CLASS;
      else
      {
        throw new RuntimeException("Method %s called by %s produced result that is neither InputStream nor OutputStream: %s".format(
          method.getSignature(),
          body.getMethod().getSignature(),
          type
        ));
      }

      UnitSequence units = new UnitSequence(body);
      units.assign(result, units.newObject(
        cls,
        result,
        StringConstant.v(this.tag),
        units.extendedFormat(
          formatString,
          result,
          UnitParser.getInvocationBase(unit),
          UnitParser.getInvocationArgs(unit)
        )
      ));
      units.insertAfter(unit);
    }
  }
}
