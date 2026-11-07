package com.clueteleports;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the current route direction on the native minimap rim. */
final class ClueMinimapOverlay extends Overlay
{
	private static final BasicStroke OUTLINE = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final int RIM_MARGIN = 2;
	// A client game cycle is 20 ms: show for 0.8 seconds, then pause for 0.4 seconds.
	private static final int BLINK_CYCLES = 60;
	private static final int VISIBLE_CYCLES = 40;
	private final Client client;
	private final Supplier<WorldPoint> clueTarget;
	private Shape fixedMask;
	private Shape resizableMask;

	ClueMinimapOverlay(Client client, Supplier<WorldPoint> clueTarget)
	{
		this.client = Objects.requireNonNull(client, "client");
		this.clueTarget = Objects.requireNonNull(clueTarget, "clueTarget");
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldPoint target = clueTarget.get();
		if (target == null || client.getGameState() != GameState.LOGGED_IN
			|| client.getVarbitValue(VarbitID.MINIMAP_TOGGLE) != 0
			|| client.getVarbitValue(VarbitID.MINIMAP_STATE) != 0
			|| Math.floorMod(client.getGameCycle(), BLINK_CYCLES) >= VISIBLE_CYCLES
			|| isVisible(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)))
		{
			return null;
		}

		boolean resized = client.isResized();
		Widget minimap = client.getWidget(minimapWidgetId(resized));
		if (!isVisible(minimap))
		{
			return null;
		}
		Rectangle bounds = minimap.getBounds();
		if (bounds.isEmpty())
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		WorldView view = player == null ? null : player.getWorldView();
		if (view == null || view.isInstance() || view.getId() != WorldView.TOPLEVEL || view.getPlane() != target.getPlane())
		{
			return null;
		}
		// World locations are server tiles. Local locations interpolate as the player moves.
		WorldPoint playerLocation = player.getWorldLocation();
		if (playerLocation == null || playerLocation.getPlane() != target.getPlane() || playerLocation.equals(target))
		{
			return null;
		}
		Shape mask = minimapMask(resized);
		if (mask == null)
		{
			return null;
		}
		double bearing = Math.atan2(target.getY() - playerLocation.getY(), target.getX() - playerLocation.getX());
		// As in Perspective.localToMinimap, rotate by minimap yaw and reverse world Y for canvas Y.
		double angle = (client.getCameraYawTarget() & 0x3fff) * Perspective.UNIT14 - bearing;
		Shape arrow = rimArrow(mask, bounds.width, bounds.height, angle);
		if (arrow == null)
		{
			return null;
		}

		Graphics2D copy = (Graphics2D) graphics.create();
		try
		{
			copy.clip(bounds);
			copy.translate(bounds.x, bounds.y);
			copy.clip(mask);
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			copy.setStroke(OUTLINE);
			copy.setColor(Color.BLACK);
			copy.draw(arrow);
			copy.setColor(Color.YELLOW);
			copy.fill(arrow);
		}
		finally
		{
			copy.dispose();
		}
		return null;
	}

	private int minimapWidgetId(boolean resized)
	{
		if (!resized) return InterfaceID.Toplevel.MINIMAP;
		return client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
			? InterfaceID.ToplevelPreEoc.MINIMAP : InterfaceID.ToplevelOsrsStretch.MINIMAP;
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private Shape minimapMask(boolean resized)
	{
		if (resized && resizableMask == null)
		{
			resizableMask = loadMask(SpriteID.RESIZE_MAP_MASK);
		}
		else if (!resized && fixedMask == null)
		{
			fixedMask = loadMask(SpriteID.FIXED_MAP_MASK);
		}
		return resized ? resizableMask : fixedMask;
	}

	private Shape loadMask(int spriteId)
	{
		SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
		if (sprites == null || sprites.length == 0 || sprites[0] == null)
		{
			return null;
		}
		SpritePixels sprite = sprites[0];
		int width = sprite.getWidth();
		int height = sprite.getHeight();
		int[] pixels = sprite.getPixels();
		if (width <= 0 || height <= 0 || pixels == null || (long) width * height > pixels.length)
		{
			return null;
		}
		Path2D mask = new Path2D.Double();
		// Transparent spans are the native map aperture, including the fixed layout's cutouts.
		for (int y = 0; y < height; y++)
		{
			int start = -1;
			for (int x = 0; x <= width; x++)
			{
				boolean visible = x < width && pixels[y * width + x] == 0;
				if (visible && start < 0)
				{
					start = x;
				}
				else if (!visible && start >= 0)
				{
					mask.append(new Rectangle(start, y, x - start, 1), false);
					start = -1;
				}
			}
		}
		return new Area(mask);
	}

	private static Shape rimArrow(Shape mask, int width, int height, double angle)
	{
		double ux = Math.cos(angle);
		double uy = Math.sin(angle);
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(0, 0);
		arrow.lineTo(-10 * ux - 4 * uy, -10 * uy + 4 * ux);
		arrow.lineTo(-7 * ux, -7 * uy);
		arrow.lineTo(-10 * ux + 4 * uy, -10 * uy - 4 * ux);
		arrow.closePath();
		Rectangle2D footprint = arrow.getBounds2D();
		double footprintWidth = footprint.getWidth() + 2 * RIM_MARGIN;
		double footprintHeight = footprint.getHeight() + 2 * RIM_MARGIN;

		double centerX = width / 2.0;
		double centerY = height / 2.0;
		double rim = Math.min(centerX / Math.abs(ux), centerY / Math.abs(uy));
		// Walk inward from the widget edge until the entire arrow and outline fit the aperture.
		for (int radius = (int) rim; radius >= Math.min(width, height) / 4; radius--)
		{
			double x = centerX + radius * ux;
			double y = centerY + radius * uy;
			double left = x + footprint.getX() - RIM_MARGIN;
			double top = y + footprint.getY() - RIM_MARGIN;
			if (left >= 0 && top >= 0 && left + footprintWidth <= width && top + footprintHeight <= height
				&& mask.contains(left, top, footprintWidth, footprintHeight))
			{
				return AffineTransform.getTranslateInstance(x, y).createTransformedShape(arrow);
			}
		}
		return null;
	}
}
