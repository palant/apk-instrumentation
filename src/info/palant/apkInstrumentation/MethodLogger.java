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

  private final SootMethod method_Log_i;
  private final SootMethod method_Object_toString;
  private final SootMethod method_StringBuilder_init;
  private final SootMethod method_StringBuilder_append;

  public MethodLogger(Properties config)
  {
    Scene.v().addBasicClass("android.util.Log", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.Object", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.String", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.StringBuilder", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
    Scene.v().loadNecessaryClasses();

    this.method_Log_i = Scene.v().getMethod("<android.util.Log: int i(java.lang.String,java.lang.String)>");
    this.method_Object_toString = Scene.v().getMethod("<java.lang.Object: java.lang.String toString()>");
    this.method_StringBuilder_init = Scene.v().getMethod("<java.lang.StringBuilder: void <init>(java.lang.String)>");
    this.method_StringBuilder_append = Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>");

    String filterSpec = config.getProperty("MethodLogger.filter");
    if (filterSpec != null)
      this.filter = new Filter(filterSpec);
    else
      this.filter = null;

    this.tag = config.getProperty("MethodLogger.tag");
    if (tag == null)
      tag = "APKInstrumentation";
  }

  @Override
  protected void internalTransform(Body b, String phaseName, Map<String, String> options)
  {
    JimpleBody body = (JimpleBody)b;
    if (this.filter != null && !this.filter.matches(body))
      return;

    List<Unit> units = new ArrayList<>();

    Local messageStringified = Utils.generateNewLocal(body, RefType.v("java.lang.String"));
    List<Local> parameters = body.getParameterLocals();
    if (parameters.size() > 0)
    {
      Local message = Utils.generateNewLocal(body, RefType.v("java.lang.StringBuilder"));
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

        Local stringified = Utils.generateNewLocal(body, RefType.v("java.lang.String"));
        units.add(Utils.stringify(parameter, stringified));
        units.add(
          Jimple.v().newInvokeStmt(
            Jimple.v().newVirtualInvokeExpr(
              message,
              this.method_StringBuilder_append.makeRef(),
              stringified
            )
          )
        );
      }

      units.add(
        Jimple.v().newAssignStmt(
          messageStringified,
          Jimple.v().newVirtualInvokeExpr(
            message,
            this.method_Object_toString.makeRef()
          )
        )
      );
    }
    else
    {
      units.add(
        Jimple.v().newAssignStmt(
          messageStringified,
          StringConstant.v("Entered method " + body.getMethod().getSignature())
        )
      );
    }

    units.add(
      Jimple.v().newInvokeStmt(
        Jimple.v().newStaticInvokeExpr(
          this.method_Log_i.makeRef(),
          StringConstant.v(this.tag),
          messageStringified
        )
      )
    );

    body.getUnits().insertBefore(units, body.getFirstNonIdentityStmt());
    body.validate();
  }
}
