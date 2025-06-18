package logicscreenshot;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.Group;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.logic.LCanvas.*;
import mindustry.mod.*;
import mindustry.ui.Styles;

import java.time.*;
import java.time.format.*;

public class Main extends Mod{
    private static Fi screenshotFi;
    private static Scene scene;

    public Main(){
        Events.on(ClientLoadEvent.class, e -> {
            screenshotFi = Vars.screenshotDirectory.child("logic");
            scene = new Scene();

            Vars.ui.logic.shown(() -> Core.app.post(Main::injectButton));
        });
    }

    private static void injectButton() {
        Button editButton = Vars.ui.logic.find("edit");
        if(editButton == null){
            Vars.ui.showErrorMessage("@logicscreenshot.incompatible");
            return;
        }

        editButton.addCaptureListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                super.clicked(event, x, y);

                Core.app.post(() -> {
                    Dialog dialog = Core.scene.getDialog();
                    ScrollPane pane = (ScrollPane) dialog.cont.getChildren().find(e -> e instanceof ScrollPane);

                    if(pane == null){
                        Vars.ui.showErrorMessage("@logicscreenshot.incompatible");
                        return;
                    }

                    Element element = pane.getWidget();
                    if(!(element instanceof Table t) || !(t.getChildren().first() instanceof Table table)){
                        Vars.ui.showErrorMessage("@logicscreenshot.incompatible");
                        return;
                    }

                    table.row();
                    table.button("@screenshot", Icon.image, Styles.flatt, Main::logicScreenshot)
                    .marginLeft(12f).tooltip(Core.bundle.format("logicscreenshot.directoryHint", screenshotFi.absolutePath()));
                });
            }
        });

        // bubble listener is out of function.
//        editButton.clicked(() -> {
//
//        });
    }

    private static void logicScreenshot(){
        DragLayout sts = Vars.ui.logic.canvas.statements;

        Group elem = sts.parent;
        elem.pack();

        Seq<JumpCurve> jumpCurves = sts.jumps.getChildren().as();
        bestHeight(jumpCurves);
        float jumpHeight = getJumpCurveHeight(jumpCurves);
        for(JumpCurve jumpCurve : jumpCurves){
            jumpCurve.cullable = false;
        }

        float width = elem.getWidth() + jumpHeight;
        float height = elem.getHeight();
        Pixmap pixmap = screenShoot(elem, Tmp.r1.set(0, 0, width, height));

        for(JumpCurve jumpCurve : jumpCurves){
            jumpCurve.cullable = true;
        }
        elem.invalidateHierarchy();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String formattedDateTime = LocalDateTime.now().format(formatter);
        Fi fi = screenshotFi.child(formattedDateTime + ".png");
        PixmapIO.writePng(fi, pixmap);
        pixmap.dispose();

        Vars.ui.showInfoToast(Core.bundle.format("screenshot.saveHint", fi.absolutePath()), 3);
    }

    private static Pixmap screenShoot(Element elem, Rect area){
        Group parent = elem.parent;
        int lastIndex = -1;

        if(parent != null){
            lastIndex = parent.getChildren().indexOf(elem);
            parent.getChildren().remove(lastIndex);
            elem.parent = null;
        }

        scene.add(elem);
        scene.act();

        float lastX = elem.x;
        float lastY = elem.y;
        elem.setPosition(0, 0);

        int width = (int)area.width;
        int height = (int)area.height;
        int x = (int)area.x;
        int y = (int)area.y;

        int bufferWidth = Math.min(width, Gl.maxTextureSize);
        int bufferHeight = Math.min(height, Gl.maxTextureSize);
        int chunksX = Mathf.ceil((float)width / bufferWidth);
        int chunksY = Mathf.ceil((float)height / bufferHeight);
        Pixmap result = new Pixmap(width, height);

        scene.getViewport().update(bufferWidth, bufferHeight, false);
        FrameBuffer buffer = new FrameBuffer(bufferWidth, bufferHeight);

        for(int ix = 0; ix < chunksX; ix++){
            for(int iy = 0; iy < chunksY; iy++){
                int px = ix * bufferWidth;
                int py = iy * bufferHeight;
                int cw = Math.min(width - (x + px), bufferWidth);
                int ch = Math.min(height - (y + py), bufferHeight);

                scene.getCamera().position.set(px + (float)bufferWidth / 2, py + (float)bufferHeight / 2);

                buffer.begin();
                Gl.clearColor(Color.clear.r, Color.clear.g, Color.clear.b, Color.clear.a);
                Gl.clear(Gl.colorBufferBit);
                scene.draw();
                Draw.flush();
                Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, cw, ch, true);
                buffer.end();

                result.draw(pixmap, px, result.height - py - pixmap.height);
                pixmap.dispose();
            }
        }

        buffer.dispose();

        elem.setPosition(lastX, lastY);

        scene.clear();
        if(parent != null && lastIndex != -1){
            parent.getChildren().insert(lastIndex, elem);
            elem.parent = parent;
            Reflect.invoke(Element.class, elem, "setScene", new Object[]{parent.getScene()}, Scene.class);
        }

        return result;
    }

    private static void bestHeight(Seq<JumpCurve> jumpCurves){
        for(JumpCurve jumpCurve : jumpCurves){
            // height From LCanvas
            float height = Scl.scl(Core.graphics.isPortrait() ? 20f : 40f) + Scl.scl(Core.graphics.isPortrait() ? 8f : 10f) * jumpCurve.predHeight;
            Reflect.set(jumpCurve, "uiHeight", height);
            jumpCurve.markedDone = true;
        }
    }

    private static float getJumpCurveHeight(Seq<JumpCurve> jumpCurves){
        float max = Float.MIN_VALUE;
        for(JumpCurve jumpCurve : jumpCurves){
            max = Math.max(Reflect.get(jumpCurve, "uiHeight"), max);
        }
        return max;
    }
}