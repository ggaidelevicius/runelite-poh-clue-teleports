package com.clueteleports;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("clueteleports")
public interface ClueTeleportsConfig extends Config
{
	@ConfigItem(
		keyName = "overlayMode",
		name = "Overlay mode",
		description = "Setup always uses Full.",
		position = 0
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.FULL;
	}

	@ConfigItem(
		keyName = "chatRecommendations",
		name = "Chat messages",
		description = "Announce new teleport recommendations.",
		position = 1
	)
	default boolean chatRecommendations()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minimapArrow",
		name = "Minimap arrow",
		description = "Point toward the clue after teleporting.",
		position = 2
	)
	default boolean minimapArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightHouseObject",
		name = "Highlight in house",
		description = "Outline the recommended teleport in your house.",
		position = 3
	)
	default boolean highlightHouseObject()
	{
		return true;
	}

	@ConfigItem(
		keyName = "houseObjectColor",
		name = "Highlight colour",
		description = "Colour of the outline around the recommended teleport.",
		position = 4
	)
	default Color houseObjectColor()
	{
		return HouseLearningOverlay.RECOMMENDED;
	}

	enum OverlayMode
	{
		FULL("Full"),
		COMPACT("Compact"),
		HIDDEN("Hidden");

		private final String label;

		OverlayMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
