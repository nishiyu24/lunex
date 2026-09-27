package com.nishiyu.lunex.webrender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LayoutBox {
    public HtmlNode node;
    public Map<String, String> style;
    public List<LayoutBox> children = new ArrayList<>();
    public int x, y, w, h;

    public String displayText = "";

    public int mT, mR, mB, mL, pT, pR, pB, pL, bT, bR, bB, bL, gap;
    public boolean isAbsolute = false, isFixed = false, isRelative = false;
    public int zIndex = 0;
    public float fontSize = 1.0f;
    public List<String> textLines = null;
    public List<InlineFragment> inlineFragments = new ArrayList<>();
    public List<GridItem> gridItems = new ArrayList<>();
    public float[] gridColWidths;
    public float[] gridRowHeights;
    public int gridColsCount = 1;
    public int gridRowsCount = 1;

    public LayoutBox(HtmlNode node, Map<String, String> style, Map<String, String> env) {
        this.node = node;
        this.style = style;

        String t = node.text;
        if (t != null) {
            t = t.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ");
            if (env != null) {
                for (Map.Entry<String, String> e : env.entrySet()) {
                    t = t.replace("{{" + e.getKey() + "}}", e.getValue());
                }
            }
        }
        this.displayText = t != null ? t : "";

        if (env != null) {
            for (Map.Entry<String, String> attr : node.attrs.entrySet()) {
                String val = attr.getValue();
                if (val != null && val.contains("{{")) {
                    for (Map.Entry<String, String> e : env.entrySet()) {
                        val = val.replace("{{" + e.getKey() + "}}", e.getValue());
                    }
                    node.attrs.put(attr.getKey(), val);
                }
            }
        }

        String pos = style.getOrDefault("position", "static");

        this.isFixed = pos.equals("fixed");
        this.isAbsolute = pos.equals("absolute") || this.isFixed;
        this.isRelative = pos.equals("relative");
        try {
            if (style.containsKey("z-index")) this.zIndex = Integer.parseInt(style.get("z-index"));
        } catch (Exception ignored) {
        }
    }

    public int getMinContentWidth() {
        if (style.containsKey("min-width")) return CssParser.parseDim(style.get("min-width"), 9999, 1920, 1080);
        if (node.tag.equals("#text")) {
            int maxWordW = 0;
            String text = this.displayText;
            if (style.getOrDefault("font-weight", "").matches("(?i)bold|bolder|700|800|900")) text = "\u00A7l" + text;
            String[] words = text.split("(?<=\\s)|(?=\\s)|(?<=[、。！？.,])");
            for (String word : words)
                if (!word.trim().isEmpty())
                    maxWordW = Math.max(maxWordW, TextMetrics.getDisplayWidth(word, this.fontSize));
            return maxWordW;
        } else if (children.isEmpty()) {
            return this.w > 0 ? this.w : 16;
        } else {
            int max = 0;
            for (LayoutBox c : children)
                if (!c.isAbsolute && !c.style.getOrDefault("display", "block").equals("none"))
                    max = Math.max(max, c.getMinContentWidth() + c.mL + c.mR);
            return max + pL + pR + bL + bR;
        }
    }

    private void computeGridLayout(int tentativeInnerW, int parentW, int parentH, int rootW, int rootH) {
        String[] colDefs = style.getOrDefault("grid-template-columns", "1fr").split("\\s+");
        gridColsCount = colDefs.length;
        gridColWidths = new float[gridColsCount];
        float totalFr = 0;
        int fixedW = 0;
        for (int i = 0; i < gridColsCount; i++) {
            if (colDefs[i].endsWith("fr")) {
                totalFr += Float.parseFloat(colDefs[i].replace("fr", ""));
            } else {
                gridColWidths[i] = CssParser.parseDim(colDefs[i], tentativeInnerW, rootW, rootH);
                fixedW += gridColWidths[i];
            }
        }
        int availableW = Math.max(0, tentativeInnerW - (gridColsCount - 1) * gap - fixedW);
        for (int i = 0; i < gridColsCount; i++) {
            if (colDefs[i].endsWith("fr") && totalFr > 0) {
                gridColWidths[i] = availableW * (Float.parseFloat(colDefs[i].replace("fr", "")) / totalFr);
            }
        }

        gridItems.clear();
        gridRowsCount = 1;
        boolean[][] occupied = new boolean[100][gridColsCount];
        int currentR = 0, currentC = 0;

        for (LayoutBox child : children) {
            if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
            GridItem item = new GridItem();
            item.box = child;

            String gc = child.style.get("grid-column");
            item.colSpan = 1;
            item.colStart = -1;
            if (gc != null) {
                if (gc.contains("/")) {
                    String[] parts = gc.split("/");
                    item.colStart = parseGridLine(parts[0].trim()) - 1;
                    String end = parts[1].trim();
                    if (end.startsWith("span ")) item.colSpan = parseGridLine(end.substring(5));
                    else item.colSpan = Math.max(1, parseGridLine(end) - 1 - item.colStart);
                } else if (gc.startsWith("span ")) {
                    item.colSpan = parseGridLine(gc.substring(5));
                } else {
                    item.colStart = parseGridLine(gc) - 1;
                }
            }
            item.colSpan = Math.min(item.colSpan, gridColsCount);

            String gr = child.style.get("grid-row");
            item.rowSpan = 1;
            item.rowStart = -1;
            if (gr != null) {
                if (gr.contains("/")) {
                    String[] parts = gr.split("/");
                    item.rowStart = parseGridLine(parts[0].trim()) - 1;
                    String end = parts[1].trim();
                    if (end.startsWith("span ")) item.rowSpan = parseGridLine(end.substring(5));
                    else item.rowSpan = Math.max(1, parseGridLine(end) - 1 - item.rowStart);
                } else if (gr.startsWith("span ")) {
                    item.rowSpan = parseGridLine(gr.substring(5));
                } else {
                    item.rowStart = parseGridLine(gr) - 1;
                }
            }

            if (item.rowStart == -1 || item.colStart == -1) {
                while (true) {
                    if (item.colStart != -1) currentC = item.colStart;
                    if (currentC + item.colSpan > gridColsCount) {
                        currentC = 0;
                        currentR++;
                    }

                    boolean fit = true;
                    for (int r = 0; r < item.rowSpan; r++) {
                        for (int c = 0; c < item.colSpan; c++) {
                            int checkR = (item.rowStart != -1 ? item.rowStart : currentR) + r;
                            int checkC = currentC + c;
                            if (checkR < 100 && checkC < gridColsCount && occupied[checkR][checkC]) {
                                fit = false;
                                break;
                            }
                        }
                        if (!fit) break;
                    }
                    if (fit) {
                        if (item.rowStart == -1) item.rowStart = currentR;
                        if (item.colStart == -1) item.colStart = currentC;
                        break;
                    } else currentC++;
                }
            }

            gridRowsCount = Math.max(gridRowsCount, item.rowStart + item.rowSpan);
            for (int r = 0; r < item.rowSpan; r++) {
                for (int c = 0; c < item.colSpan; c++) {
                    if (item.rowStart + r < 100 && item.colStart + c < gridColsCount)
                        occupied[item.rowStart + r][item.colStart + c] = true;
                }
            }
            gridItems.add(item);

            float childGridW = 0;
            for (int c = 0; c < item.colSpan; c++) {
                if (item.colStart + c < gridColsCount) childGridW += gridColWidths[item.colStart + c];
            }
            childGridW += Math.max(0, item.colSpan - 1) * gap;

            int w = (int) childGridW;
            item.box.w = w;

            String oldW = item.box.style.get("width");
            int forcedW = w;
            if (item.box.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                forcedW -= (item.box.pL + item.box.pR + item.box.bL + item.box.bR);
            item.box.style.put("width", Math.max(0, forcedW) + "px");

            item.box.computeSize(w, parentH, false, false, "stretch", false, rootW, rootH);

            if (oldW != null) item.box.style.put("width", oldW);
            else item.box.style.remove("width");
        }

        gridRowHeights = new float[gridRowsCount];
        String[] rowDefs = style.containsKey("grid-template-rows") ? style.get("grid-template-rows").split("\\s+") : new String[0];

        for (int r = 0; r < gridRowsCount; r++) {
            if (r < rowDefs.length && !rowDefs[r].equals("auto") && !rowDefs[r].endsWith("fr")) {
                gridRowHeights[r] = CssParser.parseDim(rowDefs[r], parentH, rootW, rootH);
            } else {
                float maxH = 0;
                for (GridItem item : gridItems) {
                    if (item.rowStart == r && item.rowSpan == 1)
                        maxH = Math.max(maxH, item.box.h + item.box.mT + item.box.mB);
                }
                gridRowHeights[r] = maxH;
            }
        }

        String align = style.getOrDefault("align-items", "stretch");
        if (align.equals("stretch")) {
            for (GridItem item : gridItems) {
                float childGridH = 0;
                for (int r = 0; r < item.rowSpan; r++) {
                    if (item.rowStart + r < gridRowsCount) childGridH += gridRowHeights[item.rowStart + r];
                }
                childGridH += Math.max(0, item.rowSpan - 1) * gap;

                int targetH = (int) childGridH - item.box.mT - item.box.mB;
                if (item.box.h < targetH && !item.box.style.containsKey("height")) {
                    item.box.h = Math.max(0, targetH);
                    String oldH = item.box.style.get("height");
                    int forcedH = item.box.h;
                    if (item.box.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                        forcedH -= (item.box.pT + item.box.pB + item.box.bT + item.box.bB);
                    item.box.style.put("height", Math.max(0, forcedH) + "px");

                    item.box.computeSize(item.box.w, item.box.h, false, false, "stretch", false, rootW, rootH);

                    if (oldH != null) item.box.style.put("height", oldH);
                    else item.box.style.remove("height");
                }
            }
        }
    }

    private int parseGridLine(String val) {
        try {
            return Integer.parseInt(val.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return 1;
        }
    }

    public void computeSize(int parentW, int parentH, boolean isParentFlexRow, boolean isParentFlexColumn, String parentAlignItems, boolean isParentShrink, int rootW, int rootH) {
        if (style.getOrDefault("display", "block").equals("none")) {
            this.w = 0;
            this.h = 0;
            return;
        }
        if (this.isFixed) {
            parentW = rootW;
            parentH = rootH;
        }

        if (style.containsKey("flex")) {
            String flexStr = style.get("flex").trim();
            if (flexStr.equals("auto")) {
                style.putIfAbsent("flex-grow", "1");
                style.putIfAbsent("flex-shrink", "1");
                style.putIfAbsent("flex-basis", "auto");
            } else if (flexStr.equals("none")) {
                style.putIfAbsent("flex-grow", "0");
                style.putIfAbsent("flex-shrink", "0");
                style.putIfAbsent("flex-basis", "auto");
            } else if (flexStr.matches("^[\\d.]+$")) {
                style.putIfAbsent("flex-grow", flexStr);
                style.putIfAbsent("flex-basis", "0%");
            } else {
                String[] parts = flexStr.split("\\s+");
                if (parts.length > 0) style.putIfAbsent("flex-grow", parts[0]);
                if (parts.length > 1 && parts[1].matches("^[\\d.]+$")) style.putIfAbsent("flex-shrink", parts[1]);
                if (parts.length > 2) style.putIfAbsent("flex-basis", parts[2]);
                else if (parts.length == 2 && !parts[1].matches("^[\\d.]+$")) style.putIfAbsent("flex-basis", parts[1]);
            }
        }

        String fs = style.getOrDefault("font-size", "1rem");
        int fsPx = CssParser.parseDim(fs, parentW, rootW, rootH);
        this.fontSize = (fsPx > 0) ? (fsPx / 16.0f) : 1.0f;

        String[] mStr = CssParser.parseBoxEdgesStr(style.get("margin"));
        this.mT = CssParser.parseDim(style.getOrDefault("margin-top", mStr[0]), parentH, rootW, rootH);
        this.mB = CssParser.parseDim(style.getOrDefault("margin-bottom", mStr[2]), parentH, rootW, rootH);
        String mLStr = style.getOrDefault("margin-left", mStr[3]), mRStr = style.getOrDefault("margin-right", mStr[1]);
        boolean autoLeft = mLStr.equals("auto"), autoRight = mRStr.equals("auto");
        this.mL = autoLeft ? 0 : CssParser.parseDim(mLStr, parentW, rootW, rootH);
        this.mR = autoRight ? 0 : CssParser.parseDim(mRStr, parentW, rootW, rootH);

        String[] pStr = CssParser.parseBoxEdgesStr(style.get("padding"));
        this.pT = CssParser.parseDim(style.getOrDefault("padding-top", pStr[0]), parentH, rootW, rootH);
        this.pR = CssParser.parseDim(style.getOrDefault("padding-right", pStr[1]), parentW, rootW, rootH);
        this.pB = CssParser.parseDim(style.getOrDefault("padding-bottom", pStr[2]), parentH, rootW, rootH);
        this.pL = CssParser.parseDim(style.getOrDefault("padding-left", pStr[3]), parentW, rootW, rootH);

        this.bT = CssParser.parseDim(style.getOrDefault("border-top-width", "0"), parentH, rootW, rootH);
        this.bR = CssParser.parseDim(style.getOrDefault("border-right-width", "0"), parentW, rootW, rootH);
        this.bB = CssParser.parseDim(style.getOrDefault("border-bottom-width", "0"), parentH, rootW, rootH);
        this.bL = CssParser.parseDim(style.getOrDefault("border-left-width", "0"), parentW, rootW, rootH);

        this.gap = CssParser.parseDim(style.getOrDefault("gap", "0"), parentW, rootW, rootH);
        boolean isBorderBox = !style.getOrDefault("box-sizing", "border-box").equals("content-box");

        String widthStr = style.getOrDefault("width", "").trim();
        boolean hasWidth = style.containsKey("width") && !widthStr.equals("auto");
        boolean isPercentWidthAndShrinking = hasWidth && widthStr.contains("%") && (isParentFlexRow || isParentShrink);

        int horizontalDecoration = pL + pR + bL + bR, verticalDecoration = pT + pB + bT + bB;
        boolean isFlexBasisZero = style.getOrDefault("flex-basis", "auto").equals("0") || style.getOrDefault("flex-basis", "auto").equals("0%") || style.getOrDefault("flex-basis", "auto").equals("0px");
        String display = style.getOrDefault("display", "block");

        if (hasWidth && !isPercentWidthAndShrinking) {
            this.w = CssParser.parseDim(widthStr, parentW, rootW, rootH);
            if (!isBorderBox) this.w += horizontalDecoration;
        } else if (node.tag.equals("#text")) {
            int limitW = parentW;
            if (style.containsKey("max-width")) {
                int mw = CssParser.parseDim(style.get("max-width"), parentW, rootW, rootH);
                if (mw > 0) limitW = Math.min(limitW, mw);
            }
            if (limitW <= 0) limitW = 9999;
            String text = this.displayText;
            if (style.getOrDefault("font-weight", "").matches("(?i)bold|bolder|700|800|900")) text = "\u00A7l" + text;
            this.textLines = TextMetrics.wrapText(text, limitW, this.fontSize);
            int maxLineW = 0;
            for (String s : textLines) maxLineW = Math.max(maxLineW, TextMetrics.getDisplayWidth(s, this.fontSize));
            this.w = maxLineW + horizontalDecoration;

        } else if (node.tag.equals("img")) {
            // ★修正: タグがimgであっても src に従ってアイテム(effect)としてサイズ計算する
            String src = node.attrs.getOrDefault("src", "");
            if (src.startsWith("http://") || src.startsWith("https://")) {
                int[] imgSize = ServerImageRegistry.getSize(src);
                this.w = (imgSize != null && imgSize[0] > 0) ? imgSize[0] + horizontalDecoration : 32 + horizontalDecoration;
            } else if (src.startsWith("item:") || src.startsWith("effect:")) {
                this.w = 16 + horizontalDecoration;
            } else {
                this.w = 16 + horizontalDecoration;
            }
        } else if (node.tag.equals("input")) {
            int inputW = 120;
            String type = node.attrs.getOrDefault("type", "text");
            if (type.equals("checkbox") || type.equals("radio")) inputW = 16;
            else if (type.equals("range")) inputW = 120;
            else if (node.attrs.containsKey("size")) {
                try {
                    inputW = Integer.parseInt(node.attrs.get("size")) * 8;
                } catch (Exception ignored) {
                }
            }
            this.w = inputW + horizontalDecoration;
        } else if (isAbsolute || isParentFlexRow || isParentShrink || display.equals("inline-block") || display.equals("inline")) {
            if (isParentFlexRow && isFlexBasisZero && !hasWidth) this.w = horizontalDecoration;
            else this.w = -1;
        } else if (isParentFlexColumn && !parentAlignItems.equals("stretch")) {
            this.w = -1;
        } else {
            this.w = Math.max(0, parentW - mL - mR);
        }

        if (this.w != -1 && style.containsKey("max-width")) {
            String maxWStr = style.get("max-width");
            if (!(maxWStr.contains("%") && isParentShrink)) {
                int maxW = CssParser.parseDim(maxWStr, parentW, rootW, rootH);
                if (!isBorderBox) maxW += horizontalDecoration;
                this.w = Math.min(this.w, maxW);
            }
        }
        if (this.w != -1 && style.containsKey("min-width")) {
            int minW = CssParser.parseDim(style.get("min-width"), parentW, rootW, rootH);
            if (!isBorderBox) minW += horizontalDecoration;
            this.w = Math.max(this.w, minW);
        }

        int tentativeInnerW = this.w == -1 ? Math.max(0, parentW - mL - mR - horizontalDecoration) : Math.max(0, this.w - horizontalDecoration);

        if (display.equals("grid")) {
            computeGridLayout(tentativeInnerW, parentW, parentH, rootW, rootH);

            int totalH = 0;
            for (GridItem item : gridItems) {
                float itemY = item.box.mT + item.box.mB + item.box.h;
                for (int r = 0; r < item.rowStart; r++) itemY += gridRowHeights[r] + gap;
                totalH = Math.max(totalH, (int) itemY);
            }

            if (!style.containsKey("height") || style.getOrDefault("height", "").trim().equals("auto")) {
                this.h = Math.max(this.h, totalH + verticalDecoration);
            }
            if (this.w == -1) {
                int totalW = 0;
                for (int c = 0; c < gridColsCount; c++) totalW += gridColWidths[c];
                totalW += Math.max(0, gridColsCount - 1) * gap;
                this.w = totalW + horizontalDecoration;
            }
        }

        boolean isFlexRow = display.equals("flex") && style.getOrDefault("flex-direction", "row").equals("row");
        boolean isFlexColumn = display.equals("flex") && style.getOrDefault("flex-direction", "row").equals("column");
        String alignItems = style.getOrDefault("align-items", "stretch");

        int maxChildW = 0, sumChildW = 0, activeChildren = 0;

        if (!display.equals("grid")) {
            for (LayoutBox child : children) {
                if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                child.computeSize(tentativeInnerW, parentH, isFlexRow, isFlexColumn, alignItems, this.w == -1, rootW, rootH);
                maxChildW = Math.max(maxChildW, child.w + child.mL + child.mR);
                sumChildW += child.w + child.mL + child.mR;
                activeChildren++;
            }

            if (this.w == -1) {
                if (isFlexRow)
                    this.w = sumChildW + (activeChildren > 1 ? gap * (activeChildren - 1) : 0) + horizontalDecoration;
                else this.w = maxChildW + horizontalDecoration;
            }
        }

        if (style.containsKey("max-width")) {
            String maxWStr = style.get("max-width");
            if (!(maxWStr.contains("%") && isParentShrink)) {
                int maxW = CssParser.parseDim(maxWStr, parentW, rootW, rootH);
                if (!isBorderBox) maxW += horizontalDecoration;
                this.w = Math.min(this.w, maxW);
            }
        }

        if (!isAbsolute) {
            if (autoLeft && autoRight) {
                this.mL = Math.max(0, (parentW - this.w) / 2);
                this.mR = Math.max(0, parentW - this.w - this.mL);
            } else if (autoLeft) this.mL = Math.max(0, parentW - this.w - this.mR);
            else if (autoRight) this.mR = Math.max(0, parentW - this.w - this.mL);
        }

        float lh = 1.375f;
        if (style.containsKey("line-height")) {
            try {
                lh = Float.parseFloat(style.get("line-height"));
            } catch (Exception e) {
                if (style.get("line-height").endsWith("px"))
                    lh = Float.parseFloat(style.get("line-height").replace("px", "")) / 16.0f;
            }
        }

        String heightStr = style.getOrDefault("height", "").trim();
        if (style.containsKey("height") && !heightStr.equals("auto")) {
            this.h = CssParser.parseDim(heightStr, parentH, rootW, rootH);
            if (!isBorderBox) this.h += verticalDecoration;
        } else if (style.containsKey("aspect-ratio") && this.w > 0) {
            try {
                int innerW = Math.max(0, this.w - horizontalDecoration);
                if (style.get("aspect-ratio").contains("/")) {
                    String[] parts = style.get("aspect-ratio").split("/");
                    float rw = Float.parseFloat(parts[0].trim()), rh = Float.parseFloat(parts[1].trim());
                    if (rw > 0) this.h = (int) (innerW * (rh / rw)) + verticalDecoration;
                } else {
                    float ratio = Float.parseFloat(style.get("aspect-ratio"));
                    if (ratio > 0) this.h = (int) (innerW / ratio) + verticalDecoration;
                }
            } catch (Exception ignored) {
                this.h = this.w;
            }
        } else if (node.tag.equals("#text")) {
            int lineCount = this.textLines != null && !this.textLines.isEmpty() ? this.textLines.size() : 1;
            this.h = (Math.max(0, lineCount - 1) * (int) (16 * this.fontSize * lh)) + (int) (16 * this.fontSize) + verticalDecoration;
        } else if (node.tag.equals("img")) {
            String src = node.attrs.getOrDefault("src", "");
            if (src.startsWith("http://") || src.startsWith("https://")) {
                int[] imgSize = ServerImageRegistry.getSize(src);
                if (imgSize != null && imgSize[0] > 0 && imgSize[1] > 0) {
                    if (this.w > 0 && (!style.containsKey("height") || heightStr.equals("auto")))
                        this.h = (int) (Math.max(0, this.w - horizontalDecoration) * ((float) imgSize[1] / imgSize[0])) + verticalDecoration;
                    else this.h = imgSize[1] + verticalDecoration;
                } else this.h = 32 + verticalDecoration;
                // ★修正: "effect:" から始まる場合も追加
            } else if (src.startsWith("item:") || src.startsWith("effect:")) {
                this.h = 16 + verticalDecoration;
            } else {
                this.h = 16 + verticalDecoration;
            }
        } else if (node.tag.equals("input")) {
            this.h = 16 + verticalDecoration;
            if (node.tag.equals("input") && !node.attrs.getOrDefault("type", "text").equals("checkbox") && !node.attrs.getOrDefault("type", "text").equals("radio") && !node.attrs.getOrDefault("type", "text").equals("range")) {
                this.h = (int) (16 * this.fontSize) + 8 + verticalDecoration;
            }
        } else if (!display.equals("grid")) {
            int totalH = 0;
            String flexWrap = style.getOrDefault("flex-wrap", "nowrap");
            if (display.equals("flex") && isFlexRow && flexWrap.equals("wrap")) {
                int currentLineW = 0, currentLineH = 0, actualInnerW = Math.max(0, this.w - horizontalDecoration);
                for (LayoutBox child : children) {
                    if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                    int childOuterW = child.w + child.mL + child.mR, childOuterH = child.h + child.mT + child.mB;
                    if (currentLineW + childOuterW > actualInnerW && currentLineW > 0) {
                        totalH += currentLineH + gap;
                        currentLineW = 0;
                        currentLineH = 0;
                    }
                    currentLineW += childOuterW + gap;
                    currentLineH = Math.max(currentLineH, childOuterH);
                }
                totalH += currentLineH;
            } else if (display.equals("flex") && isFlexRow) {
                for (LayoutBox child : children)
                    if (!child.isAbsolute && !child.style.getOrDefault("display", "block").equals("none"))
                        totalH = Math.max(totalH, child.h + child.mT + child.mB);
            } else {
                for (LayoutBox child : children) {
                    if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                    totalH += child.h + child.mT + child.mB;
                }
                if (activeChildren > 1) totalH += gap * (activeChildren - 1);
            }
            this.h = totalH + verticalDecoration;
        }

        if (style.containsKey("min-height")) {
            int minH = CssParser.parseDim(style.get("min-height"), parentH, rootW, rootH);
            if (!isBorderBox) minH += verticalDecoration;
            this.h = Math.max(this.h, minH);
        }

        int actualInnerW = Math.max(0, this.w - horizontalDecoration), actualInnerH = Math.max(0, this.h - verticalDecoration);

        if (display.equals("flex")) {
            String align = style.getOrDefault("align-items", "stretch"), flexWrap = style.getOrDefault("flex-wrap", "nowrap");
            if (align.equals("stretch") && flexWrap.equals("nowrap")) {
                for (LayoutBox child : children) {
                    if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                    boolean stretched = false;
                    String oldProp = null;
                    if (isFlexRow && !child.style.containsKey("height")) {
                        int targetH = actualInnerH - child.mT - child.mB;
                        if (child.h < targetH) {
                            child.h = Math.max(0, targetH);
                            stretched = true;
                            oldProp = child.style.get("height");
                            int forcedH = child.h;
                            if (child.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                                forcedH -= (child.pT + child.pB + child.bT + child.bB);
                            child.style.put("height", Math.max(0, forcedH) + "px");
                        }
                    } else if (isFlexColumn && !child.style.containsKey("width")) {
                        int targetW = actualInnerW - child.mL - child.mR;
                        if (child.w < targetW) {
                            child.w = Math.max(0, targetW);
                            stretched = true;
                            oldProp = child.style.get("width");
                            int forcedW = child.w;
                            if (child.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                                forcedW -= (child.pL + child.pR + child.bL + child.bR);
                            child.style.put("width", Math.max(0, forcedW) + "px");
                        }
                    }
                    if (stretched) {
                        child.computeSize(child.w, child.h, false, false, "stretch", false, rootW, rootH);
                        if (isFlexRow) {
                            if (oldProp != null) child.style.put("height", oldProp);
                            else child.style.remove("height");
                        } else {
                            if (oldProp != null) child.style.put("width", oldProp);
                            else child.style.remove("width");
                        }
                    }
                }
            }

            float totalGrowW = 0, totalGrowH = 0;
            int usedW = 0, usedH = 0;
            for (LayoutBox child : children) {
                if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                if (isFlexRow) {
                    usedW += child.w + child.mL + child.mR;
                    if (child.style.containsKey("flex-grow"))
                        totalGrowW += Float.parseFloat(child.style.get("flex-grow"));
                } else if (isFlexColumn) {
                    usedH += child.h + child.mT + child.mB;
                    if (child.style.containsKey("flex-grow"))
                        totalGrowH += Float.parseFloat(child.style.get("flex-grow"));
                }
            }
            if (activeChildren > 1) {
                if (isFlexRow) usedW += gap * (activeChildren - 1);
                if (isFlexColumn) usedH += gap * (activeChildren - 1);
            }

            if (isFlexRow && flexWrap.equals("nowrap") && totalGrowW > 0 && actualInnerW > usedW) {
                int extraW = Math.max(0, actualInnerW - usedW);
                for (LayoutBox child : children) {
                    if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                    if (child.style.containsKey("flex-grow")) {
                        float grow = Float.parseFloat(child.style.get("flex-grow"));
                        child.w += (int) (extraW * (grow / totalGrowW));
                        String oldW = child.style.get("width");
                        int forcedW = child.w;
                        if (child.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                            forcedW -= (child.pL + child.pR + child.bL + child.bR);
                        child.style.put("width", Math.max(0, forcedW) + "px");
                        child.computeSize(child.w, child.h, false, false, "stretch", false, rootW, rootH);
                        if (oldW != null) child.style.put("width", oldW);
                        else child.style.remove("width");
                    }
                }
            } else if (isFlexRow && flexWrap.equals("nowrap") && actualInnerW < usedW) {
                String overflowX = style.getOrDefault("overflow-x", style.getOrDefault("overflow", "visible"));
                if (!overflowX.equals("scroll") && !overflowX.equals("auto")) {
                    int deficitW = usedW - actualInnerW;
                    boolean[] frozen = new boolean[children.size()];
                    int loopCount = 0;
                    while (deficitW > 0 && loopCount < 10) {
                        float currentTotalShrinkW = 0;
                        for (int i = 0; i < children.size(); i++) {
                            LayoutBox child = children.get(i);
                            if (frozen[i] || child.isAbsolute || child.style.getOrDefault("display", "block").equals("none"))
                                continue;
                            float shrink = Float.parseFloat(child.style.getOrDefault("flex-shrink", "1"));
                            currentTotalShrinkW += shrink * child.w;
                        }
                        if (currentTotalShrinkW <= 0) break;

                        int remainingDeficit = 0;
                        for (int i = 0; i < children.size(); i++) {
                            LayoutBox child = children.get(i);
                            if (frozen[i] || child.isAbsolute || child.style.getOrDefault("display", "block").equals("none"))
                                continue;
                            float shrink = Float.parseFloat(child.style.getOrDefault("flex-shrink", "1"));
                            if (shrink > 0) {
                                int shrinkAmount = (int) (deficitW * ((shrink * child.w) / currentTotalShrinkW));
                                int minW = child.getMinContentWidth();
                                if (child.w - shrinkAmount <= minW) {
                                    remainingDeficit += shrinkAmount - (child.w - minW);
                                    child.w = minW;
                                    frozen[i] = true;
                                } else {
                                    child.w -= shrinkAmount;
                                }
                            }
                        }
                        deficitW = remainingDeficit;
                        loopCount++;
                    }
                    for (LayoutBox child : children) {
                        if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                        String oldW = child.style.get("width");
                        int forcedW = child.w;
                        if (child.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                            forcedW -= (child.pL + child.pR + child.bL + child.bR);
                        child.style.put("width", Math.max(0, forcedW) + "px");
                        child.computeSize(child.w, -1, false, false, "stretch", false, rootW, rootH);
                        if (oldW != null) child.style.put("width", oldW);
                        else child.style.remove("width");
                    }
                }
            } else if (isFlexColumn && flexWrap.equals("nowrap") && totalGrowH > 0 && actualInnerH > usedH) {
                int extraH = Math.max(0, actualInnerH - usedH);
                for (LayoutBox child : children) {
                    if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                    if (child.style.containsKey("flex-grow")) {
                        float grow = Float.parseFloat(child.style.get("flex-grow"));
                        child.h += (int) (extraH * (grow / totalGrowH));
                        String oldH = child.style.get("height");
                        int forcedH = child.h;
                        if (child.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                            forcedH -= (child.pT + child.pB + child.bT + child.bB);
                        child.style.put("height", Math.max(0, forcedH) + "px");
                        child.computeSize(child.w, child.h, false, false, "stretch", false, rootW, rootH);
                        if (oldH != null) child.style.put("height", oldH);
                        else child.style.remove("height");
                    }
                }
            } else if (isFlexColumn && flexWrap.equals("nowrap") && actualInnerH < usedH) {
                String overflowY = style.getOrDefault("overflow-y", style.getOrDefault("overflow", "visible"));
                if (!overflowY.equals("scroll") && !overflowY.equals("auto")) {
                    int deficitH = usedH - actualInnerH;
                    boolean[] frozen = new boolean[children.size()];
                    int loopCount = 0;
                    while (deficitH > 0 && loopCount < 10) {
                        float currentTotalShrinkH = 0;
                        for (int i = 0; i < children.size(); i++) {
                            LayoutBox child = children.get(i);
                            if (frozen[i] || child.isAbsolute || child.style.getOrDefault("display", "block").equals("none"))
                                continue;
                            float shrink = Float.parseFloat(child.style.getOrDefault("flex-shrink", "1"));
                            currentTotalShrinkH += shrink * child.h;
                        }
                        if (currentTotalShrinkH <= 0) break;

                        int remainingDeficit = 0;
                        for (int i = 0; i < children.size(); i++) {
                            LayoutBox child = children.get(i);
                            if (frozen[i] || child.isAbsolute || child.style.getOrDefault("display", "block").equals("none"))
                                continue;
                            float shrink = Float.parseFloat(child.style.getOrDefault("flex-shrink", "1"));
                            if (shrink > 0) {
                                int shrinkAmount = (int) (deficitH * ((shrink * child.h) / currentTotalShrinkH));
                                int minH = 24;
                                if (child.h - shrinkAmount <= minH) {
                                    remainingDeficit += shrinkAmount - (child.h - minH);
                                    child.h = minH;
                                    frozen[i] = true;
                                } else {
                                    child.h -= shrinkAmount;
                                }
                            }
                        }
                        deficitH = remainingDeficit;
                        loopCount++;
                    }
                    for (LayoutBox child : children) {
                        if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                        String oldH = child.style.get("height");
                        int forcedH = child.h;
                        if (child.style.getOrDefault("box-sizing", "border-box").equals("content-box"))
                            forcedH -= (child.pT + child.pB + child.bT + child.bB);
                        child.style.put("height", Math.max(0, forcedH) + "px");
                        child.computeSize(child.w, child.h, false, false, "stretch", false, rootW, rootH);
                        if (oldH != null) child.style.put("height", oldH);
                        else child.style.remove("height");
                    }
                }
            }
        }

        for (LayoutBox child : children) {
            if (child.isAbsolute && !child.style.getOrDefault("display", "block").equals("none")) {
                child.computeSize(actualInnerW, actualInnerH, false, false, "stretch", false, rootW, rootH);
            }
        }

        if (!display.equals("grid") && (!style.containsKey("height") || style.get("height").equals("auto"))) {
            int contentH = 0;
            if (display.equals("flex")) {
                if (isFlexRow) {
                    for (LayoutBox child : children) {
                        if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                        contentH = Math.max(contentH, child.h + child.mT + child.mB);
                    }
                } else if (isFlexColumn) {
                    int activeCountLocal = 0;
                    for (LayoutBox child : children) {
                        if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                        contentH += child.h + child.mT + child.mB;
                        activeCountLocal++;
                    }
                    if (activeCountLocal > 1) contentH += gap * (activeCountLocal - 1);
                }
            } else {
                for (LayoutBox child : children) {
                    if (child.isAbsolute || child.style.getOrDefault("display", "block").equals("none")) continue;
                    contentH += child.h + child.mT + child.mB;
                }
            }
            this.h = Math.max(this.h, contentH + verticalDecoration);
        }
    }

    public void layout(int parentX, int parentY, int parentOuterW, int parentOuterH, int rootW, int rootH) {
        if (style.getOrDefault("display", "block").equals("none")) return;
        int refX = this.isFixed ? 0 : parentX;
        int refY = this.isFixed ? 0 : parentY;
        int refW = this.isFixed ? rootW : parentOuterW;
        int refH = this.isFixed ? rootH : parentOuterH;

        if (this.isAbsolute) {
            if (style.containsKey("right"))
                this.x = refX + refW - this.w - CssParser.parseDim(style.get("right"), refW, rootW, rootH) - mR;
            else this.x = refX + CssParser.parseDim(style.getOrDefault("left", "0"), refW, rootW, rootH) + mL;
            if (style.containsKey("bottom"))
                this.y = refY + refH - this.h - CssParser.parseDim(style.get("bottom"), refH, rootW, rootH) - mB;
            else this.y = refY + CssParser.parseDim(style.getOrDefault("top", "0"), refH, rootW, rootH) + mT;
        } else {
            this.x = refX + mL;
            this.y = refY + mT;
        }

        int scrollX = 0, scrollY = 0;
        try {
            if (node.attrs.containsKey("data-scroll-x"))
                scrollX = (int) Float.parseFloat(node.attrs.get("data-scroll-x"));
            if (node.attrs.containsKey("data-scroll-y"))
                scrollY = (int) Float.parseFloat(node.attrs.get("data-scroll-y"));
        } catch (Exception ignored) {
        }

        int innerX = this.x + pL + bL - scrollX;
        int innerY = this.y + pT + bT - scrollY;

        String display = style.getOrDefault("display", "block"), flexDir = style.getOrDefault("flex-direction", "row");
        String flexWrap = style.getOrDefault("flex-wrap", "nowrap"), justify = style.getOrDefault("justify-content", "flex-start"), align = style.getOrDefault("align-items", "stretch");

        List<LayoutBox> flowChildren = new ArrayList<>();
        for (LayoutBox child : children) {
            if (child.style.getOrDefault("display", "block").equals("none")) continue;
            if (child.isAbsolute) child.layout(this.x - scrollX, this.y - scrollY, this.w, this.h, rootW, rootH);
            else flowChildren.add(child);
        }

        if (display.equals("grid")) {
            for (GridItem item : gridItems) {
                float itemX = innerX;
                for (int c = 0; c < item.colStart; c++) itemX += gridColWidths[c] + gap;

                float itemY = innerY;
                for (int r = 0; r < item.rowStart; r++) itemY += gridRowHeights[r] + gap;

                item.box.layout((int) itemX + item.box.mL, (int) itemY + item.box.mT, item.box.w, item.box.h, rootW, rootH);
            }
        } else if (display.equals("flex") && flexDir.equals("row") && flexWrap.equals("wrap")) {
            List<List<LayoutBox>> lines = new ArrayList<>();
            List<LayoutBox> currentLine = new ArrayList<>();
            int currentLineW = 0;
            for (LayoutBox child : flowChildren) {
                int childW = child.w + child.mL + child.mR;
                if (currentLineW + childW > (this.w - pL - pR - bL - bR) && !currentLine.isEmpty()) {
                    lines.add(currentLine);
                    currentLine = new ArrayList<>();
                    currentLineW = 0;
                }
                currentLine.add(child);
                currentLineW += childW + gap;
            }
            if (!currentLine.isEmpty()) lines.add(currentLine);

            int lineY = innerY;
            for (List<LayoutBox> line : lines) {
                int lineH = 0, lineW = 0;
                for (LayoutBox child : line) {
                    lineH = Math.max(lineH, child.h + child.mT + child.mB);
                    lineW += child.w + child.mL + child.mR;
                }
                int activeCount = line.size(), dynamicGap = gap, startX = innerX;
                int totalLineContentW = lineW + gap * Math.max(0, activeCount - 1);

                float containerInnerW = Math.max(totalLineContentW, (this.w - pL - pR - bL - bR));

                if (justify.equals("space-between") && activeCount > 1)
                    dynamicGap = (int) (Math.max(0, containerInnerW - lineW) / (activeCount - 1));
                else if (justify.equals("center"))
                    startX = (int) (startX + Math.max(0, containerInnerW - totalLineContentW) / 2);
                else if (justify.equals("flex-end"))
                    startX = (int) (startX + Math.max(0, containerInnerW - totalLineContentW));

                for (LayoutBox child : line) {
                    int itemY = lineY;
                    if (align.equals("center")) itemY += Math.max(0, lineH - (child.h + child.mT + child.mB)) / 2;
                    else if (align.equals("flex-end")) itemY += Math.max(0, lineH - (child.h + child.mT + child.mB));
                    else if (align.equals("stretch") && !child.style.containsKey("height"))
                        child.h = Math.max(child.h, lineH - child.mT - child.mB);
                    child.layout(startX, itemY, child.w, child.h, rootW, rootH);
                    startX += child.w + child.mL + child.mR + dynamicGap;
                }
                lineY += lineH + gap;
            }
        } else if (display.equals("flex")) {
            boolean isRow = flexDir.equals("row");
            int mainSpace = isRow ? (this.w - pL - pR - bL - bR) : (this.h - pT - pB - bT - bB), mainTotalSize = 0, activeCount = flowChildren.size();
            for (LayoutBox child : flowChildren)
                mainTotalSize += isRow ? (child.w + child.mL + child.mR) : (child.h + child.mT + child.mB);
            int currentMain = isRow ? innerX : innerY, dynamicGap = gap;

            int totalMainSpaceWithGap = mainTotalSize + gap * Math.max(0, activeCount - 1);
            int containerMainSpace = Math.max(totalMainSpaceWithGap, mainSpace);

            if (justify.equals("space-between") && activeCount > 1)
                dynamicGap = Math.max(0, containerMainSpace - mainTotalSize) / (activeCount - 1);
            else if (justify.equals("center"))
                currentMain += Math.max(0, containerMainSpace - totalMainSpaceWithGap) / 2;
            else if (justify.equals("flex-end")) currentMain += Math.max(0, containerMainSpace - totalMainSpaceWithGap);

            for (LayoutBox child : flowChildren) {
                int crossSpace = isRow ? (this.h - pT - pB - bT - bB) : (this.w - pL - pR - bL - bR), crossStart = isRow ? innerY : innerX;
                int childCrossSize = isRow ? (child.h + child.mT + child.mB) : (child.w + child.mL + child.mR);

                if (align.equals("center")) crossStart += Math.max(0, crossSpace - childCrossSize) / 2;
                else if (align.equals("flex-end")) crossStart += Math.max(0, crossSpace - childCrossSize);
                else if (align.equals("stretch")) {
                    if (isRow && !child.style.containsKey("height"))
                        child.h = Math.max(child.h, crossSpace - child.mT - child.mB);
                    else if (!isRow && !child.style.containsKey("width"))
                        child.w = Math.max(child.w, crossSpace - child.mL - child.mR);
                }
                int childX = isRow ? currentMain : crossStart, childY = isRow ? crossStart : currentMain;
                child.layout(childX, childY, child.w, child.h, rootW, rootH);
                currentMain += (isRow ? child.w + child.mL + child.mR : child.h + child.mT + child.mB) + dynamicGap;
            }
        } else {
            String textAlign = style.getOrDefault("text-align", "left");
            int currentY = innerY;
            List<LayoutBox> inlineRun = new ArrayList<>();
            for (LayoutBox child : flowChildren) {
                if (isInlineBox(child)) {
                    inlineRun.add(child);
                } else {
                    if (!inlineRun.isEmpty()) {
                        currentY = layoutInlineContext(inlineRun, innerX, currentY, (this.w - pL - pR - bL - bR), textAlign, rootW, rootH);
                        inlineRun.clear();
                    }
                    if (child.style.getOrDefault("margin-left", "").equals("auto") && child.style.getOrDefault("margin-right", "").equals("auto")) {
                        child.layout(innerX + Math.max(0, ((this.w - pL - pR - bL - bR) - (child.w + child.mL + child.mR)) / 2), currentY, (this.w - pL - pR - bL - bR), child.h, rootW, rootH);
                    } else {
                        child.layout(innerX, currentY, (this.w - pL - pR - bL - bR), child.h, rootW, rootH);
                    }
                    currentY += child.h + child.mT + child.mB + gap;
                }
            }
            if (!inlineRun.isEmpty())
                layoutInlineContext(inlineRun, innerX, currentY, (this.w - pL - pR - bL - bR), textAlign, rootW, rootH);
        }

        if (this.isRelative) {
            int offX = 0, offY = 0;
            if (style.containsKey("left")) offX += CssParser.parseDim(style.get("left"), parentOuterW, rootW, rootH);
            else if (style.containsKey("right"))
                offX -= CssParser.parseDim(style.get("right"), parentOuterW, rootW, rootH);
            if (style.containsKey("top")) offY += CssParser.parseDim(style.get("top"), parentOuterH, rootW, rootH);
            else if (style.containsKey("bottom"))
                offY -= CssParser.parseDim(style.get("bottom"), parentOuterH, rootW, rootH);
            if (offX != 0 || offY != 0) applyRelativeOffset(offX, offY);
        }

        int maxChildRight = this.x + pL + bL;
        int maxChildBottom = this.y + pT + bT;
        for (LayoutBox child : children) {
            if (child.style.getOrDefault("display", "block").equals("none") || child.isAbsolute) continue;
            maxChildRight = Math.max(maxChildRight, child.x + child.w + child.mR + scrollX);
            maxChildBottom = Math.max(maxChildBottom, child.y + child.h + child.mB + scrollY);
        }
        if (display.equals("grid")) {
            for (GridItem item : gridItems) {
                if (item.box.isAbsolute) continue;
                maxChildRight = Math.max(maxChildRight, item.box.x + item.box.w + item.box.mR + scrollX);
                maxChildBottom = Math.max(maxChildBottom, item.box.y + item.box.h + item.box.mB + scrollY);
            }
        }
        int scrollWidth = Math.max(this.w, maxChildRight - this.x + pR + bR);
        int scrollHeight = Math.max(this.h, maxChildBottom - this.y + pB + bB);
        this.node.attrs.put("data-scroll-w", String.valueOf(scrollWidth));
        this.node.attrs.put("data-scroll-h", String.valueOf(scrollHeight));
    }

    private void applyRelativeOffset(int dx, int dy) {
        this.x += dx;
        this.y += dy;
        for (LayoutBox child : children) child.applyRelativeOffset(dx, dy);
        for (InlineFragment frag : inlineFragments) {
            frag.x += dx;
            frag.y += dy;
        }
    }

    private boolean isInlineBox(LayoutBox box) {
        if (box.isAbsolute) return false;
        String display = box.style.getOrDefault("display", "");
        if (display.equals("inline") || display.equals("inline-block")) return true;
        if (display.equals("block") || display.equals("flex") || display.equals("grid")) return false;
        String tag = box.node.tag;
        return tag.equals("#text") || tag.equals("br") || tag.equals("span") || tag.equals("a") || tag.equals("b") || tag.equals("i") || tag.equals("strong") || tag.equals("em") || tag.equals("img") || tag.equals("input") || tag.equals("button");
    }

    private void flattenInlines(List<LayoutBox> boxes, List<FlatRun> runs, List<LayoutBox> parents) {
        for (LayoutBox b : boxes) {
            if (b.node.tag.equals("#text") && b.displayText != null && !b.displayText.isEmpty()) {
                runs.add(new FlatRun(b, true, b.displayText, new ArrayList<>(parents)));
            } else if (b.node.tag.equals("br")) {
                runs.add(new FlatRun(b, false, "\n", new ArrayList<>(parents)));
            } else if (b.style.getOrDefault("display", "").equals("inline-block") || b.style.getOrDefault("display", "").equals("inline") || b.node.tag.equals("img") || b.node.tag.equals("input") || b.node.tag.equals("button")) {
                runs.add(new FlatRun(b, false, null, new ArrayList<>(parents)));
            } else {
                parents.add(b);
                flattenInlines(b.children, runs, parents);
                parents.remove(parents.size() - 1);
            }
        }
    }

    private int layoutInlineContext(List<LayoutBox> inlines, int startX, int startY, int maxW, String textAlign, int rootW, int rootH) {
        List<FlatRun> runs = new ArrayList<>();
        flattenInlines(inlines, runs, new ArrayList<>());
        for (LayoutBox inlineBox : inlines) clearFragmentsRecursively(inlineBox);

        int lineX = 0, lineY = startY, maxLineH = 0;
        List<InlineFragment> currentLine = new ArrayList<>();
        List<List<InlineFragment>> lines = new ArrayList<>();

        for (FlatRun run : runs) {
            float lh = 1.375f;
            if (run.box.style.containsKey("line-height")) {
                try {
                    lh = Float.parseFloat(run.box.style.get("line-height"));
                } catch (Exception e) {
                    if (run.box.style.get("line-height").endsWith("px"))
                        lh = Float.parseFloat(run.box.style.get("line-height").replace("px", "")) / 16f;
                }
            }
            int decoT = 0, decoB = 0, decoL = 0, decoR = 0;
            for (LayoutBox p : run.inlineParents) {
                decoT += p.pT + p.bT;
                decoB += p.pB + p.bB;
                decoL += p.pL + p.bL;
                decoR += p.pR + p.bR;
            }

            if (run.isText) {
                String text = run.text;
                boolean isBold = run.box.style.getOrDefault("font-weight", "").matches("(?i)bold|bolder|700|800|900");
                if (isBold) text = "§l" + text;

                float scale = run.box.fontSize;
                int fontH = (int) (16 * scale), runH = (int) (fontH * lh), totalRunH = runH + decoT + decoB;

                String[] words = text.split("(?<=\\s)|(?=\\s)|(?<=[、。！？.,])");
                lineX += decoL;

                for (String word : words) {
                    if (word.isEmpty()) continue;
                    int wordW = TextMetrics.getDisplayWidth(word, scale);

                    if (lineX + wordW + decoR > maxW + 4 && lineX > decoL && !word.trim().isEmpty()) {
                        lines.add(currentLine);
                        lineY += maxLineH > 0 ? maxLineH : totalRunH;
                        currentLine = new ArrayList<>();
                        lineX = decoL;
                        maxLineH = 0;
                    }
                    InlineFragment frag = new InlineFragment(run.box, lineX, decoT, wordW, runH, word);
                    frag.parents = run.inlineParents;
                    currentLine.add(frag);
                    lineX += wordW;
                    maxLineH = Math.max(maxLineH, totalRunH);
                }
                lineX += decoR;
            } else if ("\n".equals(run.text)) {
                lines.add(currentLine);
                lineY += maxLineH > 0 ? maxLineH : (int) (16 * run.box.fontSize * lh);
                currentLine = new ArrayList<>();
                lineX = 0;
                maxLineH = 0;
            } else {
                int boxOuterW = run.box.w + run.box.mL + run.box.mR, boxOuterH = run.box.h + run.box.mT + run.box.mB;
                if (lineX + boxOuterW > maxW + 4 && lineX > 0) {
                    lines.add(currentLine);
                    lineY += maxLineH > 0 ? maxLineH : boxOuterH;
                    currentLine = new ArrayList<>();
                    lineX = 0;
                    maxLineH = 0;
                }
                InlineFragment frag = new InlineFragment(run.box, lineX + run.box.mL, 0, run.box.w, run.box.h, null);
                frag.parents = run.inlineParents;
                currentLine.add(frag);
                lineX += boxOuterW;
                maxLineH = Math.max(maxLineH, boxOuterH);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
            lineY += maxLineH;
        }

        int currentDrawY = startY;
        for (List<InlineFragment> line : lines) {
            int lineMaxH = 0;
            for (InlineFragment f : line) {
                int decoH = 0;
                if (f.parents != null) for (LayoutBox p : f.parents) decoH += p.pT + p.bT + p.pB + p.bB;
                lineMaxH = Math.max(lineMaxH, f.h + f.box.mT + f.box.mB + decoH);
            }

            int actualLineW = line.isEmpty() ? 0 : (line.get(line.size() - 1).x + line.get(line.size() - 1).w - line.get(0).x);
            int offsetX = startX;
            if (textAlign.equals("center")) offsetX += Math.max(0, (maxW - actualLineW) / 2);
            else if (textAlign.equals("right")) offsetX += Math.max(0, maxW - actualLineW);

            for (InlineFragment f : line) {
                int decoT = 0, decoB = 0, decoL = 0, decoR = 0;
                if (f.parents != null) {
                    for (LayoutBox p : f.parents) {
                        decoT += p.pT + p.bT;
                        decoB += p.pB + p.bB;
                        decoL += p.pL + p.bL;
                        decoR += p.pR + p.bR;
                    }
                }
                int outerH = f.h + f.box.mT + f.box.mB;
                int offsetY = currentDrawY + Math.max(0, (lineMaxH - (outerH + decoT + decoB)) / 2);

                f.x += offsetX;
                f.y = offsetY + decoT + f.box.mT;
                f.box.inlineFragments.add(f);

                if (f.parents != null) {
                    for (LayoutBox p : f.parents) {
                        InlineFragment pFrag = new InlineFragment(p, f.x - decoL, offsetY, f.w + decoL + decoR, f.h + decoT + decoB, null);
                        p.inlineFragments.add(pFrag);
                    }
                }
                if (!f.isText()) f.box.layout(f.x, f.y - f.box.mT, f.w, f.h, rootW, rootH);
            }
            currentDrawY += lineMaxH;
        }
        return currentDrawY;
    }

    private void clearFragmentsRecursively(LayoutBox box) {
        box.inlineFragments.clear();
        for (LayoutBox child : box.children) clearFragmentsRecursively(child);
    }

    public static class GridItem {
        public int colStart, colSpan;
        public int rowStart, rowSpan;
        public LayoutBox box;
    }

    public static class InlineFragment {
        public LayoutBox box;
        public int x, y, w, h;
        public String text;
        public List<LayoutBox> parents;

        public InlineFragment(LayoutBox box, int x, int y, int w, int h, String text) {
            this.box = box;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
        }

        public boolean isText() {
            return text != null;
        }
    }

    private static class FlatRun {
        LayoutBox box;
        boolean isText;
        String text;
        List<LayoutBox> inlineParents;

        public FlatRun(LayoutBox box, boolean isText, String text, List<LayoutBox> inlineParents) {
            this.box = box;
            this.isText = isText;
            this.text = text;
            this.inlineParents = inlineParents;
        }
    }
}