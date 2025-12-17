package com.fileexplorer.ui;

import com.fileexplorer.ui.service.CursorService;
import java.util.Objects;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public final class ResizeGrip extends StackPane {

    private static final double SIZE = 24.0;

    private final Canvas canvas;

    private Stage stage;
    private CursorService cursorService;

    private double pressScreenX;
    private double pressScreenY;
    private double pressWidth;
    private double pressHeight;

    private boolean dark;

    public ResizeGrip() {
        this.canvas = new Canvas(SIZE, SIZE);

        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);

        getStyleClass().add("resize-grip");
        getChildren().add(canvas);

        this.stage = null;
        this.cursorService = null;
        this.dark = false;

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);

        redraw();
    }

    public void attach(Stage stage, CursorService cursorService) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.cursorService = cursorService;
        if (cursorService != null) {
            setCursor(cursorService.cursorNwseResize());
        }
    }

    public void setDark(boolean dark) {
        this.dark = dark;
        redraw();
    }

    private void onPressed(MouseEvent e) {
        if (stage == null) return;
        pressScreenX = e.getScreenX();
        pressScreenY = e.getScreenY();
        pressWidth = stage.getWidth();
        pressHeight = stage.getHeight();
        e.consume();
    }

    private void onDragged(MouseEvent e) {
        if (stage == null) return;

        double dx = e.getScreenX() - pressScreenX;
        double dy = e.getScreenY() - pressScreenY;

        double newW = Math.max(stage.getMinWidth(), pressWidth + dx);
        double newH = Math.max(stage.getMinHeight(), pressHeight + dy);

        stage.setWidth(newW);
        stage.setHeight(newH);
        e.consume();
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, SIZE, SIZE);

        Color dot = dark ? Color.rgb(255, 255, 255, 0.35) : Color.rgb(0, 0, 0, 0.30);
        g.setFill(dot);

        double r = 1.25;
        double step = 5.0;
        double baseX = SIZE - 5.0;
        double baseY = SIZE - 5.0;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                double x = baseX - (col * step);
                double y = baseY - (row * step);
                g.fillOval(x - r, y - r, r * 2.0, r * 2.0);
            }
        }
    }
}
