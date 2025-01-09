package minesweeper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.List;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import java.util.function.BiFunction;
import java.io.IOException;
import java.text.ParseException;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.net.URLClassLoader;
import java.net.URL;

public final class Graphics {
    long start_time = 0;
    long total_time = -1;

    int games_won = 0;
    int games_lost = 0;

    public final class GameBoard extends Game {

        // Nested class representing one tile of a Minesweeper game.
        public final class BoardTile extends JButton {
            // Set up the aesthetics for the tiles
            private static final Map<Integer, Color> colors = Map.ofEntries(
                Map.entry(0, new Color(192, 192, 192)),
                Map.entry(1, new Color(0, 0, 255)),
                Map.entry(2, new Color(0, 128, 0)),
                Map.entry(3, new Color(255, 0, 0)),
                Map.entry(4, new Color(0, 0, 128)),
                Map.entry(5, new Color(128, 0, 0)),
                Map.entry(6, new Color(0, 128, 128)),
                Map.entry(7, new Color(0, 0, 0)),
                Map.entry(8, new Color(128, 128, 128)),
                Map.entry(MINE, new Color(0, 0, 0))
            );
            private static final Map<String, Icon> icons = Map.ofEntries(
                Map.entry("mine", new ImageIcon("assets/9.png")),
                Map.entry("flag", new ImageIcon("assets/flag.png")),
                Map.entry("wrong_flag", new ImageIcon("assets/9x.png"))
            );
            private static final int tile_size = 32;
            private static final Dimension tile_dimension = new Dimension(tile_size, tile_size);
            private static final Border tile_border = new BevelBorder(BevelBorder.RAISED);
            private static final Border open_border = null;
            private static final Font tile_font = new Font("Monospaced", Font.BOLD, tile_size * 2 / 3);

            public BoardTile(final Location loc) {
                this.addMouseListener(new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        if(GameBoard.this.state.compareTo(State.ACTIVE)>0){
                            return;
                        }
                        int v = GameBoard.this.board[loc.row][loc.col];
                        if(SwingUtilities.isLeftMouseButton(e)){
                            // When left click is held on the tile, give it a special border.
                            if(v==UNKNOWN){
                                BoardTile.this.setBorder(open_border);
                            }
                        }
                        else if(SwingUtilities.isRightMouseButton(e)){
                            // Flag a tile when right clicked.
                            if(v==UNKNOWN || v==MINE){
                                GameBoard.this.flag(loc);
                            }
                        }
                        else if(SwingUtilities.isMiddleMouseButton(e)){
                            // Do "chording" when middle clicking.
                            for(Location n : GameBoard.this.neighbors(loc)){
                                if(GameBoard.this.board[n.row][n.col]==UNKNOWN){
                                    GameBoard.this.tiles[n.row][n.col].setBorder(open_border);
                                }
                            }
                            if(v!=MINE){
                                BoardTile.this.setBorder(open_border);
                            }
                        }
                    }

                    public void mouseReleased(MouseEvent e) {
                        int v = GameBoard.this.board[loc.row][loc.col];
                        if(SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)){
                            // When left click (or middle click) is released, set the border back to normal.
                            if(v==UNKNOWN || v==MINE){
                                BoardTile.this.setBorder(tile_border);
                            }
                        }
                        if(SwingUtilities.isMiddleMouseButton(e)){
                            // Attempt chording.
                            if(v==UNKNOWN || v==MINE){
                                // If the tile doesn't have a number, we can't chord, but we still want to reset the look of its neighbor tiles
                                v = -1;
                            }
                            for(Location n : GameBoard.this.neighbors(loc)){
                                if(GameBoard.this.board[n.row][n.col]==MINE){
                                    v--;
                                }
                            }
                            for(Location n : GameBoard.this.neighbors(loc)){
                                if(GameBoard.this.board[n.row][n.col]==UNKNOWN){
                                    if(v==0){
                                        GameBoard.this.open(n);
                                    }
                                    else{
                                        GameBoard.this.tiles[n.row][n.col].setBorder(tile_border);
                                    }
                                }
                            }
                            v = GameBoard.this.board[loc.row][loc.col];
                        }
                    }
                });
                // When the tile is clicked, open it.
                this.addActionListener((ActionEvent e) -> {
                    GameBoard.this.open(loc);
                });
                // Configure the look and feel of the tiles.
                this.setUI(new BasicButtonUI());
                this.setBorder(tile_border);
                this.setRolloverEnabled(false);
                this.setFont(tile_font);
                this.setPreferredSize(tile_dimension);
                this.setMinimumSize(tile_dimension);
                this.setMaximumSize(tile_dimension);
                this.setBackground(colors.get(0));
            }

            // Method to set the flag icon on the tile.
            public void setFlag(boolean status) {
                if (status) {
                    this.setIcon(icons.get("flag"));
                } else {
                    this.setIcon(null);
                }
            }

            // Method to reveal the content of the tile.
            public void revealAs(int n) {
                this.setBorder(open_border);
                if (n == MINE) {
                    this.setIcon(icons.get("mine"));
                } else {
                    final Color text_color = colors.get(n);
                    this.setForeground(text_color);
                    this.setText(Integer.toString(n));
                }
            }

            // Method to reveal the tile at the end of the game.
            public void revealEndgame(Tile info) {
                if (info.number == MINE) {
                    if (!info.flagged) {
                        this.setIcon(icons.get("mine"));
                    }
                    if (info.open) {
                        this.setBackground(Color.RED);
                    }
                } else if (info.flagged) {
                    this.setIcon(icons.get("wrong_flag"));
                }
            }
        }

        public JPanel root;
        public JLabel counter;
        private BoardTile[][] tiles;

        // Constructors for initializing the game board.
        public GameBoard(String filename) throws IOException, ParseException {
            super(filename);
            this.init();
        }

        public GameBoard(int height, int width, int mines) {
            this(height, width, mines, true);
        }

        public GameBoard(int height, int width, int mines, boolean zero_start) {
            super(height, width, mines, zero_start);
            this.init();
        }

        // Initialize the game board.
        private void init() {
            this.counter = new JLabel();
            this.counter.setForeground(Color.RED);
            this.counter.setFont(new Font("Monospaced", Font.BOLD, 32));
            this.root = new JPanel(new GridLayout(height, width));
            this.tiles = new BoardTile[this.height][this.width];
            for (int r = 0; r < this.height; r++) {
                for (int c = 0; c < this.width; c++) {
                    BoardTile tile = this.new BoardTile(new Location(r, c));
                    this.root.add(tile);
                    this.tiles[r][c] = tile;
                }
            }
            int length = Integer.toString(this.mines).length();
            this.counter.setText(String.format("%0" + length + "d", this.minecount));
        }

        // Method to open a tile.
        public void open(Location loc) {
            super.open(loc);
            if (this.full_board[loc.row][loc.col].open) {
                int n = this.full_board[loc.row][loc.col].number;
                Graphics.invokeSafe(() -> {
                    this.tiles[loc.row][loc.col].revealAs(n);
                });
            }
        }

        // Method to flag a tile.
        public void flag(Location loc) {
            super.flag(loc);
            if (this.state.compareTo(State.ACTIVE) > 0) {
                return;
            }
            if (!this.full_board[loc.row][loc.col].open) {
                Graphics.invokeSafe(() -> {
                    this.tiles[loc.row][loc.col].setFlag(this.full_board[loc.row][loc.col].flagged);
                });
            }
        }

        // Method to set the game state.
        protected void setState(State state) {
            super.setState(state);
            switch (state) {
                case State.ACTIVE -> {
                    Graphics.this.start_time = System.currentTimeMillis();
                }
                case State.WIN -> {
                    this.win();
                }
                case State.LOSE -> {
                    this.lose();
                }
            }
        }

        // Method to handle losing the game.
        public void lose() {
            Graphics.this.total_time = System.currentTimeMillis() - Graphics.this.start_time;
            Graphics.this.games_lost++;
            Graphics.invokeSafe(() -> {
                for (int r = 0; r < this.height; r++) {
                    for (int c = 0; c < this.width; c++) {
                        this.tiles[r][c].revealEndgame(this.full_board[r][c]);
                    }
                }
            });
        }

        // Method to handle winning the game.
        public void win() {
            Graphics.this.total_time = System.currentTimeMillis() - Graphics.this.start_time;
            Graphics.this.games_won++;
            Graphics.invokeSafe(() -> {
                for (int r = 0; r < this.height; r++) {
                    for (int c = 0; c < this.width; c++) {
                        if (this.full_board[r][c].number == MINE && !this.full_board[r][c].flagged) {
                            this.tiles[r][c].setFlag(true);
                        }
                    }
                }
            });
            this.setMinecount(0);
        }

        // Method to set the mine count.
        public void setMinecount(int n) {
            super.setMinecount(n);
            final int length = Integer.toString(this.mines).length();
            Graphics.invokeSafe(() -> {
                this.counter.setText(String.format("%0" + length + "d", n));
            });
        }

        private Thread worker = null;

        // Method to start a worker thread for AI actions.
        private void start_worker(Runnable task) {
            if (this.ai == null) {
                return;
            }
            if (this.state.compareTo(Game.State.ACTIVE) > 0) {
                return;
            }
            if (this.worker != null && this.worker.isAlive()) {
                return;
            }
            this.worker = new Thread(task);
            this.worker.start();
        }

        // Method to process AI actions.
        protected void process_action(Agent.Action move) {
            if (!Thread.currentThread().isInterrupted()) {
                super.process_action(move);
            }
        }

        // Method for AI to play the game.
        public void ai_play() {
            while (this.state.compareTo(State.ACTIVE) <= 0 && !Thread.currentThread().isInterrupted()) {
                this.ai_move();
            }
        }

        // Method for AI to make a move in a separate thread.
        public void ai_move_thread() {
            this.start_worker(this::ai_move);
        }

        // Method for AI to play the full game in a separate thread.
        public void ai_play_thread() {
            if (this.worker != null && this.worker.isAlive()) {
                this.worker.interrupt();
                return;
            }
            this.start_worker(this::ai_play);
        }
    }

    private final JFrame frame;
    private final JPanel board_container;
    private GameBoard board = null;

    private final static ImageIcon normal_icon = new ImageIcon("assets/swag.png");


    private int height_entered = 16;
    private int width_entered = 30;
    private int mines_entered = 99;
    private boolean zero_start_entered = true;
    private String import_path_entered;
    private Class<? extends Agent> ai_class = minesweeper.strategies.LitStrategy.class;

    private URLClassLoader loader = new URLClassLoader(new URL[]{});

    // Constructor to initialize the graphics.
    public Graphics() {
        this.frame = new JFrame("Mining sweeper");
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setResizable(false);
        this.frame.setLayout(new BorderLayout(2, 2));

        this.board_container = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        this.frame.add(this.board_container, BorderLayout.SOUTH);

        JPanel buttons_container = new JPanel(new GridLayout(1, 4, 2, 2));
        this.frame.add(buttons_container, BorderLayout.EAST);

        // New game button.
        JButton new_game_button = new JButton(" New game ");
        new_game_button.addActionListener((ActionEvent e) -> {
            this.newGame();
        });
        buttons_container.add(new_game_button);

        // AI move button.
        JButton ai_move_button = new JButton("AI move");
        ai_move_button.addActionListener((ActionEvent e) -> {
            this.board.ai_move_thread();
        });
        buttons_container.add(ai_move_button);

        // AI autoplay button.
        final String autoplay_inactive = "AI autoplay";
        final String autoplay_active = "Stop";
        final JButton ai_autoplay_button = new JButton(autoplay_inactive);
        ai_autoplay_button.addActionListener((ActionEvent e) -> {
            ai_autoplay_button.setText(ai_autoplay_button.getText().equals(autoplay_inactive) ? autoplay_active : autoplay_inactive);
            this.board.ai_play_thread();
        });
        new_game_button.addActionListener((ActionEvent e) -> {
            ai_autoplay_button.setText(autoplay_inactive);
        });
        buttons_container.add(ai_autoplay_button);

        // Settings button.
        JButton settings_button = new JButton("Settings");
        buttons_container.add(settings_button);

        // Settings panel.
        final JPanel settings = new JPanel(new GridBagLayout());
        final GridBagConstraints settings_constraints = new GridBagConstraints();
        settings_constraints.gridy = 1;
        settings_constraints.gridx = 0;
        settings_constraints.insets = new Insets(4, 4, 4, 4);
        settings_constraints.fill = GridBagConstraints.HORIZONTAL;

        // Add dimension inputs.
        BiFunction<String, Integer, JSpinner> add_dimension_input = (String label, Integer initial) -> {
            settings.add(new JLabel(label), settings_constraints);
            settings_constraints.gridx++;
            JSpinner input = new JSpinner(new SpinnerNumberModel(initial.intValue(), 0, 999, 1));
            settings.add(input, settings_constraints);
            settings_constraints.gridx--;
            settings_constraints.gridy++;
            return input;
        };
        final JSpinner height_input = add_dimension_input.apply("Height", this.height_entered);
        final JSpinner width_input = add_dimension_input.apply("Width", this.width_entered);
        final JSpinner mines_input = add_dimension_input.apply("Mines", this.mines_entered);

        // Zero start input.
        settings.add(new JLabel("1st move 0"), settings_constraints);
        settings_constraints.gridx++;
        final JCheckBox zero_input = new JCheckBox();
        zero_input.setSelected(this.zero_start_entered);
        settings.add(zero_input, settings_constraints);
        settings_constraints.gridx--;
        settings_constraints.gridy++;

        // Import board input.
        settings_constraints.gridwidth = GridBagConstraints.REMAINDER;
        settings.add(new JSeparator(), settings_constraints);
        settings_constraints.gridwidth = 1;
        settings_constraints.gridy++;

        settings.add(new JLabel("Imported board"), settings_constraints);
        settings_constraints.gridx++;
        final JCheckBox import_input = new JCheckBox();
        settings.add(import_input, settings_constraints);
        settings_constraints.gridx--;
        settings_constraints.gridy++;

        final JTextField path_entry = new JTextField();
        settings_constraints.gridwidth = GridBagConstraints.REMAINDER;
        settings.add(path_entry, settings_constraints);
        settings_constraints.gridwidth = 1;
        settings_constraints.gridy++;

        // File chooser for importing boards.
        final JFileChooser fc = new JFileChooser("./boards");
        fc.setFileFilter(new FileNameExtensionFilter("Plaintext (.txt)", "txt"));
        fc.setMultiSelectionEnabled(false);

        final JButton import_button = new JButton("Import...");
        settings.add(import_button, settings_constraints);
        settings_constraints.gridy++;
        import_button.addActionListener((ActionEvent e) -> {
            switch (fc.showOpenDialog(settings)) {
                case JFileChooser.APPROVE_OPTION:
                    path_entry.setText(fc.getSelectedFile().getPath());
                    break;
            }
        });

        import_input.addActionListener((ActionEvent e) -> {
            for (JComponent c : new JComponent[]{height_input, width_input, mines_input, zero_input}) {
                c.setEnabled(!import_input.isSelected());
            }
            for (JComponent c : new JComponent[]{import_button, path_entry}) {
                c.setEnabled(import_input.isSelected());
            }
        });
        import_input.setSelected(true);
        import_input.doClick();

        // Button to export the current board.
        JButton export_button = new JButton("Export...");
        settings.add(export_button, settings_constraints);
        settings_constraints.gridy++;
        export_button.addActionListener((ActionEvent e) -> {
            switch (fc.showSaveDialog(settings)) {
                case JFileChooser.APPROVE_OPTION:
                    String filename = fc.getSelectedFile().getPath();
                    if (!fc.getFileFilter().accept(fc.getSelectedFile())) {
                        filename += ".txt";
                    }
                    try {
                        this.board.export(filename);
                        path_entry.setText(filename);
                    } catch (Exception exc) {
                        JOptionPane.showMessageDialog(settings, exc);
                    }
                    break;
            }
        });

        settings_constraints.gridwidth = GridBagConstraints.REMAINDER;
        settings.add(new JSeparator(), settings_constraints);
        settings_constraints.gridy++;
        settings.add(new JLabel("Agent's fully qualified name"), settings_constraints);
        settings_constraints.gridy++;

        // AI entry field.
        final JTextField ai_entry = new JTextField(this.ai_class != null ? this.ai_class.getName() : "");
        settings.add(ai_entry, settings_constraints);
        settings_constraints.gridy++;

        // File chooser for adding classes to the class loader.
        final JFileChooser ai_chooser = new JFileChooser(".");
        ai_chooser.setFileFilter(new FileNameExtensionFilter("JVM classes (*.class, *.jar, folders containing classes)", "jar", "class"));
        ai_chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        final JButton class_adder_button = new JButton("Add to class loader...");
        settings.add(class_adder_button, settings_constraints);
        settings_constraints.gridy++;
        class_adder_button.addActionListener((ActionEvent e) -> {
            switch (ai_chooser.showOpenDialog(settings)) {
                case JFileChooser.APPROVE_OPTION:
                    File f = ai_chooser.getSelectedFile();
                    if (f.getPath().endsWith(".class")) {
                        f = f.getParentFile();
                    }
                    try {
                        URL[] urls = new URL[this.loader.getURLs().length + 1];
                        System.arraycopy(this.loader.getURLs(), 0, urls, 0, urls.length - 1);
                        urls[urls.length - 1] = f.toURI().toURL();
                        this.loader = new URLClassLoader(urls);
                    } catch (Exception exc) {
                        JOptionPane.showMessageDialog(settings, exc);
                    }
            }
        });

        settings.add(new JSeparator(), settings_constraints);
        settings_constraints.gridy++;

        // Time and stats display.
        final JLabel time_display = new JLabel("No games played yet");
        time_display.setForeground(new Color(0.6f, 0.0f, 0.0f));
        settings.add(time_display, settings_constraints);
        settings_constraints.gridy++;

        final JLabel stats_display = new JLabel();
        stats_display.setForeground(new Color(0.6f, 0.0f, 0.0f));
        settings.add(stats_display, settings_constraints);
        settings_constraints.gridwidth = 1;
        settings_constraints.gridy++;

        // Settings button action listener to make a settings window popup.
        settings_button.addActionListener((ActionEvent e) -> {
            // Add stats to the settings popup
            if (this.total_time >= 0) {
                time_display.setText(String.format("Last game completed: %.2fs", 0.001 * total_time));
                stats_display.setText(String.format("%.2f%% win rate", (100.0 * games_won) / (games_won + games_lost)));
            }
            // Open the settings popup
            switch (JOptionPane.showConfirmDialog(this.frame, 
                settings, 
                "Minesweeper settings", 
                JOptionPane.OK_CANCEL_OPTION, 
                JOptionPane.PLAIN_MESSAGE, 
                new ImageIcon(this.frame.getIconImages().get(0))
            )) {
                // If the settings popup was closed normally, update the game's settings based on what was entered
                case JOptionPane.CLOSED_OPTION:
                case JOptionPane.OK_OPTION:
                    this.height_entered = (int) height_input.getValue();
                    this.width_entered = (int) width_input.getValue();
                    this.mines_entered = (int) mines_input.getValue();
                    this.zero_start_entered = zero_input.isSelected();
                    this.import_path_entered = import_input.isSelected() ? path_entry.getText() : null;
                    if ((this.ai_class != null ? this.ai_class.getName() : "") != ai_entry.getText()) {
                        this.initialize_ai(ai_entry.getText());
                    }
                    break;
                // If the settings popup was cancelled, set the settings entries to what they were originally
                case JOptionPane.NO_OPTION:
                case JOptionPane.CANCEL_OPTION:
                    height_input.setValue(this.height_entered);
                    width_input.setValue(this.width_entered);
                    mines_input.setValue(this.mines_entered);
                    zero_input.setSelected(this.zero_start_entered);
                    path_entry.setText(this.import_path_entered);
                    ai_entry.setText((this.ai_class != null ? this.ai_class.getName() : ""));
            }
        });

        this.newGame();
        this.frame.setVisible(true);
    }


    // Method to initialize the AI.
    public void initialize_ai(String class_name) {
        if (class_name != null && class_name.length() > 0) {
            try {
                // First try loading the class with the default class loader, and if that fails try with the custom loader
                if (this.ai_class == null || !this.ai_class.getName().equals(class_name)) {
                    try {
                        this.ai_class = Class.forName(class_name).asSubclass(Agent.class);
                    } catch (ClassNotFoundException exc) {
                        this.ai_class = Class.forName(class_name, true, this.loader).asSubclass(Agent.class);
                    }
                }
                // Instantiate the AI if its class was loaded successfully
                Agent a = (Agent) (this.ai_class.getDeclaredMethod("newAgent", Game.class).invoke(null, this.board));
                this.board.attach(a);
                return;
            } catch (ClassNotFoundException exc) {
                JOptionPane.showMessageDialog(this.frame, exc.toString() + "\nTry adding its file's location to the class loader");
            } catch (Exception exc) {
                JOptionPane.showMessageDialog(this.frame, exc);
            }
        }
        this.ai_class = null;
        this.board.attach(null);
    }

    // Method to start a new game.
    public void newGame() {
        GameBoard gb;
        try {
            if (this.import_path_entered != null) {
                if (this.import_path_entered.length() == 0) {
                    throw new IllegalStateException("Board import location is empty");
                }       
                gb = this.new GameBoard(import_path_entered);
            } else {
                gb = this.new GameBoard(this.height_entered,this.width_entered,this.mines_entered, this.zero_start_entered);
            }
        }
        catch (Exception exc) {
            JOptionPane.showMessageDialog(this.frame, exc);
            return;
        } if(this.board != null) {
            this.frame.remove(this.board.counter);
            this.board_container.remove(this.board.root);
        }
        this.board = gb;

        this.frame.add(this.board.counter, BorderLayout.WEST);
        this.board_container.add(this.board.root);

        this.frame.setIconImage(Graphics.normal_icon.getImage());

        this.frame.pack();
        this.frame.revalidate();
        this.frame.repaint();

        this.initialize_ai((this.ai_class != null ? this.ai_class.getName() : ""));
    }

    // Human player will be playing from EDT whereas AI will be playing from a different thread.
    // invokeAndWait slows the AI down a lot but with invokeLater the tiles will appear to be opened out of order which looks terrible.
    private static void invokeSafe(Runnable r){
        if(SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(r);
        }
        else {
            try{
                //Not allowed in EDT
                SwingUtilities.invokeAndWait(r);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Opens a graphics window when this class is run.
    public static void main(String[] args){
        new Graphics();
    }
}