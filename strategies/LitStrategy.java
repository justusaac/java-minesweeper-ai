package minesweeper.strategies;

import minesweeper.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.math.BigInteger;
import java.math.BigDecimal;

public class LitStrategy implements Agent{
	private final Game game;
	private final Stack<Agent.Action> moves = new Stack<>();
	private Runnable[] search_steps;
	private Runnable[] guess_steps;
	private LitStrategy(Game game){
		this.game = game;
		this.search_steps = new Runnable[]{
			this::firstMove,
			this::singlePointSearch,
			() -> this.pairwiseSearch(this::neighbors),
			() -> this.pairwiseSearch(this::second_neighbors),
			this::generateSubsets,
			this::calculateMineProbabilities,
			this::probabilitySafeMoves,
		};
		this.guess_steps = new Runnable[]{
			this::calculateNumberProbabilities,
			this::takeForcedGuesses,
			this::probabilityBestGuess,
			this::randomProbabilityGuess,
			this::randomCornerGuess,
		};
	}
	public static Agent newAgent(Game game){
		return new LitStrategy(game);
	}

	protected Agent.Action getStoredMove(){
		while(!this.moves.empty()){
			Agent.Action move = this.moves.pop();
			if(this.game.board[move.location.row][move.location.col]==Game.UNKNOWN){
				return move;
			}
		}
		return null;
	}
	public Agent.Action getMove(){
		for(Runnable[] steps : new Runnable[][]{this.search_steps, this.guess_steps}){
			for(Runnable step : steps){
				Agent.Action res = this.getStoredMove();
				if(res!=null){
					return res;
				}
				step.run();
			}
		}
		return this.getStoredMove();
	}

	protected void flag(Game.Location loc){
		this.moves.push(new Agent.Action(Agent.Action.Type.FLAG, loc));
	}
	protected void open(Game.Location loc){
		this.moves.push(new Agent.Action(Agent.Action.Type.OPEN, loc));
	}


	private boolean in_bounds(Game.Location pos){
		return pos.row>=0 && pos.col>=0 && pos.row<this.game.height && pos.col<this.game.width;
	}
	private final HashMap<Game.Location, Game.Location[]> neighbor_cache = new HashMap<>();
	private Game.Location[] neighbors(Game.Location pos){
		Game.Location[] cached = neighbor_cache.get(pos);
		if(cached==null){
			List<Game.Location> ans = new ArrayList<>();
			for(int r=-1; r<=1; r++){
				for(int c=-1; c<=1; c++){
					Game.Location n = new Game.Location(pos.row+r, pos.col+c);
					if((r|c)!=0 && this.in_bounds(n)){
						ans.add(n);
					}
				}
			}
			cached = ans.toArray(new Game.Location[0]);
			neighbor_cache.put(pos, cached);
		}
		return cached;
	}
	private final HashMap<Game.Location, Game.Location[]> second_neighbor_cache = new HashMap<>();
	private Game.Location[] second_neighbors(Game.Location pos){
		//All tiles not bordering pos that share multiple neighbors with it
		/*
		.xxx.
		x...x
		x.o.x
		x...x
		.xxx.
		*/

		Game.Location[] cached = second_neighbor_cache.get(pos);
		if(cached==null){
			List<Game.Location> ans = new ArrayList<>();
			for(int m=0; m<=1; m++){
				for(int d=-1; d<=1; d++){
					for(int r=0; r<=1; r++){
						Game.Location n = new Game.Location(pos.row+((m*2-1)*2*r+d*(1-r)), pos.col+((m*2-1)*2*(1-r)+d*r));
						if(this.in_bounds(n)){
							ans.add(n);
						}
					}
				}
			}
			cached = ans.toArray(new Game.Location[0]);
			second_neighbor_cache.put(pos, cached);
		}
		return cached;
	}

	protected void firstMove(){
		if(this.game.getState()==Game.State.BEFORE){
			if(this.game.zero_start){
				//Determined experimentally
				Game.Location pos = new Game.Location(2,3);
				this.open(new Game.Location(
					Math.max(0,Math.min(this.game.height-1, pos.row)),
					Math.max(0,Math.min(this.game.width-1, pos.col))
				));
			}
			else{
				this.open(new Game.Location(0,0));
			}
		}
	}

	protected void singlePointSearch(){
		//Look for obvious moves where all/none of a tile's neighbors are mines
		final int[][] board = this.game.board;
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
							this.open(loc);
						}
					}
				}
				if(unknown_mines==unknown){
					for(Game.Location loc : this.neighbors(pos)){
						if(board[loc.row][loc.col]==Game.UNKNOWN){
							this.flag(loc);
						}
					}
				}
			}
		}
	}

	protected void pairwiseSearch(Function<Game.Location, Game.Location[]> neighborhood){
		final int[][] board = this.game.board;
		//Look for moves where all/none of a tile's mines are bordering another tile
		for(int r1=0; r1<this.game.height; r1++){
			for(int c1=0; c1<this.game.width; c1++){
				if(board[r1][c1]==Game.UNKNOWN || board[r1][c1]==Game.MINE){
					continue;
				}
				Game.Location loc1 = new Game.Location(r1,c1);
				for(Game.Location loc2 : neighborhood.apply(loc1)){
					if(board[loc2.row][loc2.col]==Game.UNKNOWN || board[loc2.row][loc2.col]==Game.MINE){
						continue;
					}
					if(!(loc2.row>loc1.row) && !(loc1.row==loc2.row && loc2.col>loc1.col)){
						//Avoid checking the same pair twice
						continue;
					}
					this.checkPair(loc1, loc2);
				}
			}
		}
	}
	private void checkPair(Game.Location loc1, Game.Location loc2){
		final int[][] board = this.game.board;
		int shared = 0;
		int mines1 = board[loc1.row][loc1.col];
		int unique1 = 0;
		for(Game.Location n1 : this.neighbors(loc1)){
			switch(board[n1.row][n1.col]){
			case Game.MINE:
				mines1--;
				break;
			case Game.UNKNOWN:
				unique1++;
				for(Game.Location n2 : this.neighbors(loc2)){
					if(n1.equals(n2)){
						shared++;
						unique1--;
						break;
					}
				}
			}
		}
		if(shared==0){
			return;
		}
		int mines2 = board[loc2.row][loc2.col];
		int unique2 = -1*shared;
		for(Game.Location n2 : this.neighbors(loc2)){
			switch(board[n2.row][n2.col]){
			case Game.MINE:
				mines2--;
				break;
			case Game.UNKNOWN:
				unique2++;
			}
		}
		int min = Math.max(Math.max(mines1-unique1, mines2-unique2),0);
		int max = Math.min(Math.min(mines1, mines2), shared);
		if(min==mines1){
			this.process_all_unique(Agent.Action.Type.OPEN, loc1, loc2);
		}
		if(max+unique1==mines1){
			this.process_all_unique(Agent.Action.Type.FLAG, loc1, loc2);
		}
		if(min==mines2){
			this.process_all_unique(Agent.Action.Type.OPEN, loc2, loc1);
		}
		if(max+unique2==mines2){
			this.process_all_unique(Agent.Action.Type.FLAG, loc2, loc1);
		}
	}
	private void process_all_unique(Agent.Action.Type a, Game.Location loc, Game.Location other){
		outer: for(Game.Location target : this.neighbors(loc)){
			if(this.game.board[target.row][target.col]!=Game.UNKNOWN){
				continue;
			}
			for(Game.Location n2 : this.neighbors(other)){
				if(n2.equals(target)){
					continue outer;
				}
			}
			if(a==Agent.Action.Type.OPEN){
				this.open(target);
			}
			else{
				this.flag(target);
			}
		}
	}


	protected class Subset{
		public final Set<Game.Location> open_area;
		public final Set<Game.Location> hidden_area;
		public int max_mines;
		public Subset(Set<Game.Location> open, Set<Game.Location> closed){
			this.open_area = open;
			this.hidden_area = closed;
			this.max_mines = Math.min(LitStrategy.this.game.minecount, this.hidden_area.size());
		}
		public int hashCode(){
			return this.open_area.hashCode();
		}
		public boolean equals(Object other){
			if(other!=null && other.getClass().equals(this.getClass())){
				return this.open_area.equals(((Subset)other).open_area) 
					&& this.hidden_area.equals(((Subset)other).hidden_area);
			}
			return false;
		}

		public List<Map<Game.Location, BigInteger>> mine_occurrences;
		public List<Map<Game.Location, BigInteger[]>> number_occurrences;
		public BigInteger[] minecount_occurrences;
		private void prepare_data_structures(){
			final int[][] board = LitStrategy.this.game.board;
			this.mine_occurrences = new ArrayList<>();
			this.number_occurrences = new ArrayList<>();
			this.minecount_occurrences = new BigInteger[this.max_mines+1];
			Arrays.fill(this.minecount_occurrences, BigInteger.ZERO);
			for(int i=0; i<=this.max_mines+1; i++){
				Map<Game.Location, BigInteger> mine_occurrence = new HashMap<>();
				Map<Game.Location, BigInteger[]> number_occurrence = new HashMap<>();
				this.mine_occurrences.add(mine_occurrence);
				this.number_occurrences.add(number_occurrence);
				for(Game.Location loc : this.hidden_area){
					mine_occurrence.put(loc, BigInteger.ZERO);
					if(!number_occurrence.containsKey(loc)){
						BigInteger[] numbers = new BigInteger[Game.MINE];
						Arrays.fill(numbers, BigInteger.ZERO);
						number_occurrence.put(loc, numbers);
					}
					for(Game.Location n : LitStrategy.this.neighbors(loc)){
						if(board[n.row][n.col]==Game.UNKNOWN && !number_occurrence.containsKey(n)){
							BigInteger[] numbers = new BigInteger[Game.MINE];
							Arrays.fill(numbers, BigInteger.ZERO);
							number_occurrence.put(n, numbers);
						}
					}
				}
			}
			
		}

		private class SearchState{
			public final Set<Game.Location> candidates;
			public final Set<Game.Location> selected;
			public final Map<Game.Location, Integer> mines_remaining;
			public final Map<Game.Location, Integer> empty_remaining;
			public SearchState(Set<Game.Location> candidates, Set<Game.Location> selected, Map<Game.Location, Integer> mines_remaining, Map<Game.Location, Integer> empty_remaining){
				this.candidates=candidates;
				this.selected=selected;
				this.mines_remaining=mines_remaining;
				this.empty_remaining=empty_remaining;
			}
			public SearchState(SearchState other){
				this.candidates=new HashSet<>(other.candidates);
				this.selected=new HashSet<>(other.selected);
				this.mines_remaining=new HashMap<>(other.mines_remaining);
				this.empty_remaining=new HashMap<>(other.empty_remaining);
			}
			public boolean isComplete(){
				for(Integer i : this.mines_remaining.values()){
					if(i!=0){
						return false;
					}
				}
				return true;
			}
			public boolean discard(Game.Location loc){
				if(!this.candidates.remove(loc)){
					return false;
				}
				for(Game.Location n : LitStrategy.this.neighbors(loc)){
					empty_remaining.computeIfPresent(n, (Game.Location l, Integer x)->(x-1));
				}
				return true;
			}
			public boolean select(Game.Location loc){
				if(!this.discard(loc)){
					return false;
				}
				this.selected.add(loc);
				for(Game.Location n : LitStrategy.this.neighbors(loc)){
					Integer res = mines_remaining.computeIfPresent(n, (Game.Location l, Integer x)->(x-1));
				}
				return true;
			}
			public boolean deadEnd(){
				for(Game.Location loc : Subset.this.open_area){
					int need = this.mines_remaining.get(loc);
					int space = this.empty_remaining.get(loc);
					if(need<0 || space<need || Subset.this.max_mines-this.selected.size()<need){
						return true;
					}
					if(need>0){
						int count = 0;
						for(Game.Location n : LitStrategy.this.neighbors(loc)){
							if(this.candidates.contains(n)){
								count++;
							}
						}
						if(count<need){
							return true;
						}
					}
				}
				return false;
			}
			public boolean reduce(){
				boolean changed = false;
				for(Game.Location loc : Subset.this.open_area){
					//Make any trivially mandatory moves
					int empty = this.empty_remaining.get(loc);
					int mines = this.mines_remaining.get(loc);
					if(mines==0){
						for(Game.Location n : LitStrategy.this.neighbors(loc)){
							changed |= this.discard(n);
						}
					}
					if(mines==empty){
						for(Game.Location n : LitStrategy.this.neighbors(loc)){
							changed |= this.select(n);
						}
					}
				}
				return changed;
			}
			public boolean checkDisjoint(){
				//If were small there's no need to split
				if(this.candidates.size()<=3){
					return false;
				}
				Stack<Game.Location> stack = new Stack<>();
				//Pick 1 candidate arbitrarily
				for(Game.Location loc : this.candidates){
					stack.push(loc);
					break;
				}
				Set<Game.Location> candidates_found = new HashSet<>();
				candidates_found.add(stack.peek());
				Set<Game.Location> open_found = new HashSet<>();
				while(!stack.empty()){
					Game.Location loc = stack.pop();
					if(this.candidates.contains(loc)){
						for(Game.Location n : LitStrategy.this.neighbors(loc)){
							if(Subset.this.open_area.contains(n) && open_found.add(n)){
								stack.push(n);
							}
						}
					}
					else{
						for(Game.Location n : LitStrategy.this.neighbors(loc)){
							if(this.candidates.contains(n) && candidates_found.add(n)){
								stack.push(n);
							}
						}
					}
				}

				//If either side is too small, either there is no other side or we dont need to split
				if(1 >= Math.min(this.candidates.size()-candidates_found.size(), candidates_found.size())){
					return false;
				}

				//The subset can be solved with 2 different subsets independently!
				Set<Game.Location> candidates_rest = new HashSet<>(this.candidates);
				candidates_rest.removeAll(candidates_found);
				Set<Game.Location> open_rest = new HashSet<>(Subset.this.open_area);
				open_rest.removeAll(open_found);

				final Subset[] split = new Subset[]{
					LitStrategy.this.new Subset(open_found, candidates_found),
					LitStrategy.this.new Subset(open_rest, candidates_rest),
				};

				//Generate all the combinations in each section
				for(Subset ss : split){
					ss.prepare_data_structures();
					Map<Game.Location, Integer> mines_remaining = new HashMap<>(this.mines_remaining);
					mines_remaining.keySet().retainAll(ss.open_area);
					Map<Game.Location, Integer> empty_remaining = new HashMap<>(this.empty_remaining);
					empty_remaining.keySet().retainAll(ss.open_area);
					SearchState init = ss.new SearchState(
						new HashSet<>(ss.hidden_area), new HashSet<>(), mines_remaining, empty_remaining
					);
					ss.generateCombinationsRecursive(init);
					if(LitStrategy.countNonzero(ss.minecount_occurrences)==0){
						return false;
					}
				}

				//Combine the 2 subsections and the previous progress
				List<List<Integer>> lengths = new ArrayList<>();
				for(Subset ss : split){
					List<Integer> local_lengths = new ArrayList<>();
					for(int i=0; i<ss.minecount_occurrences.length; i++){
						if(ss.minecount_occurrences[i].equals(BigInteger.ZERO)){
							continue;
						}
						local_lengths.add(i);
					}
					lengths.add(local_lengths);
				}

				LitStrategy.cartesianProduct(lengths, (List<Integer> subset_minecounts) -> {
					int total_length = this.selected.size();
					for(int n : subset_minecounts){
						total_length+=n;
					}
					if(total_length>Subset.this.max_mines){
						return;
					}
					BigInteger total_multiplier = BigInteger.ONE;
					for(int i=0; i<split.length; i++){
						total_multiplier = total_multiplier.multiply(
							split[i].minecount_occurrences[subset_minecounts.get(i)]
						);
					}
					//Combine the mine occurrences from the 2 subsets
					Subset.this.minecount_occurrences[total_length] = Subset.this.minecount_occurrences[total_length].add(total_multiplier);
					Map<Game.Location, BigInteger> total_mine_occurrences = Subset.this.mine_occurrences.get(total_length);
					for(int i=0; i<split.length; i++){
						int local_mines = subset_minecounts.get(i);
						BigInteger multiplier = total_multiplier.divide(split[i].minecount_occurrences[local_mines]);
						Map<Game.Location, BigInteger> local_mine_occurrences = split[i].mine_occurrences.get(local_mines);
						for(Game.Location loc : local_mine_occurrences.keySet()){
							BigInteger total = local_mine_occurrences.get(loc).multiply(multiplier);
							total_mine_occurrences.put(loc,
								total_mine_occurrences.get(loc).add(total)
							);
						}
					}
					for(Game.Location loc : this.selected){
						total_mine_occurrences.put(loc,
							total_mine_occurrences.get(loc).add(total_multiplier)
						);
					}

					//Combine the number occurrences from the 2 subsets
					Map<Game.Location, BigInteger[]> local_number_occurrences = new HashMap<>();

					for(Game.Location loc : Subset.this.number_occurrences.get(total_length).keySet()){
						if(this.selected.contains(loc)){
							//Even tho itll have data about its numbers it is not relevant cause its always a mine
							continue;
						}
						BigInteger[] total_numbers  = new BigInteger[Game.MINE];
						Arrays.fill(total_numbers,BigInteger.ZERO);
						total_numbers[0] = BigInteger.ONE;
						local_number_occurrences.put(loc,total_numbers);
					}
					for(int i=0; i<split.length; i++){
						int local_mines = subset_minecounts.get(i);
						BigInteger multiplier = total_multiplier.divide(split[i].minecount_occurrences[local_mines]);
						for(Game.Location loc : local_number_occurrences.keySet()){

							BigInteger[] total_numbers = local_number_occurrences.get(loc);

							BigInteger[] local_numbers = split[i].number_occurrences.get(local_mines).get(loc);
							if(local_numbers==null){
								local_numbers = new BigInteger[Game.MINE];
								Arrays.fill(local_numbers,BigInteger.ZERO);
								local_numbers[0] = split[i].minecount_occurrences[local_mines];
							}

							BigInteger[] new_total_numbers = new BigInteger[Game.MINE];
							Arrays.fill(new_total_numbers, BigInteger.ZERO);
							for(int local_n=0; local_n<local_numbers.length; local_n++){
								if(local_numbers[local_n].equals(BigInteger.ZERO)){
									continue;
								}
								for(int old_number = 0; old_number<total_numbers.length; old_number++){
									BigInteger new_count = local_numbers[local_n].multiply(total_numbers[old_number]);
									if(new_count.equals(BigInteger.ZERO)){
										continue;
									}
									int new_idx = local_n+old_number;
									new_total_numbers[new_idx] = new_total_numbers[new_idx].add(new_count);
								}
							}
							local_number_occurrences.put(loc, new_total_numbers);
						}

					}

					//Adjust the numbers for the tiles already selected
					for(Game.Location loc : this.selected){
						for(Game.Location n : LitStrategy.this.neighbors(loc)){
							BigInteger[] numbers = local_number_occurrences.get(n);
							if(numbers==null){
								continue;
							}
							//Increase all the numbers by 1
							System.arraycopy(numbers, 0, numbers, 1, numbers.length-1);
							numbers[0] = BigInteger.ZERO;
						}
					}
					//Update the subset with these numbers
					for(Map.Entry<Game.Location,BigInteger[]> e : local_number_occurrences.entrySet()){
						BigInteger[] src = e.getValue();
						BigInteger[] dest = Subset.this.number_occurrences.get(total_length).get(e.getKey());
						for(int i=0; i<src.length; i++){
							dest[i] = dest[i].add(src[i]);
						}
					}
				});

				//trvth nvke
				return true;
			}
		}


		private void generateCombinations(){
			this.prepare_data_structures();

			Map<Game.Location, Integer> mines_remaining = new HashMap<>();
			Map<Game.Location, Integer> empty_remaining = new HashMap<>();
			for(Game.Location loc : this.open_area){
				int mines = LitStrategy.this.game.board[loc.row][loc.col];
				int empty = 0;
				for(Game.Location n : LitStrategy.this.neighbors(loc)){
					switch(LitStrategy.this.game.board[n.row][n.col]){
					case Game.MINE:
						mines--;
						break;
					case Game.UNKNOWN:
						empty++;
						break;
					}
				}
				mines_remaining.put(loc, mines);
				empty_remaining.put(loc, empty);
			}
			this.generateCombinationsRecursive(
				new SearchState(
					new HashSet<>(this.hidden_area), new HashSet<>(), mines_remaining, empty_remaining
				)
			);
		}
		private void generateCombinationsRecursive(SearchState state){
			if(state.selected.size()>this.max_mines){
				return;
			}
			//Process a complete valid combination
			if(state.isComplete()){
				Map<Game.Location, BigInteger> occur = this.mine_occurrences.get(state.selected.size());
				for(Game.Location loc : state.selected){
					occur.put(loc, occur.get(loc).add(BigInteger.ONE));
				}
				this.minecount_occurrences[state.selected.size()] = this.minecount_occurrences[state.selected.size()].add(BigInteger.ONE);
				for(Map.Entry<Game.Location, BigInteger[]> entry : this.number_occurrences.get(state.selected.size()).entrySet()){
					if(!state.selected.contains(entry.getKey())){
						int n=0;
						for(Game.Location neighbor : LitStrategy.this.neighbors(entry.getKey())){
							if(state.selected.contains(neighbor)){
								n++;
							}
						}
						entry.getValue()[n] = entry.getValue()[n].add(BigInteger.ONE);
					}
				}
				return;
			}
			if(state.candidates.size()==0 || state.deadEnd()){
				return;
			}
			if(state.reduce()){
				this.generateCombinationsRecursive(state);
				return;
			}
			if(state.checkDisjoint()){
				return;
			}
			//Deepen search
			if(state.selected.size()<this.max_mines){
				//Sort based on a "fail first heuristic" increases performance
				//Most to least neighbors that are open in this subset works ok
				List<Game.Location> choices = new ArrayList<>(state.candidates);
				final Map<Game.Location, Integer> edge_score = new HashMap<>();
				for(Game.Location loc : choices){
					int i=0;
					for(Game.Location n : LitStrategy.this.neighbors(loc)){
						if(Subset.this.open_area.contains(n)){
							i++;
						}
					}
					edge_score.put(loc, i);
				}
				choices.sort((Game.Location a, Game.Location b)->edge_score.get(b)-edge_score.get(a));
				for(Game.Location n : choices){
					SearchState nxt = new SearchState(state);
					state.discard(n);
					nxt.select(n);
					this.generateCombinationsRecursive(nxt);
				}
			}
		}
	}

	List<Subset> subsets;
	protected void generateSubsets(){
		//Depth-first search to identify disjoint areas that have useful info
		final int[][] board = this.game.board;
		List<Subset> new_subsets = new ArrayList<>();
		Set<Game.Location> found = new HashSet<>();
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(board[r][c]==Game.UNKNOWN || board[r][c]==Game.MINE){
					continue;
				}
				Game.Location loc = new Game.Location(r,c);
				if(!found.add(loc)){
					continue;
				}
				Set<Game.Location> open_subset = new HashSet<>();
				Set<Game.Location> hidden_subset = new HashSet<>();
				Stack<Game.Location> stack = new Stack<>();
				stack.push(loc);
				while(!stack.empty()){
					Game.Location curr = stack.pop();
					if(board[curr.row][curr.col]==Game.UNKNOWN){
						hidden_subset.add(curr);
						for(Game.Location n : this.neighbors(curr)){
							if(board[n.row][n.col]!=Game.UNKNOWN && board[n.row][n.col]!=Game.MINE){
								if(found.add(n)){
									stack.push(n);
								}
							}
						}
					}
					else if(board[curr.row][curr.col]!=Game.MINE){
						open_subset.add(curr);
						for(Game.Location n : this.neighbors(curr)){
							if(board[n.row][n.col]==Game.UNKNOWN){
								if(found.add(n)){
									stack.push(n);
								}
							}
						}
					}
				}
				if(hidden_subset.size()==0){
					continue;
				}
				//Caching info about subsets thatve already been found
				Subset ss = new Subset(open_subset, hidden_subset);
				int idx = this.subsets==null ? -1 : this.subsets.indexOf(ss);
				if(idx==-1){
					ss.generateCombinations();
					new_subsets.add(ss);
				}
				else{
					Subset cached = this.subsets.get(idx);
					new_subsets.add(cached);
				}
			}
		}
		this.subsets = new_subsets;
	}

	Map<Game.Location, Double> mine_probabilities;
	protected List<BigInteger[]> subset_count_multipliers;
	protected void calculateMineProbabilities(){
		final int[][] board = this.game.board;
		int unknown = 0;
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(board[r][c]==Game.UNKNOWN){
					unknown++;
				}
			}
		}
		List<List<Integer>> subset_counts = new ArrayList<>();
		final List<BigInteger[]> subset_count_multipliers = new ArrayList<>();
		for(Subset ss : this.subsets){
			unknown -= ss.hidden_area.size();
			List<Integer> counts = new ArrayList<>();
			for(int i=0; i<ss.minecount_occurrences.length; i++){
				if(!ss.minecount_occurrences[i].equals(BigInteger.ZERO)){
					counts.add(i);
				}
			}
			subset_counts.add(counts);
			BigInteger[] multipliers = new BigInteger[counts.get(counts.size()-1)+1];
			for(int i=0; i<multipliers.length; i++){
				multipliers[i] = BigInteger.ZERO;
			}
			subset_count_multipliers.add(multipliers);
		}
		final int unknown_squares = unknown;
		final BigInteger[] total_lengths = new BigInteger[this.game.minecount+1];
		Arrays.fill(total_lengths, BigInteger.ZERO);
		
		//Calculate number of times each permutation occurs in total
		cartesianProduct(subset_counts, (List<Integer> lengths) -> {
			int sum_mines = 0;
			for(int i=0; i<lengths.size(); i++){
				sum_mines += lengths.get(i);
			}
			if(sum_mines>this.game.minecount || sum_mines+unknown_squares<this.game.minecount){
				return;
			}
			BigInteger total_combinations = LitStrategy.comb(unknown_squares, this.game.minecount-sum_mines);
			for(int i=0; i<lengths.size(); i++){
				total_combinations = total_combinations.multiply(
					this.subsets.get(i).minecount_occurrences[lengths.get(i)]
				);
			}
			total_lengths[sum_mines] = total_lengths[sum_mines].add(total_combinations);

			for(int i=0; i<lengths.size(); i++){
				BigInteger delta = total_combinations.divide(
					this.subsets.get(i).minecount_occurrences[lengths.get(i)]
				);
				BigInteger[] multiplier = subset_count_multipliers.get(i);
				multiplier[lengths.get(i)]=multiplier[lengths.get(i)].add(delta);
			}
		});
		this.subset_count_multipliers = subset_count_multipliers;

		//Calculate probability of each tile being a mine
		BigInteger total_combinations = BigInteger.ZERO;
		for(BigInteger i : total_lengths){
			total_combinations = total_combinations.add(i);
		}
		this.mine_probabilities = new HashMap<>();
		for(int i=0; i<this.subsets.size(); i++){
			Subset ss = this.subsets.get(i);
			BigInteger[] multipliers = subset_count_multipliers.get(i);
			for(Game.Location loc : ss.hidden_area){
				BigInteger total_occurrences = BigInteger.ZERO;
				for(int size=0; size<multipliers.length; size++){
					total_occurrences = total_occurrences.add(
						multipliers[size].multiply(
							ss.mine_occurrences.get(size).get(loc)
						)
					);
				}
				this.mine_probabilities.put(loc, LitStrategy.ratio(total_occurrences,total_combinations));
			}
		}
		//Probability of all the other tiles being mines, keyed as null
		double unknown_probability = 0;
		if(unknown_squares>0){
			for(int i=0; i<total_lengths.length; i++){
				double length_combinations = LitStrategy.ratio(total_lengths[i],total_combinations);
				unknown_probability += (double)(this.game.minecount-i) / unknown_squares * length_combinations;
			}
			this.mine_probabilities.put(null, unknown_probability);
		}


		//System.out.println(this.mine_probabilities);

	}


	Map<Game.Location, double[]> number_probabilities;
	protected void calculateNumberProbabilities(){
		//Same setup used when calculating mine probabilities
		final int[][] board = this.game.board;
		int unknown = 0;
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(board[r][c]==Game.UNKNOWN){
					unknown++;
				}
			}
		}
		List<List<Integer>> subset_counts = new ArrayList<>();
		for(Subset ss : this.subsets){
			unknown -= ss.hidden_area.size();
			List<Integer> counts = new ArrayList<>();
			for(int i=0; i<ss.minecount_occurrences.length; i++){
				if(!ss.minecount_occurrences[i].equals(BigInteger.ZERO)){
					counts.add(i);
				}
			}
			subset_counts.add(counts);
		}
		final int unknown_squares = unknown;

		//Calculate number of times each tile contains each number
		final Map<Game.Location, BigInteger[]> number_occurrences = new HashMap<>();
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(this.game.board[r][c]!=Game.UNKNOWN){
					continue;
				}
				Game.Location loc = new Game.Location(r,c);
				if(!number_occurrences.containsKey(loc)){
					BigInteger[] arr = new BigInteger[Game.MINE];
					Arrays.fill(arr, BigInteger.ZERO);
					number_occurrences.put(loc, arr);
				}
			}
		}

		cartesianProduct(subset_counts, (List<Integer> lengths) -> {
			Map<Game.Location, BigInteger[]> combination_numbers = new HashMap<>();
			for(int i=0; i<lengths.size(); i++){
				Map<Game.Location, BigInteger[]> subset_numbers = this.subsets.get(i).number_occurrences.get(lengths.get(i));
				BigInteger mult = this.subset_count_multipliers.get(i)[lengths.get(i)];
				for(Game.Location loc : subset_numbers.keySet()){
					if(board[loc.row][loc.col]!=Game.UNKNOWN){
						//When reusing subsets, some tiles that have been flagged after its construction can still remain in here because it doesn't change the subset's identity
						//Of course u don't want to consider guessing there but they can just be ignored
						continue;
					}
					BigInteger[] numbers = subset_numbers.get(loc);
					BigInteger[] scaled_numbers = new BigInteger[numbers.length];
					for(int j=0; j<scaled_numbers.length; j++){
						scaled_numbers[j] = mult.multiply(numbers[j]);
					}
					if(combination_numbers.containsKey(loc)){
						//Handling tiles affected by multiple subsets
						BigInteger[] old_numbers = combination_numbers.get(loc);
						BigInteger[] new_numbers = new BigInteger[old_numbers.length];
						Arrays.fill(new_numbers, BigInteger.ZERO);
						for(int x=0; x<scaled_numbers.length; x++){
							for(int y=0; y<old_numbers.length; y++){
								BigInteger n = scaled_numbers[x].multiply(old_numbers[y]);
								if(!n.equals(BigInteger.ZERO)){
									new_numbers[x+y] = new_numbers[x+y].add(n);
								}
							}
						}
						scaled_numbers = new_numbers;
					}
					combination_numbers.put(loc, scaled_numbers);
				}
			}

			//Adjust the numbers for the unaccounted tiles and the flags
			int distributed_mines = this.game.minecount;
			for(int x : lengths){
				distributed_mines -= x;
			}
			for(int r=0; r<this.game.height; r++){
				for(int c=0; c<this.game.width; c++){
					if(this.game.board[r][c]!=Game.UNKNOWN){
						continue;
					}
					Game.Location loc = new Game.Location(r,c);
					int flag_shift = 0;
					int random_shift = 0;
					for(Game.Location n : this.neighbors(loc)){
						int val = board[n.row][n.col];
						if(val == Game.MINE){
							flag_shift++;
						}
						else if(val==Game.UNKNOWN && !this.mine_probabilities.containsKey(n)){
							random_shift++;
						}
					}

					BigInteger[] counts = combination_numbers.get(loc);
					if(counts==null){
						counts = new BigInteger[Game.MINE];
						Arrays.fill(counts,BigInteger.ZERO);
						counts[0]=BigInteger.ONE;
					}
					BigInteger[] adjusted_counts = new BigInteger[counts.length];
					Arrays.fill(adjusted_counts,BigInteger.ZERO);

					int mine_spaces = unknown_squares-(this.mine_probabilities.containsKey(loc)?0:1);
					int min_mines = Math.max(0, distributed_mines-(mine_spaces-random_shift));
					random_shift = Math.min(random_shift, distributed_mines)-min_mines;

					for(int mines = 0; mines<=random_shift; mines++){
						BigInteger mines_ways = LitStrategy.hypergeometric_occurrences(mine_spaces, distributed_mines, random_shift, mines);
						for(int i=0; i<counts.length; i++){
							if(!counts[i].equals(BigInteger.ZERO)){
								int adjusted_idx = i+flag_shift+min_mines+mines;
								adjusted_counts[adjusted_idx] = adjusted_counts[adjusted_idx].add(
									counts[i].multiply(mines_ways)
								);
							}
						}
					}
					combination_numbers.put(loc, adjusted_counts);
				}
			}
			

			//Add er to the tally
			for(Game.Location loc : combination_numbers.keySet()){
				BigInteger[] occurrences = number_occurrences.get(loc);
				BigInteger[] local_occurrences = combination_numbers.get(loc);
				for(int i=0; i<occurrences.length; i++){
					occurrences[i] = occurrences[i].add(local_occurrences[i]);
				}
			}
		});
		
		//Average out the number of occurrences to get probabilities
		this.number_probabilities = new HashMap<>();
		for(Game.Location loc : number_occurrences.keySet()){
			BigInteger[] occurrences = number_occurrences.get(loc);
			BigInteger total = BigInteger.ZERO;
			for(int i=0; i<occurrences.length; i++){
				total = total.add(occurrences[i]);
			}
			double[] probabilities = new double[occurrences.length];
			if(total.equals(BigInteger.ZERO)){
				probabilities[0]=1.0;
			}
			else{
				for(int i=0; i<occurrences.length; i++){
					probabilities[i] = LitStrategy.ratio(occurrences[i],total);
				}
			}
			this.number_probabilities.put(loc, probabilities);
		}
		
		/*
		System.out.println(this.subsets.size());
		for(Map.Entry<Game.Location, double[]> e : this.number_probabilities.entrySet()){
			System.out.print(e.getKey());
			System.out.println(Arrays.toString(e.getValue()));
		}
		System.out.println("--");
		throw new RuntimeException();*/
	}
	protected static <T> void cartesianProduct(List<List<T>> counts, Consumer<List<T>> callback){
		LitStrategy.cartesianProductRecursive(counts, callback, new ArrayList<>());
	}
	private static <T> void cartesianProductRecursive(List<List<T>> counts, Consumer<List<T>> callback, List<T> progress){
		if(progress.size()==counts.size()){
			callback.accept(progress);
		}
		else{
			for(T i : counts.get(progress.size())){
				progress.add(i);
				cartesianProductRecursive(counts, callback, progress);
				progress.remove(progress.size()-1);
			}
		}
	}

	//Caching all these "n choose r" results really makes a difference
	private static final List<BigInteger> comb_cache = new ArrayList<>();
	private static BigInteger comb(int n, int r){
		int index = (n*(n+1)/2)+r;
		if(comb_cache.size()<=index){
			//Triangular root of the size to determine what's already been done
			int previous_n = ((int)Math.sqrt(comb_cache.size()*8+1)-1)/2-1;
			for(int curr_n = previous_n+1; curr_n<=n; curr_n++){
        		BigInteger res = BigInteger.ONE;
        		comb_cache.add(res);
				for(int i=1; i<=curr_n; i++){
					if(i<=curr_n-i){
			            res = res.multiply(BigInteger.valueOf(curr_n - i + 1)).divide(BigInteger.valueOf(i));
	        			comb_cache.add(res);
	        		}
	        		else{
	        			comb_cache.add(comb_cache.get(comb_cache.size() + curr_n - 2*i ));
	        		}
        		}
        	}
        }
        return comb_cache.get(index);
	}
	private static BigInteger hypergeometric_occurrences(int trials, int successes, int observed, int observed_successes){
		//Given there are N minesweeper squares and i know K of them are mines
		//If i observe X of them, how many ways can Y out of those X be mines?
		return LitStrategy.comb(successes,observed_successes).multiply(LitStrategy.comb(trials-successes, observed-observed_successes));
		//Sum this up through all values of Y from 0 to X = N choose X
	}
	private static double ratio(BigInteger top, BigInteger bot){
		//If you just donvert them straight to doubles they will often be infinity and then you get NaN fun time
		
		BigDecimal top_dec = new BigDecimal(top);
		BigDecimal bot_dec = new BigDecimal(bot);
		return top_dec.divide(bot_dec, 18, java.math.RoundingMode.HALF_EVEN).doubleValue();
	}
	private static int countNonzero(double[] arr){
		int ans = 0;
		for(double d : arr){
			if(d!=0.0){
				ans++;
			}
		}
		return ans;
	}
	private static int countNonzero(int[] arr){
		int ans = 0;
		for(int d : arr){
			if(d!=0){
				ans++;
			}
		}
		return ans;
	}
	private static int countNonzero(BigInteger[] arr){
		int ans = 0;
		for(BigInteger d : arr){
			if(!d.equals(BigInteger.ZERO)){
				ans++;
			}
		}
		return ans;
	}


	protected void probabilitySafeMoves(){
		for(Map.Entry<Game.Location, Double> entry : this.mine_probabilities.entrySet()){
			Consumer<Game.Location> method = null;
			if(entry.getValue()==1.0){
				method = this::flag;
			}
			else if(entry.getValue()==0.0){
				method = this::open;
			}
			else{
				continue;
			}
			if(entry.getKey() == null){
				for(int r=0; r<this.game.height; r++){
					for(int c=0; c<this.game.width; c++){
						Game.Location loc = new Game.Location(r,c);
						if(this.game.board[r][c]==Game.UNKNOWN && !this.mine_probabilities.containsKey(loc)){
							method.accept(loc);
						}
					}
				}
			}
			else{
				method.accept(entry.getKey());
			}
		}
	}

	protected void takeForcedGuesses(){
		for(Subset ss : this.subsets){
			//Make sure subset doesnt have multiple valid lengths
			if(LitStrategy.countNonzero(ss.minecount_occurrences)!=1){
				continue;
			}
			if(this.checkSubsetSameBorders(ss.hidden_area)){
				//Ability to gain new info tie-broken by safety percentage
				Game.Location choice = Collections.min(ss.hidden_area,
					(Game.Location a, Game.Location b)->
						Double.compare(this.mine_probabilities.get(a), this.mine_probabilities.get(b))
						-Game.MINE*Integer.compare(
							Math.min(2,LitStrategy.countNonzero(this.number_probabilities.get(a))), 
							Math.min(2,LitStrategy.countNonzero(this.number_probabilities.get(b)))
						)
				);
				/*
				System.out.println(ss.hidden_area);
				for(Game.Location loc : ss.hidden_area){
					System.out.print(loc);
					System.out.print(this.mine_probabilities.get(loc));
					System.out.println(Arrays.toString(this.number_probabilities.get(loc)));
				}
				System.out.println(choice);*/
				this.open(choice);
				continue;
			}
			//Identify future 5050s
			//These situations are rare but this is a good strategy for them
			Game.Location[] hiddens = ss.hidden_area.toArray(new Game.Location[0]);
			for(int i=0; i<hiddens.length; i++){
				Game.Location a = hiddens[i];
				for(int j=0; j<i; j++){
					Game.Location b = hiddens[j];
					//Test that they border the same non-flag tiles
					if(uniqueInfoSources(a,b) || uniqueInfoSources(b,a)){
						continue;
					}
					//Test that they cannot be both mines
					if(checkBothMines(a,b)){
						continue;
					}
					
					//If they have the same info, i think it wont matter which one is guessed
					this.open(a);
					//System.out.print(a);
					//System.out.println(b);
				}
			}
		}
	}
	private static boolean adjacent(Game.Location a, Game.Location b){
		return Math.abs(a.row-b.row)<=1 && Math.abs(a.col-b.col)<=1;
	}
	//Tests if a has any way of gaining information that wont be also gained for b
	private boolean uniqueInfoSources(Game.Location a, Game.Location b){
		for(Game.Location n : this.neighbors(a)){
			if(n.equals(b) || this.game.board[n.row][n.col]==Game.MINE){
				continue;
			}
			if(!adjacent(n,b)){
				return true;
			}
		}
		return false;
	}
	//Check if all the hidden tiles in the subset border the same hidden tiles outside the subset
	private boolean checkSubsetSameBorders(Collection<Game.Location> area){
		Set<Game.Location> potential_info = null;
		for(Game.Location loc : area){
			Set<Game.Location> local_info = new HashSet<>();
			for(Game.Location n : this.neighbors(loc)){
				if(!area.contains(n) && this.game.board[n.row][n.col]==Game.UNKNOWN){
					local_info.add(n);
				}
			}
			if(potential_info==null){
				potential_info = local_info;
			}
			else if(!potential_info.equals(local_info)){
				return false;
			}
		}
		return true;
	}
	//Is it possible for a and b to both be mines?
	private boolean checkBothMines(Game.Location a, Game.Location b){
		Set<Game.Location> neighbor_intersection = new HashSet<>(Arrays.asList(this.neighbors(a)));
		neighbor_intersection.retainAll(Arrays.asList(this.neighbors(b)));
		for(Game.Location loc : neighbor_intersection){
			int mines_need = this.game.board[loc.row][loc.col]-2;
			for(Game.Location n : this.neighbors(loc)){
				if(this.game.board[n.row][n.col]==Game.MINE){
					mines_need--;
				}
			}
			if(mines_need<0){
				return false;
			}
		}
		return true;
	}

	protected void probabilityBestGuess(){
		if(this.mine_probabilities.size()==0){
			return;
		}
		List<Map.Entry<Game.Location,Double>> candidates = this.mine_probabilities.entrySet().stream().filter((Map.Entry<Game.Location,Double> e)->
			//Dont consider opening tiles that can only have 1 possible number on them
			e.getKey()==null || LitStrategy.countNonzero(this.number_probabilities.get(e.getKey()))>1
		).collect(Collectors.toList());
		if(candidates.size()==0){
			//This would happen if there's no more information possibly gained
			return;
		}
		candidates.sort((Map.Entry<Game.Location,Double> a, Map.Entry<Game.Location,Double> b) -> 
			Double.compare(a.getValue(),b.getValue())
		);
		double best_safety = candidates.get(0).getValue();
		//Maybe there is a better threshold, more tuning may be needed
		double safety_threshold = best_safety+0.1;

		double best_score = -1;
		Game.Location best_move = null;

		for(int i=0; i<candidates.size() && candidates.get(i).getValue()<safety_threshold; i++){
			Game.Location loc = candidates.get(i).getKey();
			if(loc==null){
				Iterable<Game.Location> best = this.findBestUninformedGuesses();
				List<Map.Entry<Game.Location,Double>> uninformed = new ArrayList<>();
				for(Game.Location g : best){
					uninformed.add(new AbstractMap.SimpleImmutableEntry<>(
						g, candidates.get(i).getValue()
					));
				}
				candidates.addAll(i+1, uninformed);
			}
			else{
				double progress = this.progressProbability(loc, this.number_probabilities.get(loc));
				//This one is unscientific, maybe there is a better way to score them
				double score = (1-candidates.get(i).getValue()) * progress;
				if(score>best_score){
					best_move = loc;
					best_score = score;
				}
			}
		}
		if(best_move!=null){
			//System.out.println(best_move);
			this.open(best_move);
		}
	}

	private Collection<Game.Location> findBestUninformedGuesses(){
		//The best tiles that are not touching any open tiles
		int best_score = 0;
		int best_hidden = 0;
		Map<Game.Location,Integer> all_proximity = new HashMap<>();
		List<Function<Game.Location,Game.Location[]>> neighbor_fns = List.of(
			this::second_neighbors,
			this::neighbors,
			(Game.Location loc)->new Game.Location[]{loc}
		);
		for(Subset ss : this.subsets){
			for(Game.Location loc : ss.hidden_area){
				for(int i=0; i<neighbor_fns.size(); i++){
					int score = i+1;
					for(Game.Location n : neighbor_fns.get(i).apply(loc)){
						Integer prev_score = all_proximity.get(n);
						if(prev_score==null || prev_score<score){
							all_proximity.put(n,score);
						}
					}
				}
			}
		}
		Set<Game.Location> candidates = new HashSet<>();
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				Game.Location loc = new Game.Location(r,c);
				if(this.game.board[r][c]==Game.UNKNOWN && !this.mine_probabilities.containsKey(loc)){

					int hidden = 0;
					int proximity = 0;
					for(Game.Location n : this.neighbors(loc)){
						if(this.game.board[n.row][n.col] == Game.UNKNOWN){
							hidden++;
						}
						Integer s = all_proximity.get(n);
						if(s!=null){
							proximity=Math.max(proximity, s);
						}
					}
					//Fewest neighboring unknown tiles tie-broken by being nearish to an open tile
					int score = (Game.MINE-hidden)*(neighbor_fns.size()+1)+proximity;
					if(hidden>0 && score>=best_score){
						if(score>best_score){
							best_score=score;
							best_hidden = hidden;
							candidates.clear();
						}
						candidates.add(loc);
					}
				}
			}
		}
		return candidates;
	}
	private double progressProbability(Game.Location loc, double[] number_weights){
		if(LitStrategy.countNonzero(number_weights)<=1){
			//If the tile can only have 1 number on it, there isnt much point in opening it
			return 0.0;
		}
		double answer = 0.0;
		for(int number=0; number<number_weights.length; number++){
			double weight = number_weights[number];
			if(weight==0.0){
				continue;
			}
			if(this.testProgress(loc, number)){
				answer += weight;
			}
		}
		/*
		System.out.print(loc);
		System.out.print(Arrays.toString(number_weights));
		System.out.println(answer);*/
		return answer;
	}
	private boolean testProgress(Game.Location loc, int number){
		List<Subset> backup_subsets = new ArrayList<>(this.subsets);
		Map<Game.Location, Double> backup_mine_probabilities = this.mine_probabilities;
		Map<Game.Location, double[]> backup_number_probabilities = this.number_probabilities;

		this.game.board[loc.row][loc.col] = number;
		boolean answer = false;

		RuntimeException err = null;

		this.moves.push(null);
		for(Runnable step : new Runnable[]{
			this::singlePointSearch,
			() -> this.pairwiseSearch(this::neighbors),
			() -> this.pairwiseSearch(this::second_neighbors),
		}){
			try{
				step.run();
			}
			catch(RuntimeException e){
				err = e;
				break;
			}
			while(this.moves.peek()!=null){
				Agent.Action move = this.moves.pop();
				if(move.type == Agent.Action.Type.OPEN){
					answer = true;
					break;
				}
			}
		}
		for(
			Agent.Action move = this.moves.pop();
			move!=null;
			move=this.moves.pop()
		){}

		this.game.board[loc.row][loc.col] = Game.UNKNOWN;

		this.subsets = backup_subsets;
		this.mine_probabilities = backup_mine_probabilities;
		this.number_probabilities = backup_number_probabilities;

		if(err!=null){
			throw err;
		}

		return answer;
	}

	protected void randomProbabilityGuess(){
		double best_probability = 1.0;
		CornerGuesser cg = new CornerGuesser();
		for(Map.Entry<Game.Location, Double> e : this.mine_probabilities.entrySet()){
			if(e.getKey()==null){
				continue;
			}
			double p = e.getValue();
			if(p<=best_probability){
				if(p<best_probability){
					best_probability = p;
					cg = new CornerGuesser();
				}
				cg.add(e.getKey());
			}
		}
		if(cg.get()!=null){
			this.open(cg.get());
		}
	}

	protected void randomCornerGuess(){
		CornerGuesser cg = new CornerGuesser();
		for(int r=0; r<this.game.height; r++){
			for(int c=0; c<this.game.width; c++){
				if(this.game.board[r][c]==Game.UNKNOWN && !this.mine_probabilities.containsKey(new Game.Location(r,c))){
					cg.add(new Game.Location(r,c));
				}
			}
		}
		this.open(cg.get());
	}
	public class CornerGuesser{
		private Random rand;
		private Game.Location res = null;
		private int neighbor_count = Game.MINE;
		private int candidate_count = 0;
		public CornerGuesser(){
			this.rand = new Random(21);
		}
		public void add(Game.Location loc){
			int hidden_neighbors = 0;
			for(Game.Location n : LitStrategy.this.neighbors(loc)){
				if(LitStrategy.this.game.board[n.row][n.col]==Game.UNKNOWN){
					hidden_neighbors++;
				}
			}
			if(hidden_neighbors<this.neighbor_count){
				this.neighbor_count = hidden_neighbors;
				this.candidate_count = 0;
			}
			if(hidden_neighbors>this.neighbor_count){
				return;
			}
			if(this.candidate_count == this.rand.nextInt(this.candidate_count+1)){
				res = loc;
			}
			this.candidate_count++;
		}
		public Game.Location get(){
			return this.res;
		}
	}
}
