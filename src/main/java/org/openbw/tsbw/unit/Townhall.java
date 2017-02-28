package org.openbw.tsbw.unit;

import org.openbw.bwapi.BWMap;
import org.openbw.bwapi.DamageEvaluator;

import bwapi.Unit;

/**
 * A super class for command center, hatchery, and nexus.
 * Allows for more generic implementations of behavior dealing with e.g. any mains or where to place an expansion. 
 */
public abstract class Townhall extends Building {

	public Townhall(DamageEvaluator damageEvaluator, BWMap bwMap, Unit bwUnit, int timeSpotted) {
		super(damageEvaluator, bwMap, bwUnit, timeSpotted);
	}
}
