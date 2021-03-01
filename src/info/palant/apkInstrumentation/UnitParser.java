/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;

public class UnitParser
{
  public static AssignStmt getAssignment(Unit unit)
  {
    if (unit instanceof AssignStmt)
      return (AssignStmt)unit;
    return null;
  }

  public static boolean isAssignment(Unit unit)
  {
    return getAssignment(unit) != null;
  }

  public static Value getAssignmentTarget(Unit unit)
  {
    AssignStmt assignment = getAssignment(unit);
    if (assignment != null)
      return assignment.getLeftOp();
    return null;
  }

  public static InvokeExpr getInvocation(Unit unit)
  {
    AssignStmt assignment = getAssignment(unit);
    if (assignment != null && assignment.getRightOp() instanceof InvokeExpr)
      return (InvokeExpr)assignment.getRightOp();
    else if (unit instanceof InvokeStmt)
      return ((InvokeStmt)unit).getInvokeExpr();
    return null;
  }

  public static boolean isInvocation(Unit unit)
  {
    return getInvocation(unit) != null;
  }

  public static SootMethod getInvocationMethod(Unit unit)
  {
    InvokeExpr invocation = getInvocation(unit);
    if (invocation != null)
      return invocation.getMethod();
    return null;
  }

  public static Value getInvocationBase(Unit unit)
  {
    InvokeExpr invocation = getInvocation(unit);
    if (invocation != null && invocation instanceof InstanceInvokeExpr)
      return ((InstanceInvokeExpr)invocation).getBase();
    return null;
  }

  public static Value getInvocationArg(Unit unit, int index)
  {
    InvokeExpr invocation = getInvocation(unit);
    if (invocation != null)
      return invocation.getArg(index);
    return null;
  }

  public static List<Value> getInvocationArgs(Unit unit)
  {
    InvokeExpr invocation = getInvocation(unit);
    if (invocation != null)
      return invocation.getArgs();
    return null;
  }
}
