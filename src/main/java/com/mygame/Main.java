package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.collision.CollisionResults;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class Main extends SimpleApplication {

    private int playerHealth = 100;
    private List<Geometry> enemies = new ArrayList<>();

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setMoveSpeed(10f);
        initKeys();

        // Spawnea enemigos al iniciar
        for (int i = 0; i < 5; i++) {
            spawnEnemy(i * 5);
        }
    }

    private void initKeys() {
        inputManager.addMapping("Shoot", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(actionListener, "Shoot");
    }

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
                System.out.println("¡Te golpearon! Vida restante: " + playerHealth);
                rootNode.detachChild(enemy);
                iterator.remove();

                if (playerHealth <= 0) {
                    System.out.println("¡Has perdido!");
                    stop(); // Termina el juego
                }
            }
        }
    }
}
