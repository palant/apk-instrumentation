/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import soot.SootClass;
import soot.SootMethod;
import soot.Type;

public class MethodConfig
{
  private Map<String,Map<String,String>> methods = new HashMap<String,Map<String,String>>();
  private List<String[]> classPrefixes = new ArrayList<String[]>();

  public MethodConfig(Properties config, String configPrefix)
  {
    for (String property: config.stringPropertyNames())
    {
      if (!property.startsWith(configPrefix))
        continue;

      this.add(property.substring(configPrefix.length()), config.getProperty(property));
    }
  }

  public MethodConfig(String key, String value)
  {
    this.add(key, value);
  }

  private void add(String key, String value)
  {
    StringTokenizer tokenizer = new StringTokenizer(key);
    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken();
      if (token.equals(""))
        continue;

      int index = token.indexOf(":");
      if (index < 0)
      {
        if (token.endsWith("*"))
          this.classPrefixes.add(new String[] {
            token.substring(0, token.length() - 1),
            value
          });
      }
      else
      {
        String className = token.substring(0, index).trim();
        String methodName = token.substring(index + 1).trim();
        if (!this.methods.containsKey(className))
          this.methods.put(className, new HashMap<String,String>());
        this.methods.get(className).put(methodName, value);
      }
    }

  }

  public String get(SootMethod method)
  {
    SootClass cls = method.getDeclaringClass();
    while (true)
    {
      String className = cls.getName();
      for (String[] prefixConfig: this.classPrefixes)
      {
        if (className.startsWith(prefixConfig[0]))
          return prefixConfig[1];
      }

      Map<String,String> classConfig = this.methods.get(className);
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
        if (methodConfig == null)
          methodConfig = classConfig.get("*");

        if (methodConfig != null)
          return methodConfig;
      }
      if (!cls.hasSuperclass())
        return null;
      cls = cls.getSuperclass();
    }
  }
}
