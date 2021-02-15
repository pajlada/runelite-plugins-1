package com.lmsnotifier;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.HintArrowType;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Last Man Standing"
)
public class LMSPlugin extends Plugin
{
	static final String CONFIG_GROUP_KEY = "lmsconfig";

	private static final WorldArea lmsCompetitiveLobby = new WorldArea(3138, 3639, 8, 7, 0);
	private static final WorldArea lmsCasualLobby = new WorldArea(3139, 3639, 6, 6, 1);
	private static final WorldArea lmsHighStakesLobby = new WorldArea(3138, 3639, 8, 7, 2);
	private static final Set<Integer> lms_chest_ids = new HashSet<>(ObjectID.CHEST_29069, ObjectID.CHEST_29070);
	boolean inGame = false;
	List<GameObject> crates = new LinkedList<>();
	private boolean inLobby = false;
	private WorldPoint originalHintPoint;
	@Inject
	private Client client;

	@Inject
	private LMSConfig config;

	@Inject
	private Notifier notifier;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LMSOverlay overlay;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Lms Notifier started!");
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Lms Notifier stopped!");
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOADING)
		{
			return;
		}

		crates.clear();

		if (inLobby && config.notifiesGameStart())
		{
			notifier.notify("Last Man Standing has started!");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		inLobby = client.getLocalPlayer().getWorldLocation().distanceTo(lmsCompetitiveLobby) == 0
			|| client.getLocalPlayer().getWorldLocation().distanceTo(lmsCasualLobby) == 0
			|| client.getLocalPlayer().getWorldLocation().distanceTo(lmsHighStakesLobby) == 0;
		tryUpdateSafeZoneArrow();
	}

	private void tryUpdateSafeZoneArrow()
	{
		if (!inGame)
		{
			originalHintPoint = null;
			return;
		}

		if (!config.pointToSafeZone())
		{
			return;
		}

		if (!client.hasHintArrow() || !client.getHintArrowType().equals(HintArrowType.WORLD_POSITION))
		{
			originalHintPoint = null;
			return;
		}

		if (originalHintPoint == null)
		{
			originalHintPoint = client.getHintArrowPoint();
		}

		int arrowSceneX = originalHintPoint.getX() * 4 - client.getBaseX() * 4 + 2 - client.getLocalPlayer().getLocalLocation().getX() / 32;
		int arrowSceneY = originalHintPoint.getY() * 4 - client.getBaseY() * 4 + 2 - client.getLocalPlayer().getLocalLocation().getY() / 32;
		int distance = arrowSceneX * arrowSceneX + arrowSceneY * arrowSceneY;

		if (distance >= 90_000) // hint arrow won't show in minimap
		{
			// make a closer new point for the arrow that is in the same direction
			double theta = Math.atan2(arrowSceneY, arrowSceneX);
			int newX = (int) (74 * Math.cos(theta));
			int newY = (int) (74 * Math.sin(theta));
			WorldPoint newArrow = new WorldPoint(client.getLocalPlayer().getWorldLocation().getX() + newX, client.getLocalPlayer().getWorldLocation().getY() + newY, 0);
			client.setHintArrow(newArrow);
		}
		else if (!client.getHintArrowPoint().equals(originalHintPoint))
		{
			restoreOriginalHint();
		}
	}

	private void restoreOriginalHint()
	{
		if (originalHintPoint != null && client.hasHintArrow())
		{
			client.clearHintArrow();
			client.setHintArrow(originalHintPoint);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed ev)
	{
		if (ev.getGroupId() == WidgetInfo.LMS_KDA.getGroupId())
		{
			inGame = false;
			originalHintPoint = null;
			client.clearHintArrow();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() == WidgetInfo.LMS_KDA.getGroupId())
		{
			inGame = true;
			originalHintPoint = null;
		}
	}

	@Provides
	LMSConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LMSConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP_KEY))
		{
			return;
		}
		if (event.getKey().equals(LMSConfig.POINT_SAFE_KEY) && Boolean.FALSE.toString().equals(event.getNewValue()))
		{
			restoreOriginalHint();
		}
	}

	boolean highlightChests()
	{
		switch (config.highlightChests())
		{
			case NEVER:
				return false;
			case HAS_KEY:
				return client.getItemContainer(InventoryID.INVENTORY).contains(ItemID.BLOODY_KEY) || client.getItemContainer(InventoryID.INVENTORY).contains(ItemID.BLOODIER_KEY);
			case ALWAYS:
			default:
				return true;
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		crates.remove(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (lms_chest_ids.contains(event.getGameObject().getId()))
		{
			crates.add(event.getGameObject());
		}
	}
}
