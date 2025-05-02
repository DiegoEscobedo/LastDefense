package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.CollisionResults;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Lemur
import com.simsilica.lemur.*;
import com.simsilica.lemur.style.BaseStyles;
import com.simsilica.lemur.GuiGlobals;

public class Main extends SimpleApplication {

    private boolean left, right, forward, back;

    private int playerHealth = 100;
    private List<Geometry> enemies = new ArrayList<>();
    private BetterCharacterControl playerControl;

    // HUD
    private Label lifeLabel;
    private Container hudContainer;

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setMoveSpeed(10f);
        BulletAppState bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        initKeys();
        initLemur();
        updateHUD();
        inputManager.addMapping("Left",  new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Back", new KeyTrigger(KeyInput.KEY_S));

        inputManager.addListener(actionListener1, "Left", "Right", "Forward", "Back");

        // Crear un nodo para el jugador
        Node playerNode = new Node("Player");
        rootNode.attachChild(playerNode);

        // Añadir control de personaje al nodo del jugador
        playerControl = new BetterCharacterControl(1.5f, 6f, 80f); // ancho, alto, masa
        playerControl.setGravity(new Vector3f(0, -20f, 0)); // gravedad personalizada
        playerNode.addControl(playerControl);

        // Añadir el control de física a bulletAppState
        bulletAppState.getPhysicsSpace().add(playerControl);

        // Posicionar al jugador en el suelo
        playerNode.setLocalTranslation(0, 5, 0);

        // Crear enemigos
        for (int i = 0; i < 11; i++) {
            spawnEnemy(i * 5);
        }
        
        // Crear un plano (suelo) visual
        Box floorBox = new Box(50, 1, 50); // tamaño del suelo
        Geometry floorGeo = new Geometry("Floor", floorBox);
        Material floorMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        floorMat.setColor("Color", ColorRGBA.Gray);
        floorGeo.setMaterial(floorMat);
        floorGeo.setLocalTranslation(0, -1, 0);
        rootNode.attachChild(floorGeo);

        // Agregar colisión al suelo
        RigidBodyControl floorPhys = new RigidBodyControl(0.0f); // 0 = estático
        floorGeo.addControl(floorPhys);
        bulletAppState.getPhysicsSpace().add(floorPhys);

    }

    private void initKeys() {
        inputManager.addMapping("Shoot", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(actionListener, "Shoot");
    }
    
    private ActionListener actionListener1 = new ActionListener() {
        public void onAction(String name, boolean isPressed, float tpf) {
            switch (name) {
                case "Left":    left = isPressed; break;
                case "Right":   right = isPressed; break;
                case "Forward": forward = isPressed; break;
                case "Back":    back = isPressed; break;
            }
        }
    };

    
    private final ActionListener actionListener = new ActionListener() {
        public void onAction(String name, boolean isPressed, float tpf) {
            if (name.equals("Shoot") && !isPressed) {
                shoot();
            }
        }
    };

    private void shoot() {
        CollisionResults results = new CollisionResults();
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        rootNode.collideWith(ray, results);

        if (results.size() > 0) {
            Spatial hit = results.getClosestCollision().getGeometry();
            if (enemies.contains(hit)) {
                System.out.println("¡Disparo acertado! Enemigo eliminado.");
                rootNode.detachChild(hit);
                enemies.remove(hit);
            }
        } else {
            System.out.println("Disparo fallido.");
        }
    }

    private void spawnEnemy(float offsetZ) {
        Box box = new Box(1, 1, 1);
        Geometry enemy = new Geometry("Enemy", box);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Red);
        enemy.setMaterial(mat);
        enemy.setLocalTranslation(new Vector3f(0, 1, -10 - offsetZ));
        rootNode.attachChild(enemy);
        enemies.add(enemy);
    }

    @Override
    public void simpleUpdate(float tpf) {
        Iterator<Geometry> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            Geometry enemy = iterator.next();
            Vector3f dir = cam.getLocation().subtract(enemy.getLocalTranslation()).normalizeLocal();
            enemy.move(dir.mult(tpf * 2));

            if (enemy.getLocalTranslation().distance(cam.getLocation()) < 2f) {
                playerHealth -= 10;
                updateHUD();
                System.out.println("¡Te golpearon! Vida restante: " + playerHealth);
                rootNode.detachChild(enemy);
                iterator.remove();

                if (playerHealth <= 0) {
                    showGameOver();

                }
            }
        }
        
        Vector3f walkDirection = new Vector3f();

        if (forward)  walkDirection.addLocal(cam.getDirection().setY(0).normalizeLocal());
        if (back)     walkDirection.addLocal(cam.getDirection().setY(0).negate().normalizeLocal());
        if (left)     walkDirection.addLocal(cam.getLeft().setY(0).normalizeLocal());
        if (right)    walkDirection.addLocal(cam.getLeft().setY(0).negate().normalizeLocal());

        playerControl.setWalkDirection(walkDirection.mult(5));
        cam.setLocation(playerControl.getSpatial().getWorldTranslation().add(0, 2, 0));
    }

    // ----------- HUD con Lemur -----------

    private void initLemur() {
        GuiGlobals.initialize(this);
        GuiGlobals.getInstance().getStyles().setDefaultStyle("default");

        hudContainer = new Container();
        guiNode.attachChild(hudContainer);
        hudContainer.setLocalTranslation(10, cam.getHeight() - 10, 0); // esquina superior izquierda

        lifeLabel = hudContainer.addChild(new Label("Vida: 100"));
    }

    private void updateHUD() {
        lifeLabel.setText("Vida: " + playerHealth);
    }

    private void showGameOver() {
        Container gameOverContainer = new Container();
        guiNode.attachChild(gameOverContainer);
        gameOverContainer.setLocalTranslation(cam.getWidth() / 2f - 100, cam.getHeight() / 2f + 50, 0);

        Label gameOverLabel = gameOverContainer.addChild(new Label("¡Has perdido!"));
        gameOverLabel.setFontSize(30);
        gameOverLabel.setColor(ColorRGBA.Red);

        Button retryButton = gameOverContainer.addChild(new Button("Reintentar"));
        retryButton.addClickCommands((Button btn) -> restartGame());
    }
    
    private void restartGame() {
        // Limpia todo
        rootNode.detachAllChildren();
        guiNode.detachAllChildren();
        enemies.clear();

        // Reinicia variables
        playerHealth = 100;

        // Reinicia interfaz y enemigos
        initLemur();
        updateHUD();
        for (int i = 0; i < 5; i++) {
            spawnEnemy(i * 5);
        }
    }


}
