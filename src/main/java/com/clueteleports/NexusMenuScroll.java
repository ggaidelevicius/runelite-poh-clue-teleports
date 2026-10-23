package com.clueteleports;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * One-time UI scrolling to an existing, available portal-nexus destination.
 *
 * Native telenexus_teleport_init (2672) calls script2674, which lays TEXT1 and
 * EXTRAS rows into one cumulative SCROLLING1 scroll space. Its scrollbar is
 * rebuilt from SCROLLING1.getScrollY(); unlike fairy rings, no separate saved
 * varc scroll position is used. scrollbar_vertical_setdragger (740) supplies
 * the existing native thumb/cap positions mirrored below.
 * See runelite/cs2-scripts at 1d418ffb6bce96c2d0712eb2652712d28e01b115,
 * scripts/[proc,script2674].cs2 and
 * scripts/[proc,scrollbar_vertical_setdragger].cs2.
 *
 * This class changes only local widget layout, without executing scripts,
 * changing game variables, or invoking any destination's action.
 */
public final class NexusMenuScroll
{
	private static final int[] ROW_CONTAINERS = {
		InterfaceID.TelenexusTeleport.TEXT1, InterfaceID.TelenexusTeleport.EXTRAS
	};
	private Widget openScroll;
	private String destination;
	private boolean positioned;

	/** Pass null when there is no applicable own-house nexus recommendation. */
	public void update(Client client, String destinationId)
	{
		if (!client.isClientThread())
		{
			throw new IllegalStateException("Nexus menu scrolling requires the client thread");
		}
		TeleportDestination selected = destinationId == null ? null
			: TeleportCatalog.byId(destinationId)
				.filter(value -> value.getSource() == TeleportSource.PORTAL_NEXUS).orElse(null);
		if (selected == null)
		{
			reset();
			return;
		}
		Widget scroll = client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1);
		Rectangle viewport = bounds(scroll);
		if (viewport == null || bounds(client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE)) == null)
		{
			reset();
			return;
		}
		if (scroll != openScroll || !selected.getId().equals(destination))
		{
			openScroll = scroll;
			destination = selected.getId();
			positioned = false;
		}
		if (positioned)
		{
			return;
		}
		Widget entry = findEntry(client, selected);
		Rectangle row = bounds(entry);
		if (row == null || row.x >= viewport.x + viewport.width || row.x + row.width <= viewport.x)
		{
			return;
		}
		int current = scroll.getScrollY();
		if (current < 0)
		{
			return;
		}
		if (row.y >= viewport.y && row.y + row.height <= viewport.y + viewport.height)
		{
			positioned = true;
			return;
		}
		int height = scroll.getScrollHeight();
		int top = row.y - viewport.y + current;
		if (top < 0 || height <= 0 || top + row.height > height)
		{
			// A missing row or partially hydrated extent may become ready later.
			return;
		}
		int desired = Math.max(0, Math.min(Math.max(0, height - viewport.height),
			top - Math.max(0, (viewport.height - row.height) / 2)));
		if (desired != current)
		{
			scroll.setScrollY(desired);
			scroll.revalidateScroll();
			syncNativeThumb(client.getWidget(InterfaceID.TelenexusTeleport.SCROLLBAR1), scroll);
		}
		positioned = true;
	}

	public void reset()
	{
		openScroll = null;
		destination = null;
		positioned = false;
	}

	private static Widget findEntry(Client client, TeleportDestination selected)
	{
		for (int component : ROW_CONTAINERS)
		{
			Widget container = client.getWidget(component);
			if (container == null || container.isHidden())
			{
				continue;
			}
			Widget[] rows = container.getDynamicChildren();
			if (rows != null)
			{
				for (Widget row : rows)
				{
					if (row != null && bounds(row) != null
						&& HouseLearningOverlay.matchesAvailableRow(selected, row))
					{
						return row;
					}
				}
			}
		}
		return null;
	}

	private static void syncNativeThumb(Widget bar, Widget scroll)
	{
		Rectangle track = bounds(bar);
		Rectangle viewport = bounds(scroll);
		if (track == null || viewport == null || track.height <= 32)
		{
			return;
		}
		Widget[] children = bar.getDynamicChildren();
		if (children == null || children.length != 6)
		{
			return;
		}
		// The native six-graphic bar has track, dragger, two caps, and two arrows.
		// Do not reposition a partially built or replaced/custom scrollbar.
		for (int index = 0; index < children.length; index++)
		{
			Widget child = children[index];
			if (child == null || child.getType() != WidgetType.GRAPHIC || bounds(child) == null
				|| child.getParentId() != InterfaceID.TelenexusTeleport.SCROLLBAR1 || child.getIndex() != index)
			{
				return;
			}
		}
		int thumbHeight = children[1].getBounds().height;
		int travel = track.height - 32 - thumbHeight;
		if (thumbHeight < 10 || travel < 0)
		{
			return;
		}
		int maximum = Math.max(1, scroll.getScrollHeight() - viewport.height);
		int actual = Math.max(0, Math.min(scroll.getScrollY(), maximum));
		int top = 16 + (int) ((long) travel * actual / maximum);
		children[1].setOriginalY(top);
		children[2].setOriginalY(top);
		children[3].setOriginalY(top + thumbHeight - 5);
		children[1].revalidate();
		children[2].revalidate();
		children[3].revalidate();
	}

	private static Rectangle bounds(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.width > 0 && bounds.height > 0 ? bounds : null;
	}
}
