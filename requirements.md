# Basic Tower Defense Game for Android

## Overview

I want to build a tower defense game.  The ultimate goal is to learn to make these sorts of games, so that I can build one I've been thinking of for a long time.  This needs to target android phones.

## Requirements

* Create your own graphics.  I'm looking for something very simple - bold geometric shapes
* Portrait mode
* Every game is either 10, 20, 25 or 30 waves
* Maps are randomly created
* Difficulty rises with the wave number, resetting at wave one of each game. Wave 8 is wave 8
  whatever the length, so a 30-wave game is harder than a 10-wave one rather than the same arc
  stretched out - but the player has had thirty waves of income, building and route to match it
* There should be these kinds of enemies
	- Square - basic tank units
	- Triangle - fast but weak
	- Circle - Takes multiple hits to kill
* These should be these weapon types
	- Square - basic "one shot" bullets at a medium pace
	- Hexagon - Explosions with area-of-effect damage.  Slow pace
	- Plus-sign - Lasers that go through every unit in line. Reaches further than the other two
	  weapons, but under half the width of the board - not across the whole map
* Add music.  Write the music yourself. It should be early-techno style, very simple

## Maps and movement

There is no pre-set path. The board is an open field, and enemies work out their own way across
it from the entry to the exit.

* A weapon can be placed anywhere on the open field
* Maps are populated with fixed blockers that enemies cannot move through
* Enemies cannot move through weapons either, so placing a weapon changes the path
* A placement that would leave no way at all from the entry to the exit is refused, and so is
  one that would strand an enemy already on the board. The board can be bent, not sealed.
* A weapon cannot be dropped onto a cell an enemy is standing on or walking into
* The route the enemies are currently taking is drawn on the board, so the effect of building
  can be seen while building

## The wave timer

* The start-wave button carries a 15 second countdown, shown as a number and as a bar draining
  across the button
* When it reaches zero the next wave starts on its own
* Pressing the button before then pays 2 gold for every second still on the clock, so going
  early is a trade: less time to build, more money to build with
* A wave that arrives on its own pays nothing

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
* Taking the early-start bonus plays a short run of bright blips, like coins landing
* Like the music, every effect is synthesized in code. There are no audio files in the project.

## Game controls

* A pause button. The board holds where it is and shows that it is paused; building,
  upgrading and selling still work while held.
* A speed button cycling 1x, 2x and 5x, and back to 1x from 5x. The button shows the speed it
  is currently running at.
* Two separate sound buttons: one for the music and one for the sound effects, so either can
  be silenced without the other. A muted button is struck through as well as dimmed, so its
  state reads without relying on colour.
