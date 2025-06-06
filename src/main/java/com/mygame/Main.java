package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioNode;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath; // Necesario para FastMath.PI si rotas paredes
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
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
import java.util.Random; // Importar para generación aleatoria
import java.util.logging.Level; // Para debug de AssetManager
import java.util.logging.Logger; // Para debug de AssetManager


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

    // Instancia de Random para generar números aleatorios (para enemigos)
    private Random random = new Random(); 
    
    private float spawnTimer = 0f;
    private float gameTimer = 0f;
    private final float SPAWN_INTERVAL = 2f; // Generar un enemigo cada 2 segundos
    private final float GAME_DURATION = 60f; // Duración del juego en segundos (1 minuto)
    private boolean spawningActive = false;
    private AudioNode enemyEliminationSound;
    
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

        // Opcional: Habilitar logging detallado para el AssetManager
        Logger.getLogger(AssetManager.class.getName()).setLevel(Level.FINER);

        // Inicializar BulletAppState una vez para la simulación física
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        // Habilitar la visualización de depuración de Bullet
        // bulletAppState.setDebugEnabled(true); // Descomentar para ver colisiones físicas

        // Inicializar la librería GUI Lemur
        System.setProperty("lemur.groovy.scripting", "false"); // Add this line

        GuiGlobals.initialize(this);
        GuiGlobals.getInstance().getStyles().setDefaultStyle("default"); // Establecer el estilo por defecto

        // Añadir iluminación a la escena
        addLighting();

        // Configurar mapeo de teclas para las acciones del jugador
        initKeys();

        // Crear componentes del jugador (Nodo y Control) solo una vez al iniciar la aplicación
        createPlayerComponents();
        


        enemyEliminationSound = new AudioNode(assetManager, "Sounds/roblox-death-sound_1_wnMo9iW.wav", false); // <-- ¡CAMBIA LA RUTA!
        enemyEliminationSound.setPositional(false); // Si es false, el sonido no dependerá de la posición. Si es true, sí.
        enemyEliminationSound.setVolume(2); // Ajusta el volumen (1 es normal, 2 es el doble, etc.)
        enemyEliminationSound.setLooping(false); // No queremos que el sonido se repita en bucle
        rootNode.attachChild(enemyEliminationSound);

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
        // Limpiar el espacio físico existente en lugar de recrear BulletAppState.
        // Esto es más eficiente y menos propenso a errores.
        if (bulletAppState != null) {
            bulletAppState.getPhysicsSpace().removeAll(rootNode); // Elimina los controles físicos de todos los hijos del rootNode
        } else {
            // Esto solo debería ocurrir la primera vez que se llama a resetGame()
            // si bulletAppState aún no se inicializó en simpleInitApp, lo cual ya haces.
            bulletAppState = new BulletAppState();
            stateManager.attach(bulletAppState);
        }

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
        float floorLimit = 45f; // Rango para spawn, ajustado para no estar en el borde
        int numberOfEnemies = 15; // Cantidad de enemigos a spawnear

        gameTimer = 0f;
        spawnTimer = 0f;
        spawningActive = true;
        
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
            
        addWallsAroundFloor();
        
                // --- Agregar el Techo ---
        float floorSize = 50f; // Usa el mismo tamaño que tu suelo y paredes
        float wallHeight = 10f; // Usa la misma altura que tus paredes

        Box ceilingBox = new Box(floorSize, 0.5f, floorSize); // Una caja del tamaño del suelo pero delgada
        Geometry ceilingGeo = new Geometry("Ceiling", ceilingBox);

        // Cargar y aplicar la textura del techo
        Texture ceilingTexture = assetManager.loadTexture("Textures/sky2.jpg"); // <-- ¡CAMBIA ESTO A TU RUTA DE TEXTURA!
        ceilingTexture.setWrap(Texture.WrapMode.Repeat); // Para que la textura se repita si el techo es grande

        Material ceilingMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        ceilingMat.setTexture("DiffuseMap", ceilingTexture);
        ceilingMat.setBoolean("UseMaterialColors", true);
        ceilingMat.setColor("Diffuse", ColorRGBA.White);
        // Opcional: si la textura tiene un lado "interior" y "exterior" y quieres que se vea de ambos lados
        ceilingMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        ceilingGeo.setMaterial(ceilingMat);
        // Posicionar el techo justo encima de las paredes
        // La altura (Y) será la altura de la pared + la mitad del grosor del techo
        ceilingGeo.setLocalTranslation(0, 40f + 0.5f, 0); // 0.5f es el grosor del techo en la Box
        rootNode.attachChild(ceilingGeo);
        
        // Reiniciar la salud del jugador y actualizar el Heads-Up Display (HUD).
        playerHealth = 100;
        // Reinicializar los elementos del HUD de Lemur, ya que fueron desvinculados por guiNode.detachAllChildren().
        initLemurHUD();
        updateHUD();

        // Volver a añadir la mira al centro de la pantalla.
        addCrosshair();
    }

    // Modifica el método para aceptar un Vector3f
    private void spawnEnemy(Vector3f position) {
        // Asegurar que la carpeta Assets esté registrada para la carga de modelos.
        assetManager.registerLocator("Assets/", FileLocator.class);

        // Cargar modelo del enemigo
        Node enemy = (Node) assetManager.loadModel("Models/insectoid_monster_rig.j3o");
        enemy.setLocalTranslation(position); // Usa la posición recibida
        enemy.setName("Enemy");

        // Añadir al grafo de escena
        rootNode.attachChild(enemy);
        enemies.add(enemy);

        // Agregar una esfera roja visible como hijo del enemigo (representa el hitbox)
        Sphere esfera = new Sphere(20, 20, 0.25f); // Resolución y radio
        Geometry esferaGeo = new Geometry("EnemyHitbox", esfera);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Red);
        esferaGeo.setMaterial(mat);
        esferaGeo.setLocalTranslation(0, 0.35f, -0.2f); // Posición relativa al enemigo
        enemy.attachChild(esferaGeo); // Visiblemente es parte del enemigo

        // Crear colisión con forma de esfera
        SphereCollisionShape sphereShape = new SphereCollisionShape(0.55f);
        RigidBodyControl enemyPhys = new RigidBodyControl(sphereShape, 0f);
        enemy.addControl(enemyPhys);
        enemyPhys.setKinematic(true); // Para que se muevan por script, no por físicas

        bulletAppState.getPhysicsSpace().add(enemyPhys);

        System.out.println("Enemigo añadido: " + enemy.getName() + " en " + enemy.getLocalTranslation());
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
                enemyEliminationSound.playInstance();
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

                // --- ¡REPRODUCE EL SONIDO AQUÍ TAMBIÉN! ---
                enemyEliminationSound.playInstance(); // Reproduce una instancia del sonido
                // --- FIN REPRODUCE SONIDO ---

                iterator.remove();
                System.out.println("Enemigo " + enemy.getName() + " ELIMINADO por proximidad.");

                if (playerHealth <= 0) {
                    showGameOver();
                }
            }
        }
        
        if (spawningActive) {
            gameTimer += tpf; // Incrementa el temporizador general del juego
            spawnTimer += tpf; // Incrementa el temporizador de spawn

            // Generar un enemigo si el tiempo de spawn ha pasado
            if (spawnTimer >= SPAWN_INTERVAL) {
                float floorLimit = 45f; // Asegúrate de que esta variable esté accesible o re-declarada aquí
                float randomX = (random.nextFloat() * 2 * floorLimit) - floorLimit;
                float randomZ = (random.nextFloat() * 2 * floorLimit) - floorLimit;
                Vector3f spawnPosition = new Vector3f(randomX, 2f, randomZ);
                spawnEnemy(spawnPosition);
                spawnTimer -= SPAWN_INTERVAL; // Reinicia el temporizador de spawn (o ponlo a 0f)
            }

            // Detener la generación después de la duración del juego
            if (gameTimer >= GAME_DURATION) {
                spawningActive = false; // Desactiva la generación de enemigos
                System.out.println("¡Se acabó el tiempo de generación de enemigos!");
                showVictory();
                // Aquí podrías añadir lógica adicional si no quedan enemigos
                // pero ya no se generarán más.
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
        crosshair.setSize(guiFont.getCharSet().getRenderedSize() * 1);
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

        // En addLighting()
        // Luz principal (ejemplo)
        DirectionalLight sun1 = new DirectionalLight();
        sun1.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        sun1.setColor(ColorRGBA.White.mult(0.7f)); // Un poco menos intensa para combinar
        rootNode.addLight(sun1);

        // Segunda luz direccional desde otra dirección (ejemplo: desde el +Z, +X)
        DirectionalLight sun2 = new DirectionalLight();
        sun2.setDirection(new Vector3f(-1, -1, 1).normalizeLocal()); // Luz de "relleno"
        sun2.setColor(ColorRGBA.White.mult(0.3f)); // Más suave
        rootNode.addLight(sun2);
        
        // Segunda luz direccional desde otra dirección (ejemplo: desde el +Z, +X)
        DirectionalLight sun3 = new DirectionalLight();
        sun3.setDirection(new Vector3f(1, -1, 1).normalizeLocal()); // Luz de "relleno"
        sun3.setColor(ColorRGBA.White.mult(0.3f)); // Más suave
        rootNode.addLight(sun3);
        
        DirectionalLight ceilingLight = new DirectionalLight();
        // Esta dirección es clave: apunta directamente hacia arriba
        ceilingLight.setDirection(new Vector3f(0, 1, 0).normalizeLocal());
        // Puedes ajustar la intensidad (ej. 0.5f) y el color si quieres un efecto particular
        ceilingLight.setColor(ColorRGBA.White.mult(0.5f)); // Una luz más suave
        rootNode.addLight(ceilingLight);
       

        // Asegúrate de que la luz ambiental siga siendo razonable, ej:
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.Gray.mult(0.9f)); // O ColorRGBA.White.mult(0.5f);
        rootNode.addLight(ambient);
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
    
    /**
    * Crea paredes alrededor del suelo para evitar que el jugador se caiga.
    */
    private void addWallsAroundFloor() {
        float floorSize = 50f;     // La mitad del tamaño del suelo
        float wallThickness = 1f;  // Grosor de las paredes
        float wallHeight = 30f;    // Altura de las paredes

        // En addWallsAroundFloor()
        Texture wallTexture = assetManager.loadTexture("Textures/OIP.jpg"); // Vuelve a tu textura
        wallTexture.setWrap(Texture.WrapMode.Repeat);
        Material wallMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        wallMat.setTexture("DiffuseMap", wallTexture);
        wallMat.setBoolean("UseMaterialColors", true);
        wallMat.setColor("Diffuse", ColorRGBA.White);
        wallMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
                // Puedes mantener el FaceCullMode.Off aunque en Unshaded no es tan crítico, pero no hace daño
        wallMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        // Pared - Norte (Ahora su cara interior mira hacia -Z)
        Box northWallBox = new Box(floorSize, wallHeight, wallThickness);
        Geometry northWall = new Geometry("NorthWall", northWallBox);
        northWall.setMaterial(wallMat);
        // Movemos la pared hacia afuera un poco para que el centro de la caja esté en floorSize + wallThickness/2
        // Esto hace que la cara que mira hacia -Z (hacia el centro del mapa) sea la visible.
        northWall.setLocalTranslation(0, wallHeight / 2f, floorSize + wallThickness / 2f);
        rootNode.attachChild(northWall);
        RigidBodyControl northWallPhys = new RigidBodyControl(0.0f);
        northWall.addControl(northWallPhys);
        bulletAppState.getPhysicsSpace().add(northWallPhys);

        // Pared - Sur (Ahora su cara interior mira hacia +Z)
        Box southWallBox = new Box(floorSize, wallHeight, wallThickness);
        Geometry southWall = new Geometry("SouthWall", southWallBox);
        southWall.setMaterial(wallMat);
        // Movemos la pared hacia afuera un poco para que el centro de la caja esté en -floorSize - wallThickness/2
        // Esto hace que la cara que mira hacia +Z (hacia el centro del mapa) sea la visible.
        southWall.setLocalTranslation(0, wallHeight / 2f, -(floorSize + wallThickness / 2f));
        rootNode.attachChild(southWall);
        RigidBodyControl southWallPhys = new RigidBodyControl(0.0f);
        southWall.addControl(southWallPhys);
        bulletAppState.getPhysicsSpace().add(southWallPhys);

        // Pared - Este (Ahora su cara interior mira hacia -X)
        Box eastWallBox = new Box(wallThickness, wallHeight, floorSize);
        Geometry eastWall = new Geometry("EastWall", eastWallBox);
        eastWall.setMaterial(wallMat);
        // Movemos la pared hacia afuera un poco para que el centro de la caja esté en floorSize + wallThickness/2
        // Esto hace que la cara que mira hacia -X (hacia el centro del mapa) sea la visible.
        eastWall.setLocalTranslation(floorSize + wallThickness / 2f, wallHeight / 2f, 0);
        rootNode.attachChild(eastWall);
        RigidBodyControl eastWallPhys = new RigidBodyControl(0.0f);
        eastWall.addControl(eastWallPhys);
        bulletAppState.getPhysicsSpace().add(eastWallPhys);

        // Pared - Oeste (Ahora su cara interior mira hacia +X)
        Box westWallBox = new Box(wallThickness, wallHeight, floorSize);
        Geometry westWall = new Geometry("WestWall", westWallBox);
        westWall.setMaterial(wallMat);
        // Movemos la pared hacia afuera un poco para que el centro de la caja esté en -floorSize - wallThickness/2
        // Esto hace que la cara que mira hacia +X (hacia el centro del mapa) sea la visible.
        westWall.setLocalTranslation(-(floorSize + wallThickness / 2f), wallHeight / 2f, 0);
        rootNode.attachChild(westWall);
        RigidBodyControl westWallPhys = new RigidBodyControl(0.0f);
        westWall.addControl(westWallPhys);
        bulletAppState.getPhysicsSpace().add(westWallPhys);
    }
}
