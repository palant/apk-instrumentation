/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import soot.Body;
import soot.Local;
import soot.PrimType;
import soot.Scene;
import soot.Type;
import soot.Unit;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.Jimple;

public class Utils
{
  public static Local generateNewLocal(Body body, Type type)
  {
    LocalGenerator lg = new LocalGenerator(body);
    return lg.generateLocal(type);
  }

  public static Unit stringify(Local value, Local result)
  {
    Type type = value.getType();
    String typeSignature = (type instanceof PrimType ? type.toString() : "java.lang.Object");
    if (typeSignature == "byte" || typeSignature == "short")
      typeSignature = "int";
    return Jimple.v().newAssignStmt(
      result,
      Jimple.v().newStaticInvokeExpr(
        Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(" + typeSignature + ")>").makeRef(),
        value
      )
    );
  }
}
