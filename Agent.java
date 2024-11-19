package minesweeper;

public interface Agent{
	//Data class to be given to a `minesweeper.Game` as instructions for what to do
	public static class Action{
		public enum Type{
			OPEN,
			FLAG,
		}
		public final Type type;
		public final Game.Location location;
		public Action(Type type, Game.Location location){
			this.type=type;
			this.location=location;
		}
	}	

	public static Agent newAgent(Game game){
		//Override me
		return null;
	};
	public Action getMove();
}