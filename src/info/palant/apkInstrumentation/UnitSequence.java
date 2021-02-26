/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.ArrayList;
import java.util.Collections;

import soot.ArrayType;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.StringConstant;

public class UnitSequence extends ArrayList<Unit>
{
  private LocalGenerator generator;

  public UnitSequence(Body body)
  {
    super();

    this.generator = new LocalGenerator(body);
  }

  public Local newLocal(Type type)
  {
    return this.generator.generateLocal(type);
  }

  public void log(String tag, Value message)
  {
    this.add(
      Jimple.v().newInvokeStmt(
        Jimple.v().newStaticInvokeExpr(
          Scene.v().getMethod("<android.util.Log: int i(java.lang.String,java.lang.String)>").makeRef(),
          StringConstant.v(tag),
          message
        )
      )
    );
  }

  public Local stringify(Value value)
  {
    Type type = value.getType();
    String typeSignature = (type instanceof PrimType ? type.toString() : "java.lang.Object");
    if (typeSignature == "byte" || typeSignature == "short")
      typeSignature = "int";

    Local result = this.newLocal(RefType.v("java.lang.String"));
    this.add(
      Jimple.v().newAssignStmt(
        result,
        Jimple.v().newStaticInvokeExpr(
          Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(" + typeSignature + ")>").makeRef(),
          value
        )
      )
    );
    return result;
  }

  public Local boxPrimitive(Value value)
  {
    PrimType origType = (PrimType)value.getType();
    RefType boxedType = origType.boxedType();
    Local result = this.newLocal(boxedType);

    this.add(
      Jimple.v().newAssignStmt(
        result,
        Jimple.v().newStaticInvokeExpr(
          boxedType.getSootClass().getMethod("valueOf", Collections.singletonList(origType)).makeRef(),
          value
        )
      )
    );

    return result;
  }

  public Local newObject(String type, Value... params)
  {
    return this.newObject(RefType.v(type), params);
  }

  public Local newObject(RefType type, Value... params)
  {
    Local object = this.newLocal(type);
    this.add(
      Jimple.v().newAssignStmt(
        object,
        Jimple.v().newNewExpr(type)
      )
    );

    ArrayList<Type> paramTypes = new ArrayList<Type>();
    for (Value param: params)
      paramTypes.add(param.getType());
    this.add(
      Jimple.v().newInvokeStmt(
        Jimple.v().newSpecialInvokeExpr(
          object,
          type.getSootClass().getMethod("<init>", paramTypes).makeRef(),
          params
        )
      )
    );

    return object;
  }

  public Local arrayLiteral(Type elementType, Value... elements)
  {
    Type arrayType = ArrayType.v(elementType, 1);
    Local array = this.newLocal(arrayType);
    this.add(
      Jimple.v().newAssignStmt(
        array,
        Jimple.v().newNewArrayExpr(elementType, IntConstant.v(elements.length))
      )
    );

    int index = 0;
    for (Value element: elements)
    {
      this.add(
        Jimple.v().newAssignStmt(
          Jimple.v().newArrayRef(array, IntConstant.v(index++)),
          element
        )
      );
    }

    return array;
  }

  public Local format(Value formatStr, Value... args)
  {
    for (int i = 0; i < args.length; i++)
    {
      if (args[i] == null)
        args[i] = NullConstant.v();
      else if (args[i].getType() instanceof PrimType)
        args[i] = this.boxPrimitive(args[i]);
    }
    Local argsArray = this.arrayLiteral(RefType.v("java.lang.Object"), args);

    Local result = this.newLocal(RefType.v("java.lang.String"));
    this.add(
      Jimple.v().newAssignStmt(
        result,
        Jimple.v().newStaticInvokeExpr(
          Scene.v().getMethod("<java.lang.String: java.lang.String format(java.lang.String,java.lang.Object[])>").makeRef(),
          formatStr,
          argsArray
        )
      )
    );
    return result;
  }

  public Local getIdentity(Value obj)
  {
    Local result = this.newLocal(IntType.v());
    this.add(
      Jimple.v().newAssignStmt(
        result,
        Jimple.v().newStaticInvokeExpr(
          Scene.v().getMethod("<java.lang.System: int identityHashCode(java.lang.Object)>").makeRef(),
          obj
        )
      )
    );
    return result;
  }
}
