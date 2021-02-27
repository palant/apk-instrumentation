/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import soot.Body;
import soot.Local;
import soot.SootModuleResolver;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.SpecialInvokeExpr;

public abstract class ClassInjector
{
  public static void injectClass(String spec)
  {
    SootClass cls = SootModuleResolver.v().resolveClass(spec, SootClass.BODIES);
    cls.setApplicationClass();

    // Type Assigner will mess up super() calls in constructors, change:
    //
    //   $var = (SuperClass) this;
    //   specialinvoke $var.<SuperClass: void <init>(...)>(...);
    //
    // back to:
    //
    //   specialinvoke this.<SuperClass: void <init>(...)>(...);
    for (SootMethod method: cls.getMethods())
    {
      if (!method.getName().equals("<init>"))
        continue;

      Body body = method.retrieveActiveBody();
      Unit remove = null;
      Local casted = null;
      for (Unit unit: body.getUnits())
      {
        if (unit instanceof AssignStmt)
        {
          AssignStmt assignment = (AssignStmt)unit;
          if (assignment.getRightOp() instanceof CastExpr)
          {
            CastExpr cast = (CastExpr)assignment.getRightOp();
            if (cls.getSuperclass().getType().equals(cast.getType()) && cast.getOp() == body.getThisLocal())
            {
              remove = unit;
              casted = (Local)assignment.getLeftOp();
            }
          }
        }
        else if (unit instanceof InvokeStmt)
        {
          InvokeStmt invocation = (InvokeStmt)unit;
          if (invocation.getInvokeExpr() instanceof SpecialInvokeExpr)
          {
            SpecialInvokeExpr expr = (SpecialInvokeExpr)invocation.getInvokeExpr();
            if (casted != null && expr.getBase() instanceof Local && ((Local)expr.getBase()).getName().equals(casted.getName()))
              expr.setBase(body.getThisLocal());
          }
        }
      }
      if (remove != null)
      {
        body.getUnits().remove(remove);
        body.validate();
      }
    }
  }
}
