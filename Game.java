package minesweeper;

import java.util.List;
import java.util.ArrayList;
import java.lang.Math;
import java.util.Random;
import java.util.Arrays;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.IOException;

public class Game{
	//Data class for a cell on a minesweeper board
	protected static class Tile{
		public final int number;
		public boolean open = false;
		public boolean flagged;
		public Tile(int number){
			this(number, false);
		}
		public Tile(int number, boolean flagged){
			this.number = number;
			this.flagged = flagged;
		}
	}
	//Data class representing 2d indices
	public static class Location{
		public final int row;
		public final int col;
		private final int hash;
		public Location(int row, int col){
			this.row=row;
			this.col=col;
			this.hash=(row+col)*(row+col+1)/2+row;
		}
		public String toString(){
			return String.format("(%d,%d)",this.row,this.col);
		}
		public int hashCode(){
			return this.hash;
		}
		public boolean equals(Object other){
			if(other instanceof Location){
				Location loc = (Location) other;
				return loc.row==this.row && loc.col==this.col;
			}
			return false;
		}
	}
	//These states have hierarchical order, they get compared to each other
	public enum State{
		BEFORE,
		READY,
		ACTIVE,
		WIN,
		LOSE,
	}

	//Configuration for the game
	public final int height, width, mines;
	public final boolean zero_start;

	//Current state of the game
	public int minecount;
	public int[][] board;

	// Constants to be used as part of the board, alongside numbers 0-8
	public static final int MINE = 9;
	public static final int UNKNOWN = -1;

	//Internal book-keeping
	private int safe_opened;
	protected State state = State.BEFORE;
	protected Tile[][] full_board = null;

	protected Agent ai;

	public Game(String filename) throws IOException, ParseException{
		Set<Location> mine_locations = new HashSet<>();
		try(Scanner file = new Scanner(Files.newBufferedReader(Paths.get(filename)))){
			Scanner numbers = new Scanner(file.nextLine()).useDelimiter("\\D+");
			try{
				this.height = numbers.nextInt();
				this.width = numbers.nextInt();
			}
			catch(Exception e){
				throw new ParseException(String.format("%s 1st line: expected 2 ints height,width", filename), 1);
			}
			for(int line = 2; file.hasNextLine(); line++){
				numbers = new Scanner(file.nextLine()).useDelimiter("\\D+");
				Location loc;
				try{
					loc = new Location(numbers.nextInt(), numbers.nextInt());
				}
				catch(Exception e){
					throw new ParseException(String.format("%s line %d: expected 2 ints row,col", filename, line), line);
				}
				if(!this.in_bounds(loc)){
					throw new ParseException(String.format("%s line %d: %s is out of bounds for %dx%d", filename, line, loc, this.height, this.width), line);
				}
				mine_locations.add(loc);
			}
		}
		this.mines = mine_locations.size();
		this.zero_start = false;
		this.init();
		for(Location mine : mine_locations){
			this.full_board[mine.row][mine.col] = new Tile(MINE);
		}
		this.assignNumbers();
	}
	public Game(int height, int width, int mines){
		this(height, width, mines, true);
	}
	public Game(int height, int width, int mines, boolean zero_start){
		if (mines>(height*width)-(zero_start ? 9 : 1) || mines<0){
			throw new IllegalArgumentException(String.format("Can't fit %d mines",mines));
		}
		this.height=height;
		this.width=width;
		this.mines=mines;
		this.zero_start=zero_start;
		this.init();
	}
	private void init(){
		this.safe_opened = (height*width)-mines;
		this.minecount = mines;
		this.board = new int[height][width];
		for(int[] row : this.board){
			Arrays.fill(row, UNKNOWN);
		}
		this.full_board = new Tile[height][width];
		for(int r=0; r<this.height; r++){
			for(int c=0; c<this.width; c++){
				this.full_board[r][c] = new Tile(UNKNOWN);
			}
		}
	}

	protected void generateBoard(Location first_loc){
		this.generateBoard(first_loc, new Random());
	}
	protected void generateBoard(Location first_loc, Random random){
		//Select all locations that would be allowed to have a mine
		List<Location> locations = new ArrayList<>();
		int distance_threshold = this.zero_start ? 1 : 0;
		for(int r=0; r<this.height; r++){
			for(int c=0; c<this.width; c++){
				if (Math.abs(first_loc.row-r)<=distance_threshold && Math.abs(first_loc.col-c)<=distance_threshold){
					continue;
				}
				locations.add(new Location(r,c));
			}
		}
		//Select `mines` of those locations randomly
		for(int i=locations.size()-1; i>=locations.size()-mines; i--){
			int swap_idx = random.nextInt(i+1);
			Location mine = locations.get(swap_idx);
			this.full_board[mine.row][mine.col] = new Tile(MINE, this.full_board[mine.row][mine.col].flagged);
			locations.set(swap_idx, locations.get(i));
		}
		this.assignNumbers();
	}
	private void assignNumbers(){
		//Assign numbers to all the other tiles based on how many mines they border
		for(int r=0; r<this.height; r++){
			for(int c=0; c<this.width; c++){
				if(this.full_board[r][c].number != MINE){
					int count = 0;
					for(Location l : this.neighbors(new Location(r,c))){
						if(this.full_board[l.row][l.col]!=null && this.full_board[l.row][l.col].number == MINE){
							count++;
						}
					}
					boolean flag = this.full_board[r][c].flagged;
					this.full_board[r][c] = new Tile(count, this.full_board[r][c].flagged);
				}
			}
		}
		this.state = State.READY;
	}

	public void flag(Location loc){
		if(!in_bounds(loc)){
			throw new IllegalArgumentException(String.format("Can't flag at %s",loc));
		}
		if(this.state.compareTo(State.ACTIVE) > 0){
			return;
		}
		Tile t = this.full_board[loc.row][loc.col];
		if(t.open){
			return;
		}
		t.flagged = !t.flagged;
		this.board_view_set(loc, (t.flagged ? MINE : UNKNOWN));
		this.setMinecount(this.minecount + (t.flagged ? -1 : 1));

	}
	public void open(Location loc){
		if(!in_bounds(loc)){
			throw new IllegalArgumentException(String.format("Can't open at %s",loc));
		}
		if(this.state == State.BEFORE){
			this.generateBoard(loc);
		}
		if(this.state == State.READY){
			this.setState(State.ACTIVE);
		}
		if(this.state != State.ACTIVE){
			return;
		}
		if(this.full_board[loc.row][loc.col].flagged || this.full_board[loc.row][loc.col].open){
			return;
		}
		this.full_board[loc.row][loc.col].open = true;
		int n = this.full_board[loc.row][loc.col].number;
		this.board_view_set(loc, n);
		if(n==0){
			for(Location l : this.neighbors(loc)){
				this.open(l);
			}
		}
		if(n==MINE){
			this.setState(State.LOSE);
		}
		else if(--this.safe_opened == 0){
			this.setState(State.WIN);
		}
	}


	public State getState(){
		return this.state;
	}
	//These are here for derived classes to listen for an event
	protected void setState(State state){
		this.state = state;
	}
	protected void setMinecount(int n){
		this.minecount = n;
	}


	//This setter is here in case a player screws with the board
	//It is fine if they do that cause it would not affect the games functioning
	protected void board_view_set(Location loc, int value){
		if(loc.row<this.board.length && loc.col<this.board[loc.row].length){
			this.board[loc.row][loc.col] = value;
		}
	}

	public void attach(Agent a){
		this.ai = a;
	}
	protected void process_action(Agent.Action move){
		if(move == null || move.location==null || move.type==null){
			throw new NullPointerException("AI returned a null move");
		}
		if(move.type == Agent.Action.Type.OPEN){
			this.open(move.location);
		}
		else if(move.type == Agent.Action.Type.FLAG){
			this.flag(move.location);
		}
	}
	public void ai_move(){
		if(this.ai == null){
			return;
		}
		Agent.Action move = this.ai.getMove();
		this.process_action(move);
	}
	public void ai_play(){
		if(this.ai == null){
			return;
		}
		while(this.state.compareTo(State.ACTIVE) <= 0){
			this.ai_move();
		}
	}

	private boolean in_bounds(Location pt){
		return pt.row>=0 && pt.col>=0 && pt.row<this.height && pt.col<this.width;
	}
	protected final Location[] neighbors(Location loc){
		Location[] locations = new Location[]{
			new Location(loc.row-1, loc.col-1),
			new Location(loc.row-1, loc.col),
			new Location(loc.row-1, loc.col+1),
			new Location(loc.row, loc.col-1),
			new Location(loc.row, loc.col+1),
			new Location(loc.row+1, loc.col-1),
			new Location(loc.row+1, loc.col),
			new Location(loc.row+1, loc.col+1)
		};
		return Arrays.stream(locations).filter(this::in_bounds).toArray(Location[]::new);
	}

	public void export(String filename) throws IOException{
		if(filename==null || filename.length()==0){
			filename="MinesweeperGameBoard.txt";
		}
		Files.createDirectories(Paths.get(filename).getParent());
		try(BufferedWriter file = Files.newBufferedWriter(Paths.get(filename))){
			file.write(String.format("%dx%d\n",this.height, this.width));
			for(int r=0; r<this.height; r++){
				for(int c=0; c<this.width; c++){
					if(this.state!=State.BEFORE ? this.full_board[r][c].number==MINE : this.full_board[r][c].flagged){
						file.write(String.format("%d,%d\n",r,c));
					}
				}
			}
		}
	}
}