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
import soot.BodyTransformer;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootField;
import soot.Type;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;

public class MethodLogger extends BodyTransformer
{
  private final Filter filter;
  private String tag;

  private final SootMethod method_StringBuilder_init;
  private final SootMethod method_StringBuilder_append;

  public MethodLogger(Properties config)
  {
    Scene.v().addBasicClass("android.util.Log", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.Object", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.String", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.StringBuilder", SootClass.SIGNATURES);
    Scene.v().loadNecessaryClasses();

    this.method_StringBuilder_init = Scene.v().getMethod("<java.lang.StringBuilder: void <init>(java.lang.String)>");
    this.method_StringBuilder_append = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");

    String filterSpec = config.getProperty("MethodLogger.filter");
    if (filterSpec != null)
      this.filter = new Filter(filterSpec);
    else
      this.filter = null;

    this.tag = config.getProperty("MethodLogger.tag");
    if (tag == null)
      tag = "MethodLogger";
  }

  @Override
  protected void internalTransform(Body b, String phaseName, Map<String, String> options)
  {
    JimpleBody body = (JimpleBody)b;
    if (this.filter != null && !this.filter.matches(body))
      return;

    UnitSequence units = new UnitSequence(body);

    List<Local> parameters = body.getParameterLocals();
    if (parameters.size() > 0)
    {
      Local message = units.newLocal(RefType.v("java.lang.StringBuilder"));
      units.add(
        Jimple.v().newAssignStmt(
          message,
          Jimple.v().newNewExpr(RefType.v("java.lang.StringBuilder"))
        )
      );

      units.add(
        Jimple.v().newInvokeStmt(
          Jimple.v().newSpecialInvokeExpr(
            message,
            this.method_StringBuilder_init.makeRef(),
            StringConstant.v("Entered method " + body.getMethod().getSignature() + " with parameters: ")
          )
        )
      );

      boolean first = true;
      for (Local parameter: parameters)
      {
        if (first)
          first = false;
        else
        {
          units.add(
            Jimple.v().newInvokeStmt(
              Jimple.v().newVirtualInvokeExpr(
                message,
                this.method_StringBuilder_append.makeRef(),
                StringConstant.v(", ")
              )
            )
          );
        }

        parameter = units.stringify(parameter);
        units.add(
          Jimple.v().newInvokeStmt(
            Jimple.v().newVirtualInvokeExpr(
              message,
              this.method_StringBuilder_append.makeRef(),
              parameter
            )
          )
        );
      }

      units.log(this.tag, units.stringify(message));
    }
    else
      units.log(this.tag, StringConstant.v("Entered method " + body.getMethod().getSignature()));

    body.getUnits().insertBefore(units, body.getFirstNonIdentityStmt());
    body.validate();
  }
}
