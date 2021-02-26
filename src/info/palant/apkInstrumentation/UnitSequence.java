/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import soot.ArrayType;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
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
    this.call(RefType.v("android.util.Log"), "i", StringConstant.v(tag), message);
  }

  public Local stringify(Value value)
  {
    Type type = value.getType();
    if (type instanceof PrimType)
      type = Type.toMachineType(type);
    else
      type = RefType.v("java.lang.Object");

    return this.call(
      RefType.v("java.lang.String").getSootClass().getMethod("valueOf", Collections.singletonList(type)),
      RefType.v("java.lang.String"),
      value
    );
  }

  public Local boxPrimitive(Value value)
  {
    RefType boxedType = ((PrimType)value.getType()).boxedType();
    return this.call(boxedType, "valueOf", boxedType, value);
  }

  public static List<Type> toTypes(Value... values)
  {
    ArrayList<Type> types = new ArrayList<Type>();
    for (Value value: values)
      types.add(value.getType());
    return types;
  }

  public Local newObject(String type, Value... params)
  {
    return this.newObject(RefType.v(type), params);
  }

  public Local newObject(RefType type, Value... params)
  {
    Local object = this.assign(Jimple.v().newNewExpr(type));
    this.add(
      Jimple.v().newInvokeStmt(
        Jimple.v().newSpecialInvokeExpr(
          object,
          type.getSootClass().getMethod("<init>", toTypes(params)).makeRef(),
          params
        )
      )
    );

    return object;
  }

  public Local arrayLiteral(Type elementType, Value... elements)
  {
    Type arrayType = ArrayType.v(elementType, 1);
    Local array = this.assign(Jimple.v().newNewArrayExpr(elementType, IntConstant.v(elements.length)));

    int index = 0;
    for (Value element: elements)
    {
      this.assign(
        Jimple.v().newArrayRef(array, IntConstant.v(index++)),
        element
      );
    }

    return array;
  }

  public Local assign(Value value)
  {
    return this.assign(value.getType(), value);
  }

  public Local assign(Type type, Value value)
  {
    Local result = this.newLocal(type);
    this.assign(result, value);
    return result;
  }

  public void assign(Value left, Value right)
  {
    this.add(Jimple.v().newAssignStmt(left, right));
  }

  public void call(RefType type, String name, Value... params)
  {
    this.call(type.getSootClass().getMethod(name, toTypes(params)), params);
  }

  public void call(SootMethod method, Value... params)
  {
    this.add(Jimple.v().newInvokeStmt(
      Jimple.v().newStaticInvokeExpr(
        method.makeRef(),
        params
      )
    ));
  }

  public Local call(RefType type, String name, Type returnType, Value... params)
  {
    return this.call(type.getSootClass().getMethod(name, toTypes(params)), returnType, params);
  }

  public Local call(SootMethod method, Type returnType, Value... params)
  {
    return this.assign(
      returnType,
      Jimple.v().newStaticInvokeExpr(
        method.makeRef(),
        params
      )
    );
  }

  public void call(Local base, String name, Value... params)
  {
    this.call(base, ((RefType)base.getType()).getSootClass().getMethod(name, toTypes(params)), params);
  }

  public void call(Local base, SootMethod method, Value... params)
  {
    this.add(Jimple.v().newInvokeStmt(
      Jimple.v().newVirtualInvokeExpr(
        base,
        method.makeRef(),
        params
      )
    ));
  }

  public Local call(Local base, String name, Type returnType, Value... params)
  {
    return this.call(base, ((RefType)base.getType()).getSootClass().getMethod(name, toTypes(params)), returnType, params);
  }

  public Local call(Local base, SootMethod method, Type returnType, Value... params)
  {
    return this.assign(
      returnType,
      Jimple.v().newVirtualInvokeExpr(
        base,
        method.makeRef(),
        params
      )
    );
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

    return this.call(RefType.v("java.lang.String"), "format", RefType.v("java.lang.String"), formatStr, argsArray);
  }

  public Local getIdentity(Value obj)
  {
    SootMethod method = RefType.v("java.lang.System").getSootClass().getMethod("identityHashCode", Collections.singletonList(RefType.v("java.lang.Object")));
    return this.call(method, IntType.v(), obj);
  }
}
