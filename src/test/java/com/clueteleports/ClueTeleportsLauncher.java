package com.clueteleports;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development client entry point, started explicitly by the user. */
public final class ClueTeleportsLauncher
{
	private ClueTeleportsLauncher()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClueTeleportsPlugin.class);
		RuneLite.main(args);
	}
}
