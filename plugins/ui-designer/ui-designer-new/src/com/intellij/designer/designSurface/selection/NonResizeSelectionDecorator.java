/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.designSurface.selection;

import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.tools.DragTracker;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class NonResizeSelectionDecorator implements ComponentDecorator {
  private final Color myColor;
  private final int myLineWidth;

  public NonResizeSelectionDecorator(Color color, int lineWidth) {
    myColor = color;
    myLineWidth = Math.max(lineWidth, 1);
  }

  @Override
  public InputTool findTargetTool(DecorationLayer layer, RadComponent component, int x, int y) {
    Rectangle bounds = layer.getComponentBounds(component);

    Rectangle top = new Rectangle(bounds.x, bounds.y, bounds.width, myLineWidth);
    Rectangle bottom = new Rectangle(bounds.x, bounds.y + bounds.height - myLineWidth, bounds.width, myLineWidth);
    Rectangle left = new Rectangle(bounds.x, bounds.y, myLineWidth, bounds.height);
    Rectangle right = new Rectangle(bounds.x + bounds.width - myLineWidth, bounds.y, myLineWidth, bounds.height);

    if (top.contains(x, y) || bottom.contains(x, y) || left.contains(x, y) || right.contains(x, y)) {
      return new DragTracker();
    }

    return null;
  }

  @Override
  public void decorate(DecorationLayer layer, Graphics2D g, RadComponent component) {
    g.setColor(myColor);
    if (myLineWidth > 1) {
      g.setStroke(new BasicStroke(myLineWidth));
    }

    Rectangle bounds = layer.getComponentBounds(component);
    g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
  }
}