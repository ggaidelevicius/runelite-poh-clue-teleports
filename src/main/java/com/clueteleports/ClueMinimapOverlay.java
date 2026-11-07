package com.clueteleports;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.Objects;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the current route waypoint on the native minimap. */
final class ClueMinimapOverlay extends Overlay
{
	private static final Color ARROW_COLOR = new Color(85, 225, 255);
	private static final BasicStroke OUTLINE = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	// A client game cycle is 20 ms. Keep the pulse visible throughout its 1.2-second period.
	private static final int PULSE_CYCLES = 60;
	private final Client client;
	private final Supplier<WorldPoint> waypoint;
	private Shape fixedMask;
	private Shape resizableMask;

	ClueMinimapOverlay(Client client, Supplier<WorldPoint> waypoint)
	{
		this.client = Objects.requireNonNull(client, "client");
		this.waypoint = Objects.requireNonNull(waypoint, "waypoint");
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldPoint target = waypoint.get();
		if (target == null || client.getGameState() != GameState.LOGGED_IN
			|| client.getVarbitValue(VarbitID.MINIMAP_TOGGLE) != 0
			|| client.getVarbitValue(VarbitID.MINIMAP_STATE) != 0
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
		LocalPoint playerLocation = player.getLocalLocation();
		LocalPoint targetLocation = LocalPoint.fromWorld(view, target);
		if (playerLocation == null || targetLocation == null)
		{
			return null;
		}
		Point from = Perspective.localToMinimap(client, playerLocation);
		Point to = Perspective.localToMinimap(client, targetLocation);
		if (from == null || to == null || from.equals(to) || !bounds.contains(to.getX(), to.getY()))
		{
			return null;
		}
		Shape mask = minimapMask(resized);
		if (mask == null || !mask.contains(to.getX() - bounds.x, to.getY() - bounds.y))
		{
			return null;
		}

		Graphics2D copy = (Graphics2D) graphics.create();
		try
		{
			copy.clip(bounds);
			copy.clip(AffineTransform.getTranslateInstance(bounds.x, bounds.y).createTransformedShape(mask));
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			double phase = Math.floorMod(client.getGameCycle(), PULSE_CYCLES) * (2 * Math.PI / PULSE_CYCLES);
			copy.setComposite(AlphaComposite.SrcOver.derive((float) (0.7 + 0.3 * Math.cos(phase))));
			Shape arrow = arrow(from, to);
			copy.setStroke(OUTLINE);
			copy.setColor(Color.BLACK);
			copy.draw(arrow);
			copy.setColor(ARROW_COLOR);
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
		return mask;
	}

	private static Shape arrow(Point from, Point to)
	{
		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double length = Math.hypot(dx, dy);
		double ux = dx / length;
		double uy = dy / length;
		int x = to.getX();
		int y = to.getY();
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(x, y);
		arrow.lineTo(x - 10 * ux - 4 * uy, y - 10 * uy + 4 * ux);
		arrow.lineTo(x - 7 * ux, y - 7 * uy);
		arrow.lineTo(x - 10 * ux + 4 * uy, y - 10 * uy - 4 * ux);
		arrow.closePath();
		return arrow;
	}
}
