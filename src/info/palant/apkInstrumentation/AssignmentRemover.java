/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.util.Map;
import java.util.Properties;

import soot.Body;
import soot.BodyTransformer;
import soot.jimple.AssignStmt;

public class AssignmentRemover extends BodyTransformer
{
  private MethodConfig filter;
  private String type;

  public AssignmentRemover(Properties config)
  {
    this.type = config.getProperty("AssignmentRemover.type");
    if (this.type == null)
      throw new RuntimeException("Please add AssignmentRemover.type option to config file.");

    String filterSpec = config.getProperty("AssignmentRemover.filter");
    if (filterSpec != null)
      this.filter = new MethodConfig(filterSpec, "");
    else
      this.filter = null;
  }

  @Override
  protected void internalTransform(Body body, String phaseName, Map<String, String> options)
  {
    if (this.filter != null && this.filter.get(body.getMethod()) == null)
      return;

    body.getUnits().removeIf(unit -> {
      if (unit instanceof AssignStmt)
      {
        String typeName = ((AssignStmt)unit).getLeftOp().getType().toString();
        if (typeName.equals(this.type))
          return true;
      }
      return false;
    });

    body.validate();
  }
}
