# Basic Tower Defense Game for Android

## Overview

I want to build a tower defense game.  The ultimate goal is to learn to make these sorts of games, so that I can build one I've been thinking of for a long time.  This needs to target android phones.

## Requirements

* Create your own graphics.  I'm looking for something very simple - bold geometric shapes
* Portrait mode
* Every game is either 10, 20, 25 or 30 waves
* Maps are randomly created
* There is no concert of easy or hard, just make the maps without worrying about difficulty
* There should be these kinds of enemies
	- Square - basic tank units
	- Triangle - fast but weak
	- Circle - Takes multiple hits to kill
* These should be these weapon types
	- Square - basic "one shot" bullets at a medium pace
	- Hexagon - Explosions with area-of-effect damage.  Slow pace
	- Plus-sign - Lasers that go through all the units until the end of the board
* Add music.  Write the music yourself. It should be early-techno style, very simple

## Sound effects

* Every weapon has its own shot sound, so you can tell what is firing without looking
	- Square - a dry, clipped pop, kept quiet because it fires constantly
	- Hexagon - a hollow launch thump, then a much louder explosion when the shell lands
	- Plus-sign - a downward zap
* Every enemy has its own sound when it is destroyed, matched to what it is
	- Square - a low, dull crunch, because it is the tank
	- Triangle - a light, high tick, because it is the flimsy one
	- Circle - a metallic clank, because it is the armoured one
* Upgrading a weapon plays a rising three-note chime
* Like the music, every effect is synthesized in code. There are no audio files in the project.

## Game controls

* A pause button. The board holds where it is and shows that it is paused; building,
  upgrading and selling still work while held.
* A speed button cycling 1x, 2x and 5x, and back to 1x from 5x. The button shows the speed it
  is currently running at.
* A single sound button mutes and unmutes everything, music and effects together.
