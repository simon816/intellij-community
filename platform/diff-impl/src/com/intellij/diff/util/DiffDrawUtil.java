/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class DiffDrawUtil {
  private static final int STRIPE_LAYER = HighlighterLayer.ERROR - 1;
  private static final int DEFAULT_LAYER = HighlighterLayer.SELECTION - 3;
  private static final int INLINE_LAYER = HighlighterLayer.SELECTION - 2;
  private static final int LINE_MARKER_LAYER = HighlighterLayer.SELECTION - 1;

  private static final double CTRL_PROXIMITY_X = 0.3;

  public static final LineSeparatorRenderer BORDER_LINE_RENDERER = new LineSeparatorRenderer() {
    @Override
    public void drawLine(Graphics g, int x1, int x2, int y) {
      Rectangle clip = g.getClipBounds();
      x2 = clip.x + clip.width;
      g.setColor(JBColor.border());
      g.drawLine(x1, y, x2, y);
    }
  };

  private DiffDrawUtil() {
  }

  @NotNull
  public static Color getDividerColor() {
    return getDividerColor(null);
  }

  @NotNull
  public static Color getDividerColor(@Nullable Editor editor) {
    return getDividerColorFromScheme(editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme());
  }

  @NotNull
  public static Color getDividerColorFromScheme(@NotNull EditorColorsScheme scheme) {
    Color gutterBackground = scheme.getColor(EditorColors.GUTTER_BACKGROUND);
    if (gutterBackground == null) {
      gutterBackground = EditorColors.GUTTER_BACKGROUND.getDefaultColor();
    }
    return gutterBackground;
  }

  public static void drawConnectorLineSeparator(@NotNull Graphics2D g,
                                                int x1, int x2,
                                                int start1, int end1,
                                                int start2, int end2) {
    drawConnectorLineSeparator(g, x1, x2, start1, end1, start2, end2, null);
  }

  public static void drawConnectorLineSeparator(@NotNull Graphics2D g,
                                                int x1, int x2,
                                                int start1, int end1,
                                                int start2, int end2,
                                                @Nullable EditorColorsScheme scheme) {
    DiffLineSeparatorRenderer.drawConnectorLine(g, x1, x2, start1, start2, end1 - start1, scheme);
  }

  public static void drawChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color,
                                         boolean doubleLine, boolean dottedLine) {
    if (dottedLine && doubleLine) {
      UIUtil.drawBoldDottedLine(g, x1, x2, y - 1, null, color, false);
      UIUtil.drawBoldDottedLine(g, x1, x2, y, null, color, false);
    }
    else if (dottedLine) {
      UIUtil.drawBoldDottedLine(g, x1, x2, y - 1, null, color, false);
    }
    else if (doubleLine) {
      UIUtil.drawLine(g, x1, y, x2, y, null, color);
      UIUtil.drawLine(g, x1, y + 1, x2, y + 1, null, color);
    }
    else {
      UIUtil.drawLine(g, x1, y, x2, y, null, color);
    }
  }

  public static void drawTrapezium(@NotNull Graphics2D g,
                                   int x1, int x2,
                                   int start1, int end1,
                                   int start2, int end2,
                                   @Nullable Color fillColor,
                                   @Nullable Color borderColor) {
    if (fillColor != null) {
      final int[] xPoints = new int[]{x1, x2, x2, x1};
      final int[] yPoints = new int[]{start1, start2, end2 + 1, end1 + 1};

      g.setColor(fillColor);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }

    if (borderColor != null) {
      g.setColor(borderColor);
      g.drawLine(x1, start1, x2, start2);
      g.drawLine(x1, end1, x2, end2);
    }
  }

  public static void drawCurveTrapezium(@NotNull Graphics2D g,
                                        int x1, int x2,
                                        int start1, int end1,
                                        int start2, int end2,
                                        @Nullable Color fillColor,
                                        @Nullable Color borderColor) {
    Shape upperCurve = makeCurve(x1, x2, start1, start2, true);
    Shape lowerCurve = makeCurve(x1, x2, end1 + 1, end2 + 1, false);
    Shape lowerCurveBorder = makeCurve(x1, x2, end1, end2, false);

    if (fillColor != null) {
      Path2D path = new Path2D.Double();
      path.append(upperCurve, true);
      path.append(lowerCurve, true);

      g.setColor(fillColor);
      g.fill(path);
    }

    if (borderColor != null) {
      g.setColor(borderColor);
      g.draw(upperCurve);
      g.draw(lowerCurveBorder);
    }
  }

  private static Shape makeCurve(int x1, int x2, int y1, int y2, boolean forward) {
    int width = x2 - x1;
    if (forward) {
      return new CubicCurve2D.Double(x1, y1,
                                     x1 + width * CTRL_PROXIMITY_X, y1,
                                     x1 + width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     x1 + width, y2);
    }
    else {
      return new CubicCurve2D.Double(x1 + width, y2,
                                     x1 + width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     x1 + width * CTRL_PROXIMITY_X, y1,
                                     x1, y1);
    }
  }

  //
  // Impl
  //

  public static int lineToY(@NotNull Editor editor, int line) {
    Document document = editor.getDocument();
    if (line >= getLineCount(document)) {
      int y = lineToY(editor, getLineCount(document) - 1);
      return y + editor.getLineHeight() * (line - getLineCount(document) + 1);
    }
    return editor.logicalPositionToXY(editor.offsetToLogicalPosition(document.getLineStartOffset(line))).y;
  }

  @NotNull
  private static TextAttributes getTextAttributes(@NotNull final TextDiffType type,
                                                  @Nullable final Editor editor,
                                                  final boolean ignored) {
    return new TextAttributes() {
      @Override
      public Color getBackgroundColor() {
        return ignored ? type.getIgnoredColor(editor) : type.getColor(editor);
      }
    };
  }

  @NotNull
  private static TextAttributes getStripeTextAttributes(@NotNull final TextDiffType type,
                                                        @NotNull final Editor editor) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return type.getMarkerColor(editor);
      }
    };
  }

  private static void installEmptyRangeRenderer(@NotNull RangeHighlighter highlighter,
                                                @NotNull TextDiffType type) {
    highlighter.setCustomRenderer(new DiffEmptyHighlighterRenderer(type));
  }

  @NotNull
  private static LineSeparatorRenderer createDiffLineRenderer(@NotNull final Editor editor,
                                                              @NotNull final TextDiffType type,
                                                              final boolean doubleLine,
                                                              final boolean resolved) {
    return new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        // TODO: change LineSeparatorRenderer interface ?
        Rectangle clip = g.getClipBounds();
        x2 = clip.x + clip.width;
        drawChunkBorderLine((Graphics2D)g, x1, x2, y, type.getColor(editor), doubleLine, resolved);
      }
    };
  }

  //
  // Highlighters
  //

  // TODO: desync of range and 'border' line markers on typing

  @NotNull
  public static List<RangeHighlighter> createHighlighter(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type,
                                                         boolean ignored) {
    return new LineHighlighterBuilder(editor, startLine, endLine, type).withIgnored(ignored).done();
  }

  @NotNull
  public static List<RangeHighlighter> createHighlighter(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type,
                                                         boolean ignored, boolean resolved, boolean hideWithoutLineNumbers) {
    return new LineHighlighterBuilder(editor, startLine, endLine, type).withIgnored(ignored).withResolved(resolved)
      .withHideWithoutLineNumbers(hideWithoutLineNumbers).done();
  }

  @NotNull
  public static List<RangeHighlighter> createInlineHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type) {
    return new InlineHighlighterBuilder(editor, start, end, type).done();
  }

  @NotNull
  public static List<RangeHighlighter> createLineMarker(@NotNull final Editor editor, int line1, int line2,
                                                        @NotNull final TextDiffType type, final boolean resolved) {
    if (line1 == line2) {
      if (line1 == 0) return Collections.emptyList();
      return createLineMarker(editor, line1 - 1, type, SeparatorPlacement.BOTTOM, true, resolved);
    }
    else {
      return ContainerUtil.concat(
        createLineMarker(editor, line1, type, SeparatorPlacement.TOP, false, resolved),
        createLineMarker(editor, line2 - 1, type, SeparatorPlacement.BOTTOM, false, resolved)
      );
    }
  }

  @NotNull
  public static List<RangeHighlighter> createLineMarker(@NotNull Editor editor, int line, @NotNull final TextDiffType type,
                                                        @NotNull final SeparatorPlacement placement) {
    return new LineMarkerBuilder(editor, line, placement).withType(type).doneDefaultRenderer();
  }

  @NotNull
  private static List<RangeHighlighter> createLineMarker(@NotNull final Editor editor, int line, @NotNull final TextDiffType type,
                                                         @NotNull final SeparatorPlacement placement,
                                                         final boolean doubleLine, final boolean resolved) {
    return new LineMarkerBuilder(editor, line, placement).withType(type).withResolved(resolved).doneDefaultRenderer(doubleLine);
  }

  @NotNull
  public static List<RangeHighlighter> createBorderLineMarker(@NotNull final Editor editor, int line,
                                                              @NotNull final SeparatorPlacement placement) {
    return new LineMarkerBuilder(editor, line, placement).withRenderer(BORDER_LINE_RENDERER).done();
  }

  @NotNull
  public static List<RangeHighlighter> createLineSeparatorHighlighter(@NotNull Editor editor,
                                                                      int offset1,
                                                                      int offset2,
                                                                      @NotNull BooleanGetter condition) {
    RangeHighlighter marker = editor.getMarkupModel()
      .addRangeHighlighter(offset1, offset2, LINE_MARKER_LAYER, null, HighlighterTargetArea.LINES_IN_RANGE);

    DiffLineSeparatorRenderer renderer = new DiffLineSeparatorRenderer(editor, condition);
    marker.setLineSeparatorPlacement(SeparatorPlacement.TOP);
    marker.setLineSeparatorRenderer(renderer);
    marker.setLineMarkerRenderer(renderer);

    return Collections.singletonList(marker);
  }

  private static class LineHighlighterBuilder {
    @NotNull private final Editor editor;
    @NotNull private final TextDiffType type;
    private final int startLine;
    private final int endLine;

    private boolean ignored = false;
    private boolean resolved = false;
    private boolean hideWithoutLineNumbers = false;

    private LineHighlighterBuilder(@NotNull Editor editor, int startLine, int endLine, @NotNull TextDiffType type) {
      this.editor = editor;
      this.type = type;
      this.startLine = startLine;
      this.endLine = endLine;
    }

    @NotNull
    public LineHighlighterBuilder withIgnored(boolean ignored) {
      this.ignored = ignored;
      return this;
    }

    @NotNull
    public LineHighlighterBuilder withResolved(boolean resolved) {
      this.resolved = resolved;
      return this;
    }

    public LineHighlighterBuilder withHideWithoutLineNumbers(boolean hideWithoutLineNumbers) {
      this.hideWithoutLineNumbers = hideWithoutLineNumbers;
      return this;
    }

    @NotNull
    public List<RangeHighlighter> done() {
      boolean isEmptyRange = startLine == endLine;
      boolean isLastLine = endLine == getLineCount(editor.getDocument());

      TextRange offsets = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine);
      int start = offsets.getStartOffset();
      int end = offsets.getEndOffset();

      TextAttributes attributes = isEmptyRange || resolved ? null : getTextAttributes(type, editor, ignored);
      TextAttributes stripeAttributes = isEmptyRange || resolved ? null : getStripeTextAttributes(type, editor);

      RangeHighlighter highlighter = editor.getMarkupModel()
        .addRangeHighlighter(start, end, DEFAULT_LAYER, attributes, HighlighterTargetArea.LINES_IN_RANGE);

      highlighter.setLineMarkerRenderer(new DiffLineMarkerRenderer(highlighter, type, ignored, resolved,
                                                                   hideWithoutLineNumbers, isEmptyRange, isLastLine));

      if (stripeAttributes == null) return Collections.singletonList(highlighter);

      RangeHighlighter stripeHighlighter = editor.getMarkupModel()
        .addRangeHighlighter(start, end, STRIPE_LAYER, stripeAttributes, HighlighterTargetArea.LINES_IN_RANGE);

      return ContainerUtil.list(highlighter, stripeHighlighter);
    }
  }

  private static class InlineHighlighterBuilder {
    @NotNull private final Editor editor;
    @NotNull private final TextDiffType type;
    private final int start;
    private final int end;

    private InlineHighlighterBuilder(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type) {
      this.editor = editor;
      this.type = type;
      this.start = start;
      this.end = end;
    }

    @NotNull
    public List<RangeHighlighter> done() {
      TextAttributes attributes = getTextAttributes(type, editor, false);

      RangeHighlighter highlighter = editor.getMarkupModel()
        .addRangeHighlighter(start, end, INLINE_LAYER, attributes, HighlighterTargetArea.EXACT_RANGE);

      if (start == end) installEmptyRangeRenderer(highlighter, type);

      return Collections.singletonList(highlighter);
    }
  }

  private static class LineMarkerBuilder {
    @NotNull private final Editor editor;
    @NotNull private final SeparatorPlacement placement;
    private final int line;

    private boolean resolved = false;
    @Nullable private TextDiffType type;
    @Nullable private LineSeparatorRenderer renderer;

    private LineMarkerBuilder(@NotNull Editor editor, int line, @NotNull SeparatorPlacement placement) {
      this.editor = editor;
      this.line = line;
      this.placement = placement;
    }

    @NotNull
    public LineMarkerBuilder withType(@NotNull TextDiffType type) {
      this.type = type;
      return this;
    }

    @NotNull
    public LineMarkerBuilder withResolved(boolean resolved) {
      this.resolved = resolved;
      return this;
    }

    @NotNull
    public LineMarkerBuilder withRenderer(@NotNull LineSeparatorRenderer renderer) {
      this.renderer = renderer;
      return this;
    }

    @NotNull
    public List<RangeHighlighter> doneDefaultRenderer() {
      return doneDefaultRenderer(false);
    }

    @NotNull
    public List<RangeHighlighter> doneDefaultRenderer(boolean doubleLine) {
      assert type != null;
      this.renderer = createDiffLineRenderer(editor, type, doubleLine, resolved);
      return done();
    }

    @NotNull
    public List<RangeHighlighter> done() {
      // We won't use addLineHighlighter as it will fail to add marker into empty document.
      //RangeHighlighter highlighter = editor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.SELECTION - 1, null);

      int offset = DocumentUtil.getFirstNonSpaceCharOffset(editor.getDocument(), line);
      RangeHighlighter highlighter = editor.getMarkupModel()
        .addRangeHighlighter(offset, offset, LINE_MARKER_LAYER, null, HighlighterTargetArea.LINES_IN_RANGE);

      highlighter.setLineSeparatorPlacement(placement);
      highlighter.setLineSeparatorRenderer(renderer);

      if (type == null || resolved) return Collections.singletonList(highlighter);

      TextAttributes stripeAttributes = getStripeTextAttributes(type, editor);
      RangeHighlighter stripeHighlighter = editor.getMarkupModel()
        .addRangeHighlighter(offset, offset, STRIPE_LAYER, stripeAttributes, HighlighterTargetArea.LINES_IN_RANGE);

      return ContainerUtil.list(highlighter, stripeHighlighter);
    }
  }
}
