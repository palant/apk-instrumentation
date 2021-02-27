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

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
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

  private void insertLogging(Body body, Unit insertAfter, String formatStr, Value[] args)
  {
    UnitSequence units = new UnitSequence(body);

    // %x is the identity parameter, call getIdentity() for the corresponding value
    int index = -1;
    for (int i = 0; i < args.length; i++)
    {
      index = formatStr.indexOf('%', index + 1);
      if (index < 0)
        break;
      if (index + 1 < formatStr.length() && formatStr.charAt(index + 1) == 'x' && i < args.length)
        args[i] = (args[i] == null ? IntConstant.v(0) : units.getIdentity(args[i]));
    }

    units.log(this.tag, units.format(
      formatStr,
      args
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
      if (!(unit instanceof AssignStmt))
        continue;

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
      String className = method.getDeclaringClass().getName();
      final Value thisRef = ((InstanceInvokeExpr)invocation).getBase();
      final Value result = (unit instanceof AssignStmt ? ((AssignStmt)unit).getLeftOp() : null);
      if (className.equals("java.net.URL") && method.getName().equals("openConnection"))
      {
        this.insertLogging(
          body, unit, "Method %s opened URLConnection %x to URL %s",
          new Value[] {
            StringConstant.v(body.getMethod().getSignature()),
            result,
            thisRef
          }
        );
      }
      else if (className.equals("java.net.URLConnection") || className.equals("java.net.HttpURLConnection") || className.equals("javax.net.ssl.HttpsURLConnection"))
      {
        switch (method.getSubSignature())
        {
          case "void addRequestProperty(java.lang.String,java.lang.String)":
            this.insertLogging(
              body, unit, "Method %s added request property to URLConnection %x: %s=%s",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                thisRef,
                invocation.getArg(0),
                invocation.getArg(1)
              }
            );
            break;
          case "void connect()":
            this.insertLogging(
              body, unit, "Method %s called connect() on URLConnection %x",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                thisRef
              }
            );
            break;
          case "int getContentLength()":
            this.insertLogging(
              body, unit, "Method %s retrieved content length on URLConnection %x (%i)",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                thisRef,
                result
              }
            );
            break;
          case "java.io.InputStream getInputStream()":
          {
            if (result == null || !this.logResponses)
              break;

            UnitSequence units = new UnitSequence(body);

            Local formatStr = units.format(
              "Received data from URLConnection %x: \"%%s\"",
              units.getIdentity(thisRef)
            );

            units.assign(result, units.newObject(
              INPUT_STREAM_CLASS,
              result,
              StringConstant.v(this.tag),
              formatStr
            ));

            units.insertAfter(unit);
            break;
          }
          case "java.lang.String getContentType()":
            this.insertLogging(
              body, unit, "Method %s retrieved content type on URLConnection %x (%s)",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                thisRef,
                result
              }
            );
            break;
          case "java.io.OutputStream getOutputStream()":
          {
            if (result == null || !this.logRequestBodies)
              break;

            UnitSequence units = new UnitSequence(body);

            Local formatStr = units.format(
              "Sent data to URLConnection %x: \"%%s\"",
              units.getIdentity(thisRef)
            );

            units.assign(result, units.newObject(
              OUTPUT_STREAM_CLASS,
              result,
              StringConstant.v(this.tag),
              formatStr
            ));

            units.insertAfter(unit);
            break;
          }
          case "java.lang.String getHeaderField(java.lang.String)":
            this.insertLogging(
              body, unit, "Method %s retrieved header field %s on URLConnection %x (%s)",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                invocation.getArg(0),
                thisRef,
                result
              }
            );
            break;
          case "int getResponseCode()":
            this.insertLogging(
              body, unit, "Method %s retrieved response code on URLConnection %x (%i)",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                thisRef,
                result
              }
            );
            break;
          case "void setRequestMethod(java.lang.String)":
            this.insertLogging(
              body, unit, "Method %s set request method on URLConnection %x to %s",
              new Value[] {
                StringConstant.v(body.getMethod().getSignature()),
                thisRef,
                invocation.getArg(0)
              }
            );
            break;
        }
      }
    }
  }
}
