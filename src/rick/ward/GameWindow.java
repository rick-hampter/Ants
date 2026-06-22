package rick.ward;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GameWindow extends JFrame {

	public enum cursorLooks {
		CLICKER, SHOVEL, BOOT, ANT
	}

	enum Rotation {
		NORTH, SOUTH, EAST, WEST
	}

	public BufferedImage rotateImage(BufferedImage src, Rotation rotation) {
		if (src == null) {
			throw new IllegalArgumentException("Source image cannot be null.");
		}

		int w = src.getWidth();
		int h = src.getHeight();

		AffineTransform tx = new AffineTransform();

		switch (rotation) {
		case EAST:
			tx.translate(h, 0);
			tx.rotate(Math.toRadians(90));
			break;

		case SOUTH:
			tx.translate(w, h);
			tx.rotate(Math.toRadians(180));
			break;

		case WEST:
			tx.translate(0, w);
			tx.rotate(Math.toRadians(270));
			break;

		default:
			return src;
		}

		int newType = (src.getType() == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_INT_ARGB : src.getType();

		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);

		BufferedImage dest;
		if (rotation == Rotation.EAST || rotation == Rotation.WEST) {
			dest = new BufferedImage(h, w, newType);
		} else {
			dest = new BufferedImage(w, h, newType);
		}

		return op.filter(src, dest);
	}

	public static boolean[][] grid = new boolean[50][33];
	public static boolean[][] uneditable = new boolean[50][33];
	Ant[][] antGrid = new Ant[50][33];
	Ant[][] antGridHistory;
	private final boolean[][] toggleHistory = new boolean[50][33];

	final long MOVE_INTERVAL = 250_000_000L;

	int cellSize = 16;
	int rows = grid[0].length;
	int cols = grid.length;
	int offset = 0;

	cursorLooks Cursor = cursorLooks.CLICKER;
	private boolean hungy = false;

	public void playWav(File SFX) {
		new Thread(() -> {
			try {

				AudioInputStream audioIn = AudioSystem.getAudioInputStream(SFX);

				Clip clip = AudioSystem.getClip();

				clip.open(audioIn);

				clip.start();

				clip.addLineListener(event -> {
					if (event.getType() == LineEvent.Type.STOP) {
						clip.close();
					}
				});

			} catch (Exception e) {
				System.err.println("e_rr0r" + SFX);
				e.printStackTrace();
			}
		}).start();
	}

	private static final int WIDTH = 800;
	private static final int HEIGHT = 640;
	private static final int TARGET_FPS = 60;

	private int mouseX = 0;
	private int mouseY = 0;
	private boolean mouseDown = false;
	private boolean mouseWasDown = false;
	public boolean buildMode = false;
	public boolean killMode = false;
	public boolean paused = false;
	public boolean holdingAnt = false;
	public boolean showGrid = false;
	public boolean debugMode = false;

	private long lastSfxTime = 0;

	private BufferedImage plainDirt;
	private BufferedImage blueprint;

	private BufferedImage pause;

	private BufferedImage cursor0;
	private BufferedImage cursor1;
	private BufferedImage cursor2;
	private BufferedImage cursor3;

	private BufferedImage cursorShovel;
	private BufferedImage cursorBoot;

	private BufferedImage UI;
	private static File uiSFX = new File("ui.wav");

	private BufferedImage bgImage;

	private BufferedImage ladder;

	private BufferedImage ant_basic;
	private static File splatSFX = new File("splat.wav");
	private BufferedImage wall;
	private BufferedImage floor;
	private static File digSFX = new File("dig.wav");

	private BufferedImage buildButton0;
	private BufferedImage buildButton1;
	private BufferedImage buildButton2;

	private BufferedImage pauseButton0;
	private BufferedImage pauseButton1;
	private BufferedImage pauseButton2;

	private BufferedImage killButton0;
	private BufferedImage killButton1;
	private BufferedImage killButton2;

	private final Canvas canvas = new Canvas() {
		@Override
		public void paint(Graphics g) {
		}
	};

	public GameWindow() {
		Timer drawTimer = new Timer(1000 / 60, e -> repaint());
		drawTimer.start();
		Timer updateTimer = new Timer(100, e -> update());
		updateTimer.start();
		setTitle("Ants: Hillbuilder");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setResizable(false);

		canvas.setPreferredSize(new Dimension(WIDTH, HEIGHT));
		canvas.setIgnoreRepaint(true);
		add(canvas);
		pack();
		setLocationRelativeTo(null);

		Toolkit tk = Toolkit.getDefaultToolkit();
		BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Cursor hiddenCursor = tk.createCustomCursor(blank, new Point(0, 0), "hidden");
		canvas.setCursor(hiddenCursor);

		canvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				mouseDown = true;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				mouseDown = false;
			}
		});

		canvas.setFocusable(true);
		canvas.requestFocus();
		canvas.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int key = e.getKeyCode();
				if (key == KeyEvent.VK_ESCAPE) {
					System.exit(0);
				} else if (key == KeyEvent.VK_G) {
					if (showGrid) {
						showGrid = false;
					} else {
						showGrid = true;
					}
				} else if (key == KeyEvent.VK_COMMA) {
					if (debugMode) {
						debugMode = false;
					} else {
						debugMode = true;
					}
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}
		});
	}

	public void moveActors() {
		antGridHistory = new Ant[50][33];
		int LatestRand;

		for (int x = 0; x < 50; x++) {
			for (int y = 0; y < 33; y++) {
				Ant ant = antGrid[x][y];
				if (ant != null) {
					LatestRand = (int) (Math.random() * 4 + 1);
					switch (LatestRand) {
					case 1:
						if (x + 1 < 50 && grid[x + 1][y] && antGrid[x + 1][y] == null
								&& antGridHistory[x + 1][y] == null) {
							ant.rotation = Rotation.EAST;
							antGridHistory[x + 1][y] = ant;
						} else {
							antGridHistory[x][y] = ant;
						}
						break;
					case 2:
						if (x - 1 >= 0 && grid[x - 1][y] && antGrid[x - 1][y] == null
								&& antGridHistory[x - 1][y] == null) {
							ant.rotation = Rotation.WEST;
							antGridHistory[x - 1][y] = ant;
						} else {
							antGridHistory[x][y] = ant;
						}
						break;
					case 3:
						if (y + 1 < 33 && grid[x][y + 1] && antGrid[x][y + 1] == null
								&& antGridHistory[x][y + 1] == null) {
							ant.rotation = Rotation.SOUTH;
							antGridHistory[x][y + 1] = ant;
						} else {
							antGridHistory[x][y] = ant;
						}
						break;
					case 4:
						if (y - 1 >= 0 && grid[x][y - 1] && antGrid[x][y - 1] == null
								&& antGridHistory[x][y - 1] == null) {
							ant.rotation = Rotation.NORTH;
							antGridHistory[x][y - 1] = ant;
						} else {
							antGridHistory[x][y] = ant;
						}
						break;
					}
				}
			}
		}

		antGrid = antGridHistory;
	}

	public void run() throws IOException {
		setVisible(true);
		canvas.createBufferStrategy(2);
		final long nsPerFrame = 1_000_000_000L / TARGET_FPS;

		setup();
		while (true) {
			long now = System.nanoTime();
			update();
			long frameStart = System.nanoTime();

			do {
				Graphics2D g = (Graphics2D) canvas.getBufferStrategy().getDrawGraphics();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				draw(g);
				drawCursor(g);

				g.dispose();
			} while (canvas.getBufferStrategy().contentsRestored());
			canvas.getBufferStrategy().show();

			long elapsed = System.nanoTime() - frameStart;
			long sleepNs = nsPerFrame - elapsed;
			if (sleepNs > 0) {
				try {
					Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
				} catch (InterruptedException ignored) {
				}
			}
		}
	}

	private BufferedImage antButton0;
	private BufferedImage antButton1;
	private BufferedImage antButton2;
	
	
	private Map<Integer, BufferedImage> borderTextures = new HashMap<>();
	private void setup() throws IOException {
		 BufferedImage border       = ImageIO.read(new File("border.png"));
		    BufferedImage borderCorner = ImageIO.read(new File("border_corner.png"));

		    BufferedImage edgeN = border;
		    BufferedImage edgeE = rotateImage(border, Rotation.EAST);
		    BufferedImage edgeS = rotateImage(border, Rotation.SOUTH);
		    BufferedImage edgeW = rotateImage(border, Rotation.WEST);

		    BufferedImage cornerNE = borderCorner;
		    BufferedImage cornerSE = rotateImage(borderCorner, Rotation.EAST);
		    BufferedImage cornerSW = rotateImage(borderCorner, Rotation.SOUTH);
		    BufferedImage cornerNW = rotateImage(borderCorner, Rotation.WEST);

		    borderTextures.put(N, edgeN);
		    borderTextures.put(E, edgeE);
		    borderTextures.put(S, edgeS);
		    borderTextures.put(W, edgeW);

		    borderTextures.put(N | E, cornerNE);
		    borderTextures.put(S | E, cornerSE);
		    borderTextures.put(S | W, cornerSW);
		    borderTextures.put(N | W, cornerNW);
		uneditable[41][32] = true;
		uneditable[42][32] = true;
		uneditable[43][32] = true;
		uneditable[44][32] = true;
		uneditable[45][32] = true;
		uneditable[46][32] = true;
		uneditable[47][32] = true;

		uneditable[14][3] = true;
		uneditable[14][4] = true;

		antGrid[14][5] = new Ant(Rotation.NORTH);

		uneditable[14][5] = true;
		uneditable[14][6] = true;
		uneditable[13][5] = true;
		uneditable[13][6] = true;
		uneditable[15][5] = true;
		uneditable[15][6] = true;

		grid[14][5] = true;
		grid[14][6] = true;
		grid[13][5] = true;
		grid[13][6] = true;
		grid[15][5] = true;
		grid[15][6] = true;

		cursor0 = ImageIO.read(new File("cursor_0.png"));
		cursor1 = ImageIO.read(new File("cursor_1.png"));
		cursor2 = ImageIO.read(new File("cursor_2.png"));
		cursorShovel = ImageIO.read(new File("cursor_shovel.png"));
		cursorBoot = ImageIO.read(new File("boot.png"));
		ant_basic = ImageIO.read(new File("ant_default.png"));

		pauseButton0 = ImageIO.read(new File("pause0.png"));
		pauseButton1 = ImageIO.read(new File("pause_2.png"));
		pauseButton2 = ImageIO.read(new File("pause_3.png"));

		killButton0 = ImageIO.read(new File("kill_0.png"));
		killButton1 = ImageIO.read(new File("kill_1.png"));
		killButton2 = ImageIO.read(new File("kill_2.png"));
		
		antButton0 = ImageIO.read(new File("ant_button_0.png"));
		antButton1 = ImageIO.read(new File("ant_button_1.png"));
		antButton2 = ImageIO.read(new File("ant_button_2.png"));

		pause = ImageIO.read(new File("pause_indicator.png"));

		plainDirt = ImageIO.read(new File("plain_dirt.png"));
		blueprint = ImageIO.read(new File("blueprint.png"));

		UI = ImageIO.read(new File("stone_ui.png"));

		ladder = ImageIO.read(new File("ladder.png"));

		floor = ImageIO.read(new File("floor.png"));

		wall = ImageIO.read(new File("wall.png"));

		buildButton0 = ImageIO.read(new File("build_button_0.png"));

		buildButton1 = ImageIO.read(new File("build_button_1.png"));

		buildButton2 = ImageIO.read(new File("build_button_2.png"));
		try {

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private int tick = 0;

	private void update() {
		int currentCol = (mouseX - offset) / cellSize;
		int currentRow = (mouseY - offset) / cellSize;
		tick++;
		if (tick >= 500) {
			tick = 0;
		}
		if (tick % 23 == 0 && !paused) {
			moveActors();
		}
		if (buildMode) {
			Cursor = cursorLooks.SHOVEL;
		} else if (killMode) {

			Cursor = cursorLooks.BOOT;
		} else if(holdingAnt) {
			Cursor = cursorLooks.ANT;
		}else {

			Cursor = cursorLooks.CLICKER;
		}
		if(holdingAnt) {
			buildMode = false;
			killMode = false;
		} else if (buildMode){
			killMode = false;
		}
		long currentTime = System.currentTimeMillis();
		if (mouseDown && !mouseWasDown && holdingAnt && !(mouseX > 450 && mouseY > 490 && mouseX < 515 && mouseY < 555)) {
			holdingAnt = false;
			if (currentCol >= 0 && currentCol < antGrid.length && currentRow >= 0 && currentRow < antGrid[0].length) {

				if (antGrid[currentCol][currentRow] == null && grid[currentCol][currentRow]) {
					antGrid[currentCol][currentRow] = new Ant(Rotation.NORTH);

					if (currentTime - lastSfxTime >= 90) {
						playWav(splatSFX);
						lastSfxTime = currentTime;
					}
				}

			}
		}
		if  (mouseDown && !mouseWasDown && mouseX > 450 && mouseY > 490 && mouseX < 515 && mouseY < 555) {
			if(holdingAnt) {
				holdingAnt = false;
			} else {
				holdingAnt = true;
			}
			if (currentTime - lastSfxTime >= 90) {
				playWav(uiSFX);
				lastSfxTime = currentTime;
			}
		}
		if (mouseDown && !mouseWasDown && killMode) {
			if (currentCol >= 0 && currentCol < antGrid.length && currentRow >= 0 && currentRow < antGrid[0].length) {

				if (antGrid[currentCol][currentRow] != null) {
					antGrid[currentCol][currentRow] = null;

					if (currentTime - lastSfxTime >= 90) {
						playWav(splatSFX);
						lastSfxTime = currentTime;
					}
				}
			}
		}

		if (mouseDown && !mouseWasDown && mouseX > 448 && mouseY > 568 && mouseX < 516 && mouseY < 640) {
			if (killMode) {
				killMode = false;
				buildMode = false;
			} else {

				killMode = true;
			}
			if (currentTime - lastSfxTime >= 90) {
				playWav(uiSFX);
				lastSfxTime = currentTime;
			}
		}
		if (mouseDown && !mouseWasDown && mouseX > 530 && mouseY > 515 && mouseX < 640 && mouseY < 630) {
			if (paused) {
				paused = false;
			} else {
				paused = true;
			}
			if (currentTime - lastSfxTime >= 90) {
				playWav(uiSFX);
				lastSfxTime = currentTime;
			}
		}
		if (mouseDown && !mouseWasDown && ((mouseX > 650 && mouseY > 500) && (mouseX < 775 && mouseY < 625))) {
			if (buildMode) {
				buildMode = false;
				killMode = false;
				paused = false;
			} else {
				buildMode = true;
				paused = true;
			}
			if (currentTime - lastSfxTime >= 90) {
				playWav(uiSFX);
				lastSfxTime = currentTime;
			}

		}

		if (mouseDown && buildMode) {
			int col = (mouseX - offset) / cellSize;
			int row = (mouseY - offset) / cellSize;

			if (col >= 0 && col < grid.length && row >= 0 && row < grid[0].length) {

				boolean isPlacingNewCell = !grid[col][row];

				boolean hasAdjacentTrue = false;
				if (isPlacingNewCell) {
					hasAdjacentTrue = (col > 0 && grid[col - 1][row]) || (col < grid.length - 1 && grid[col + 1][row])
							|| (row > 0 && grid[col][row - 1]) || (row < grid[0].length - 1 && grid[col][row + 1]);
				}

				boolean canToggle = !isPlacingNewCell || hasAdjacentTrue;

				if (canToggle && !toggleHistory[col][row] && !uneditable[col][row] && antGrid[col][row] == null) {
					grid[col][row] = !grid[col][row];

					if (currentTime - lastSfxTime >= 90) {
						playWav(digSFX);
						lastSfxTime = currentTime;
					}

					toggleHistory[col][row] = true;
				}
			}
		} else if (mouseWasDown) {
			for (int i = 0; i < toggleHistory.length; i++) {
				for (int j = 0; j < toggleHistory[0].length; j++) {
					toggleHistory[i][j] = false;
				}
			}
		}

		mouseWasDown = mouseDown;
	}

	private boolean isFloor(boolean[][] grid, int col, int row) {
		if (col < 0 || col >= grid.length)
			return false;
		if (row < 0 || row >= grid[0].length)
			return false;
		return grid[col][row];
	}

	private static final int N = 1;
	private static final int S = 2;
	private static final int E = 4;
	private static final int W = 8;
	private static final int NE = 16;
	private static final int NW = 32;
	private static final int SE = 64;
	private static final int SW = 128;

	private int getNeighbourMask(boolean[][] grid, int col, int row) {
		int mask = 0;
		if (isFloor(grid, col, row - 1))
			mask |= N;
		if (isFloor(grid, col, row + 1))
			mask |= S;
		if (isFloor(grid, col + 1, row))
			mask |= E;
		if (isFloor(grid, col - 1, row))
			mask |= W;
		if (isFloor(grid, col + 1, row - 1))
			mask |= NE;
		if (isFloor(grid, col - 1, row - 1))
			mask |= NW;
		if (isFloor(grid, col + 1, row + 1))
			mask |= SE;
		if (isFloor(grid, col - 1, row + 1))
			mask |= SW;
		return mask;
	}

	private void draw(Graphics2D g) {
		Point p = MouseInfo.getPointerInfo().getLocation();
		SwingUtilities.convertPointFromScreen(p, canvas);
		mouseX = p.x;
		mouseY = p.y;
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		if (!buildMode) {
			g.drawImage(plainDirt, 0, 0, WIDTH, HEIGHT, null);
		} else {

			g.drawImage(blueprint, 0, 0, WIDTH, HEIGHT, null);
		}

		g.drawImage(UI, 0, 0, WIDTH, HEIGHT, null);

		g.setColor(Color.WHITE);
		g.drawString("Test Version 0.0.0a", 20, 30);
		g.setColor(Color.LIGHT_GRAY);

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int x = offset + (col * cellSize);
				int y = offset + (row * cellSize);

				if (isFloor(grid, col, row)) {
					g.drawImage(floor, x, y, cellSize, cellSize, null);
				}
				if (row - 1 != -1 && row + 1 != 33) {
					if ((grid[col][row + 1]) && !grid[col][row]) {
						g.drawImage(wall, x, y, cellSize, cellSize, null);
					}
				}
				if (!grid[col][row]) {
				    int mask = getNeighbourMask(grid, col, row);

				    boolean n = (mask & N) != 0;
				    boolean s = (mask & S) != 0;
				    boolean e = (mask & E) != 0;
				    boolean w = (mask & W) != 0;
				    boolean ne = (mask & NE) != 0;
				    boolean nw = (mask & NW) != 0;
				    boolean se = (mask & SE) != 0;
				    boolean sw = (mask & SW) != 0;

				    if (n) g.drawImage(borderTextures.get(N), x, y, cellSize, cellSize, null);
				    if (s) g.drawImage(borderTextures.get(S), x, y, cellSize, cellSize, null);
				    if (e) g.drawImage(borderTextures.get(E), x, y, cellSize, cellSize, null);
				    if (w) g.drawImage(borderTextures.get(W), x, y, cellSize, cellSize, null);

				    if (ne && !n && !e) g.drawImage(borderTextures.get(N | E), x, y, cellSize, cellSize, null);
				    if (nw && !n && !w) g.drawImage(borderTextures.get(N | W), x, y, cellSize, cellSize, null);
				    if (se && !s && !e) g.drawImage(borderTextures.get(S | E), x, y, cellSize, cellSize, null);
				    if (sw && !s && !w) g.drawImage(borderTextures.get(S | W), x, y, cellSize, cellSize, null);
				}
			}
		}
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int x = offset + (col * cellSize);
				int y = offset + (row * cellSize);

				int biggerCellsize = (int) ((int) cellSize * 1.5);
				if (row - 1 != -1 && row + 1 != 33 && antGrid[col][row] != null) {
					switch (antGrid[col][row].getRot()) {
					case NORTH:
						g.drawImage(ant_basic, x - 4, y - 4, biggerCellsize, biggerCellsize, null);
						break;
					case EAST:
						g.drawImage(rotateImage(ant_basic, Rotation.EAST), x - 4, y - 4, biggerCellsize, biggerCellsize,
								null);
						break;
					case SOUTH:
						g.drawImage(rotateImage(ant_basic, Rotation.SOUTH), x - 4, y - 4, biggerCellsize,
								biggerCellsize, null);
						break;
					case WEST:
						g.drawImage(rotateImage(ant_basic, Rotation.WEST), x - 4, y - 4, biggerCellsize, biggerCellsize,
								null);
						break;
					}
				}
			}
		}

		g.drawImage(ladder, 0, 0, WIDTH, HEIGHT, null);

		if (showGrid) {
			for (int row = 0; row < rows; row++) {
				for (int col = 0; col < cols; col++) {
					int x = offset + (col * cellSize);
					int y = offset + (row * cellSize);

					g.drawRect(x, y, cellSize, cellSize);
				}
			}
		}
		if (!buildMode) {

			if (((mouseX > 650 && mouseY > 500) && (mouseX < 775 && mouseY < 625))) {
				g.drawImage(buildButton2, 0, 0, WIDTH, HEIGHT, null);
			} else {
				g.drawImage(buildButton0, 0, 0, WIDTH, HEIGHT, null);

			}
			if (debugMode) {
				g.drawString("mouseX" + mouseX, 20, 50);
				g.drawString("mouseY" + mouseY, 20, 70);
			}
		} else {
			g.drawImage(buildButton1, 0, 16, WIDTH, HEIGHT, null);
		}
		if (paused) {
			g.drawImage(pause, 0, 0, WIDTH, HEIGHT, null);
		}
		if (!paused) {
			if (mouseX > 530 && mouseY > 515 && mouseX < 640 && mouseY < 630) {
				g.drawImage(pauseButton2, -4, 0, WIDTH, HEIGHT, null);
			} else {
				g.drawImage(pauseButton1, 0, -8, WIDTH, HEIGHT, null);
			}
		} else {
			g.drawImage(pauseButton1, 0, 8, WIDTH, HEIGHT, null);
		}
		if (!killMode) {
			if (mouseX > 450 && mouseY > 570 && mouseX < 515 && mouseY < 640) {
				g.drawImage(killButton1, -8, 0, WIDTH, HEIGHT, null);
			} else {
				g.drawImage(killButton0, -8, 0, WIDTH, HEIGHT, null);
			}
		} else if (killMode) {
			g.drawImage(killButton2, -8, 0, WIDTH, HEIGHT, null);
		}
		if (!holdingAnt) {
			if (mouseX > 450 && mouseY > 490 && mouseX < 515 && mouseY < 555) {
				g.drawImage(antButton1, -8, 0, WIDTH, HEIGHT, null);
			} else {
				g.drawImage(antButton0, -8, 0, WIDTH, HEIGHT, null);
			}
		} else if (holdingAnt) {
			g.drawImage(antButton2, -8, 0, WIDTH, HEIGHT, null);
		}
		if ((mouseX > 530 && mouseY > 515 && mouseX < 640 && mouseY < 630)
				|| ((mouseX > 650 && mouseY > 500) && (mouseX < 775 && mouseY < 625))) {
			hungy = true;
		} else {
			hungy = false;
		}
	}

	private void drawCursor(Graphics2D g) {
		switch (Cursor) {
		case CLICKER:
			if (hungy) {
				g.drawImage(cursor1, mouseX, mouseY, 24, 24, null);
			} else {
				g.drawImage(cursor0, mouseX, mouseY, 24, 24, null);
			}
			if (mouseDown) {
				g.drawImage(cursor2, mouseX, mouseY, 24, 24, null);
			}
			break;
		case SHOVEL:
			g.drawImage(cursorShovel, mouseX, mouseY, 56, 56, null);
			break;
		case BOOT:
			g.drawImage(cursorBoot, mouseX - 24, mouseY - 28, 56, 56, null);
			break;
		case ANT:
			g.drawImage(ant_basic, mouseX - 8, mouseY - 8, 24, 24, null);
			break;
		default:
			break;
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			GameWindow gw = new GameWindow();
			gw.setVisible(true);
			new Thread(() -> {
				try {
					gw.run();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}).start();
		});
	}
}