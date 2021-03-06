package com.zelkatani.conquest.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.zelkatani.conquest.ConquestCamera;
import com.zelkatani.conquest.Player;
import com.zelkatani.conquest.entities.Tile;
import com.zelkatani.conquest.pathfinding.Pathway;

public class Grabber extends InputAdapter {
    private enum Mode {
        FIRST, SECOND, NONE;

        private static Mode[] modes = values();
        public Mode next() {
            return modes[(this.ordinal() + 1) % modes.length];
        }
    }

    private Array<Tile> tiles;
    private Rectangle rect;
    private ConquestCamera cam;

    private Manager manager;
    private Pathway pathway;
    private Player player;

    private float touchX, touchY, mouseX, mouseY;
    private Mode mode;

    public Grabber(Array<Tile> tiles, ConquestCamera cam, Manager manager, Pathway pathway, Player player) {
        this.tiles = tiles;
        rect = new Rectangle();
        this.cam = cam;

        this.manager = manager;
        this.pathway = pathway;
        this.player = player;

        mode = Mode.FIRST;
    }

    public void draw(ShapeRenderer renderer) {
        if (rect.width < 25 * cam.zoom || rect.height < 25 * cam.zoom) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        renderer.setProjectionMatrix(cam.combined);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(new Color(0x228b2255));
        renderer.rect(rect.x, rect.y, rect.width, rect.height);

        renderer.set(ShapeRenderer.ShapeType.Line);
        renderer.setColor(Color.BLACK);
        renderer.rect(rect.x, rect.y, rect.width, rect.height);
        renderer.end();
    }

    public void grab() {
        float width = touchX - mouseX, height = mouseY - touchY;
        float offX = width < 0 ? width : 0, offY = height < 0 ? height : 0;

        Vector3 vector3 = correct(mouseX, mouseY);

        rect.set(vector3.x + offX * cam.zoom, vector3.y + offY * cam.zoom, Math.abs(width * cam.zoom), Math.abs(height * cam.zoom));

        if (rect.width < 10 || rect.height < 10) return;
        for (Tile tile : tiles) {
            tile.setHovered(tile.getOwner() == player && rect.contains(tile.getCenter()));
        }
    }

    private Vector3 correct(float pointX, float pointY) {
        Vector3 vector3 = new Vector3(pointX, pointY, 0);
        cam.unproject(vector3);

        return vector3;
    }

    @Override
    public boolean mouseMoved (int screenX, int screenY) {
        Vector3 vector3 = correct(screenX, screenY);
        for (Tile tile : tiles) {
            tile.setHovered(!tile.isHidden() && tile.getRectangle().contains(vector3.x, vector3.y));
        }

        return false;
    }

    @Override
    public boolean touchDown (int screenX, int screenY, int pointer, int button) {
        touchX = mouseX = screenX;
        touchY = mouseY = screenY;

        Vector3 vector3 = correct(screenX, screenY);

        boolean any = true;
        for (Tile tile : tiles) {
            if (tile.isHidden()) continue;
            if (tile.getOwner() == player || mode.equals(Mode.SECOND)) {
                tile.setHovered(false);
                tile.setSelected(tile.getRectangle().contains(vector3.x, vector3.y));
            }
            if (tile.isSelected()) {
                any = false;
                if (mode.equals(Mode.NONE)) {
                    mode = Mode.FIRST;
                }
            }
        }

        if (any) {
            pathway.clear();
        }

        return false;
    }

    @Override
    public boolean touchUp (int screenX, int screenY, int pointer, int button) {
        mouseX = touchX = screenX;
        mouseY = touchY = screenY;

        Array<Tile> selected = new Array<>();
        Vector3 vector3 = correct(screenX, screenY);

        for (Tile tile : tiles) {
            if (tile.isHidden()) continue;
            if (mode == Mode.SECOND || tile.getOwner() == player) {
                tile.setSelected(rect.contains(tile.getCenter()) || tile.getRectangle().contains(vector3.x, vector3.y));
            }

            if (tile.isSelected()) {
                selected.add(tile);
            }
        }

        if (selected.size == 0) {
            mode = Mode.FIRST;
            return false;
        }

        switch (mode) {
            case FIRST: {
                pathway.setStart(selected);
                break;
            }
            case SECOND: {
                if (pathway.getStart().size != 1 || pathway.getStart().get(0) != selected.get(0)) {
                    for (Tile t : pathway.getStart()) {
                        t.setSelected(true);
                    }

                    pathway.setEnd(selected.get(0));
                    manager.setVisible(true);
                } else {
                    pathway.getStart().get(0).setSelected(false);
                    pathway.clear();
                }
            }
        }

        mode = mode.next();
        return false;
    }

    @Override
    public boolean touchDragged (int screenX, int screenY, int pointer) {
        mouseX = screenX;
        mouseY = screenY;

        if (Math.abs(touchX - mouseX) < 10 || Math.abs(touchY - mouseY) < 10) return false;

        pathway.clear();
        mode = Mode.FIRST;

        for (Tile tile : tiles) {
            if (tile.isHidden() || tile.getOwner() != player) continue;
            tile.setHovered(rect.contains(tile.getCenter()));
        }
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        cam.zoom += 0.05 * amount;
        cam.handle();
        return false;
    }
}
