package minesweeper.strategies;

import minesweeper.*;
import java.util.*;
import java.lang.reflect.Field;

//Using reflection to access private fields and cheat (for fun)
public class Cheater implements Agent{
	private Stack<Game.Location> safe = new Stack<>();
	private Game game;

	private Cheater(Game game){
		this.game = game;
	}
	public static Agent newAgent(Game game){
		return new Cheater(game);
	}
	private void populateSafe(){
		Field tile_number_field = null;
		Field tile_open_field = null;
		Class game_class = Game.class;
		for(Class c : game_class.getDeclaredClasses()){
			if(c.getName().equals(game_class.getName() + "$Tile")){
				try{
					tile_number_field = c.getDeclaredField("number");
					tile_open_field = c.getDeclaredField("open");
					break;
				}catch (NoSuchFieldException e){}
			}
		}

		Field game_board_field = null;
		try{
			game_board_field = game_class.getDeclaredField("full_board");
		}catch (NoSuchFieldException e){}
		game_board_field.setAccessible(true);
		Object[][] board = null;
		try{
			board = (Object[][])(game_board_field.get(this.game));
		}catch (IllegalAccessException e){}

		for(int i=board.length-1; i>=0; i--){
			for(int j=board[i].length-1; j>=0; j--){
				Object t = board[i][j];
				try{
					if((boolean)tile_open_field.get(t) == false && (int)tile_number_field.get(t) != Game.MINE){
						this.safe.push(new Game.Location(i,j));
					}
				}catch (IllegalAccessException e){}
			}
		}
	}
	public Agent.Action getMove(){
		if(this.game.getState().compareTo(Game.State.READY)<0){
			return new Agent.Action(Agent.Action.Type.OPEN, new Game.Location(0,0));
		}
		if(this.safe.empty()){
			this.populateSafe();
		}
		Game.Location loc = this.safe.pop();
		while(this.game.board[loc.row][loc.col] != Game.UNKNOWN){
			loc=this.safe.pop();
		}
		return new Agent.Action(Agent.Action.Type.OPEN, loc);
	}
}