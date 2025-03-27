import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Super Mario From Wish");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(new MarioGame());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

class MarioGame extends JPanel implements KeyListener, Runnable {
    // Screen and world constants
    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;
    private static final int GROUND_LEVEL = 500;
    private static final int GRAVITY = 1;
    private static final int JUMP_FORCE = -20;
    private static final int TILE_SIZE = 32;

    // Game objects
    private Player player;
    private List<Platform> platforms;
    private List<Enemy> enemies;
    private List<Coin> coins;
    private List<Cloud> clouds;
    private List<Block> blocks;
    private List<PowerUp> powerUps;
    private List<Particle> particles;
    private List<FloatingText> floatingTexts = new ArrayList<>();
    private long gameStartTime;
    private int score = 0;
    private boolean gameOver = false;
    private int coins_collected = 0;
    private int lives = 3;

    // Sound system
    private SoundManager soundManager;
    private boolean lastSoundToggle = false;

    // Combo system
    private int comboCount = 0;
    private long lastComboTime = 0;
    private static final long COMBO_TIMEOUT = 2000; // 2 seconds to continue a combo

    // Visual effects
    private int screenShake = 0;

    // Input state
    private boolean[] keys = new boolean[256];
    private boolean isRunning = true;

    // Camera and world generation
    private int cameraX = 0;
    private int worldRightEdge = SCREEN_WIDTH; // Rightmost x generated so far
    private Random random = new Random();

    // Game state
    private enum GameState { TITLE, PLAYING, GAME_OVER, PAUSED }
    private GameState gameState = GameState.TITLE;

    public MarioGame() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(92, 148, 252)); // Sky blue background
        setFocusable(true);
        addKeyListener(this);

        // Initialize sound manager
        soundManager = new SoundManager();

        initGame();
        new Thread(this).start();
    }

    private void initGame() {
        // Initialize player
        player = new Player(100, GROUND_LEVEL - 50);

        // Initialize lists for game objects
        platforms = new ArrayList<>();
        enemies = new ArrayList<>();
        coins = new ArrayList<>();
        clouds = new ArrayList<>();
        blocks = new ArrayList<>();
        powerUps = new ArrayList<>();
        particles = new ArrayList<>();
        floatingTexts = new ArrayList<>();
        gameStartTime = System.currentTimeMillis();

        // Create ground platform
        for (int i = -20; i < 60; i++) {
            platforms.add(new Platform(i * TILE_SIZE, GROUND_LEVEL, TILE_SIZE, TILE_SIZE));

            // Underground tiles (visual only)
            for (int j = 1; j < 4; j++) {
                platforms.add(new Platform(i * TILE_SIZE, GROUND_LEVEL + j * TILE_SIZE, TILE_SIZE, TILE_SIZE));
            }
        }

        // Add some floating platforms
        platforms.add(new Platform(300, GROUND_LEVEL - 100, 100, 20));
        platforms.add(new Platform(500, GROUND_LEVEL - 150, 100, 20));
        platforms.add(new Platform(700, GROUND_LEVEL - 120, 100, 20));

        // Add some question blocks
        blocks.add(new Block(350, GROUND_LEVEL - 200, Block.Type.QUESTION, Block.Content.COIN));
        blocks.add(new Block(550, GROUND_LEVEL - 240, Block.Type.QUESTION, Block.Content.POWER_UP));

        // Place some coins
        coins.add(new Coin(320, GROUND_LEVEL - 130));
        coins.add(new Coin(520, GROUND_LEVEL - 180));

        // Add enemies
        enemies.add(new Enemy(600, GROUND_LEVEL - 30, 1));

        // Create some clouds
        for (int i = 0; i < 5; i++) {
            int x = random.nextInt(SCREEN_WIDTH * 2) - SCREEN_WIDTH;
            int y = random.nextInt(100) + 30;
            int width = random.nextInt(70) + 80;
            int height = random.nextInt(30) + 40;
            int speed = random.nextInt(2) + 1;
            clouds.add(new Cloud(x, y, width, height, speed));
        }

        worldRightEdge = SCREEN_WIDTH * 2;
        score = 0;
        coins_collected = 0;
        lives = 3;
        gameOver = false;
        gameState = GameState.TITLE;

        // Start background music
        soundManager.loop("theme");
    }

    @Override
    public void run() {
        while (isRunning) {
            updateGame();
            repaint();
            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void updateGame() {
        switch (gameState) {
            case TITLE:
                if (keys[KeyEvent.VK_ENTER]) {
                    gameState = GameState.PLAYING;
                }
                break;
            case PLAYING:
                updatePlaying();
                break;
            case GAME_OVER:
                if (keys[KeyEvent.VK_R]) {
                    initGame();
                }
                break;
            case PAUSED:
                if (keys[KeyEvent.VK_P]) {
                    gameState = GameState.PLAYING;
                }
                if (keys[KeyEvent.VK_S] && !lastSoundToggle) {
                    soundManager.setEnabled(!soundManager.isEnabled());
                    lastSoundToggle = true;
                } else if (!keys[KeyEvent.VK_S]) {
                    lastSoundToggle = false;
                }
                break;
        }
    }

    private void updatePlaying() {
        if (gameOver) return;

        // Process input for left/right movement and jumping
        if (keys[KeyEvent.VK_LEFT]) {
            player.moveLeft();
        }
        if (keys[KeyEvent.VK_RIGHT]) {
            player.moveRight();
        }
        if (keys[KeyEvent.VK_SPACE] && player.isOnGround()) {
            player.jump();
            soundManager.play("jump");
        }
        if (keys[KeyEvent.VK_P]) {
            gameState = GameState.PAUSED;
        }
        if (keys[KeyEvent.VK_S] && !lastSoundToggle) {
            soundManager.setEnabled(!soundManager.isEnabled());
            lastSoundToggle = true;
        } else if (!keys[KeyEvent.VK_S]) {
            lastSoundToggle = false;
        }

        // Apply gravity and update player
        player.setVelY(player.getVelY() + GRAVITY);
        player.update();
        player.updateAnimation();

        // Reset onGround flag before collision checks
        player.setOnGround(false);

        // Check collision with platforms
        for (Platform platform : platforms) {
            if (player.getBounds().intersects(platform.getBounds())) {
                handlePlatformCollision(player, platform);
            }
        }

        // Check collision with blocks
        for (Block block : blocks) {
            if (player.getBounds().intersects(block.getBounds())) {
                handleBlockCollision(player, block);
            }
        }

        // Update and check collision with power-ups
        updatePowerUps();

        // Update enemies and check for collisions
        updateEnemies();

        // Update and check collision with coins
        updateCoins();

        // Update clouds
        updateClouds();

        // Update particles
        updateParticles();

        // Update combo system
        updateComboSystem();

        // Update floating text
        updateFloatingTexts();

        // Update power-up animation effects
        updatePowerUpAnimation();

        // Add speed line effects
        addSpeedLines();

        // Update camera to follow the player
        cameraX = Math.max(cameraX, player.getX() - 300);

        // Generate new terrain as player advances
        if (worldRightEdge - cameraX < SCREEN_WIDTH * 1.5) {
            generateTerrain();
        }

        // Remove objects that have scrolled off screen (to the left)
        int removalX = cameraX - 300;
        platforms.removeIf(p -> p.getX() + p.getWidth() < removalX && p.getY() != GROUND_LEVEL);
        coins.removeIf(c -> c.getX() + c.getWidth() < removalX);
        enemies.removeIf(e -> e.getX() + e.getWidth() < removalX);
        blocks.removeIf(b -> b.getX() + b.getWidth() < removalX);
        powerUps.removeIf(p -> p.getX() + p.getWidth() < removalX);
        particles.removeIf(p -> p.getX() + p.getSize() < removalX || p.getLifetime() <= 0);

        // End game if the player falls below the screen
        if (player.getY() > SCREEN_HEIGHT) {
            die();
        }
    }

    private void updatePowerUps() {
        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp powerUp = it.next();
            powerUp.update();

            // Apply gravity
            powerUp.setVelY(powerUp.getVelY() + GRAVITY);

            // Check ground collision
            for (Platform platform : platforms) {
                if (new Rectangle(powerUp.getX(), powerUp.getY() + powerUp.getHeight(),
                        powerUp.getWidth(), 1).intersects(platform.getBounds())) {
                    powerUp.setY(platform.getY() - powerUp.getHeight());
                    powerUp.setVelY(0);
                    break;
                }
            }

            // Check if player collects power-up
            if (player.getBounds().intersects(powerUp.getBounds())) {
                if (powerUp.getType() == PowerUp.Type.MUSHROOM) {
                    player.powerUp();
                    score += 1000;
                    soundManager.play("powerup");
                }
                it.remove();
            }
        }
    }

    private void updateEnemies() {
        Iterator<Enemy> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            Enemy enemy = enemyIterator.next();
            enemy.update();

            // Apply gravity
            enemy.setVelY(enemy.getVelY() + GRAVITY);

            // Check ground collision
            boolean onGround = false;
            for (Platform platform : platforms) {
                if (new Rectangle(enemy.getX(), enemy.getY() + enemy.getHeight(),
                        enemy.getWidth(), 1).intersects(platform.getBounds())) {
                    enemy.setY(platform.getY() - enemy.getHeight());
                    enemy.setVelY(0);
                    onGround = true;
                    break;
                }
            }

            // Check if enemy fell in a pit
            if (enemy.getY() > SCREEN_HEIGHT) {
                enemyIterator.remove();
                continue;
            }

            // Check collision with player
            if (player.getBounds().intersects(enemy.getBounds())) {
                // If player is falling and his feet are just above the enemy, kill enemy (stomp)
                if (player.getVelY() > 0 && player.getY() + player.getHeight() - 15 < enemy.getY()) {
                    // Create death animation particles
                    for (int i = 0; i < 8; i++) {
                        int particleX = enemy.getX() + enemy.getWidth() / 2;
                        int particleY = enemy.getY() + enemy.getHeight() / 2;
                        int particleSize = 6;
                        int particleLifetime = 30;
                        Color particleColor = new Color(100, 100, 100);

                        // Random velocity
                        double angle = Math.random() * 2 * Math.PI;
                        int speed = random.nextInt(3) + 2;
                        int velX = (int)(Math.cos(angle) * speed);
                        int velY = (int)(Math.sin(angle) * speed) - 4; // Initial upward boost

                        particles.add(new Particle(particleX, particleY, velX, velY, particleSize, particleLifetime, particleColor));
                    }

                    // Award points and remove enemy
                    enemyIterator.remove();
                    player.setVelY(JUMP_FORCE / 2); // Bounce upward

                    // Combo system
                    comboCount++;
                    lastComboTime = System.currentTimeMillis();
                    // Award bonus points for combos
                    int comboBonus = comboCount * 50;
                    score += 100 + comboBonus;

                    // Create animated text showing points
                    int pointsX = enemy.getX() + enemy.getWidth() / 2;
                    int pointsY = enemy.getY();
                    String pointsText = "+" + (100 + comboBonus);
                    addFloatingText(pointsText, pointsX, pointsY, 40);

                    // Add screen shake effect
                    addScreenShake(5);

                    soundManager.play("stomp");
                } else if (!player.isInvincible()) {
                    if (player.getPowerLevel() > 0) {
                        player.powerDown();
                        player.setInvincible(true);
                        player.setInvincibleTime(System.currentTimeMillis() + 2000);
                        soundManager.play("powerdown");
                    } else {
                        die();
                    }
                }
            }

            // Simple AI - reverse direction at edges or when hitting walls
            if (enemy.getDirection() > 0) {
                // Check if about to walk off a platform or hit a wall on the right
                boolean hasGroundAhead = false;
                boolean hasWallAhead = false;

                for (Platform platform : platforms) {
                    if (new Rectangle(enemy.getX() + enemy.getWidth(), enemy.getY() + enemy.getHeight(),
                            5, 5).intersects(platform.getBounds())) {
                        hasGroundAhead = true;
                    }

                    if (new Rectangle(enemy.getX() + enemy.getWidth(), enemy.getY(),
                            1, enemy.getHeight()).intersects(platform.getBounds())) {
                        hasWallAhead = true;
                    }
                }

                if ((!hasGroundAhead && onGround) || hasWallAhead) {
                    enemy.reverseDirection();
                }
            } else {
                // Check if about to walk off a platform or hit a wall on the left
                boolean hasGroundAhead = false;
                boolean hasWallAhead = false;

                for (Platform platform : platforms) {
                    if (new Rectangle(enemy.getX() - 5, enemy.getY() + enemy.getHeight(),
                            5, 5).intersects(platform.getBounds())) {
                        hasGroundAhead = true;
                    }

                    if (new Rectangle(enemy.getX() - 1, enemy.getY(),
                            1, enemy.getHeight()).intersects(platform.getBounds())) {
                        hasWallAhead = true;
                    }
                }

                if ((!hasGroundAhead && onGround) || hasWallAhead) {
                    enemy.reverseDirection();
                }
            }

            // Check collision with other enemies
            for (Enemy otherEnemy : enemies) {
                if (enemy != otherEnemy && enemy.getBounds().intersects(otherEnemy.getBounds())) {
                    enemy.reverseDirection();
                    otherEnemy.reverseDirection();
                    break;
                }
            }
        }
    }

    private void updateCoins() {
        Iterator<Coin> coinIterator = coins.iterator();
        while (coinIterator.hasNext()) {
            Coin coin = coinIterator.next();
            coin.update();

            if (player.getBounds().intersects(coin.getBounds())) {
                coinIterator.remove();
                score += 50;
                coins_collected++;
                soundManager.play("coin");

                // Create animated text showing points
                int pointsX = coin.getX() + coin.getWidth() / 2;
                int pointsY = coin.getY();
                addFloatingText("+50", pointsX, pointsY, 30);
            }
        }
    }

    private void updateClouds() {
        for (Cloud cloud : clouds) {
            cloud.update();
            // If a cloud goes off to the left, reposition it to the right
            if (cloud.getX() + cloud.getWidth() < cameraX / 2 - 100) {
                cloud.setX(cameraX / 2 + SCREEN_WIDTH + random.nextInt(100));
            }
        }
    }

    private void updateParticles() {
        for (Particle particle : particles) {
            particle.update();
        }
    }

    private void updateComboSystem() {
        // Check if combo has timed out
        if (comboCount > 0 && System.currentTimeMillis() - lastComboTime > COMBO_TIMEOUT) {
            comboCount = 0;
        }
    }

    private void updateFloatingTexts() {
        Iterator<FloatingText> it = floatingTexts.iterator();
        while (it.hasNext()) {
            FloatingText text = it.next();
            text.update();
            if (text.getLifetime() <= 0) {
                it.remove();
            }
        }
    }

    private void updatePowerUpAnimation() {
        // Create sparkle particles around the player when powered up
        if (player.getPowerLevel() > 0 && player.isInvincible() && Math.random() < 0.2) {
            int particleX = player.getX() + random.nextInt(player.getWidth());
            int particleY = player.getY() + random.nextInt(player.getHeight());

            int size = random.nextInt(3) + 2;
            int lifetime = random.nextInt(10) + 10;

            // Rainbow colors for power-up effect
            Color[] rainbowColors = {
                    Color.RED, Color.ORANGE, Color.YELLOW,
                    Color.GREEN, Color.BLUE, new Color(75, 0, 130),
                    new Color(148, 0, 211)
            };

            Color color = rainbowColors[random.nextInt(rainbowColors.length)];

            double velX = (random.nextDouble() * 2 - 1) * 2;
            double velY = (random.nextDouble() * 2 - 1) * 2;

            particles.add(new Particle(particleX, particleY, velX, velY, size, lifetime, color));
        }
    }

    private void addSpeedLines() {
        if (Math.abs(player.getVelX()) > 3 && player.isOnGround() && random.nextInt(10) < 3) {
            int particleX = player.getVelX() > 0 ?
                    player.getX() :
                    player.getX() + player.getWidth();
            int particleY = player.getY() + player.getHeight() - 10 + random.nextInt(10);

            double velX = player.getVelX() > 0 ? -3 - random.nextDouble() * 2 : 3 + random.nextDouble() * 2;

            Particle speedLine = new Particle(
                    particleX, particleY,
                    velX, 0,
                    random.nextInt(3) + 4,
                    15,
                    new Color(220, 220, 220, 150)
            );

            particles.add(speedLine);
        }
    }

    private void addFloatingText(String text, int x, int y, int lifetime) {
        floatingTexts.add(new FloatingText(text, x, y, lifetime));
    }

    private void addScreenShake(int amount) {
        screenShake = Math.max(screenShake, amount);
    }

    private void handlePlatformCollision(Player player, Platform platform) {
        Rectangle pBounds = player.getBounds();
        Rectangle platformBounds = platform.getBounds();

        double pLeft = pBounds.getX();
        double pRight = pBounds.getX() + pBounds.getWidth();
        double pTop = pBounds.getY();
        double pBottom = pBounds.getY() + pBounds.getHeight();

        double platformLeft = platformBounds.getX();
        double platformRight = platformBounds.getX() + platformBounds.getWidth();
        double platformTop = platformBounds.getY();
        double platformBottom = platformBounds.getY() + platformBounds.getHeight();

        double overlapX = Math.min(pRight, platformRight) - Math.max(pLeft, platformLeft);
        double overlapY = Math.min(pBottom, platformBottom) - Math.max(pTop, platformTop);

        // Resolve collision by moving player the shortest distance
        if (overlapX < overlapY) {
            // Horizontal collision
            if (pLeft < platformLeft) {
                // Player is on the left
                player.setX((int)(platformLeft - pBounds.getWidth()));
            } else {
                // Player is on the right
                player.setX((int)platformRight);
            }
            player.setVelX(0);
        } else {
            // Vertical collision
            if (pTop < platformTop) {
                // Player is above platform
                player.setY((int)(platformTop - pBounds.getHeight()));
                player.setVelY(0);
                player.setOnGround(true);
            } else {
                // Player is below platform
                player.setY((int)platformBottom);
                player.setVelY(0);
            }
        }
    }

    private void handleBlockCollision(Player player, Block block) {
        Rectangle pBounds = player.getBounds();
        Rectangle blockBounds = block.getBounds();

        double pLeft = pBounds.getX();
        double pRight = pBounds.getX() + pBounds.getWidth();
        double pTop = pBounds.getY();
        double pBottom = pBounds.getY() + pBounds.getHeight();

        double blockLeft = blockBounds.getX();
        double blockRight = blockBounds.getX() + blockBounds.getWidth();
        double blockTop = blockBounds.getY();
        double blockBottom = blockBounds.getY() + blockBounds.getHeight();

        // Check if player hits block from below (special case)
        if (player.getVelY() < 0 && pTop < blockBottom && pBottom > blockBottom &&
                pRight > blockLeft && pLeft < blockRight) {
            // Player hit block from below
            player.setY((int)blockBottom);
            player.setVelY(0);

            // Handle block hit (only if not already hit)
            if (!block.isHit() && (block.getType() == Block.Type.QUESTION || block.getType() == Block.Type.BRICK)) {
                hitBlock(block);
            }

            return;
        }

        double overlapX = Math.min(pRight, blockRight) - Math.max(pLeft, blockLeft);
        double overlapY = Math.min(pBottom, blockBottom) - Math.max(pTop, blockTop);

        // Resolve collision by moving player the shortest distance
        if (overlapX < overlapY) {
            // Horizontal collision
            if (pLeft < blockLeft) {
                // Player is on the left
                player.setX((int)(blockLeft - pBounds.getWidth()));
            } else {
                // Player is on the right
                player.setX((int)blockRight);
            }
            player.setVelX(0);
        } else {
            // Vertical collision
            if (pTop < blockTop) {
                // Player is above block
                player.setY((int)(blockTop - pBounds.getHeight()));
                player.setVelY(0);
                player.setOnGround(true);
            } else {
                // Player is below block
                player.setY((int)blockBottom);
                player.setVelY(0);
            }
        }
    }

    private void hitBlock(Block block) {
        block.setHit(true);
        soundManager.play("blockhit");

        // Create block hit animation particles
        for (int i = 0; i < 5; i++) {
            int particleX = block.getX() + block.getWidth() / 2;
            int particleY = block.getY() + block.getHeight() / 2;
            int particleSize = 3;
            int particleLifetime = 20;
            Color particleColor = block.getType() == Block.Type.QUESTION ? Color.YELLOW : new Color(210, 105, 30);

            // Random velocity
            double angle = Math.random() * 2 * Math.PI;
            int speed = random.nextInt(3) + 1;
            int velX = (int)(Math.cos(angle) * speed);
            int velY = (int)(Math.sin(angle) * speed) - 3; // Initial upward boost

            particles.add(new Particle(particleX, particleY, velX, velY, particleSize, particleLifetime, particleColor));
        }

        // Handle block contents
        if (block.getContent() == Block.Content.COIN) {
            score += 50;
            coins_collected++;

            // Create animated coin particle that pops out of the block
            int coinX = block.getX() + block.getWidth() / 2 - 10;
            int coinY = block.getY() - 20;
            int coinSize = 20;
            int coinLifetime = 30;

            Particle coinParticle = new Particle(coinX, coinY, 0, -5, coinSize, coinLifetime, Color.YELLOW) {
                @Override
                public void update() {
                    super.update();
                    // Override to add special coin animation
                    if (getVelY() < 5) { // Cap max fall speed for coins
                        setVelY(getVelY() + 0.5); // Less gravity for coin
                    }
                }

                @Override
                public void render(Graphics2D g) {
                    g.setColor(getColor());
                    g.fillOval(getX(), getY(), getSize(), getSize());
                }
            };

            particles.add(coinParticle);

            // Add floating text
            addFloatingText("+50", block.getX() + block.getWidth()/2, block.getY() - 30, 30);

            soundManager.play("coin");

            if (block.getType() == Block.Type.QUESTION) {
                block.setType(Block.Type.USED);
            }
        } else if (block.getContent() == Block.Content.POWER_UP) {
            // Spawn a power-up
            PowerUp powerUp = new PowerUp(block.getX(), block.getY() - 32, PowerUp.Type.MUSHROOM);
            powerUps.add(powerUp);

            soundManager.play("powerup");

            if (block.getType() == Block.Type.QUESTION) {
                block.setType(Block.Type.USED);
            }
        }
    }

    // Instead of creating a whole new method, you should update your existing generateTerrain method
// with these improvements. Here's a modified version of your existing method:

    private void generateTerrain() {
        int startX = worldRightEdge;
        int endX = startX + SCREEN_WIDTH * 2; // Generate further ahead

        // Generate ground
        boolean lastWasGap = false; // Track if the last section was a gap
        for (int i = startX / TILE_SIZE; i < endX / TILE_SIZE + 1; i++) {
            // Check if we want a gap (pit)
            // Only allow gaps if the previous section wasn't a gap and we're not at the start
            if (!lastWasGap && random.nextInt(100) < 10 && i > startX / TILE_SIZE + 4) {
                int gapWidth = random.nextInt(3) + 2; // 2-4 tiles

                // Ensure there's a platform to help jump to if gap is wide
                if (gapWidth > 2) {
                    // Add a floating platform to help cross larger gaps
                    int platformX = i * TILE_SIZE + TILE_SIZE;
                    int platformY = GROUND_LEVEL - random.nextInt(50) - 80; // 80-130 pixels above ground
                    int platformWidth = Math.min(gapWidth - 1, 2) * TILE_SIZE; // Platform to help cross but not covering the whole gap

                    platforms.add(new Platform(platformX, platformY, platformWidth, TILE_SIZE));

                    // 50% chance to add a coin above the platform
                    if (random.nextBoolean()) {
                        coins.add(new Coin(platformX + platformWidth / 2 - 10, platformY - 30));
                    }
                }

                i += gapWidth;
                lastWasGap = true;
                continue;
            }

            // Add ground tiles
            platforms.add(new Platform(i * TILE_SIZE, GROUND_LEVEL, TILE_SIZE, TILE_SIZE));
            lastWasGap = false;

            // Underground tiles (visual only)
            for (int j = 1; j < 4; j++) {
                platforms.add(new Platform(i * TILE_SIZE, GROUND_LEVEL + j * TILE_SIZE, TILE_SIZE, TILE_SIZE));
            }
        }

        // Generate floating platforms
        int numPlatforms = random.nextInt(3) + 2; // 2-4 platforms
        for (int i = 0; i < numPlatforms; i++) {
            int platformX = startX + random.nextInt(SCREEN_WIDTH - 100);
            int platformY = GROUND_LEVEL - random.nextInt(200) - 50; // 50-250 pixels above ground
            int platformWidth = (random.nextInt(3) + 2) * TILE_SIZE; // 2-4 tiles wide

            platforms.add(new Platform(platformX, platformY, platformWidth, TILE_SIZE));

            // 50% chance to add a coin above the platform
            if (random.nextBoolean()) {
                coins.add(new Coin(platformX + platformWidth / 2 - 10, platformY - 30));
            }
        }

        // Generate blocks
        int numBlocks = random.nextInt(3) + 1; // 1-3 blocks
        for (int i = 0; i < numBlocks; i++) {
            int blockX = startX + random.nextInt(SCREEN_WIDTH - 50);
            int blockY = GROUND_LEVEL - random.nextInt(200) - 100; // 100-300 pixels above ground

            Block.Type blockType = random.nextBoolean() ? Block.Type.QUESTION : Block.Type.BRICK;
            Block.Content blockContent = random.nextBoolean() ? Block.Content.COIN : Block.Content.POWER_UP;

            blocks.add(new Block(blockX, blockY, blockType, blockContent));
        }

        // Generate enemies
        int numEnemies = random.nextInt(3) + 1; // 1-3 enemies
        for (int i = 0; i < numEnemies; i++) {
            int enemyX = startX + random.nextInt(SCREEN_WIDTH - 50);
            int enemyY = GROUND_LEVEL - TILE_SIZE;
            int direction = random.nextBoolean() ? 1 : -1;

            enemies.add(new Enemy(enemyX, enemyY, direction));
        }

        // Generate clouds
        int numClouds = random.nextInt(3) + 1; // 1-3 clouds
        for (int i = 0; i < numClouds; i++) {
            int x = startX + random.nextInt(SCREEN_WIDTH);
            int y = random.nextInt(100) + 30;
            int width = random.nextInt(70) + 80;
            int height = random.nextInt(30) + 40;
            int speed = random.nextInt(2) + 1;
            clouds.add(new Cloud(x, y, width, height, speed));
        }

        worldRightEdge = endX;
    }

    // Update the die() method to properly reset player position and handle respawn
    private void die() {
        lives--;
        soundManager.play("death");

        if (lives <= 0) {
            gameOver = true;
            gameState = GameState.GAME_OVER;
            soundManager.stop("theme");
        } else {
            // Create death animation particles
            for (int i = 0; i < 15; i++) {
                int particleX = player.getX() + player.getWidth() / 2;
                int particleY = player.getY() + player.getHeight() / 2;
                int particleSize = random.nextInt(4) + 2;
                int particleLifetime = random.nextInt(20) + 30;
                Color particleColor = new Color(255, 50, 50);

                // Random velocity for explosion effect
                double angle = Math.random() * 2 * Math.PI;
                int speed = random.nextInt(5) + 3;
                int velX = (int)(Math.cos(angle) * speed);
                int velY = (int)(Math.sin(angle) * speed) - 5; // Initial upward boost

                particles.add(new Particle(particleX, particleY, velX, velY, particleSize, particleLifetime, particleColor));
            }

            // Find safe respawn point
            int respawnX = Math.max(100, cameraX + 100);
            int respawnY = GROUND_LEVEL - 100; // Start a bit above ground to avoid immediate collisions

            // Find the nearest platform at or above ground level
            Platform safeGround = null;
            for (Platform platform : platforms) {
                if (platform.getY() == GROUND_LEVEL &&
                        platform.getX() >= cameraX &&
                        platform.getX() < cameraX + SCREEN_WIDTH) {
                    // If this is the first safe ground we've found, or it's closer to the respawn point
                    if (safeGround == null || Math.abs(platform.getX() - respawnX) < Math.abs(safeGround.getX() - respawnX)) {
                        safeGround = platform;
                    }
                }
            }

            // If we found safe ground, position above it
            if (safeGround != null) {
                respawnX = safeGround.getX() + safeGround.getWidth() / 2 - player.getWidth() / 2;
            } else {
                // If no safe ground found in view, move camera back to find some
                // This should rarely happen if world generation is working correctly
                cameraX = Math.max(0, cameraX - SCREEN_WIDTH / 2);

                // Try to find safe ground again, but go back even further if needed
                for (Platform platform : platforms) {
                    if (platform.getY() == GROUND_LEVEL && platform.getX() >= cameraX - SCREEN_WIDTH) {
                        respawnX = platform.getX() + 10;
                        break;
                    }
                }
            }

            // Reset player state
            player.setX(respawnX);
            player.setY(respawnY);
            player.setVelX(0);
            player.setVelY(0);
            player.setPowerLevel(0);
            player.setInvincible(true);
            player.setInvincibleTime(System.currentTimeMillis() + 3000);

            // Add a visual respawn effect
            for (int i = 0; i < 10; i++) {
                int particleX = player.getX() + random.nextInt(player.getWidth());
                int particleY = player.getY() + random.nextInt(player.getHeight());
                int particleSize = random.nextInt(5) + 3;
                int particleLifetime = random.nextInt(20) + 20;

                // White sparkle particles for respawn
                Color particleColor = new Color(255, 255, 255);

                double velX = (random.nextDouble() * 4) - 2;
                double velY = -2 - random.nextDouble() * 2;

                particles.add(new Particle(particleX, particleY, velX, velY, particleSize, particleLifetime, particleColor));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        switch (gameState) {
            case TITLE:
                drawTitle(g2d);
                break;
            case PLAYING:
            case GAME_OVER:
            case PAUSED:
                drawGame(g2d);

                if (gameState == GameState.GAME_OVER) {
                    drawGameOver(g2d);
                } else if (gameState == GameState.PAUSED) {
                    drawPaused(g2d);
                }
                break;
        }
    }

    private void drawTitle(Graphics2D g) {
        g.setColor(new Color(92, 148, 252)); // Sky blue background
        g.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "Super Mario";
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, SCREEN_WIDTH / 2 - titleWidth / 2, SCREEN_HEIGHT / 3);

        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String subtitle = "Press ENTER to Start";
        int subtitleWidth = g.getFontMetrics().stringWidth(subtitle);
        g.drawString(subtitle, SCREEN_WIDTH / 2 - subtitleWidth / 2, SCREEN_HEIGHT / 2 + 50);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        String[] controls = {
                "Arrow Keys: Move",
                "Space: Jump",
                "P: Pause",
                "S: Toggle Sound"
        };

        for (int i = 0; i < controls.length; i++) {
            int width = g.getFontMetrics().stringWidth(controls[i]);
            g.drawString(controls[i], SCREEN_WIDTH / 2 - width / 2, SCREEN_HEIGHT / 2 + 100 + i * 25);
        }
    }

    private void drawGame(Graphics2D g) {
        // Apply screen shake
        int shakeX = 0;
        int shakeY = 0;
        if (screenShake > 0) {
            shakeX = random.nextInt(screenShake * 2) - screenShake;
            shakeY = random.nextInt(screenShake * 2) - screenShake;
            screenShake--;
        }

        // Draw sky gradient background
        drawSkyGradient(g);

        // Apply camera translation with shake
        g.translate(-cameraX + shakeX, shakeY);

        // Draw background elements
        drawBackground(g);

        // Draw water/lava in pits
        drawWaterAnimation(g);

        // Draw clouds with parallax effect
        for (Cloud cloud : clouds) {
            int cloudScreenX = cloud.getX() - cameraX / 2;
            g.setColor(Color.WHITE);
            g.fillOval(cloudScreenX, cloud.getY(), cloud.getWidth(), cloud.getHeight());
        }

        // Draw platforms
        for (Platform platform : platforms) {
            g.setColor(new Color(139, 69, 19)); // Brown for ground/platforms
            g.fillRect(platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight());
        }

        // Draw blocks
        for (Block block : blocks) {
            g.setColor(block.getType() == Block.Type.QUESTION ? Color.YELLOW :
                    block.getType() == Block.Type.USED ? Color.GRAY :
                            new Color(210, 105, 30)); // Brick color
            g.fillRect(block.getX(), block.getY(), block.getWidth(), block.getHeight());

            // Draw ? symbol on question blocks
            if (block.getType() == Block.Type.QUESTION && !block.isHit()) {
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("?", block.getX() + 10, block.getY() + 22);
            }
        }

        // Draw power-ups
        for (PowerUp powerUp : powerUps) {
            if (powerUp.getType() == PowerUp.Type.MUSHROOM) {
                // Draw mushroom
                g.setColor(Color.RED);
                g.fillOval(powerUp.getX(), powerUp.getY(), powerUp.getWidth(), powerUp.getHeight());

                // Draw white stem
                g.setColor(Color.WHITE);
                g.fillRect(powerUp.getX() + 8, powerUp.getY() + 16, 16, 16);
            }
        }

        // Draw animated coins
        drawAnimatedCoins(g);

        // Draw enemies with eyes and direction indicators
        for (Enemy enemy : enemies) {
            // Main body
            g.setColor(Color.GREEN.darker());
            g.fillRect(enemy.getX(), enemy.getY(), enemy.getWidth(), enemy.getHeight());

            // Eyes - white part
            g.setColor(Color.WHITE);
            int eyeOffset = enemy.getDirection() > 0 ? 18 : 5;
            g.fillOval(enemy.getX() + eyeOffset, enemy.getY() + 5, 7, 7);

            // Eyes - pupil
            g.setColor(Color.BLACK);
            int pupilOffset = enemy.getDirection() > 0 ? 20 : 7;
            g.fillOval(enemy.getX() + pupilOffset, enemy.getY() + 6, 3, 3);

            // Feet
            g.setColor(Color.BLACK);
            g.fillRect(enemy.getX() + 5, enemy.getY() + enemy.getHeight() - 3, 7, 3);
            g.fillRect(enemy.getX() + enemy.getWidth() - 12, enemy.getY() + enemy.getHeight() - 3, 7, 3);
        }

        // Draw particles
        for (Particle particle : particles) {
            particle.render(g);
        }

        // Draw floating texts
        drawFloatingTexts(g);

        // Draw player with different appearance based on power level
        drawPlayer(g, player);

        // Reset translation
        g.translate(cameraX, 0);

        // Draw HUD
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("Score: " + score, 20, 30);

        // Draw coin icon
        g.setColor(Color.YELLOW);
        g.fillOval(20, 40, 20, 20);
        g.setColor(Color.BLACK);
        g.drawOval(20, 40, 20, 20);
        g.setColor(Color.WHITE);
        g.drawString("× " + coins_collected, 50, 58);

        // Draw lives with Mario icon
        g.setColor(Color.RED);
        g.fillRect(20, 70, 20, 20);
        g.setColor(Color.WHITE);
        g.drawString("× " + lives, 50, 88);

        // Draw elapsed time
        long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
        String timeString = String.format("%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
        g.drawString("Time: " + timeString, SCREEN_WIDTH - 150, 30);

        // Draw combo text if active
        drawComboText(g);
    }

    private void drawGameOver(Graphics2D g) {
        // Draw semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        // Draw game over text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 50));
        String gameOverText = "GAME OVER";
        int textWidth = g.getFontMetrics().stringWidth(gameOverText);
        g.drawString(gameOverText, SCREEN_WIDTH / 2 - textWidth / 2, SCREEN_HEIGHT / 2);

        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String restartText = "Press R to Restart";
        int restartWidth = g.getFontMetrics().stringWidth(restartText);
        g.drawString(restartText, SCREEN_WIDTH / 2 - restartWidth / 2, SCREEN_HEIGHT / 2 + 50);

        // Draw final score
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String scoreText = "Final Score: " + score;
        int scoreWidth = g.getFontMetrics().stringWidth(scoreText);
        g.drawString(scoreText, SCREEN_WIDTH / 2 - scoreWidth / 2, SCREEN_HEIGHT / 2 + 100);
    }

    private void drawPaused(Graphics2D g) {
        // Draw semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        // Draw paused text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 50));
        String pausedText = "PAUSED";
        int textWidth = g.getFontMetrics().stringWidth(pausedText);
        g.drawString(pausedText, SCREEN_WIDTH / 2 - textWidth / 2, SCREEN_HEIGHT / 2);

        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String resumeText = "Press P to Resume";
        int resumeWidth = g.getFontMetrics().stringWidth(resumeText);
        g.drawString(resumeText, SCREEN_WIDTH / 2 - resumeWidth / 2, SCREEN_HEIGHT / 2 + 50);

        // Add sound toggle option
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String soundStatus = soundManager.isEnabled() ? "Sound: ON (S)" : "Sound: OFF (S)";
        g.drawString(soundStatus, SCREEN_WIDTH / 2 - 60, SCREEN_HEIGHT / 2 + 100);
    }

    // Updated background drawing with early mountain generation
    private void drawBackground(Graphics2D g) {
        // Create mountain data based on camera position for infinite scrolling

        // First mountain range (far) - increase visible range
        g.setColor(new Color(82, 113, 199));

        // Generate mountains based on the camera position with increased pre-generation range
        int mountainSpacing = 150; // Distance between mountain peaks
        int visibleRange = SCREEN_WIDTH + 1600; // Significantly increased to ensure mountains are generated well before visible
        int preRenderOffset = 1200; // Amount to start rendering mountains before they appear on screen

        // Calculate the first visible mountain based on camera position, starting earlier
        int firstVisibleMountain = ((cameraX - preRenderOffset) / 5) / mountainSpacing;
        int lastVisibleMountain = ((cameraX + visibleRange) / 5) / mountainSpacing;

        // Draw first distant mountain range
        for (int i = firstVisibleMountain; i <= lastVisibleMountain; i++) {
            int peakX = i * mountainSpacing + (cameraX / 5 % mountainSpacing);

            // Use a deterministic but varied height based on position
            int height = 120 + ((i * 7919) % 100); // 7919 is a prime number to create a good distribution
            int width = 180;

            int[] xPoints = {peakX - width/2, peakX, peakX + width/2};
            int[] yPoints = {GROUND_LEVEL - 50, GROUND_LEVEL - 50 - height, GROUND_LEVEL - 50};

            g.fillPolygon(xPoints, yPoints, 3);
        }

        // Second mountain range (closer, darker)
        g.setColor(new Color(62, 88, 180));

        // Recalculate for the second range with different parallax factor
        mountainSpacing = 200;
        firstVisibleMountain = ((cameraX - preRenderOffset) / 3) / mountainSpacing;
        lastVisibleMountain = ((cameraX + visibleRange) / 3) / mountainSpacing;

        for (int i = firstVisibleMountain; i <= lastVisibleMountain; i++) {
            int peakX = i * mountainSpacing + (cameraX / 3 % mountainSpacing);

            // Different height algorithm for variety
            int height = 180 + ((i * 7919 + 3541) % 70); // Different offset for variety
            int width = 220;

            int[] xPoints = {peakX - width/2, peakX, peakX + width/2};
            int[] yPoints = {GROUND_LEVEL - 40, GROUND_LEVEL - 40 - height, GROUND_LEVEL - 40};

            g.fillPolygon(xPoints, yPoints, 3);
        }

        // Third mountain range (closest, darkest) - adds more depth
        g.setColor(new Color(42, 58, 140));

        mountainSpacing = 280;
        firstVisibleMountain = ((cameraX - preRenderOffset) / 2) / mountainSpacing;
        lastVisibleMountain = ((cameraX + visibleRange) / 2) / mountainSpacing;

        for (int i = firstVisibleMountain; i <= lastVisibleMountain; i++) {
            int peakX = i * mountainSpacing + (cameraX / 2 % mountainSpacing);

            // Different height algorithm for more variety
            int height = 220 + ((i * 7919 + 1123) % 80);
            int width = 300;

            int[] xPoints = {peakX - width/2, peakX, peakX + width/2};
            int[] yPoints = {GROUND_LEVEL - 30, GROUND_LEVEL - 30 - height, GROUND_LEVEL - 30};

            g.fillPolygon(xPoints, yPoints, 3);
        }
    }

    // Update generateTerrain method to ensure safe ground is always available
    /* private void generateTerrain() {
        int startX = worldRightEdge;
        int endX = startX + SCREEN_WIDTH * 2; // Generate further ahead

        // Generate ground
        boolean lastWasGap = false; // Track if the last section was a gap
        for (int i = startX / TILE_SIZE; i < endX / TILE_SIZE + 1; i++) {
            // Check if we want a gap (pit)
            // Only allow gaps if the previous section wasn't a gap and we're not at the start
            if (!lastWasGap && random.nextInt(100) < 10 && i > startX / TILE_SIZE + 4) {
                int gapWidth = random.nextInt(3) + 2; // 2-4 tiles

                // Ensure there's a platform to jump to if gap is wide
                if (gapWidth > 2) {
                    // Add a floating platform to help cross larger gaps
                    int platformX = i * TILE_SIZE + TILE_SIZE;
                    int platformY = GROUND_LEVEL - random.nextInt(50) - 80; // 80-130 pixels above ground
                    int platformWidth = Math.min(gapWidth - 1, 2) * TILE_SIZE; // Platform to help cross but not covering the whole gap

                    platforms.add(new Platform(platformX, platformY, platformWidth, TILE_SIZE));

                    // 50% chance to add a coin above the platform
                    if (random.nextBoolean()) {
                        coins.add(new Coin(platformX + platformWidth / 2 - 10, platformY - 30));
                    }
                }

                i += gapWidth;
                lastWasGap = true;
                continue;
            }

            // Add ground tiles
            platforms.add(new Platform(i * TILE_SIZE, GROUND_LEVEL, TILE_SIZE, TILE_SIZE));
            lastWasGap = false;

            // Underground tiles (visual only)
            for (int j = 1; j < 4; j++) {
                platforms.add(new Platform(i * TILE_SIZE, GROUND_LEVEL + j * TILE_SIZE, TILE_SIZE, TILE_SIZE));
            }
        }

        // Rest of the method remains the same (floating platforms, blocks, enemies, etc.)
        // ...

        worldRightEdge = endX;
    }
        */
    private void drawSkyGradient(Graphics2D g) {
        // Create a gradient from top (light blue) to bottom (slightly darker blue)
        GradientPaint skyGradient = new GradientPaint(
                0, 0, new Color(135, 206, 235),
                0, GROUND_LEVEL, new Color(92, 148, 252)
        );
        g.setPaint(skyGradient);
        g.fillRect(0, 0, SCREEN_WIDTH, GROUND_LEVEL);
    }

    private void drawAnimatedCoins(Graphics2D g) {
        // Coin animation frame based on game time
        int coinFrame = (int)(System.currentTimeMillis() / 100) % 4;
        float coinScale = 1.0f;

        // Make coins "breathe" by scaling
        if (coinFrame == 0) coinScale = 1.0f;
        else if (coinFrame == 1) coinScale = 0.9f;
        else if (coinFrame == 2) coinScale = 0.8f;
        else coinScale = 0.9f;

        for (Coin coin : coins) {
            // Calculate center position
            int centerX = coin.getX() + coin.getWidth()/2;
            int centerY = coin.getY() + coin.getHeight()/2;

            // Calculate new dimensions with scale
            int scaledWidth = (int)(coin.getWidth() * coinScale);
            int scaledHeight = (int)(coin.getHeight() * coinScale);

            // Calculate new position to keep center point
            int scaledX = centerX - scaledWidth/2;
            int scaledY = centerY - scaledHeight/2;

            // Draw coin with shine effect
            g.setColor(Color.YELLOW);
            g.fillOval(scaledX, scaledY, scaledWidth, scaledHeight);

            // Add shine
            g.setColor(new Color(255, 255, 200));
            int shineSize = scaledWidth / 3;
            g.fillOval(scaledX + scaledWidth/4, scaledY + scaledHeight/4, shineSize, shineSize);
        }
    }

    private void drawWaterAnimation(Graphics2D g) {
        // Find gaps in the ground
        boolean inGap = false;
        int gapStart = 0;

        for (int x = cameraX - 300; x < cameraX + SCREEN_WIDTH + 300; x += 32) {
            boolean hasGround = false;

            // Check if this column has ground at GROUND_LEVEL
            for (Platform platform : platforms) {
                if (platform.getY() == GROUND_LEVEL &&
                        x >= platform.getX() &&
                        x < platform.getX() + platform.getWidth()) {
                    hasGround = true;
                    break;
                }
            }

            if (!hasGround && !inGap) {
                // Start of a gap
                inGap = true;
                gapStart = x;
            } else if (hasGround && inGap) {
                // End of a gap, draw water/lava
                inGap = false;
                int gapEnd = x;

                // Water surface animation
                double time = System.currentTimeMillis() / 500.0;
                int waterLevel = GROUND_LEVEL + 10 + (int)(Math.sin(time) * 4);

                // Choose between water and lava based on position
                boolean isLava = (gapStart / 1000) % 2 == 0;
                Color waterColor = isLava ? new Color(255, 100, 0) : new Color(60, 170, 255);
                Color deepColor = isLava ? new Color(200, 60, 0) : new Color(0, 100, 200);

                // Draw water gradient
                GradientPaint waterGradient = new GradientPaint(
                        0, waterLevel, waterColor,
                        0, SCREEN_HEIGHT, deepColor
                );
                g.setPaint(waterGradient);
                g.fillRect(gapStart, waterLevel, gapEnd - gapStart, SCREEN_HEIGHT - waterLevel);

                // Draw surface ripples
                g.setColor(isLava ? new Color(255, 200, 0, 100) : new Color(255, 255, 255, 100));
                for (int i = 0; i < (gapEnd - gapStart) / 10; i++) {
                    int rippleX = gapStart + i * 10;
                    int rippleHeight = (int)(Math.sin(time + i * 0.3) * 3);
                    g.drawLine(rippleX, waterLevel + rippleHeight, rippleX + 5, waterLevel);
                }

                // Add bubbles/particles
                if (random.nextInt(20) < 3) {
                    int particleX = gapStart + random.nextInt(gapEnd - gapStart);
                    int particleY = waterLevel + 20 + random.nextInt(40);
                    int size = random.nextInt(4) + 3;

                    Color particleColor = isLava ?
                            new Color(255, 200, 0, 150) :
                            new Color(255, 255, 255, 150);

                    Particle bubble = new Particle(
                            particleX, particleY,
                            0, -1 - random.nextDouble(),
                            size,
                            30 + random.nextInt(20),
                            particleColor
                    );

                    particles.add(bubble);
                }
            }
        }

        // Close the last gap if needed
        if (inGap) {
            int gapEnd = cameraX + SCREEN_WIDTH + 300;

            // Water surface animation (same as above)
            double time = System.currentTimeMillis() / 500.0;
            int waterLevel = GROUND_LEVEL + 10 + (int)(Math.sin(time) * 4);

            boolean isLava = (gapStart / 1000) % 2 == 0;
            Color waterColor = isLava ? new Color(255, 100, 0) : new Color(60, 170, 255);
            Color deepColor = isLava ? new Color(200, 60, 0) : new Color(0, 100, 200);

            GradientPaint waterGradient = new GradientPaint(
                    0, waterLevel, waterColor,
                    0, SCREEN_HEIGHT, deepColor
            );
            g.setPaint(waterGradient);
            g.fillRect(gapStart, waterLevel, gapEnd - gapStart, SCREEN_HEIGHT - waterLevel);
        }
    }

    private void drawPlayer(Graphics2D g, Player player) {
        if (!player.isInvincible() || (System.currentTimeMillis() / 100) % 2 == 0) {
            boolean facingRight = player.getVelX() >= 0;
            int frameOffset = player.getAnimFrame() * 2; // Use animation frame through getter

            if (player.getPowerLevel() == 0) {
                // Small Mario
                g.setColor(Color.RED);

                // Base body
                g.fillRect(player.getX(), player.getY(), player.getWidth(), player.getHeight());

                // Hat
                g.fillRect(player.getX() - 2, player.getY(), player.getWidth() + 4, 10);

                // Eyes
                g.setColor(Color.WHITE);
                g.fillOval(player.getX() + (facingRight ? 18 : 5), player.getY() + 5, 7, 7);
                g.setColor(Color.BLACK);
                g.fillOval(player.getX() + (facingRight ? 20 : 7), player.getY() + 6, 3, 3);

                // Shirt and overalls
                g.setColor(Color.BLUE);
                g.fillRect(player.getX(), player.getY() + 20, player.getWidth(), player.getHeight() - 20);

                // Animate legs based on movement
                if (Math.abs(player.getVelX()) > 0 && player.isOnGround()) {
                    // Running animation
                    int legOffset = frameOffset % 8; // 0, 2, 4, 6

                    // Left leg
                    g.setColor(Color.DARK_GRAY);
                    if (legOffset < 4) {
                        // Left leg forward
                        g.fillRect(player.getX() - 5 + legOffset,
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    } else {
                        // Left leg back
                        g.fillRect(player.getX() + 5 - (legOffset - 4),
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    }

                    // Right leg (opposite phase)
                    int rightLegOffset = (legOffset + 4) % 8;
                    if (rightLegOffset < 4) {
                        // Right leg forward
                        g.fillRect(player.getX() + player.getWidth() - 12 - 5 + rightLegOffset,
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    } else {
                        // Right leg back
                        g.fillRect(player.getX() + player.getWidth() - 12 + 5 - (rightLegOffset - 4),
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    }
                } else {
                    // Standing pose
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(player.getX(), player.getY() + player.getHeight() - 8, 12, 8);
                    g.fillRect(player.getX() + player.getWidth() - 12, player.getY() + player.getHeight() - 8, 12, 8);
                }

                // Jumping pose
                if (!player.isOnGround()) {
                    // Adjust arm position for jump
                    g.setColor(Color.RED);
                    g.fillRect(player.getX() + (facingRight ? 0 : 15),
                            player.getY() + 15,
                            15, 5);
                }
            } else {
                // Big Mario with similar animation
                g.setColor(Color.RED);
                g.fillRect(player.getX(), player.getY(), player.getWidth(), player.getHeight());

                // Hat
                g.fillRect(player.getX() - 2, player.getY(), player.getWidth() + 4, 10);

                // Eyes
                g.setColor(Color.WHITE);
                g.fillOval(player.getX() + (facingRight ? 18 : 5), player.getY() + 10, 7, 7);
                g.setColor(Color.BLACK);
                g.fillOval(player.getX() + (facingRight ? 20 : 7), player.getY() + 11, 3, 3);

                // Shirt and overalls
                g.setColor(Color.BLUE);
                g.fillRect(player.getX(), player.getY() + 30, player.getWidth(), player.getHeight() - 30);

                // Animate legs based on movement
                if (Math.abs(player.getVelX()) > 0 && player.isOnGround()) {
                    // Running animation
                    int legOffset = frameOffset % 8; // 0, 2, 4, 6

                    // Left leg
                    g.setColor(Color.DARK_GRAY);
                    if (legOffset < 4) {
                        // Left leg forward
                        g.fillRect(player.getX() - 5 + legOffset,
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    } else {
                        // Left leg back
                        g.fillRect(player.getX() + 5 - (legOffset - 4),
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    }

                    // Right leg (opposite phase)
                    int rightLegOffset = (legOffset + 4) % 8;
                    if (rightLegOffset < 4) {
                        // Right leg forward
                        g.fillRect(player.getX() + player.getWidth() - 12 - 5 + rightLegOffset,
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    } else {
                        // Right leg back
                        g.fillRect(player.getX() + player.getWidth() - 12 + 5 - (rightLegOffset - 4),
                                player.getY() + player.getHeight() - 8,
                                12, 8);
                    }
                } else {
                    // Standing pose
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(player.getX(), player.getY() + player.getHeight() - 8, 12, 8);
                    g.fillRect(player.getX() + player.getWidth() - 12, player.getY() + player.getHeight() - 8, 12, 8);
                }
            }
        }
    }

    private void drawComboText(Graphics2D g) {
        if (comboCount > 1) {
            g.setFont(new Font("Arial", Font.BOLD, 24));
            String comboText = comboCount + "x COMBO!";

            // Calculate remaining combo time
            long remainingTime = COMBO_TIMEOUT - (System.currentTimeMillis() - lastComboTime);
            float alpha = Math.min(1.0f, remainingTime / 1000.0f);

            g.setColor(new Color(1.0f, 0.5f, 0.0f, alpha));
            g.drawString(comboText, SCREEN_WIDTH - 200, 80);

            // Draw combo timer bar
            g.setColor(new Color(1.0f, 1.0f, 1.0f, alpha * 0.7f));
            int barWidth = (int)((remainingTime / (float)COMBO_TIMEOUT) * 150);
            g.fillRect(SCREEN_WIDTH - 200, 90, barWidth, 5);
        }
    }

    private void drawFloatingTexts(Graphics2D g) {
        for (FloatingText text : floatingTexts) {
            text.render(g);
        }
    }

    // KeyListener implementations
    @Override
    public void keyPressed(KeyEvent e) {
        keys[e.getKeyCode()] = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keys[e.getKeyCode()] = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }

    // Sound manager
    class SoundManager {
        private Map<String, Clip> clips;
        private boolean soundEnabled = true;

        public SoundManager() {
            clips = new HashMap<>();
            // Pre-load common sound effects
            loadSound("jump", "/sounds/jump.wav");
            loadSound("coin", "/sounds/coin.wav");
            loadSound("powerup", "/sounds/powerup.wav");
            loadSound("powerdown", "/sounds/powerdown.wav");
            loadSound("stomp", "/sounds/stomp.wav");
            loadSound("death", "/sounds/death.wav");
            loadSound("blockhit", "/sounds/blockhit.wav");
            // Add background music
            loadSound("theme", "/sounds/theme.wav");
        }

        private void loadSound(String name, String path) {
            try {
                URL url = getClass().getResource(path);
                // If the resources aren't available, don't crash
                if (url == null) {
                    System.out.println("Warning: Sound file not found: " + path);
                    return;
                }

                AudioInputStream audioIn = AudioSystem.getAudioInputStream(url);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clips.put(name, clip);
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                System.out.println("Error loading sound: " + path);
                e.printStackTrace();
            }
        }

        public void play(String name) {
            if (!soundEnabled) return;

            Clip clip = clips.get(name);
            if (clip == null) return;

            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
        }

        public void loop(String name) {
            if (!soundEnabled) return;

            Clip clip = clips.get(name);
            if (clip == null) return;

            if (clip.isRunning()) {
                return;
            }
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }

        public void stop(String name) {
            Clip clip = clips.get(name);
            if (clip == null) return;

            clip.stop();
        }

        public void stopAll() {
            for (Clip clip : clips.values()) {
                if (clip.isRunning()) {
                    clip.stop();
                }
            }
        }

        public void setEnabled(boolean enabled) {
            this.soundEnabled = enabled;
            if (!enabled) {
                stopAll();
            }
        }

        public boolean isEnabled() {
            return soundEnabled;
        }
    }
}

// Game object classes
class GameObject {
    protected int x, y, width, height;

    public GameObject(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
}

class Player extends GameObject {
    private int velX = 0, velY = 0;
    private boolean onGround = false;
    private static final int SPEED = 5;
    private static final int JUMP_FORCE = -20;

    // Animation state
    private int animFrame = 0;
    private int animTimer = 0;
    private static final int ANIM_SPEED = 5;

    // Power-up state
    private int powerLevel = 0; // 0=small, 1=big, 2=fire
    private boolean invincible = false;
    private long invincibleTime = 0;

    public Player(int x, int y) {
        super(x, y, 30, 50);
    }

    public void update() {
        x += velX;
        y += velY;

        // Simple friction for horizontal movement
        if (velX > 0) velX--;
        else if (velX < 0) velX++;

        // Check if invincibility ended
        if (invincible && System.currentTimeMillis() > invincibleTime) {
            invincible = false;
        }
    }

    public void updateAnimation() {
        if (Math.abs(velX) > 0) {
            animTimer++;
            if (animTimer >= ANIM_SPEED) {
                animFrame = (animFrame + 1) % 4; // 4 frames of animation
                animTimer = 0;
            }
        } else {
            animFrame = 0; // Reset to standing pose when not moving
            animTimer = 0;
        }
    }

    public void moveLeft() { velX = -SPEED; }
    public void moveRight() { velX = SPEED; }

    public void jump() {
        if (onGround) {
            velY = JUMP_FORCE;
            onGround = false;
        }
    }

    public void powerUp() {
        powerLevel = 1;
        height = 70; // Make Mario taller
        y -= 20; // Adjust position for new height
    }

    public void powerDown() {
        if (powerLevel > 0) {
            powerLevel--;

            if (powerLevel == 0) {
                height = 50; // Back to small size
                y += 20; // Readjust position
            }
        }
    }

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean onGround) { this.onGround = onGround; }
    public int getVelY() { return velY; }
    public void setVelY(int velY) { this.velY = velY; }
    public int getVelX() { return velX; }
    public void setVelX(int velX) { this.velX = velX; }
    public int getPowerLevel() { return powerLevel; }
    public void setPowerLevel(int powerLevel) { this.powerLevel = powerLevel; }
    public boolean isInvincible() { return invincible; }
    public void setInvincible(boolean invincible) { this.invincible = invincible; }
    public long getInvincibleTime() { return invincibleTime; }
    public void setInvincibleTime(long invincibleTime) { this.invincibleTime = invincibleTime; }
    public int getAnimFrame() { return animFrame; }
}

class Platform extends GameObject {
    public Platform(int x, int y, int width, int height) {
        super(x, y, width, height);
    }
}

class Block extends GameObject {
    public enum Type { BRICK, QUESTION, USED }
    public enum Content { EMPTY, COIN, POWER_UP }

    private Type type;
    private Content content;
    private boolean hit = false;

    public Block(int x, int y, Type type, Content content) {
        super(x, y, 32, 32); // Standard block size
        this.type = type;
        this.content = content;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Content getContent() { return content; }
    public void setContent(Content content) { this.content = content; }
    public boolean isHit() { return hit; }
    public void setHit(boolean hit) { this.hit = hit; }
}

class Enemy extends GameObject {
    private int direction; // -1 = left, 1 = right
    private int velX = 0, velY = 0;
    private static final int SPEED = 2;

    public Enemy(int x, int y, int direction) {
        super(x, y, 30, 30);
        this.direction = direction;
        this.velX = SPEED * direction;
    }

    public void update() {
        x += velX;
        y += velY;
    }

    public void reverseDirection() {
        direction *= -1;
        velX = SPEED * direction;
    }

    public int getDirection() { return direction; }
    public int getVelY() { return velY; }
    public void setVelY(int velY) { this.velY = velY; }
}

class Coin extends GameObject {
    private int bobOffset = 0;
    private int bobDirection = 1;

    public Coin(int x, int y) {
        super(x, y, 20, 20);
    }

    public void update() {
        // Make coin bob up and down slightly
        bobOffset += bobDirection;
        if (bobOffset > 5) bobDirection = -1;
        if (bobOffset < -5) bobDirection = 1;
    }
}

class PowerUp extends GameObject {
    public enum Type { MUSHROOM, FIRE_FLOWER, STAR }

    private Type type;
    private int velX = 2;
    private int velY = 0;
    private int direction = 1;

    public PowerUp(int x, int y, Type type) {
        super(x, y, 32, 32);
        this.type = type;
    }

    public void update() {
        x += velX * direction;
        y += velY;
    }

    public Type getType() { return type; }
    public int getVelY() { return velY; }
    public void setVelY(int velY) { this.velY = velY; }
}

class Cloud extends GameObject {
    private int speed;

    public Cloud(int x, int y, int width, int height, int speed) {
        super(x, y, width, height);
        this.speed = speed;
    }

    public void update() {
        x += speed;
    }
}

class Particle extends GameObject {
    private double velX, velY;
    private int lifetime;
    private int initialLifetime;
    private int size;
    private Color color;

    public Particle(int x, int y, double velX, double velY, int size, int lifetime, Color color) {
        super(x, y, size, size);
        this.velX = velX;
        this.velY = velY;
        this.size = size;
        this.lifetime = lifetime;
        this.initialLifetime = lifetime;
        this.color = color;
    }

    public void update() {
        x += velX;
        y += velY;
        velY += 0.2; // Apply gravity
        lifetime--;
    }

    public void render(Graphics2D g) {
        // Fade out as lifetime decreases
        int alpha = (int)(255 * ((double)lifetime / initialLifetime));
        alpha = Math.max(0, Math.min(255, alpha)); // Ensure alpha is within 0-255

        Color fadeColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        g.setColor(fadeColor);

        g.fillRect(x, y, size, size);
    }

    public int getLifetime() {
        return lifetime;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    public int getSize() {
        return size;
    }

    public Color getColor() {
        return color;
    }

    public double getVelY() {
        return velY;
    }

    public void setVelY(double velY) {
        this.velY = velY;
    }
}

class FloatingText {
    private String text;
    private int x, y;
    private int lifetime;
    private int initialLifetime;
    private double velY = -1.5;

    public FloatingText(String text, int x, int y, int lifetime) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.lifetime = lifetime;
        this.initialLifetime = lifetime;
    }

    public void update() {
        y += velY;
        lifetime--;
    }

    public void render(Graphics2D g) {
        float alpha = Math.min(1.0f, lifetime / (float)initialLifetime);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.setColor(new Color(1.0f, 1.0f, 0.0f, alpha));

        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x - width/2, y);
    }

    public int getLifetime() {
        return lifetime;
    }
}
