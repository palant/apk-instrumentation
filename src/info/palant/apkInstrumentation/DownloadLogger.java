/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.BodyTransformer;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;

public class DownloadLogger extends BodyTransformer
{
  private final Filter filter;
  private String tag;

  public DownloadLogger(Properties config)
  {
    Scene.v().addBasicClass("android.util.Log", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.Object", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.String", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
    Scene.v().loadNecessaryClasses();

    String filterSpec = config.getProperty("DownloadLogger.filter");
    if (filterSpec != null)
      this.filter = new Filter(filterSpec);
    else
      this.filter = null;

    this.tag = config.getProperty("DownloadLogger.tag");
    if (tag == null)
      tag = "DownloadLogger";
  }

  @Override
  protected void internalTransform(Body b, String phaseName, Map<String, String> options)
  {
    JimpleBody body = (JimpleBody)b;
    if (this.filter != null && !this.filter.matches(body))
      return;

    for (Unit unit: body.getUnits().toArray(new Unit[0]))
    {
      if (!(unit instanceof AssignStmt))
        continue;

      AssignStmt assignment = (AssignStmt)unit;
      if (!(assignment.getRightOp() instanceof InstanceInvokeExpr))
        continue;

      InstanceInvokeExpr invocation = (InstanceInvokeExpr)assignment.getRightOp();
      SootMethod method = invocation.getMethod();
      if (method.getDeclaringClass().getName().equals("java.net.URL") && method.getName().equals("openConnection"))
      {
        UnitSequence units = new UnitSequence(body);

        Local message = units.format(
          StringConstant.v("Method %s opened URLConnection %x to URL %s"),
          StringConstant.v(body.getMethod().getSignature()),
          units.getIdentity(assignment.getLeftOp()),
          invocation.getBase()
        );
        units.log(this.tag, message);

        body.getUnits().insertAfter(units, unit);
        body.validate();
      }
    }
  }
}
