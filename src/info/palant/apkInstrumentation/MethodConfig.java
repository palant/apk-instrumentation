/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import soot.SootClass;
import soot.SootMethod;
import soot.Type;

public class MethodConfig
{
  private Map<String,Map<String,String>> methods;

  public MethodConfig(Properties config, String configPrefix)
  {
    this.methods = new HashMap<String,Map<String,String>>();

    for (String property: config.stringPropertyNames())
    {
      if (!property.startsWith(configPrefix))
        continue;

      String signature = property.substring(configPrefix.length());
      int index = signature.indexOf(":");
      if (index < 0)
        continue;

      String className = signature.substring(0, index).trim();
      String methodName = signature.substring(index + 1).trim();
      if (!this.methods.containsKey(className))
        this.methods.put(className, new HashMap<String,String>());
      this.methods.get(className).put(methodName, config.getProperty(property));
    }
  }

  public String get(SootMethod method)
  {
    SootClass cls = method.getDeclaringClass();
    while (true)
    {
      Map<String,String> classConfig = this.methods.get(cls.getName());
      if (classConfig != null)
      {
        String methodConfig = classConfig.get(method.getName());
        if (methodConfig == null)
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
          methodConfig = classConfig.get(signature);
        }

        if (methodConfig != null)
          return methodConfig;
      }
      if (!cls.hasSuperclass())
        return null;
      cls = cls.getSuperclass();
    }
  }
}
