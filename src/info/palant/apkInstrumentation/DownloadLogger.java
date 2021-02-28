/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Type;
import soot.Value;
import soot.BodyTransformer;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.StringConstant;

public class DownloadLogger extends BodyTransformer
{
  final static String OUTPUT_STREAM_CLASS = "info.palant.apkInstrumentation.LoggingOutputStream";
  final static String INPUT_STREAM_CLASS = "info.palant.apkInstrumentation.LoggingInputStream";
  final static Map<String,Map<String,String>> loggedCalls = Map.ofEntries(
    Map.entry("java.net.URL", Map.ofEntries(
      Map.entry("openConnection", "Method {method:%s} opened URLConnection {result:%x} to URL {this:%s}")
    )),
    Map.entry("java.net.URLConnection", Map.ofEntries(
      Map.entry("addRequestProperty(java.lang.String,java.lang.String)", "Method {method:%s} added request header to URLConnection {this:%x}: {arg0:%s}: {arg1:%s}"),
      Map.entry("connect()", "Method {method:%s} called connect() on URLConnection {this:%x}"),
      Map.entry("getContentLength()", "Method {method:%s} retrieved content length on URLConnection {this:%x} ({result:%i})"),
      Map.entry("getContentType()", "Method {method:%s} retrieved content type on URLConnection {this:%x} ({result:%s})"),
      Map.entry("getHeaderField(java.lang.String)", "Method {method:%s} retrieved header field {arg0:%s} on URLConnection {this:%x} ({result:%s})"),
      Map.entry("getResponseCode()", "Method {method:%s} retrieved response code on URLConnection {this:%x} ({result:%i})"),
      Map.entry("setRequestMethod(java.lang.String)", "Method {method:%s} set request method on URLConnection {this:%x} to {arg0:%s}"),
      Map.entry("setRequestProperty(java.lang.String,java.lang.String)", "Method {method:%s} set request header for URLConnection {this:%x}: {arg0:%s}: {arg1:%s}")
    ))
  );
  private final Filter filter;
  private String tag;
  private boolean logRequestBodies;
  private boolean logResponses;

  public DownloadLogger(Properties config)
  {
    Scene.v().addBasicClass("android.util.Log", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.io.FilterInputStream", SootClass.SIGNATURES);
    Scene.v().addBasicClass("java.io.FilterOutputStream", SootClass.SIGNATURES);
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

    this.logRequestBodies = (config.getProperty("DownloadLogger.requestBodies") != null);
    if (this.logRequestBodies)
      ClassInjector.injectClass(OUTPUT_STREAM_CLASS);

    this.logResponses = (config.getProperty("DownloadLogger.responses") != null);
    if (this.logResponses)
      ClassInjector.injectClass(INPUT_STREAM_CLASS);
  }

  private void logCall(Body body, Unit insertAfter, InvokeExpr invocation, Value thisRef, Value result)
  {
    Map<String,String> formatStrings = null;
    SootMethod method = invocation.getMethod();
    SootClass cls = method.getDeclaringClass();
    while (true)
    {
      formatStrings = loggedCalls.get(cls.getName());
      if (formatStrings != null)
        break;
      if (!cls.hasSuperclass())
        return;
      cls = cls.getSuperclass();
    }

    String formatString = formatStrings.get(method.getName());
    if (formatString == null)
    {
      String signature = method.getName() + "(";
      boolean first = true;
      for (Type type: method.getParameterTypes())
      {
        if (first)
          first = false;
        else
          signature += ",";
        signature += type.toString();
      }
      signature += ")";
      formatString = formatStrings.get(signature);
    }
    if (formatString == null)
      return;

    UnitSequence units = new UnitSequence(body);
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
        arg = StringConstant.v(body.getMethod().getSignature());
      else if (matcher.group(1).equals("this"))
        arg = thisRef;
      else if (matcher.group(1).equals("result"))
        arg = result;
      else if (matcher.group(1).startsWith("arg"))
        arg = invocation.getArg(Integer.parseInt(matcher.group(1).substring(3)));
      else
        throw new RuntimeException("Unknown parameter name " + matcher.group(1));

      if (matcher.group(2).equals("%x"))
        arg = (arg == null ? IntConstant.v(0) : units.getIdentity(arg));
      args.add(arg);
    }
    units.log(this.tag, units.format(
      cleanFormatString,
      args.toArray(new Value[0])
    ));
    units.insertAfter(insertAfter);
  }

  private void wrapResult(Body body, Unit insertAfter, InvokeExpr invocation, Value thisRef, Value result)
  {
    SootMethod method = invocation.getMethod();
    SootClass cls = method.getDeclaringClass();
    while (true)
    {
      if (cls.getName().equals("java.net.URLConnection"))
        break;
      if (!cls.hasSuperclass())
        return;
      cls = cls.getSuperclass();
    }

    String logClass;
    String formatString;
    if (method.getName().equals("getInputStream"))
    {
      if (!this.logResponses)
        return;
      logClass = INPUT_STREAM_CLASS;
      formatString = "Received data from URLConnection %x: \"%%s\"";
    }
    else if (method.getName().equals("getOutputStream"))
    {
      if (!this.logRequestBodies)
        return;
      logClass = OUTPUT_STREAM_CLASS;
      formatString = "Sent data to URLConnection %x: \"%%s\"";
    }
    else
      return;

    UnitSequence units = new UnitSequence(body);
    units.assign(result, units.newObject(
      logClass,
      result,
      StringConstant.v(this.tag),
      units.format(
        formatString,
        units.getIdentity(thisRef)
      )
    ));
    units.insertAfter(insertAfter);
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && !this.filter.matches(body))
      return;

    for (Unit unit: body.getUnits().toArray(new Unit[0]))
    {
      InvokeExpr invocation;
      if (unit instanceof AssignStmt && ((AssignStmt)unit).getRightOp() instanceof InvokeExpr)
        invocation = (InvokeExpr)((AssignStmt)unit).getRightOp();
      else if (unit instanceof InvokeStmt)
        invocation = ((InvokeStmt)unit).getInvokeExpr();
      else
        continue;

      if (!(invocation instanceof InstanceInvokeExpr))
        continue;

      SootMethod method = invocation.getMethod();
      final Value thisRef = ((InstanceInvokeExpr)invocation).getBase();
      final Value result = (unit instanceof AssignStmt ? ((AssignStmt)unit).getLeftOp() : null);
      this.logCall(body, unit, invocation, thisRef, result);
      if (result != null)
        this.wrapResult(body, unit, invocation, thisRef, result);
    }
  }
}
