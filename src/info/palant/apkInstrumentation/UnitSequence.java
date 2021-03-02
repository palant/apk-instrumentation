/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.ArrayType;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.NullConstant;
import soot.jimple.StringConstant;

public class UnitSequence extends ArrayList<Unit>
{
  private boolean inserted;
  private Body body;
  private LocalGenerator generator;

  public UnitSequence(Body body)
  {
    super();

    this.body = body;
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

  public Local cast(Value value, Type type)
  {
    return this.assign(type, Jimple.v().newCastExpr(value, type));
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

  public Local format(String formatStr, Value... args)
  {
    return this.format(StringConstant.v(formatStr), args);
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

  public Local extendedFormat(String formatString, Value result, Value thisRef, List<Value> argValues)
  {
    Matcher matcher = Pattern.compile("\\{(.*?):(%.*?)\\}").matcher(formatString);
    List<Value> args = new ArrayList<Value>();
    String cleanFormatString = "";
    int prevEnd = 0;
    while (matcher.find())
    {
      cleanFormatString += formatString.substring(prevEnd, matcher.start());
      cleanFormatString += matcher.group(2);
      prevEnd = matcher.end();

      Value arg;
      if (matcher.group(1).equals("method"))
        arg = StringConstant.v(this.body.getMethod().getSignature());
      else if (matcher.group(1).equals("result"))
        arg = result;
      else if (matcher.group(1).equals("this"))
        arg = thisRef;
      else if (matcher.group(1).equals("args"))
      {
        if (argValues.size() == 0)
          arg = StringConstant.v("");
        else if (argValues.size() == 1)
          arg = this.stringify(argValues.get(0));
        else
        {
          Local builder = this.newObject("java.lang.StringBuilder");

          boolean first = true;
          for (Value argValue: argValues)
          {
            if (first)
              first = false;
            else
              this.call(builder, "append", StringConstant.v(", "));

            this.call(builder, "append", this.stringify(argValue));
          }

          arg = this.stringify(builder);
        }
      }
      else if (matcher.group(1).startsWith("arg"))
        arg = argValues.get(Integer.parseInt(matcher.group(1).substring(3)));
      else
        throw new RuntimeException("Unknown parameter name " + matcher.group(1));

      if (matcher.group(2).equals("%x"))
        arg = (arg == null ? IntConstant.v(0) : this.getIdentity(arg));
      args.add(arg);
    }
    cleanFormatString += formatString.substring(prevEnd);
    return this.format(
      cleanFormatString,
      args.toArray(new Value[0])
    );
  }

  public Local getIdentity(Value obj)
  {
    SootClass cls = RefType.v("java.lang.System").getSootClass();
    SootMethod method = cls.getMethod("identityHashCode", Collections.singletonList(RefType.v("java.lang.Object")));
    return this.call(method, IntType.v(), obj);
  }

  public void insertBefore()
  {
    this.insertBefore(((JimpleBody)this.body).getFirstNonIdentityStmt());
  }

  public void insertBefore(Unit unit)
  {
    if (this.inserted)
      throw new RuntimeException("Attempt to insert a unit sequence twice");

    this.inserted = true;
    this.body.getUnits().insertBefore(this, unit);
    this.body.validate();
  }

  public void insertAfter(Unit unit)
  {
    if (this.inserted)
      throw new RuntimeException("Attempt to insert a unit sequence twice");

    this.inserted = true;
    this.body.getUnits().insertAfter(this, unit);
    this.body.validate();
  }
}
