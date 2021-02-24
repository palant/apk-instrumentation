/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;

import soot.jimple.JimpleBody;

public class Filter
{
  private ArrayList<String> prefixes;
  private HashSet<String> classes;
  private HashSet<String> methods;

  public Filter(String spec)
  {
    this.prefixes = new ArrayList<String>();
    this.classes = new HashSet<String>();
    this.methods = new HashSet<String>();

    StringTokenizer tokenizer = new StringTokenizer(spec);
    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken();
      if (token.equals(""))
        continue;

      if (token.endsWith("()"))
        this.methods.add(token.substring(0, token.length() - 2));
      else if (token.endsWith("*"))
        this.prefixes.add(token.substring(0, token.length() - 1));
      else
        this.classes.add(token);
    }
  }

  public boolean matches(JimpleBody body)
  {
    String className = body.getMethod().getDeclaringClass().getName();
    if (this.classes.contains(className))
      return true;

    String methodName = body.getMethod().getName();
    if (this.methods.contains(className + "." + methodName))
      return true;

    for (String prefix: this.prefixes)
      if (className.startsWith(prefix))
        return true;

    return false;
  }
}
