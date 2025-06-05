package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape; // NUEVA IMPORTACIÓN para forma de caja
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.style.BaseStyles;
import com.jme3.texture.Texture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;

public class Main extends SimpleApplication {

    private boolean left, right, forward, back;
    private int playerHealth = 100;
    private List<Spatial> enemies = new ArrayList<>();
    private BetterCharacterControl playerControl;
    private Node playerNode;
    private BulletAppState bulletAppState;

    private Label lifeLabel;
    private Container hudContainer;

    // Para la visualización del rayo de depuración
    private Geometry debugRayGeometry;

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // Configuración básica de la cámara y entrada
        flyCam.setEnabled(true);
        flyCam.setRotationSpeed(10f);
        inputManager.setCursorVisible(false);

        // Inicializar BulletAppState una vez para la simulación física
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        // Habilitar la visualización de depuración de Bullet
        bulletAppState.setDebugEnabled(true);


        // Inicializar la librería GUI Lemur
        GuiGlobals.initialize(this);
        GuiGlobals.getInstance().getStyles().setDefaultStyle("default");

        // Añadir iluminación a la escena
        addLighting();

        // Configurar mapeo de teclas para las acciones del jugador
        initKeys();

        // Crear componentes del jugador (Nodo y Control) solo una vez al iniciar la aplicación
        createPlayerComponents();

        // Realizar la configuración inicial del juego y reinicio
        resetGame();
    }

    /**
     * Crea el nodo espacial del jugador y su control de personaje.
     * Este método se llama solo una vez durante la inicialización de la aplicación.
     * NO adjunta al jugador al grafo de escena o al espacio físico aquí;
     * eso se maneja en `resetGame()` para asegurar una reinicialización adecuada.
     */
    private void createPlayerComponents() {
        playerNode = new Node("Player");
        // BetterCharacterControl define las propiedades físicas del jugador
        // (radio, altura, masa) y maneja la detección de colisiones.
        playerControl = new BetterCharacterControl(1.5f, 6f, 80f); // Radio, Altura, Masa
        playerControl.setGravity(new Vector3f(0, -20f, 0)); // Establecer gravedad para el jugador
        
        playerNode.addControl(playerControl); // Adjuntar el control al nodo del jugador
    }

    /**
     * Reinicia el juego a su estado inicial. Este método se llama al inicio
     * de la aplicación y cada vez que el juego necesita ser reiniciado (por ejemplo,
     * después de "Game Over" o "Victoria"). Limpia la escena, reinicia al jugador,
     * genera enemigos, configura el suelo y actualiza el HUD.
     */
    private void resetGame() {
        // Crucial: Desvincular y volver a vincular BulletAppState para limpiar efectivamente el espacio físico.
        if (bulletAppState != null) {
            stateManager.detach(bulletAppState);
        }
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        // Asegurarse de que el debug esté habilitado después de re-adjuntar
        bulletAppState.setDebugEnabled(true);


        // Limpiar todos los hijos del nodo raíz de la escena y del nodo GUI.
        rootNode.detachAllChildren();
        guiNode.detachAllChildren();

        // Limpiar la lista de enemigos activos.
        enemies.clear();

        // Volver a adjuntar el nodo del jugador al grafo de escena y añadir su control al espacio físico.
        rootNode.attachChild(playerNode);
        bulletAppState.getPhysicsSpace().add(playerControl); // Volver a añadir el control del jugador al espacio físico

        // Reiniciar la posición del jugador al punto de inicio.
        playerControl.warp(new Vector3f(0, 5, 0));

        // Generar un nuevo conjunto de enemigos para la ronda actual del juego.
        for (int i = 0; i < 5; i++) {
            spawnEnemy(i * 6);
        }

        // Crear y añadir el suelo a la escena.
        Box floorBox = new Box(50, 1, 50);
        Geometry floorGeo = new Geometry("Floor", floorBox);

        // Registrar la carpeta "Assets/" como localizador para cargar modelos y texturas.
        assetManager.registerLocator("Assets/", FileLocator.class);
        // Cargar y aplicar la textura del suelo, haciendo que se repita para superficies más grandes.
        Texture floorTexture = assetManager.loadTexture("Textures/suelo.jpg");
        floorTexture.setWrap(Texture.WrapMode.Repeat);

        Material floorMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        floorMat.setTexture("DiffuseMap", floorTexture);
        floorMat.setBoolean("UseMaterialColors", true);
        floorMat.setColor("Diffuse", ColorRGBA.White);

        floorGeo.setMaterial(floorMat);
        floorGeo.setLocalTranslation(0, -1, 0); // Posicionar el suelo debajo del jugador
        rootNode.attachChild(floorGeo);

        // Añadir física al suelo. Una masa de 0.0f lo convierte en un objeto estático e inamovible.
        RigidBodyControl floorPhys = new RigidBodyControl(0.0f);
        floorGeo.addControl(floorPhys);
        bulletAppState.getPhysicsSpace().add(floorPhys);

        // Reiniciar la salud del jugador y actualizar el Heads-Up Display (HUD).
        playerHealth = 100;
        // Reinicializar los elementos del HUD de Lemur, ya que fueron desvinculados por guiNode.detachAllChildren().
        initLemurHUD();
        updateHUD();

        // Volver a añadir la mira al centro de la pantalla.
        addCrosshair();
    }

    /**
     * Genera un modelo de enemigo con un desplazamiento Z especificado desde el origen.
     * @param offsetZ El desplazamiento de la coordenada Z para la posición de generación del enemigo.
     */
    private void spawnEnemy(float offsetZ) {
        // Asegurar que la carpeta Assets esté registrada para la carga de modelos.
        assetManager.registerLocator("Assets/", FileLocator.class);
        Spatial enemy = assetManager.loadModel("Models/insectoid_monster_rig.j3o");
        enemy.setLocalTranslation(new Vector3f(0, 0, -30 - offsetZ)); // Establecer posición inicial
        enemy.setName("Enemy"); // Asignar un nombre para identificación durante las comprobaciones de colisión
        rootNode.attachChild(enemy); // Añadir el enemigo al grafo de escena
        enemies.add(enemy); // Añadir a la lista de enemigos activos para seguimiento

        float boxXExtent = 0.2f;
        float boxYExtent = 0.35f;
        float boxZExtent = 0.2f;

        BoxCollisionShape boxShape = new BoxCollisionShape(new Vector3f(boxXExtent, boxYExtent, boxZExtent));
        CompoundCollisionShape compoundShape = new CompoundCollisionShape();

        // Offset centrado y más abajo
        Vector3f boxOffset = new Vector3f(0, 0.35f, -0.2f);
        compoundShape.addChildShape(boxShape, boxOffset);

        RigidBodyControl enemyPhys = new RigidBodyControl(compoundShape, 0f);
        enemy.addControl(enemyPhys);
        enemyPhys.setKinematic(true);

        bulletAppState.getPhysicsSpace().add(enemyPhys);


        System.out.println("Enemigo añadido: " + enemy.getName() + " en " + enemy.getLocalTranslation() + " con Box Extents:" + boxXExtent + "," + boxYExtent + "," + boxZExtent);
    }

    /**
     * Configura el mapeo de teclas de entrada para el movimiento del jugador (W, A, S, D) y el disparo (Botón izquierdo del ratón).
     */
    private void initKeys() {
        inputManager.addMapping("Shoot", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Back", new KeyTrigger(KeyInput.KEY_S));

        // Añadir listeners para responder a las acciones de teclas y botones del ratón.
        inputManager.addListener(actionListener, "Shoot");
        inputManager.addListener(actionListener1, "Left", "Right", "Forward", "Back");
    }

    // ActionListener para las teclas de movimiento del jugador (W, A, S, D) para establecer banderas booleanas.
    private final ActionListener actionListener1 = (name, isPressed, tpf) -> {
        switch (name) {
            case "Left": left = isPressed; break;
            case "Right": right = isPressed; break;
            case "Forward": forward = isPressed; break;
            case "Back": back = isPressed; break;
        }
    };

    // ActionListener para la acción de disparo (se activa al soltar el botón del ratón).
    private final ActionListener actionListener = (name, isPressed, tpf) -> {
        if (name.equals("Shoot") && !isPressed) {
            shoot();
        }
    };

    /**
     * Maneja la acción de disparo del jugador.
     * Utiliza `bulletAppState.getPhysicsSpace().rayTest()` para detectar colisiones
     * con las formas de colisión físicas de los objetos (como las cajas de los enemigos).
     */
    private void shoot() {
        // Usar List<PhysicsRayTestResult> para almacenar los resultados del rayTest
        List<PhysicsRayTestResult> physicsResults = new ArrayList<>();
        // Se crea un objeto Ray. Un Ray tiene un origen (Vector3f) y una dirección (Vector3f).
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());

        System.out.println("Disparando Rayo:");
        System.out.println("  Origen: " + ray.getOrigin());
        System.out.println("  Dirección: " + ray.getDirection());
        System.out.println("  Longitud: " + ray.getLimit());

        // Opcional: Visualiza el rayo en la escena con fines de depuración.
        drawDebugRay(ray);

        // Definimos una distancia máxima para el rayo de prueba físico.
        float rayTestDistance = 1000f;
        Vector3f rayEndForPhysics = ray.getOrigin().add(ray.getDirection().mult(rayTestDistance));
        
        // Realiza la prueba de rayo en el espacio físico de Bullet.
        // Esto detecta colisiones contra los RigidBodyControls y otras formas de colisión físicas.
        bulletAppState.getPhysicsSpace().rayTest(ray.getOrigin(), rayEndForPhysics, physicsResults);


        if (!physicsResults.isEmpty()) { // Comprobar si la lista no está vacía
            // --- DIAGNÓSTICO ADICIONAL ---
            System.out.println("¡Colisión detectada por rayTest!");
            System.out.println("  Número de colisiones: " + physicsResults.size());
            // --- FIN DIAGNÓSTICO ADICIONAL ---

            // Ordenar los resultados por distancia para obtener el más cercano
            Collections.sort(physicsResults, (r1, r2) -> Float.compare(r1.getHitFraction(), r2.getHitFraction()));

            PhysicsRayTestResult closestResult = physicsResults.get(0); // El resultado más cercano
            PhysicsCollisionObject hitObject = closestResult.getCollisionObject();
            // Para obtener el Spatial (el modelo 3D) asociado a este objeto físico,
            // asumimos que se guardó como el UserObject del RigidBodyControl.
            Spatial hitSpatial = (Spatial) hitObject.getUserObject();

            // --- DIAGNÓSTICO ADICIONAL ---
            if (hitSpatial != null) {
                System.out.println("  Spatial asociado al objeto físico golpeado: " + hitSpatial.getName() + " (Tipo: " + hitSpatial.getClass().getSimpleName() + ")");
                System.out.println("  Posición del Spatial golpeado: " + hitSpatial.getLocalTranslation());
                System.out.println("  Distancia a la colisión (HitFraction): " + closestResult.getHitFraction());
            } else {
                System.out.println("  ¡Advertencia! El Spatial asociado al objeto físico golpeado es null. Esto podría indicar un problema en cómo se adjuntó el RigidBodyControl.");
            }
            // --- FIN DIAGNÓSTICO ADICIONAL ---

            Spatial enemyToRemove = null;
            // Comprobamos si el Spatial golpeado es uno de nuestros enemigos.
            if (enemies.contains(hitSpatial)) {
                enemyToRemove = hitSpatial;
                System.out.println("  ¡ENEMIGO IDENTIFICADO para eliminar! Nombre: " + enemyToRemove.getName());
            } else {
                System.out.println("  El objeto físico golpeado NO es un enemigo en nuestra lista 'enemies'. Es: " + (hitSpatial != null ? hitSpatial.getName() : "null"));
            }

            if (enemyToRemove != null) {
                // Eliminar el Spatial visual del rootNode
                rootNode.detachChild(enemyToRemove);
                // Obtener y eliminar el RigidBodyControl del espacio físico
                RigidBodyControl enemyPhys = enemyToRemove.getControl(RigidBodyControl.class);
                if (enemyPhys != null) {
                    bulletAppState.getPhysicsSpace().remove(enemyPhys);
                    System.out.println("  RigidBodyControl del enemigo eliminado del espacio físico.");
                } else {
                    System.out.println("  Advertencia: No se encontró RigidBodyControl en el enemigo a eliminar.");
                }
                enemies.remove(enemyToRemove); // Eliminar de la lista de seguimiento de enemigos
                System.out.println("Enemigo " + enemyToRemove.getName() + " ELIMINADO (visual y físico).");

                if (enemies.isEmpty()) {
                    showVictory();
                }
            } else {
                System.out.println("No se encontró un enemigo válido para eliminar después de la colisión.");
            }
        } else {
            System.out.println("No se detectó ninguna colisión con el espacio físico.");
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        Iterator<Spatial> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            Spatial enemy = iterator.next();
            Vector3f dir = cam.getLocation().subtract(enemy.getLocalTranslation()).normalizeLocal();
            enemy.move(dir.mult(tpf * 1.5f));

            if (enemy.getLocalTranslation().distance(cam.getLocation()) < 2f) {
                playerHealth -= 10;
                updateHUD();
                rootNode.detachChild(enemy);
                RigidBodyControl enemyPhys = enemy.getControl(RigidBodyControl.class);
                if (enemyPhys != null) {
                    bulletAppState.getPhysicsSpace().remove(enemyPhys);
                }
                iterator.remove();
                System.out.println("Enemigo " + enemy.getName() + " ELIMINADO por proximidad.");

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

        playerControl.setWalkDirection(walkDirection.mult(5f));
        cam.setLocation(playerControl.getSpatial().getWorldTranslation().add(0, 2, 0));
    }

    private void initLemurHUD() {
        hudContainer = new Container();
        guiNode.attachChild(hudContainer);
        hudContainer.setLocalTranslation(10, cam.getHeight() - 10, 0);
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
        retryButton.addClickCommands((Button btn) -> resetGame());
    }

    private void showVictory() {
        Container victoryContainer = new Container();
        guiNode.attachChild(victoryContainer);
        victoryContainer.setLocalTranslation(cam.getWidth() / 2f - 100, cam.getHeight() / 2f + 50, 0);

        Label victoryLabel = victoryContainer.addChild(new Label("¡Victoria!"));
        victoryLabel.setFontSize(30);
        victoryLabel.setColor(ColorRGBA.Green);

        Button retryButton = victoryContainer.addChild(new Button("Reintentar"));
        retryButton.addClickCommands((Button btn) -> resetGame());
    }

    private void addCrosshair() {
        BitmapText crosshair = new BitmapText(guiFont, false);
        crosshair.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        crosshair.setText("+");
        crosshair.setColor(ColorRGBA.White);
        crosshair.setLocalTranslation(
                cam.getWidth() / 2f - crosshair.getLineWidth() / 2,
                cam.getHeight() / 2f + crosshair.getLineHeight() / 2,
                0
        );
        guiNode.attachChild(crosshair);
    }

    private void addLighting() {
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -2, -3).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);
    }

    /**
     * Dibuja un rayo de depuración temporal en la escena para visualizar los rayos de disparo.
     * Este método recibe un objeto `Ray`.
     * @param ray El objeto Ray a visualizar.
     */
    private void drawDebugRay(Ray ray) {
        if (debugRayGeometry != null) {
            debugRayGeometry.removeFromParent();
        }

        float debugRayLength = 100f;

        Box rayBox = new Box(0.05f, 0.05f, debugRayLength / 2f);
        debugRayGeometry = new Geometry("DebugRay", rayBox);

        Material redMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        redMat.setColor("Color", ColorRGBA.Red);
        debugRayGeometry.setMaterial(redMat);
        debugRayGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);

        // Calcula el punto final del rayo. `ray.getOrigin()` y `ray.getDirection()` devuelven Vector3f.
        Vector3f rayEnd = ray.getOrigin().add(ray.getDirection().mult(debugRayLength));

        // Establece la posición local de la geometría de depuración.
        // Aquí se usan operaciones con Vector3f, el resultado es un Vector3f.
        debugRayGeometry.setLocalTranslation(ray.getOrigin().add(rayEnd).mult(0.5f));

        // Orienta la geometría de depuración. `rayEnd` es un Vector3f.
        debugRayGeometry.lookAt(rayEnd, Vector3f.UNIT_Y);

        rootNode.attachChild(debugRayGeometry);
    }
}
