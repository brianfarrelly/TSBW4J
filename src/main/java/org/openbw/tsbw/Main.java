package org.openbw.tsbw;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.openbw.bwapi.BWMap;
import org.openbw.bwapi.DamageEvaluator;
import org.openbw.bwapi.InteractionHandler;
import org.openbw.bwapi.MapDrawer;
import org.openbw.tsbw.building.BuildingPlanner;
import org.openbw.tsbw.strategy.AbstractGameStrategy;
import org.openbw.tsbw.strategy.MiningFactory;
import org.openbw.tsbw.strategy.MiningStrategy;
import org.openbw.tsbw.strategy.ScoutingFactory;
import org.openbw.tsbw.strategy.ScoutingStrategy;
import org.openbw.tsbw.strategy.StrategyFactory;
import org.openbw.tsbw.unit.Barracks;
import org.openbw.tsbw.unit.Building;
import org.openbw.tsbw.unit.Bunker;
import org.openbw.tsbw.unit.CommandCenter;
import org.openbw.tsbw.unit.Geyser;
import org.openbw.tsbw.unit.Hatchery;
import org.openbw.tsbw.unit.MineralPatch;
import org.openbw.tsbw.unit.MobileUnit;
import org.openbw.tsbw.unit.Nexus;
import org.openbw.tsbw.unit.PhotonCannon;
import org.openbw.tsbw.unit.Refinery;
import org.openbw.tsbw.unit.SunkenColony;
import org.openbw.tsbw.unit.UnitFactory;
import org.openbw.tsbw.unit.Worker;

import bwapi.BWEventListener;
import bwapi.Game;
import bwapi.Key;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;

public class Main implements BWEventListener {

	private static final Logger logger = LogManager.getLogger();

	private Mirror mirror;
	
	private Player player1;
	private Player player2;
	
	private UnitInventory unitInventory1;
	private UnitInventory unitInventory2;
	private BWMap bwMap;
	private MapDrawer mapDrawer;
	private InteractionHandler interactionHandler;
	private DamageEvaluator damageEvaluator;
	private BuildingPlanner buildingPlanner;
	
	private MiningFactory miningFactory;
	private MiningStrategy miningStrategy;
	
	private ScoutingFactory scoutingFactory;
	private ScoutingStrategy scoutingStrategy;
	
	private StrategyFactory strategyFactory;
	private AbstractGameStrategy gameStrategy;
	
	private boolean scoutingEnabled = true;
	private boolean cleanLogging = false;
	private boolean gameStarted = false;
	
	public void initialize() {

		logger.debug("initializing...");
		
		this.unitInventory1 = new UnitInventory();
		this.unitInventory2 = new UnitInventory();
		
		this.bwMap = new BWMap();
		this.damageEvaluator = new DamageEvaluator();
		this.interactionHandler = new InteractionHandler();
		this.mapDrawer = new MapDrawer(false);

		this.scoutingStrategy = this.scoutingFactory.getStrategy(bwMap, mapDrawer);
		this.buildingPlanner = new BuildingPlanner(unitInventory1, interactionHandler, bwMap);

		this.miningStrategy = this.miningFactory.getStrategy(mapDrawer, interactionHandler);
		logger.debug("initializing done.");
	}
	
	public final void run() {
		
		logger.trace("executing run().");
		this.mirror = new Mirror();
		this.mirror.getModule().setEventListener(this);
		logger.debug("starting game...");
		this.mirror.startGame();
	}
	
	public Main(MiningFactory miningFactory, ScoutingFactory scoutingFactory, StrategyFactory strategyFactory) {
		
		this.miningFactory = miningFactory;
		this.scoutingFactory = scoutingFactory;
		this.strategyFactory = strategyFactory;
	}
	
	@Override
	public void onStart() {
		
		logger.info("--- starting game - {}", new Date());
		logger.debug("CWD: {}", System.getProperty("user.dir"));
		
		try {
			this.gameStarted = false;
			Game game = mirror.getGame();
			
			this.player1 = game.self();
			this.player2 = game.enemy();
			
			this.gameStrategy = strategyFactory.getStrategy(mapDrawer, bwMap, scoutingStrategy, unitInventory1, 
					unitInventory2, buildingPlanner, damageEvaluator);
			
			this.mapDrawer.initialize(game);
			this.damageEvaluator.initialize(game);
			this.bwMap.initialize(game);
			this.interactionHandler.initialize(game);
			logger.info("playing on {} (hash: {})", this.bwMap.mapFileName(), this.bwMap.mapHash());
			
			this.unitInventory1.initialize();
			this.unitInventory2.initialize();
			
			this.buildingPlanner.initialize();
			this.scoutingStrategy.initialize(unitInventory1.getScouts(), unitInventory1);
			
			this.gameStrategy.initialize();

			logger.info("latency: {} ({}). latency compensation: {}", game.getLatency(), game.getLatencyFrames(), game.isLatComEnabled());
		
			for (bwapi.Unit mineralPatch : game.getStaticMinerals()) {
				this.addToInventory(mineralPatch, unitInventory1, 0);
				this.addToInventory(mineralPatch, unitInventory2, 0);
			}
			for (bwapi.Unit geyser : game.getStaticGeysers()) {
				this.addToInventory(geyser, unitInventory1, 0);
				this.addToInventory(geyser, unitInventory2, 0);
			}
			
			game.setTextSize(bwapi.Text.Size.Enum.Default);
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
	
	@Override
	public void onEnd(boolean isWinner) {
		
		logger.info("--- ending game - {}.", (isWinner? "WIN": "LOSS"));
	}
	
	@Override
	public void onFrame() {
		
		try {
			
			int frameCount = interactionHandler.getFrameCount();
			
			if (!gameStarted || frameCount < 1) {
				return;
			}
			
			miningStrategy.run(frameCount);
			
			/*
			 * Do every 5 frames (just for performance reasons)
			 */
			if (frameCount % 5 == 0) {
				
				if (scoutingEnabled) {
					scoutingStrategy.run(frameCount);
				}
				
				buildingPlanner.run(player1.minerals(), frameCount);
				
				// some simple interaction: enable global map drawing or change logging output
				if (interactionHandler.getKeyState(Key.K_CONTROL) && interactionHandler.getKeyState(Key.K_T)) {
					mapDrawer.setEnabled(!mapDrawer.isEnabled());
				} else if (interactionHandler.getKeyState(Key.K_CONTROL) && interactionHandler.getKeyState(Key.K_R)) {
					toggleCleanLogging();
				}
			}
			
			int availableMinerals = player1.minerals() - buildingPlanner.getQueuedMinerals();
			int availableSupply = player1.supplyTotal() - player1.supplyUsed();
			
			gameStrategy.run(frameCount, availableMinerals, availableSupply);
			
			drawGameInfo();
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
		
	}
	
	private void drawGameInfo() {
		
		mapDrawer.drawTextScreen(450, 25, "game time: " + interactionHandler.getFrameCount());
		mapDrawer.drawTextScreen(530, 35, "FPS: " + interactionHandler.getFPS());
	}

	private void toggleCleanLogging() {
		
		this.cleanLogging = !cleanLogging;
		interactionHandler.sendText("clean logging: " + cleanLogging);
		String appenderToAdd = cleanLogging ? "Clean" : "Console";
		String appenderToRemove = cleanLogging ? "Console" : "Clean";
		
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        
        for (org.apache.logging.log4j.core.Logger logger : ctx.getLoggers()) {
        	
        	logger.removeAppender(config.getAppender(appenderToRemove));
        	config.addLoggerAppender(logger, config.getAppender(appenderToAdd));
        }
        ctx.updateLoggers();
		
	}
	
	@Override
	public void onSendText(String text) {
		// do nothing
		
	}
	@Override
	public void onReceiveText(Player player, String text) {
		// do nothing
		
	}
	@Override
	public void onPlayerLeft(Player player) {
		// do nothing
	}
	
	@Override
	public void onNukeDetect(Position target) {
		// do nothing
	}
	
	private void addToInventory(Unit bwUnit, UnitInventory inventory, int timeSpotted) {
		
		UnitType type = bwUnit.getType();
		boolean exists = inventory.update(bwUnit, timeSpotted);
		logger.trace("adding {} {}. exists: {}", type, bwUnit.getID(), exists);
		
		if (!exists) {
			try {
				if (type.equals(UnitType.Resource_Vespene_Geyser)) {
					
					inventory.register(UnitFactory.create(Geyser.class, bwUnit, this.bwMap));
				} else if (type.equals(UnitType.Resource_Mineral_Field)) {
					
					inventory.register(UnitFactory.create(MineralPatch.class, bwUnit, this.bwMap));
				} else if (type.isRefinery()) {
					
					inventory.register(UnitFactory.create(Refinery.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Terran_Bunker)) {
					
					inventory.register(UnitFactory.create(Bunker.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Protoss_Photon_Cannon)) {
					
					inventory.register(UnitFactory.create(PhotonCannon.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Zerg_Sunken_Colony)) {
					
					inventory.register(UnitFactory.create(SunkenColony.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Terran_Barracks)) {
								
					inventory.register(UnitFactory.create(Barracks.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Terran_Command_Center)) {
					
					inventory.register(UnitFactory.create(CommandCenter.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Zerg_Hatchery)) {
					
					inventory.register(UnitFactory.create(Hatchery.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.equals(UnitType.Protoss_Nexus)) {
					
					inventory.register(UnitFactory.create(Nexus.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.isBuilding()) {
					
					inventory.register(UnitFactory.create(Building.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (type.isWorker()) {
					
					inventory.register(UnitFactory.create(Worker.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				} else if (!bwUnit.getPlayer().isNeutral()) {
					
					inventory.register(UnitFactory.create(MobileUnit.class, damageEvaluator, bwMap, bwUnit, timeSpotted));
				}
				
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {

				logger.fatal("Could not create unit: " + e.getMessage(), e);
				System.exit(1);
			}
		}
	}
	
	@Override
	public void onUnitDiscover(Unit bwUnit) {
		
		try {
			logger.debug("onDiscover: discovered {} with ID {}", bwUnit.getType(), bwUnit.getID());
	
			if (bwUnit.getPlayer().equals(player2)) {
				addToInventory(bwUnit, unitInventory2, interactionHandler.getFrameCount());
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
	
	@Override
	public void onUnitEvade(Unit unit) {
		// do nothing
		
	}
	@Override
	public void onUnitShow(Unit unit) {
		// do nothing
		
	}
	@Override
	public void onUnitHide(Unit unit) {
		// do nothing
		
	}
	
	@Override
	public void onUnitCreate(Unit bwUnit) {
		
		try {
			logger.debug("onCreate: New {} unit created ", bwUnit.getType());
			if (bwUnit.getPlayer().equals(player1)) {
				
				if (bwUnit.getType().isBuilding()) {
					
					this.addToInventory(bwUnit, unitInventory1, interactionHandler.getFrameCount());
					if (bwUnit.getBuildUnit() != null) {
						Worker worker = unitInventory1.getAllWorkers().getValue(bwUnit.getBuildUnit().getID());
						buildingPlanner.onConstructionStarted(worker);
					}
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
	
	@Override
	public void onUnitDestroy(Unit bwUnit) {
		
		try {
			logger.debug("destroyed {} with ID {}", bwUnit.getType(), bwUnit.getID());
	
			if (bwUnit.getPlayer().equals(player1)) {
				
				onUnitDestroy(bwUnit, unitInventory1);
			} else if (bwUnit.getPlayer().equals(player2)) {
				
				onUnitDestroy(bwUnit, unitInventory2);
			} else if (bwUnit.getType().equals(UnitType.Resource_Mineral_Field)) {
				
				onUnitDestroy(bwUnit, unitInventory1);
				onUnitDestroy(bwUnit, unitInventory2);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
	
	private void onUnitDestroy(Unit bwUnit, UnitInventory unitInventory) {
		
		unitInventory.onUnitDestroy(bwUnit, interactionHandler.getFrameCount());
		
	}
	
	@Override
	public void onUnitMorph(Unit unit) {
		// do nothing
		
	}
	@Override
	public void onUnitRenegade(Unit unit) {
		// do nothing
		
	}
	@Override
	public void onSaveGame(String gameName) {
		// do nothing
	}
	
	@Override
	public void onUnitComplete(Unit bwUnit) {
		
		try {
			logger.debug("completed {} with ID {}", bwUnit.getType(), bwUnit.getID());
			
			if (bwUnit.getPlayer().equals(player1)) {
				addToInventory(bwUnit, unitInventory1, interactionHandler.getFrameCount());
			}
			
			// Once the initial 4 workers and the command centers have fired their triggers we truly start the game
			if (!gameStarted && unitInventory1.getAllWorkers().size() == 4 && !unitInventory1.getCommandCenters().isEmpty()) {
				
				unitInventory1.getMiningWorkers().addAll(unitInventory1.getAllWorkers());
				miningStrategy.initialize(unitInventory1.getCommandCenters(), unitInventory1.getMiningWorkers(), unitInventory1.getMineralPatches());
				gameStrategy.start(player1.minerals());
				gameStarted = true;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
	
	@Override
	public void onPlayerDropped(Player player) {
		// do nothing
	}
}