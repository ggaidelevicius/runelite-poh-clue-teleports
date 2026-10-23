package com.clueteleports;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum TeleportSource
{
	FAIRY_RING("Fairy ring", "fairyRingCache"),
	SPIRIT_TREE("Spirit tree", "spiritTreeCache"),
	PORTAL_NEXUS("Portal nexus", "portalNexusCache"),
	JEWELLERY_BOX("Jewellery box", "jewelleryBoxCache"),
	WILDERNESS_OBELISK("Wilderness obelisk", "wildernessObeliskCache"),
	PORTAL_CHAMBER("Portal chamber", "portalChamberCache"),
	MOUNTED_GLORY("Mounted glory", "mountedGloryCache"),
	MOUNTED_DIGSITE_PENDANT("Mounted digsite pendant", "mountedDigsiteCache"),
	MOUNTED_XERICS_TALISMAN("Mounted Xeric's talisman", "mountedXericCache"),
	MOUNTED_MYTHICAL_CAPE("Mounted mythical cape", "mountedMythicalCapeCache"),
	CAPE_HANGER("Cape hanger", "capeHangerCache"),
	HOUSE_EXIT("House exit portal", "houseExitCache");

	private static final Set<TeleportSource> MENU_SOURCES = Collections.unmodifiableSet(
		EnumSet.of(FAIRY_RING, SPIRIT_TREE, PORTAL_NEXUS, JEWELLERY_BOX, MOUNTED_DIGSITE_PENDANT, MOUNTED_XERICS_TALISMAN));
	private final String displayName;
	private final String cacheKey;

	TeleportSource(String displayName, String cacheKey)
	{
		this.displayName = displayName;
		this.cacheKey = cacheKey;
	}

	/** Menus the player needs to open; other POH fixtures are learned from the scene. */
	public static Set<TeleportSource> menuSources()
	{
		return MENU_SOURCES;
	}

	public String getCacheKey()
	{
		return cacheKey;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
