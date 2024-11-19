package minesweeper.strategies;

import minesweeper.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

//Bad strategy just for illustrative purposes
public class SinglePoint implements Agent{
	private Game game;
	private SinglePoint(Game game){
		this.game = game;
	}
	public static Agent newAgent(Game game){
		return new SinglePoint(game);
	}

	public Agent.Action getMove(){
		int[][] board = this.game.board;
		//Look for obvious moves where all/none of a tile's neighbors are mines
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(board[r][c]==Game.UNKNOWN || board[r][c]==Game.MINE){
					continue;
				}
				Game.Location pos = new Game.Location(r,c);
				int mines = 0;
				int unknown = 0;
				for(Game.Location loc : this.neighbors(pos)){
					int val = board[loc.row][loc.col];
					if(val==Game.MINE){
						mines++;
					}
					else if(val==Game.UNKNOWN){
						unknown++;
					}
				}
				if(unknown==0){
					continue;
				}
				int unknown_mines = board[r][c]-mines;
				if(unknown_mines==0){
					for(Game.Location loc : this.neighbors(pos)){
						if(board[loc.row][loc.col]==Game.UNKNOWN){
							return new Agent.Action(Agent.Action.Type.OPEN, loc);
						}
					}
				}
				else if(unknown_mines==unknown){
					for(Game.Location loc : this.neighbors(pos)){
						if(board[loc.row][loc.col]==Game.UNKNOWN){
							return new Agent.Action(Agent.Action.Type.FLAG, loc);
						}
					}
				}
			}
		}

		//Choose a random tile to open if there aren't any obvious moves
		List<Game.Location> unknowns = new ArrayList<>();
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(board[r][c]==Game.UNKNOWN){
					unknowns.add(new Game.Location(r,c));
				}
			}
		}
		return new Agent.Action(Agent.Action.Type.OPEN, unknowns.get(new Random().nextInt(unknowns.size())));
	}

	private boolean in_bounds(Game.Location pos){
		return pos.row>=0 && pos.col>=0 && pos.row<this.game.height && pos.col<this.game.width;
	}
	private Game.Location[] neighbors(Game.Location pos){
		List<Game.Location> ans = new ArrayList<>();
		for(int r=-1; r<=1; r++){
			for(int c=-1; c<=1; c++){
				Game.Location n = new Game.Location(pos.row+r, pos.col+c);
				if((r|c)!=0 && this.in_bounds(n)){
					ans.add(n);
				}
			}
		}
		return ans.toArray(new Game.Location[0]);
	}
}