/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import soot.SceneTransformer;

public class ClassReplacer extends SceneTransformer
{
  private List<String> paths = new ArrayList<String>();

  public ClassReplacer(Properties config)
  {
    StringTokenizer tokenizer = new StringTokenizer(config.getProperty("ClassReplacer.classes"));
    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken();
      if (token.equals(""))
        continue;

      paths.add(token);
    }
  }

  @Override
  protected void internalTransform(String phaseName, Map<String, String> options)
  {
    for (String path: this.paths)
      ClassInjector.injectJimple(path);
  }
}
