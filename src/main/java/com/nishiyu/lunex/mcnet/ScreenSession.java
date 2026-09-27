// 上書き: ScreenSession.java
package com.nishiyu.lunex.mcnet;

import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.webrender.HtmlNode;
import com.nishiyu.lunex.webrender.UIParser;

public class ScreenSession {
    private final String sessionId;
    private final ScreenSessionManager manager;
    private final UIParser uiParser = new UIParser();
    private int width = 3 * (int) ScreenBlockEntity.RESOLUTION;
    private int height = 4 * (int) ScreenBlockEntity.RESOLUTION;
    private UIParser.Document currentDocument;
    private String currentCss = "";
    private RenderSource lastSource;

    public ScreenSession(String sessionId, ScreenSessionManager manager) {
        this.sessionId = sessionId;
        this.manager = manager;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getCurrentCss() {
        return currentCss;
    }

    public void updateSize(int newWidth, int newHeight) {
        // サイズが本当に変わった時だけ更新をブロードキャストする
        if (this.width != newWidth || this.height != newHeight) {
            this.width = newWidth;
            this.height = newHeight;
            if (currentDocument != null) {
                requestReRender();
            }
        }
    }

    public void loadHtml(String html) {
        this.lastSource = new RenderSource(SourceType.STRING, html, "");
        this.currentCss = "";
        this.currentDocument = uiParser.parseDocument(html, "", width, height);
        requestReRender();
    }

    public void loadWithCss(String html, String css) {
        this.lastSource = new RenderSource(SourceType.STRING_WITH_CSS, html, css);
        this.currentCss = css != null ? css : "";
        this.currentDocument = uiParser.parseDocument(html, css, width, height);
        requestReRender();
    }

    public void reload() {
        if (lastSource == null) return;
        switch (lastSource.type) {
            case STRING -> loadHtml(lastSource.data1);
            case STRING_WITH_CSS -> loadWithCss(lastSource.data1, lastSource.data2);
        }
    }

    public void clear() {
        this.currentDocument = null;
        this.lastSource = null;
        this.currentCss = "";
        manager.broadcastDom(this);
    }

    public void requestReRender() {
        if (currentDocument == null) return;
        manager.broadcastDom(this);
    }

    public void patchElement(String elementId, java.util.function.Consumer<HtmlNode> domPatcher) {
        if (currentDocument == null || currentDocument.root == null) return;
        HtmlNode target = currentDocument.root.getElementById(elementId);
        if (target != null) {
            domPatcher.accept(target);
            manager.broadcastDom(this);
        }
    }

    public UIParser.Document getDocument() {
        return currentDocument;
    }

    public enum SourceType {STRING, STRING_WITH_CSS, FILE, URL}

    public record RenderSource(SourceType type, String data1, String data2) {
    }
}