package com.clueteleports.routing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static com.clueteleports.routing.WalkingCollisionMapTest.pack;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoutingDataTest
{
	private static final String HEADER = "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\tItems\tQuests\tVarbits\tVarPlayers\tDuration\n";

	@Test
	public void loadsPinnedBundledCollisionAndOnlyPhysicalTables() throws Exception
	{
		RoutingData data = RoutingData.load();
		assertTrue(data.getTransports().size() > 5000);
		assertTrue(data.getCollisionMap().hasRegion(pack(3213, 3424, 0)));
		assertTrue(data.getCollisionMap().isWalkable(pack(3213, 3424, 0)));
		assertTrue(data.getTransports().stream().anyMatch(edge -> edge.getAction().equals("Open Door 9398")));
		assertTrue(data.getTransports().stream().allMatch(edge -> edge.getFrom() != edge.getTo() && edge.getDurationTicks() >= 1));
	}

	@Test
	public void headerMappingPreservesDirectionsAndRequirements() throws Exception
	{
		String table = "Destination\tOrigin\tSkills\tDuration\tmenuOption menuTarget objectID\n"
			+ "5 4 0\t4 4 0\t70 Agility\t7\tClimb gate\n";
		RoutingData data = data(table);
		assertEquals(1, data.getTransports().size());
		RouteTransport edge = data.getTransports().get(0);
		assertEquals(pack(4, 4, 0), edge.getFrom());
		assertEquals(pack(5, 4, 0), edge.getTo());
		assertEquals(7, edge.getDurationTicks());
		assertEquals("Climb gate", edge.getAction());
		assertFalse(data.getCollisionMap().canWalk(edge.getFrom(), edge.getTo()));
		assertFalse(data.getCollisionMap().canWalk(edge.getTo(), edge.getFrom()));
	}

	@Test
	public void unknownRequirementsRemovePassageWithoutOpeningItsDoor() throws Exception
	{
		RoutingData data = data(HEADER + "4 4 0\t5 4 0\tOpen locked door\t70 UnknownSkill\t\t\t\t\t1\n");
		assertTrue(data.getTransports().isEmpty());
		assertFalse(data.getCollisionMap().canWalk(pack(4, 4, 0), pack(5, 4, 0)));
		assertFalse(data.getCollisionMap().canWalk(pack(4, 4, 0), pack(5, 5, 0)));
		assertTrue(data.getCollisionMap().canWalk(pack(4, 4, 0), pack(4, 5, 0)));
	}

	@Test
	public void rejectsGlobalOrMalformedRowsAndDoesNotInventReverseEdges() throws Exception
	{
		RoutingData data = data(HEADER
			+ "\t5 4 0\tGlobal teleport\t\t\t\t\t\t1\n"
			+ "4 4 0\t\tMissing arrival\t\t\t\t\t\t1\n"
			+ "20000 4 0\t5 4 0\tOutside world\t\t\t\t\t\t1\n"
			+ "4 4 0\t7 4 0\tEnter stairs\t\t\t\t\t\t3\n");
		assertEquals(1, data.getTransports().size());
		assertEquals(pack(7, 4, 0), data.getTransports().get(0).getTo());
	}

	@Test
	public void blankAndZeroDurationsHaveAPositivePassageCost() throws Exception
	{
		RoutingData data = data(HEADER
			+ "4 4 0\t5 4 0\tOpen door\t\t\t\t\t\t\n"
			+ "5 4 0\t4 4 0\tOpen door\t\t\t\t\t\t0\n");
		assertEquals(2, data.getTransports().size());
		assertEquals(1, data.getTransports().get(0).getDurationTicks());
		assertEquals(1, data.getTransports().get(1).getDurationTicks());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void transportCollectionIsImmutable() throws Exception
	{
		data(HEADER).getTransports().clear();
	}

	@Test(expected = IOException.class)
	public void missingCoordinateHeaderFailsTheLoad() throws Exception
	{
		data("An unknown table\n");
	}

	private static RoutingData data(String table) throws IOException
	{
		return RoutingData.load(WalkingCollisionMapTest.zip("0_0", WalkingCollisionMapTest.openPlane()),
			stream(table), stream(HEADER));
	}

	private static ByteArrayInputStream stream(String text)
	{
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}
}
