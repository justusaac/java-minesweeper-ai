## AI Minesweeper

This is a Java implementation of the classic Minesweeper game that can be played by humans, as well as programs that define a strategy for playing. 

It is made for Java 21 so make sure you have that installed. You can compile all the code like `javac -d . *.java strategies/*.java` and then open the game by running `java minesweeper.Graphics`.

You can also run `minesweeper.StrategyTest` if you want to test out an AI strategy without graphics. It will run the strategy on a bunch of random Minesweeper boards and report the win rate and speed, i.e. a Monte Carlo simulation. You can run it with the `-h` flag to see the different options you can configure it with. If the strategy throws an exception it will export the board it was on so that you can load it into the version with graphics to see what happened.

To make a new AI strategy, it just needs to be a class implementing the `Agent` interface and should redefine the `newAgent` method. The most important fields of the `Game` object to help in making a good move are `board` and `minecount`.

The strategy that I made that is in `LitStrategy.java` has a ~52% win rate on Expert difficulty. I made a video of it running:

[![Demo video](https://img.youtube.com/vi/vigix0KgzKQ/0.jpg)](https://www.youtube.com/watch?v=vigix0KgzKQ "Demo video (on Youtube)")
