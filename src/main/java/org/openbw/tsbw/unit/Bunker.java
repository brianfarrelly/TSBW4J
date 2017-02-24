package org.openbw.tsbw.unit;

import org.openbw.bwapi.BWMap;
import org.openbw.bwapi.DamageEvaluator;

import bwapi.Unit;
import bwapi.UnitType;

public class Bunker extends Building implements Construction {

	private static Bunker constructionInstance = null;
	
	Bunker(DamageEvaluator damageEvaluator, BWMap bwMap, Unit bwUnit, int timeSpotted) {
		super(damageEvaluator, bwMap, bwUnit, timeSpotted);
	}
	
	private Bunker(BWMap bwMap) {
		super(bwMap, UnitType.Terran_Bunker);
	}
	
	public static Construction getInstance(BWMap bwMap) {
		
		if (constructionInstance == null) {
			constructionInstance = new Bunker(bwMap);
		}
		return constructionInstance;
	}
}