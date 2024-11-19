package minesweeper;
import java.util.Scanner;
import java.text.DecimalFormat;
import java.io.IOException;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.Random;

public class StrategyTest extends Game{
	protected long startTime;
	protected long endTime;

	public StrategyTest(int height, int width, int mines){
		this(height, width, mines, true);
	}
	public StrategyTest(int height, int width, int mines, boolean zero_start){
		super(height,width,mines,zero_start);
	}

	public long seed;
	protected void generateBoard(Location first_loc){
		this.generateBoard(first_loc, new Random(this.seed));
	}

	protected void setState(State state){
		super.setState(state);
		if(state==State.ACTIVE){
			this.startTime = System.nanoTime();
		}
		else if(state==State.WIN || state==State.LOSE){
			this.endTime = System.nanoTime();
		}
	}

	public static void main(String[] args) throws Exception{
		long seed = new Random().nextLong();

		int trials = 1000;
		int height = 16;
		int width = 30;
		int mines = 99;
		boolean zero_start = true;

		String class_name = "minesweeper.strategies.LitStrategy";

		for(String arg : args){
			if(arg.matches(".*-h.*")){
				System.out.printf("""
Extra options you can put:
  ^(\\d+)x(\\d+)x(\\d+)$     the height, width and number of mines, default %dx%dx%d
  ^\\d+$                   the number of trials that will be run, default %d
  -h                      prints this message
  ^-seed\\d*               the seed to be used for RNG, default is random
  ^-classic$              removes the guarantee that the first tile opened will be 0
  anything else           the class name for the strategy that will be tested, default %s
				""",height,width,mines,trials,class_name);
				return;
			}
			else if(arg.matches("^(\\d+)x(\\d+)x(\\d+)$")){
				Scanner dimensions = new Scanner(arg).useDelimiter("x");
				height = dimensions.nextInt();
				width = dimensions.nextInt();
				mines = dimensions.nextInt();
			}
			else if(arg.matches("^\\d+$")){
				trials = Integer.parseInt(arg);
			}
			else if(arg.matches("^-seed\\d*.*")){
				String numbers = arg.replaceAll("[^0-9]","");
				if(numbers.length()==0){
					numbers = "0";
				}
				seed = Long.parseUnsignedLong(numbers);
			}
			else if(arg.matches("^-classic$")){
				zero_start = false;
			}
			else{
				class_name = arg;
			}
		}

		System.out.printf("Seed %s\n", Long.toUnsignedString(seed));
		final Class<? extends Agent> ai_class = Class.forName(class_name).asSubclass(Agent.class);
		System.out.printf("Testing %s for %d games on a %dx%dx%d %sboard\n", ai_class.getName(), trials, height, width, mines, zero_start?"":"classic ");

		final LongAdder time = new LongAdder();
		final LongAccumulator max_time = new LongAccumulator(Long::max, 0L);
		final LongAdder wins = new LongAdder();
		final LongAdder complete = new LongAdder();

		final Runtime runtime = Runtime.getRuntime();
		Thread report_stats = new Thread(()->{
			DecimalFormat f = new DecimalFormat("#.####");
			double win_rate = wins.doubleValue()/complete.doubleValue();
			double elapsed_seconds = time.doubleValue()/Math.pow(10,9);
			double max_seconds = max_time.doubleValue()/Math.pow(10,9);
			double average_seconds = elapsed_seconds/complete.doubleValue();
			System.out.printf(
				"\n%d wins out of %d - %s%%\n%s seconds total; %s maximum; %s average\n",
				wins.sum(), complete.sum(), f.format(win_rate*100), f.format(elapsed_seconds), f.format(max_seconds), f.format(average_seconds)
			);
		});
		runtime.addShutdownHook(report_stats);

		for(int i=0; i<trials; i++){
			final StrategyTest game = new StrategyTest(height,width,mines, zero_start);
			game.seed = new Random(seed+i).nextLong();

			final int idx = i;
			//Idk whether to run the rest of this in a thread or not
			//A completed trial will run faster, but an interrupted trial could have misleading results
			Thread export = new Thread(()->{
				String filename = String.format("./boards/StrategyTest/Board%d.txt", idx);
				try{
					game.export(filename);
				}
				catch(IOException e){
					System.out.printf("Failed to export %s: %s", filename, e);
				}
			});
			Agent a = (Agent)(ai_class.getDeclaredMethod("newAgent", Game.class).invoke(null, game));
			game.attach(a);
			try{
				runtime.addShutdownHook(export);
				game.ai_play();
				runtime.removeShutdownHook(export);
			}
			catch(IllegalStateException e){
				//This will happen when the jvm is already shutting down
				return;
			}
			long elapsed = game.endTime-game.startTime;
			time.add(elapsed);
			max_time.accumulate(elapsed);
			complete.increment();
			if(game.getState()==State.WIN){
				wins.increment();
			}
		
		}
		report_stats.run();
		runtime.removeShutdownHook(report_stats);
	}
}