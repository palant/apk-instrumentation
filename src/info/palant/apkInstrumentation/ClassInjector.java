/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.Body;
import soot.Local;
import soot.SootModuleResolver;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.parser.JimpleAST;

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

  public static void injectJimple(String path)
  {
    try
    {
      JimpleAST ast = new JimpleAST(new FileInputStream(path));
      try
      {
        ast.getSkeleton(new SootClass("Temp"));
        return;
      }
      catch (RuntimeException e)
      {
        // Ugly hack: get the class name from the error message
        Matcher matcher = Pattern.compile("expected:\\s*([^\\s,]+)").matcher(e.getMessage());
        if (matcher.find())
        {
          SootClass cls = ast.getResolver().resolveClass(matcher.group(1), SootClass.BODIES);
          if (cls.isPhantom())
            return;

          ArrayList<SootClass> interfaceList = new ArrayList<SootClass>(cls.getInterfaces());
          for (SootClass iface: interfaceList)
            cls.removeInterface(iface);
          ArrayList<SootField> fieldList = new ArrayList<SootField>(cls.getFields());
          for (SootField field: fieldList)
            cls.removeField(field);
          ArrayList<SootMethod> methodList = new ArrayList<SootMethod>(cls.getMethods());
          for (SootMethod method: methodList)
            cls.removeMethod(method);

          ast.getSkeleton(cls);
          for (SootMethod method: cls.getMethods())
            method.setActiveBody(ast.getBody(method));
        }
        else
          throw e;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }
}
