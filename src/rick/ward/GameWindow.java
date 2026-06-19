package rick.ward;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class GameWindow extends JFrame {

	public enum cursorLooks {
		CLICKER, SHOVEL
	}

	cursorLooks Cursor = cursorLooks.CLICKER;
	private boolean hungy = false;

	public void playWav(File SFX) {
		new Thread(() -> {
			try {

				// Set up the audio input stream from the file source
				AudioInputStream audioIn = AudioSystem.getAudioInputStream(SFX);

				// Get a sound clip resource line instance
				Clip clip = AudioSystem.getClip();

				// Open the audio data line stream and load it to memory
				clip.open(audioIn);

				// Play from the beginning
				clip.start();

				// Optional Listener: Automatically closes the clip line once the sound finishes
				// playing
				// This frees system audio channels and prevents native memory leaks
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
	public boolean showGrid = false;
	public boolean debugMode = false;

	private long lastSfxTime = 0;

	public static boolean[][] uneditable = new boolean[50][33];

	private BufferedImage plainDirt;
	private BufferedImage blueprint;

	private BufferedImage cursor0;
	private BufferedImage cursor1;
	private BufferedImage cursor2;
	private BufferedImage cursor3;

	private BufferedImage cursorShovel;

	private BufferedImage UI;
	private static File uiSFX = new File("ui.wav");

	private BufferedImage bgImage;

	private BufferedImage ladder;

	private BufferedImage wall;

	private BufferedImage floor;
	private static File digSFX = new File("dig.wav");

	private BufferedImage buildButton0;

	private BufferedImage buildButton1;

	private BufferedImage buildButton2;

	private final Canvas canvas = new Canvas() {
		@Override
		public void paint(Graphics g) {
		}
	};

	public GameWindow() {
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

	public void run() throws IOException {
		canvas.createBufferStrategy(2);

		final long nsPerFrame = 1_000_000_000L / TARGET_FPS;

		setup();

		while (true) {
			long frameStart = System.nanoTime();

			update();

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

	private void setup() throws IOException {
		
		uneditable[41][32] = true;
		uneditable[42][32] = true;
		uneditable[43][32] = true;
		uneditable[44][32] = true;
		uneditable[45][32] = true;
		uneditable[46][32] = true;
		uneditable[47][32] = true;
		
		
		
		
		uneditable[14][3] = true;
		uneditable[14][4] = true;

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
//			bgImage = ImageIO.read(new File("background.png"));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private final boolean[][] toggleHistory = new boolean[50][33];

	private void update() {
		if (buildMode) {
			Cursor = cursorLooks.SHOVEL;
		} else {
			Cursor = cursorLooks.CLICKER;
		}
		long currentTime = System.currentTimeMillis();
		Point p = MouseInfo.getPointerInfo().getLocation();
		SwingUtilities.convertPointFromScreen(p, canvas);
		mouseX = p.x;
		mouseY = p.y;

		if (mouseDown && !mouseWasDown && ((mouseX > 650 && mouseY > 500) && (mouseX < 775 && mouseY < 625))) {
			if (buildMode) {
				buildMode = false;
			} else {
				buildMode = true;
			}
			if (currentTime - lastSfxTime >= 90) {
				playWav(uiSFX); // Or playWav("click.wav");
				lastSfxTime = currentTime; // Reset the timer
			}

		}

		if (mouseDown && buildMode) {
			// Convert raw pixel positions into 2D grid indexes
			int col = (mouseX - offset) / cellSize;
			int row = (mouseY - offset) / cellSize;

			// Validate coordinates inside grid dimensional boundaries
			if (col >= 0 && col < grid.length && row >= 0 && row < grid[0].length) {
				// Invert the boolean value of the target cell
				if (!toggleHistory[col][row] && !uneditable[col][row]) {
					grid[col][row] = !grid[col][row];
					if (currentTime - lastSfxTime >= 90) {
						playWav(digSFX); // Or playWav("click.wav");
						lastSfxTime = currentTime; // Reset the timer
					}
					// If you implemented the sound effect from earlier, trigger it here:
					// playClickSound();

					// 3. Cache these coordinates so this cell isn't toggled again during this drag
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

	public static boolean[][] grid = new boolean[50][33];
	int cellSize = 16;
	int rows = grid[0].length;
	int cols = grid.length;
	int offset = 0;

	private void draw(Graphics2D g) {
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
				// Calculate precise pixel positions for the current cell
				int x = offset + (col * cellSize);
				int y = offset + (row * cellSize);

				// Draw the cell outline
				// g.drawRect(x, y, cellSize, cellSize);
				if (grid[col][row]) {
					g.drawImage(floor, x, y, cellSize, cellSize, null);
				}
				if (row - 1 != -1 && row + 1 != 33) {
					if (!grid[col][row - 1] && grid[col][row + 1] && !grid[col][row]) {
						g.drawImage(wall, x, y, cellSize, cellSize, null);
					}
				}
			}
		}
		g.drawImage(ladder, 0, 0, WIDTH, HEIGHT, null);

		if (showGrid) {
			for (int row = 0; row < rows; row++) {
				for (int col = 0; col < cols; col++) {
					// Calculate precise pixel positions for the current cell
					int x = offset + (col * cellSize);
					int y = offset + (row * cellSize);

					// Draw the cell outline
					g.drawRect(x, y, cellSize, cellSize);
				}
			}
		}
// 650 500 775 625
		if (!buildMode) {
			if (((mouseX > 650 && mouseY > 500) && (mouseX < 775 && mouseY < 625))) {
				hungy = true;
				g.drawImage(buildButton2, 0, 0, WIDTH, HEIGHT, null);
			} else {
				g.drawImage(buildButton0, 0, 0, WIDTH, HEIGHT, null);
				hungy = false;
			}
			if (debugMode) {
				g.drawString("mouseX" + mouseX, 20, 50);
				g.drawString("mouseY" + mouseY, 20, 70);
			}
		} else {
			g.drawImage(buildButton1, 0, 16, WIDTH, HEIGHT, null);
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
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}).start();
		});
	}

}
