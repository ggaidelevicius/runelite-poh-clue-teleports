package com.clueteleports;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the house fixture that offers the current recommendation, so the
 * player can see what to click. Read-only: it never creates menu entries or
 * interacts. Guidance only names a source once the own house is positively
 * identified and a route was found, so a changed clue or recommendation clears
 * the outline on the next tick.
 */
public final class HouseObjectOverlay extends Overlay
{
	private final Client client;
	private final Supplier<HouseLearningOverlay.Guidance> guidance;
	private final Supplier<Color> color;
	private final Painter painter;
	private HouseLearningOverlay.Guidance cachedGuidance;
	private int cachedTick = -1;
	private int cachedPlane = -1;
	private List<TileObject> cachedTargets = Collections.emptyList();

	/** Draws one outline; production uses RuneLite's model outline renderer. */
	interface Painter
	{
		void outline(TileObject object, Color color);
	}

	HouseObjectOverlay(Client client, Supplier<HouseLearningOverlay.Guidance> guidance, Supplier<Color> color,
		Painter painter)
	{
		this.client = Objects.requireNonNull(client);
		this.guidance = Objects.requireNonNull(guidance);
		this.color = Objects.requireNonNull(color);
		this.painter = Objects.requireNonNull(painter);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Color outline = color.get();
		if (outline == null)
		{
			return null;
		}
		for (TileObject object : targets(guidance.get()))
		{
			painter.outline(object, outline);
		}
		return null;
	}

	/** Rescans at most once per game tick, floor, or guidance change. */
	List<TileObject> targets(HouseLearningOverlay.Guidance state)
	{
		if (state == null || !state.isOwnHouse() || state.getSource() == null || state.getDestinationId() == null)
		{
			cachedGuidance = null;
			cachedTargets = Collections.emptyList();
			return cachedTargets;
		}
		WorldView world = client.getTopLevelWorldView();
		int plane = world == null ? -1 : world.getPlane();
		int tick = client.getTickCount();
		if (state != cachedGuidance || tick != cachedTick || plane != cachedPlane)
		{
			cachedGuidance = state;
			cachedTick = tick;
			cachedPlane = plane;
			cachedTargets = find(world, plane, state.getSource(), state.getDestinationId());
		}
		return cachedTargets;
	}

	private List<TileObject> find(WorldView world, int plane, TeleportSource source, String destinationId)
	{
		Scene scene = world == null ? null : world.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
		{
			return Collections.emptyList();
		}
		List<TileObject> found = new ArrayList<>();
		Set<TileObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Map<Integer, Boolean> matches = new HashMap<>();
		for (Tile[] column : tiles[plane])
		{
			if (column == null)
			{
				continue;
			}
			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}
				List<TileObject> objects = new ArrayList<>();
				objects.add(tile.getWallObject());
				objects.add(tile.getDecorativeObject());
				GameObject[] games = tile.getGameObjects();
				if (games != null)
				{
					Collections.addAll(objects, games);
				}
				for (TileObject object : objects)
				{
					// Large objects span several tiles; outline each only once.
					if (object != null && visited.add(object)
						&& matches.computeIfAbsent(object.getId(), id -> offers(id, source, destinationId)))
					{
						found.add(object);
					}
				}
			}
		}
		return Collections.unmodifiableList(found);
	}

	private boolean offers(int rawId, TeleportSource source, String destinationId)
	{
		ObjectComposition definition = client.getObjectDefinition(rawId);
		if (definition == null)
		{
			return false;
		}
		ObjectComposition live = definition.getImpostorIds() == null ? definition : definition.getImpostor();
		return HouseTransportReader.offers(client, live, source, destinationId);
	}
}
