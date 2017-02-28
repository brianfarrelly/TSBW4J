package org.openbw.tsbw.unit;

import java.util.List;
import java.util.Queue;

import org.openbw.bwapi.BWMap;
import org.openbw.bwapi.DamageEvaluator;
import org.openbw.tsbw.MyMap;
import org.openbw.tsbw.UnitInventory;
import org.openbw.tsbw.building.ConstructionProject;

import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;

public class Hatchery extends Townhall {

	private static Construction constructionInstance = null;
	
	public static Construction getInstance(BWMap bwMap) {
		
		if (constructionInstance == null) {
			constructionInstance = new ConstructionProvider(UnitType.Zerg_Hatchery, bwMap) {
				@Override
				public TilePosition getBuildTile(Worker builder, UnitInventory unitInventory, Queue<ConstructionProject> projects) {
					
					TilePosition buildTile = null;
					Building main = unitInventory.getMain();
					
					List<TilePosition> baseLocations = bwMap.getStartLocations();
					
					double distance = Double.MAX_VALUE;
					for (TilePosition currentPosition : baseLocations) {
						
						if (bwMap.canBuildHere(currentPosition, UnitType.Zerg_Hatchery) && MyMap.isConnected(main.getTilePosition(), currentPosition)) {
							
							double currentDistance = MyMap.getGroundDistance(main.getTilePosition(), currentPosition);
							if (currentDistance < distance) {
								buildTile = currentPosition;
								distance = currentDistance;
							}
						}
					} 
					return buildTile;
				}
			};
		}
		return constructionInstance;
	}
	
	Hatchery(DamageEvaluator damageEvaluator, BWMap bwMap, Unit bwUnit, int timeSpotted) {
		super(damageEvaluator, bwMap, bwUnit, timeSpotted);
	}
}
