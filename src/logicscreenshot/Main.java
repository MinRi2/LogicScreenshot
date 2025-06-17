package logicscreenshot;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.Group;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.logic.LCanvas.*;
import mindustry.mod.*;

import java.time.*;
import java.time.format.*;

public class Main extends Mod{
    private static Scene scene;

    public Main(){
        Events.on(ClientLoadEvent.class, e -> {
            scene = new Scene();

            LogicDialog logic = Vars.ui.logic;
            logic.shown(() -> {
                logic.buttons.button("@screenshot", Icon.image, Main::logicScreenshot);
            });
        });
    }

    private static void logicScreenshot(){
        DragLayout sts = Vars.ui.logic.canvas.statements;

        Group elem = sts.parent;

        Seq<JumpCurve> jumpCurves = sts.jumps.getChildren().as();
        bestHeight(jumpCurves);
        float jumpHeight = getJumpCurveHeight(jumpCurves);
        for(JumpCurve jumpCurve : jumpCurves){
            jumpCurve.cullable = false;
        }

        float width = elem.getPrefWidth() + jumpHeight;
        float height = elem.getPrefHeight();
        Pixmap pixmap = screenShoot(elem, Tmp.r1.set(0, 0, width, height));

        for(JumpCurve jumpCurve : jumpCurves){
            jumpCurve.cullable = true;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String formattedDateTime = LocalDateTime.now().format(formatter);
        Fi fi = Vars.screenshotDirectory.child("logic_" + formattedDateTime + ".png");
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
        elem.pack();

        float lastX = elem.x;
        float lastY = elem.y;
        elem.setPosition(0, 0);

        int width = (int)area.width;
        int height = (int)area.height;
        int x = (int)area.x;
        int y = (int)area.y;

        FrameBuffer buffer = new FrameBuffer(width, height);
        scene.getViewport().update(width, height, true);
        scene.act();
        buffer.begin(Color.clear);
        scene.draw();
        Draw.flush();
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(x, y, width, height, true);
        buffer.end();
        buffer.dispose();

        elem.setPosition(lastX, lastY);

        scene.clear();
        if(parent != null && lastIndex != -1){
            parent.getChildren().insert(lastIndex, elem);
            elem.parent = parent;
            Reflect.invoke(Element.class, elem, "setScene", new Object[]{parent.getScene()}, Scene.class);
        }

        return pixmap;
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