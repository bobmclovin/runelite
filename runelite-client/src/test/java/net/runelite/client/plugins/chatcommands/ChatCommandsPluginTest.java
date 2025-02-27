/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.chatcommands;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import static net.runelite.api.ChatMessageType.FRIENDSCHATNOTIFICATION;
import static net.runelite.api.ChatMessageType.GAMEMESSAGE;
import static net.runelite.api.ChatMessageType.TRADE;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import static net.runelite.api.widgets.WidgetID.ADVENTURE_LOG_ID;
import static net.runelite.api.widgets.WidgetID.GENERIC_SCROLL_GROUP_ID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.http.api.RuneLiteAPI;
import net.runelite.http.api.chat.ChatClient;
import net.runelite.http.api.hiscore.HiscoreClient;
import net.runelite.http.api.hiscore.HiscoreSkill;
import net.runelite.http.api.hiscore.SingleHiscoreSkillResult;
import net.runelite.http.api.hiscore.Skill;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChatCommandsPluginTest
{
	private static final String PLAYER_NAME = "Adam";

	@Mock
	@Bind
	Client client;

	@Mock
	@Bind
	ConfigManager configManager;

	@Mock
	@Bind
	ScheduledExecutorService scheduledExecutorService;

	@Mock
	@Bind
	ChatColorConfig chatColorConfig;

	@Mock
	@Bind
	ChatCommandManager chatCommandManager;

	@Mock
	@Bind
	HiscoreClient hiscoreClient;

	@Mock
	@Bind
	ChatMessageManager chatMessageManager;

	@Mock
	@Bind
	ChatClient chatClient;

	@Mock
	@Bind
	RuneLiteConfig runeLiteConfig;

	@Mock
	@Bind
	ChatCommandsConfig chatCommandsConfig;

	@Inject
	ChatCommandsPlugin chatCommandsPlugin;

	final Gson gson = RuneLiteAPI.GSON;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

		Player player = mock(Player.class);
		when(player.getName()).thenReturn(PLAYER_NAME);
		when(client.getLocalPlayer()).thenReturn(player);
	}

	@Test
	public void testStartupShutdown()
	{
		chatCommandsPlugin.startUp();
		chatCommandsPlugin.shutDown();

		ArgumentCaptor<String> registerCaptor = ArgumentCaptor.forClass(String.class);
		verify(chatCommandManager, atLeastOnce()).registerCommand(registerCaptor.capture(), any());
		verify(chatCommandManager, atLeastOnce()).registerCommandAsync(registerCaptor.capture(), any());
		verify(chatCommandManager, atLeastOnce()).registerCommandAsync(registerCaptor.capture(), any(), any());

		ArgumentCaptor<String> unregisterCaptor = ArgumentCaptor.forClass(String.class);
		verify(chatCommandManager, atLeastOnce()).unregisterCommand(unregisterCaptor.capture());

		assertEquals(Sets.newHashSet(registerCaptor.getAllValues()), Sets.newHashSet(unregisterCaptor.getAllValues()));
	}

	@Test
	public void testCorporealBeastKill()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your Corporeal Beast kill count is: <col=ff0000>4</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "corporeal beast", 4);
	}

	@Test
	public void testTheatreOfBlood()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "",
			"Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>2:42.0</col><br>Theatre of Blood wave completion time: <col=ff0000>17:00.20</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Theatre of Blood count is: <col=ff0000>73</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "theatre of blood", 73);
		verify(configManager).setRSProfileConfiguration("personalbest", "theatre of blood", 17 * 60 + .2);
	}

	@Test
	public void testTheatreOfBloodNoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "",
			"Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>2:42</col><br>Theatre of Blood wave completion time: <col=ff0000>17:00</col>. Personal best: 13:52.80", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Theatre of Blood count is: <col=ff0000>73</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "theatre of blood", 73);
		verify(configManager).setRSProfileConfiguration("personalbest", "theatre of blood", 13 * 60 + 52.8);
	}

	@Test
	public void testTheatreOfBloodStoryMode()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "",
			"Wave 'The Final Challenge' (Story Mode) complete!<br>Duration: <col=ff0000>2:42</col><br>Theatre of Blood wave completion time: <col=ff0000>17:00</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Theatre of Blood: Story Mode count is: <col=ff0000>73</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "theatre of blood story mode", 73);
		verify(configManager).setRSProfileConfiguration("personalbest", "theatre of blood story mode", 17 * 60.);
	}

	@Test
	public void testWintertodt()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your subdued Wintertodt count is: <col=ff0000>4</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "wintertodt", 4);
	}

	@Test
	public void testKreearra()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your Kree'arra kill count is: <col=ff0000>4</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "kree'arra", 4);
	}

	@Test
	public void testBarrows()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your Barrows chest count is: <col=ff0000>277</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "barrows chests", 277);
	}

	@Test
	public void testHerbiboar()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, GAMEMESSAGE, "", "Your herbiboar harvest count is: <col=ff0000>4091</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "herbiboar", 4091);
	}

	@Test
	public void testGauntlet()
	{
		ChatMessage gauntletMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Gauntlet completion count is: <col=ff0000>123</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(gauntletMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "gauntlet", 123);
	}

	@Test
	public void testCorruptedGauntlet()
	{
		ChatMessage corruptedGauntletMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Corrupted Gauntlet completion count is: <col=ff0000>4729</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(corruptedGauntletMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "corrupted gauntlet", 4729);
	}

	@Test
	public void testPersonalBest()
	{
		final String FIGHT_DURATION = "Fight duration: <col=ff0000>2:06</col>. Personal best: 1:19.";
		final String FIGHT_DURATION_PRECISE = "Fight duration: <col=ff0000>2:06.40</col>. Personal best: 1:19.20.";

		// This sets lastBoss
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Kree'arra kill count is: <col=ff0000>4</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", FIGHT_DURATION, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "kree'arra", 79.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", FIGHT_DURATION_PRECISE, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "kree'arra", 79.2);
	}

	@Test
	public void testPersonalBestNoTrailingPeriod()
	{
		final String FIGHT_DURATION = "Fight duration: <col=ff0000>0:59</col>. Personal best: 0:55";
		final String FIGHT_DURATION_PRECISE = "Fight duration: <col=ff0000>0:59.20</col>. Personal best: 0:55.40";

		// This sets lastBoss
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Zulrah kill count is: <col=ff0000>4</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", FIGHT_DURATION, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "zulrah", 55.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", FIGHT_DURATION_PRECISE, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "zulrah", 55.4);
	}

	@Test
	public void testNewPersonalBest()
	{
		final String NEW_PB = "Fight duration: <col=ff0000>3:01</col> (new personal best).";
		final String NEW_PB_PRECISE = "Fight duration: <col=ff0000>3:01.40</col> (new personal best).";

		// This sets lastBoss
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Kree'arra kill count is: <col=ff0000>4</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", NEW_PB, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "kree'arra", 181.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", NEW_PB_PRECISE, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "kree'arra", 181.4);
	}

	@Test
	public void testDuelArenaWin()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, TRADE, "", "You won! You have now won 27 duels.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "duel arena wins", 27);
		verify(configManager).setRSProfileConfiguration("killcount", "duel arena win streak", 1);
	}

	@Test
	public void testDuelArenaWin2()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, TRADE, "", "You were defeated! You have won 22 duels.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "duel arena wins", 22);
	}

	@Test
	public void testDuelArenaLose()
	{
		ChatMessage chatMessageEvent = new ChatMessage(null, TRADE, "", "You have now lost 999 duels.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessageEvent);

		verify(configManager).setRSProfileConfiguration("killcount", "duel arena losses", 999);
	}

	@Test
	public void testAgilityLap()
	{
		final String NEW_PB = "Lap duration: <col=ff0000>1:01</col> (new personal best).";
		final String NEW_PB_PRECISE = "Lap duration: <col=ff0000>1:01.20</col> (new personal best).";

		// This sets lastBoss
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Prifddinas Agility Course lap count is: <col=ff0000>2</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", NEW_PB, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "prifddinas agility course", 61.0);
		verify(configManager).setRSProfileConfiguration("killcount", "prifddinas agility course", 2);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", NEW_PB_PRECISE, null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "prifddinas agility course", 61.2);
	}

	@Test
	public void testZukNewPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your TzKal-Zuk kill count is: <col=ff0000>2</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Duration: <col=ff0000>104:31</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "tzkal-zuk", 104 * 60 + 31.0);
		verify(configManager).setRSProfileConfiguration("killcount", "tzkal-zuk", 2);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Duration: <col=ff0000>104:31.20</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);
		verify(configManager).setRSProfileConfiguration("personalbest", "tzkal-zuk", 104 * 60 + 31.2);
	}

	@Test
	public void testZukKill()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your TzKal-Zuk kill count is: <col=ff0000>3</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Duration: <col=ff0000>172:18</col>. Personal best: 134:52", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "tzkal-zuk", 134 * 60 + 52.0);
		verify(configManager).setRSProfileConfiguration("killcount", "tzkal-zuk", 3);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Duration: <col=ff0000>172:18.40</col>. Personal best: 134:52.20", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);
		verify(configManager).setRSProfileConfiguration("personalbest", "tzkal-zuk", 134 * 60 + 52.2);
	}

	@Test
	public void testGgNewPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Fight duration: <col=ff0000>1:36</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Grotesque Guardians kill count is: <col=ff0000>179</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "grotesque guardians", 96.0);
		verify(configManager).setRSProfileConfiguration("killcount", "grotesque guardians", 179);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Fight duration: <col=ff0000>1:36.40</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Grotesque Guardians kill count is: <col=ff0000>179</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "grotesque guardians", 96.4);
	}

	@Test
	public void testGgKill()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Fight duration: <col=ff0000>2:41</col>. Personal best: 2:14", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Grotesque Guardians kill count is: <col=ff0000>32</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "grotesque guardians", 2 * 60 + 14.0);
		verify(configManager).setRSProfileConfiguration("killcount", "grotesque guardians", 32);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Fight duration: <col=ff0000>2:41.40</col>. Personal best: 2:14.20", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Grotesque Guardians kill count is: <col=ff0000>32</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "grotesque guardians", 2 * 60 + 14.2);
	}

	@Test
	public void testGauntletPersonalBest()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>10:24</col>. Personal best: 7:59.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Gauntlet completion count is: <col=ff0000>124</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "gauntlet", 124);
		verify(configManager).setRSProfileConfiguration("personalbest", "gauntlet", 7 * 60 + 59.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>10:24.20</col>. Personal best: 7:52.40.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Gauntlet completion count is: <col=ff0000>124</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "gauntlet", 7 * 60 + 52.4);
	}

	@Test
	public void testGauntletNewPersonalBest()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>10:24</col> (new personal best).", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Gauntlet completion count is: <col=ff0000>124</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "gauntlet", 10 * 60 + 24.0);
		verify(configManager).setRSProfileConfiguration("killcount", "gauntlet", 124);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>10:24.40</col> (new personal best).", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Gauntlet completion count is: <col=ff0000>124</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "gauntlet", 10 * 60 + 24.4);
	}

	@Test
	public void testCoXKill()
	{
		ChatMessage chatMessage = new ChatMessage(null, FRIENDSCHATNOTIFICATION, "", "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>24+ players</col> Duration:</col> <col=ff0000>37:04</col> (new personal best)</col>>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Chambers of Xeric count is: <col=ff0000>51</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "chambers of xeric", 51);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 37 * 60 + 4.0);

		// Precise times
		chatMessage = new ChatMessage(null, FRIENDSCHATNOTIFICATION, "", "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>24+ players</col> Duration:</col> <col=ff0000>37:04.20</col> (new personal best)</col>>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Chambers of Xeric count is: <col=ff0000>51</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 37 * 60 + 4.2);
	}

	@Test
	public void testCoXKillNoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, FRIENDSCHATNOTIFICATION, "", "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>11-15 players</col> Duration:</col> <col=ff0000>23:25</col> Personal best: </col><col=ff0000>20:19</col>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Chambers of Xeric count is: <col=ff0000>52</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "chambers of xeric", 52);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 20 * 60 + 19.0);

		// Precise times
		chatMessage = new ChatMessage(null, FRIENDSCHATNOTIFICATION, "", "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>11-15 players</col> Duration:</col> <col=ff0000>23:25.40</col> Personal best: </col><col=ff0000>20:19.20</col>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your completed Chambers of Xeric count is: <col=ff0000>52</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 20 * 60 + 19.2);
	}

	@Test
	public void testAdventureLogCountersPage()
	{
		Widget advLogWidget = mock(Widget.class);
		Widget advLogExploitsTextWidget = mock(Widget.class);
		when(advLogWidget.getChild(ChatCommandsPlugin.ADV_LOG_EXPLOITS_TEXT_INDEX)).thenReturn(advLogExploitsTextWidget);
		when(advLogExploitsTextWidget.getText()).thenReturn("The Exploits of " + PLAYER_NAME);
		when(client.getWidget(WidgetInfo.ADVENTURE_LOG)).thenReturn(advLogWidget);
		when(configManager.getRSProfileConfiguration(anyString(), anyString(), any(Class.class))).thenReturn(2224.0);

		WidgetLoaded advLogEvent = new WidgetLoaded();
		advLogEvent.setGroupId(ADVENTURE_LOG_ID);
		chatCommandsPlugin.onWidgetLoaded(advLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		String COUNTER_TEXT = "Duel Arena<br>Wins: <col=d0c0b0>4</col><br>Losses: <col=d0c0b0>2</col>" +
			"<br><br>Last Man Standing<br>Rank: <col=d0c0b0>0</col>" +
			"<br><br>Treasure Trails<br>Beginner: <col=d0c0b0>0</col><br>Easy: <col=d0c0b0>7</col>" +
			"<br>Medium: <col=d0c0b0>28</col><br>Hard: <col=d0c0b0>108</col><br>Elite: <col=d0c0b0>15</col>" +
			"<br>Master: <col=d0c0b0>27</col><br>Rank: <col=d0c0b0>Novice</col>" +
			"<br><br>Chompy Hunting<br>Kills: <col=d0c0b0>1,000</col><br>Rank: <col=d0c0b0>Ogre Expert</col>" +
			"<br><br>Order of the White Knights<br>Rank: <col=d0c0b0>Master</col><br>with a kill score of <col=d0c0b0>1,300</col>" +
			"<br><br>TzHaar Fight Cave<br>Fastest run: <col=d0c0b0>38:10</col>" +
			"<br><br>Inferno<br>Fastest run: <col=d0c0b0>-</col><br><br>Zulrah<br>" +
			"Fastest kill: <col=d0c0b0>5:48</col><br><br>Vorkath<br>Fastest kill: <col=d0c0b0>1:21</col>" +
			"<br><br>Galvek<br>Fastest kill: <col=d0c0b0>-</col><br><br>Grotesque Guardians<br>" +
			"Fastest kill: <col=d0c0b0>2:49</col><br><br>Alchemical Hydra<br>Fastest kill: <col=d0c0b0>-</col>" +
			"<br><br>Hespori<br>Fastest kill: <col=d0c0b0>0:57</col><br><br>Nightmare<br>" +
			"Fastest kill: <col=d0c0b0>3:30</col><br><br>The Gauntlet<br>Fastest run: <col=d0c0b0>-</col>" +
			"<br><br>The Corrupted Gauntlet<br>Fastest run: <col=d0c0b0>-</col><br><br>Fragment of Seren<br>Fastest kill: <col=d0c0b0>-</col>" +
			"<br><br>Chambers of Xeric<br>Fastest run - (Team size: 24+ players): <col=d0c0b0>24:17</col>" +
			"<br><br>Chambers of Xeric - Challenge mode<br>Fastest run - (Team size: Solo): <col=d0c0b0>22:15</col>" +
			"<br><br>Barbarian Assault<br>High-level gambles: <col=d0c0b0>0</col><br><br>Fremennik spirits rested: <col=d0c0b0>0</col>";

		Widget countersPage = mock(Widget.class);
		when(countersPage.getText()).thenReturn(COUNTER_TEXT);
		when(client.getWidget(WidgetInfo.GENERIC_SCROLL_TEXT)).thenReturn(countersPage);

		WidgetLoaded countersLogEvent = new WidgetLoaded();
		countersLogEvent.setGroupId(GENERIC_SCROLL_GROUP_ID);
		chatCommandsPlugin.onWidgetLoaded(countersLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		verify(configManager).setRSProfileConfiguration("personalbest", "tztok-jad", 38 * 60 + 10.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "zulrah", 5 * 60 + 48.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "vorkath", 60 + 21.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "grotesque guardians", 2 * 60 + 49.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "hespori", 57.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "nightmare", 3 * 60 + 30.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 24 * 60 + 17.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric challenge mode", 22 * 60 + 15.0);
	}

	@Test
	public void testAdventurerLogCountersPage2()
	{
		Widget advLogWidget = mock(Widget.class);
		Widget advLogExploitsTextWidget = mock(Widget.class);
		when(advLogWidget.getChild(ChatCommandsPlugin.ADV_LOG_EXPLOITS_TEXT_INDEX)).thenReturn(advLogExploitsTextWidget);
		when(advLogExploitsTextWidget.getText()).thenReturn("The Exploits of " + PLAYER_NAME);
		when(client.getWidget(WidgetInfo.ADVENTURE_LOG)).thenReturn(advLogWidget);

		WidgetLoaded advLogEvent = new WidgetLoaded();
		advLogEvent.setGroupId(ADVENTURE_LOG_ID);
		chatCommandsPlugin.onWidgetLoaded(advLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		String COUNTER_TEXT = "Duel Arena<br>Wins: <col=d0c0b0>12</col><br>Losses: <col=d0c0b0>20</col>" +
			"<br><br>Last Man Standing<br>Rank: <col=d0c0b0>0</col>" +
			"<br><br>Treasure Trails<br>Beginner: <col=d0c0b0>1</col><br>Easy: <col=d0c0b0>4</col>" +
			"<br>Medium: <col=d0c0b0>35</col><br>Hard: <col=d0c0b0>66</col><br>Elite: <col=d0c0b0>2</col>" +
			"<br>Master: <col=d0c0b0>0</col><br>Rank: <col=d0c0b0>Novice</col>" +
			"<br><br>Chompy Hunting<br>Kills: <col=d0c0b0>300</col><br>Rank: <col=d0c0b0>Ogre Forester</col>" +
			"<br><br>Order of the White Knights<br>Rank: <col=d0c0b0>Unrated</col><br>with a kill score of <col=d0c0b0>99</col>" +
			"<br><br>TzHaar Fight Cave<br>Fastest run: <col=d0c0b0>65:12</col>" +
			"<br><br>Inferno<br>Fastest run: <col=d0c0b0>-</col><br><br>Zulrah<br>" +
			"Fastest kill: <col=d0c0b0>2:55</col><br><br>Vorkath<br>Fastest kill: <col=d0c0b0>1:37</col>" +
			"<br><br>Galvek<br>Fastest kill: <col=d0c0b0>-</col><br><br>Grotesque Guardians<br>" +
			"Fastest kill: <col=d0c0b0>-</col><br><br>Alchemical Hydra<br>Fastest kill: <col=d0c0b0>-</col>" +
			"<br><br>Hespori<br>Fastest kill: <col=d0c0b0>1:42</col><br><br>Nightmare<br>" +
			"Fastest kill: <col=d0c0b0>-</col><br><br>The Gauntlet<br>Fastest run: <col=d0c0b0>-</col>" +
			"<br><br>The Corrupted Gauntlet<br>Fastest run: <col=d0c0b0>-</col><br><br>Fragment of Seren<br>Fastest kill: <col=d0c0b0>-</col>" +
			"<br><br>Chambers of Xeric<br>Fastest run - (Team size: Solo): <col=d0c0b0>21:23</col><br>Fastest run - (Team size: 3 players): <col=d0c0b0>27:16</col>" +
			"<br><br>Chambers of Xeric - Challenge mode<br>Fastest run - (Team size: Solo): <col=d0c0b0>34:30</col><br>Fastest run - (Team size: 4 players): <col=d0c0b0>21:26</col>" +
			"<br><br>Barbarian Assault<br>High-level gambles: <col=d0c0b0>0</col><br><br>Fremennik spirits rested: <col=d0c0b0>0</col>";

		Widget countersPage = mock(Widget.class);
		when(countersPage.getText()).thenReturn(COUNTER_TEXT);
		when(client.getWidget(WidgetInfo.GENERIC_SCROLL_TEXT)).thenReturn(countersPage);

		WidgetLoaded countersLogEvent = new WidgetLoaded();
		countersLogEvent.setGroupId(GENERIC_SCROLL_GROUP_ID);
		chatCommandsPlugin.onWidgetLoaded(countersLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		verify(configManager).setRSProfileConfiguration("personalbest", "tztok-jad", 65 * 60 + 12.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "zulrah", 2 * 60 + 55.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "vorkath", 60 + 37.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "hespori", 60 + 42.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 21 * 60 + 23.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric challenge mode", 21 * 60 + 26.0);
	}

	@Test
	public void testAdventurerLogCountersPagePrecise()
	{
		Widget advLogWidget = mock(Widget.class);
		Widget advLogExploitsTextWidget = mock(Widget.class);
		when(advLogWidget.getChild(ChatCommandsPlugin.ADV_LOG_EXPLOITS_TEXT_INDEX)).thenReturn(advLogExploitsTextWidget);
		when(advLogExploitsTextWidget.getText()).thenReturn("The Exploits of " + PLAYER_NAME);
		when(client.getWidget(WidgetInfo.ADVENTURE_LOG)).thenReturn(advLogWidget);

		WidgetLoaded advLogEvent = new WidgetLoaded();
		advLogEvent.setGroupId(ADVENTURE_LOG_ID);
		chatCommandsPlugin.onWidgetLoaded(advLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		String COUNTER_TEXT = "Duel Arena<br>Wins: <col=d0c0b0>12</col><br>Losses: <col=d0c0b0>20</col>" +
			"<br><br>Last Man Standing<br>Rank: <col=d0c0b0>0</col>" +
			"<br><br>Treasure Trails<br>Beginner: <col=d0c0b0>1</col><br>Easy: <col=d0c0b0>4</col>" +
			"<br>Medium: <col=d0c0b0>35</col><br>Hard: <col=d0c0b0>66</col><br>Elite: <col=d0c0b0>2</col>" +
			"<br>Master: <col=d0c0b0>0</col><br>Rank: <col=d0c0b0>Novice</col>" +
			"<br><br>Chompy Hunting<br>Kills: <col=d0c0b0>300</col><br>Rank: <col=d0c0b0>Ogre Forester</col>" +
			"<br><br>Order of the White Knights<br>Rank: <col=d0c0b0>Unrated</col><br>with a kill score of <col=d0c0b0>99</col>" +
			"<br><br>TzHaar Fight Cave<br>Fastest run: <col=d0c0b0>65:12.00</col>" +
			"<br><br>Inferno<br>Fastest run: <col=d0c0b0>-</col><br><br>Zulrah<br>" +
			"Fastest kill: <col=d0c0b0>2:55.20</col><br><br>Vorkath<br>Fastest kill: <col=d0c0b0>1:37.20</col>" +
			"<br><br>Galvek<br>Fastest kill: <col=d0c0b0>-</col><br><br>Grotesque Guardians<br>" +
			"Fastest kill: <col=d0c0b0>-</col><br><br>Alchemical Hydra<br>Fastest kill: <col=d0c0b0>-</col>" +
			"<br><br>Hespori<br>Fastest kill: <col=d0c0b0>1:42.40</col><br><br>Nightmare<br>" +
			"Fastest kill: <col=d0c0b0>-</col><br><br>The Gauntlet<br>Fastest run: <col=d0c0b0>-</col>" +
			"<br><br>The Corrupted Gauntlet<br>Fastest run: <col=d0c0b0>-</col><br><br>Fragment of Seren<br>Fastest kill: <col=d0c0b0>-</col>" +
			"<br><br>Chambers of Xeric<br>Fastest run - (Team size: Solo): <col=d0c0b0>21:23.20</col><br>Fastest run - (Team size: 3 players): <col=d0c0b0>27:16.40</col>" +
			"<br><br>Chambers of Xeric - Challenge mode<br>Fastest run - (Team size: Solo): <col=d0c0b0>34:30.20</col><br>Fastest run - (Team size: 4 players): <col=d0c0b0>21:26.00</col>" +
			"<br><br>Barbarian Assault<br>High-level gambles: <col=d0c0b0>0</col><br><br>Fremennik spirits rested: <col=d0c0b0>0</col>";

		Widget countersPage = mock(Widget.class);
		when(countersPage.getText()).thenReturn(COUNTER_TEXT);
		when(client.getWidget(WidgetInfo.GENERIC_SCROLL_TEXT)).thenReturn(countersPage);

		WidgetLoaded countersLogEvent = new WidgetLoaded();
		countersLogEvent.setGroupId(GENERIC_SCROLL_GROUP_ID);
		chatCommandsPlugin.onWidgetLoaded(countersLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		verify(configManager).setRSProfileConfiguration("personalbest", "tztok-jad", 65 * 60 + 12.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "zulrah", 2 * 60 + 55.2);
		verify(configManager).setRSProfileConfiguration("personalbest", "vorkath", 60 + 37.2);
		verify(configManager).setRSProfileConfiguration("personalbest", "hespori", 60 + 42.40);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric", 21 * 60 + 23.20);
		verify(configManager).setRSProfileConfiguration("personalbest", "chambers of xeric challenge mode", 21 * 60 + 26.0);
	}

	@Test
	public void testNotYourAdventureLogCountersPage()
	{
		Widget advLogWidget = mock(Widget.class);
		Widget advLogExploitsTextWidget = mock(Widget.class);
		when(advLogWidget.getChild(ChatCommandsPlugin.ADV_LOG_EXPLOITS_TEXT_INDEX)).thenReturn(advLogExploitsTextWidget);
		when(advLogExploitsTextWidget.getText()).thenReturn("The Exploits of " + "not the player");
		when(client.getWidget(WidgetInfo.ADVENTURE_LOG)).thenReturn(advLogWidget);

		WidgetLoaded advLogEvent = new WidgetLoaded();
		advLogEvent.setGroupId(ADVENTURE_LOG_ID);
		chatCommandsPlugin.onWidgetLoaded(advLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		WidgetLoaded countersLogEvent = new WidgetLoaded();
		countersLogEvent.setGroupId(GENERIC_SCROLL_GROUP_ID);
		chatCommandsPlugin.onWidgetLoaded(countersLogEvent);
		chatCommandsPlugin.onGameTick(new GameTick());

		verifyNoMoreInteractions(configManager);
	}

	@Test
	public void testPlayerSkillLookup() throws IOException
	{
		when(chatCommandsConfig.lvl()).thenReturn(true);

		SingleHiscoreSkillResult skillResult = new SingleHiscoreSkillResult();
		skillResult.setPlayer(PLAYER_NAME);
		skillResult.setSkill(new Skill(10, 1000, -1));

		when(hiscoreClient.lookup(PLAYER_NAME, HiscoreSkill.ZULRAH, null)).thenReturn(skillResult);

		MessageNode messageNode = mock(MessageNode.class);

		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.PUBLICCHAT);
		chatMessage.setName(PLAYER_NAME);
		chatMessage.setMessageNode(messageNode);
		chatCommandsPlugin.playerSkillLookup(chatMessage, "!lvl zulrah");

		verify(messageNode).setRuneLiteFormatMessage("<colNORMAL>Level <colHIGHLIGHT>Zulrah: 1000<colNORMAL> Rank: <colHIGHLIGHT>10");
	}

	@Test
	public void testHsFloorNoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 1 time: <col=ff0000>1:19</col>. Personal best: 0:28", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 1", 28.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 1 time: <col=ff0000>1:19.20</col>. Personal best: 0:28.40", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 1", 28.4);
	}

	@Test
	public void testHsFloorPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 2 time: <col=ff0000>0:47</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 2", 47.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 2 time: <col=ff0000>0:47.20</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 2", 47.2);
	}

	@Test
	public void testHsOverallPb_Pb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>4:46</col> (new personal best)<br>Overall time: <col=ff0000>9:53</col> (new personal best)<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 4 * 60 + 46.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 9 * 60 + 53.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>4:46.20</col> (new personal best)<br>Overall time: <col=ff0000>9:53.40</col> (new personal best)<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 4 * 60 + 46.2);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 9 * 60 + 53.4);
	}

	@Test
	public void testHsOverallPb_NoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>3:26</col> (new personal best)<br>Overall time: <col=ff0000>9:17</col>. Personal best: 9:15<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 3 * 60 + 26.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 9 * 60 + 15.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>3:26.20</col> (new personal best)<br>Overall time: <col=ff0000>9:17.00</col>. Personal best: 9:15.40<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 3 * 60 + 26.2);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 9 * 60 + 15.4);
	}

	@Test
	public void testHsOverallNoPb_NoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>3:56</col>. Personal best: 3:05<br>Overall time: <col=ff0000>9:14</col>. Personal best: 7:49<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 3 * 60 + 5.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 7 * 60 + 49.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>3:56.40</col>. Personal best: 3:05.20<br>Overall time: <col=ff0000>9:14.20</col>. Personal best: 7:49.20<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 3 * 60 + 5.2);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 7 * 60 + 49.2);
	}

	@Test
	public void testHsOverallNoPb_Pb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>3:10</col>. Personal best: 3:04<br>Overall time: <col=ff0000>7:47</col> (new personal best)<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 3 * 60 + 4.0);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 7 * 60 + 47.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Floor 5 time: <col=ff0000>3:10.00</col>. Personal best: 3:04.40<br>Overall time: <col=ff0000>7:47.20</col> (new personal best)<br>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre floor 5", 3 * 60 + 4.4);
		verify(configManager).setRSProfileConfiguration("personalbest", "hallowed sepulchre", 7 * 60 + 47.2);
	}

	@Test
	public void testHsFloorKc()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "You have completed Floor 5 of the Hallowed Sepulchre! Total completions: <col=ff0000>1,114</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "hallowed sepulchre floor 5", 1114);
	}

	@Test
	public void testHsGhcKc()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "You have opened the Grand Hallowed Coffin <col=ff0000>1,542</col> times!", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "hallowed sepulchre", 1542);
	}

	@Test
	public void testJadNewPbWithLeagueTask()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your TzTok-Jad kill count is: <col=ff0000>2</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Congratulations, you've completed a master task: <col=7f3700>Complete the Fight Caves in 25:00</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Duration: <col=ff0000>21:58</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "tztok-jad", 21 * 60 + 58.0);
		verify(configManager).setRSProfileConfiguration("killcount", "tztok-jad", 2);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Duration: <col=ff0000>21:58.40</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "tztok-jad", 21 * 60 + 58.4);
	}

	@Test
	public void testJadChallengeNewPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your completion count for TzHaar-Ket-Rak's First Challenge is: <col=ff0000>1</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>1:46</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "TzHaar-Ket-Rak's First Challenge".toLowerCase(), 1);
		verify(configManager).setRSProfileConfiguration("personalbest", "TzHaar-Ket-Rak's First Challenge".toLowerCase(), 60 + 46.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>1:46.40</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "TzHaar-Ket-Rak's First Challenge".toLowerCase(), 60 + 46.4);
	}

	@Test
	public void testJadChallengeNoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your completion count for TzHaar-Ket-Rak's First Challenge is: <col=ff0000>3</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>1:10</col>. Personal best: <col=ff0000>0:59</col>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "TzHaar-Ket-Rak's First Challenge".toLowerCase(), 3);
		verify(configManager).setRSProfileConfiguration("personalbest", "TzHaar-Ket-Rak's First Challenge".toLowerCase(), 59.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Challenge duration: <col=ff0000>1:10.00</col>. Personal best: <col=ff0000>0:59.20</col>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "TzHaar-Ket-Rak's First Challenge".toLowerCase(), 59.2);
	}

	@Test
	public void testTemporossNewPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Subdued in <col=ef1020>6:35</col> (new personal best).", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Tempoross kill count is: <col=ff0000>60</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "tempoross", 60);
		verify(configManager).setRSProfileConfiguration("personalbest", "tempoross", 6 * 60 + 35.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Subdued in <col=ef1020>5:20.60</col> (new personal best).", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Tempoross kill count is: <col=ff0000>60</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "tempoross", 5 * 60 + 20.6);
	}

	@Test
	public void testTemporossNoPb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Subdued in <col=ef1020>7:40</col>. Personal best: 5:38.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Tempoross kill count is: <col=ff0000>55</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "tempoross", 55);
		verify(configManager).setRSProfileConfiguration("personalbest", "tempoross", 5 * 60 + 38.0);

		// Precise times
		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Subdued in <col=ef1020>6:19.80</col>. Personal best: 5:42.60.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Tempoross kill count is: <col=ff0000>55</col>.", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("personalbest", "tempoross", 5 * 60 + 42.6);
	}

	@Test
	public void testNightmarePb()
	{
		ChatMessage chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Your Nightmare kill count is: <col=ff0000>1130</col>", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		chatMessage = new ChatMessage(null, GAMEMESSAGE, "", "Team size: <col=ff0000>5 players</col> Fight duration: <col=ff0000>3:28</col> (new personal best)", null, 0);
		chatCommandsPlugin.onChatMessage(chatMessage);

		verify(configManager).setRSProfileConfiguration("killcount", "nightmare", 1130);
		verify(configManager).setRSProfileConfiguration("personalbest", "nightmare", 3 * 60 + 28.0);
	}

	@Test
	public void testPlayerPetList()
	{
		Widget logEntryHeaderWidget = mock(Widget.class);
		when(client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_HEADER)).thenReturn(logEntryHeaderWidget);

		Widget[] logEntryHeaderItemsWidget = new Widget[1];
		when(logEntryHeaderWidget.getChildren()).thenReturn(logEntryHeaderItemsWidget);

		Widget logEntryHeaderTitleWidget = mock(Widget.class);
		when(logEntryHeaderWidget.getChild(ChatCommandsPlugin.COL_LOG_ENTRY_HEADER_TITLE_INDEX))
			.thenReturn(logEntryHeaderTitleWidget);
		when(logEntryHeaderTitleWidget.getText()).thenReturn("All Pets");

		Widget logEntryItemsWidget = mock(Widget.class);
		when(client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_ITEMS)).thenReturn(logEntryItemsWidget);

		Widget[] logPetEntriesWidget = new Widget[3];
		for (int i = 0; i < 3; i++)
		{
			logPetEntriesWidget[i] = mock(Widget.class);
			when(logPetEntriesWidget[i].getOpacity()).thenReturn(175);
		}

		when(logPetEntriesWidget[1].getName()).thenReturn("<col=ff9040>Ikkle hydra</col>");
		when(logPetEntriesWidget[1].getOpacity()).thenReturn(0);

		when(logEntryItemsWidget.getChildren()).thenReturn(logPetEntriesWidget);

		ScriptPostFired scriptPostFired = new ScriptPostFired(ScriptID.COLLECTION_DRAW_LIST);
		chatCommandsPlugin.onScriptPostFired(scriptPostFired);

		chatCommandsPlugin.onGameTick(new GameTick());

		Pet[] playerPetList = new Pet[1];
		playerPetList[0] = Pet.IKKLE_HYDRA;

		verify(configManager).setRSProfileConfiguration("chatcommands", "pets", gson.toJson(playerPetList));
	}

	@Test
	public void testEmptyPlayerPetList()
	{
		Widget logEntryHeaderWidget = mock(Widget.class);
		when(client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_HEADER)).thenReturn(logEntryHeaderWidget);

		Widget[] logEntryHeaderItemsWidget = new Widget[1];
		when(logEntryHeaderWidget.getChildren()).thenReturn(logEntryHeaderItemsWidget);

		Widget logEntryHeaderTitleWidget = mock(Widget.class);
		when(logEntryHeaderWidget.getChild(ChatCommandsPlugin.COL_LOG_ENTRY_HEADER_TITLE_INDEX))
			.thenReturn(logEntryHeaderTitleWidget);
		when(logEntryHeaderTitleWidget.getText()).thenReturn("All Pets");

		Widget logEntryItemsWidget = mock(Widget.class);
		when(client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_ITEMS)).thenReturn(logEntryItemsWidget);

		Widget[] logPetEntriesWidget = new Widget[3];
		for (int i = 0; i < 3; i++)
		{
			logPetEntriesWidget[i] = mock(Widget.class);
			when(logPetEntriesWidget[i].getOpacity()).thenReturn(175);
		}

		when(logEntryItemsWidget.getChildren()).thenReturn(logPetEntriesWidget);

		ScriptPostFired scriptPostFired = new ScriptPostFired(ScriptID.COLLECTION_DRAW_LIST);
		chatCommandsPlugin.onScriptPostFired(scriptPostFired);

		chatCommandsPlugin.onGameTick(new GameTick());

		verify(configManager).setRSProfileConfiguration("chatcommands", "pets", gson.toJson(new Pet[0]));
	}

	@Test
	public void testUpdatePlayerPetList()
	{
		Widget logEntryHeaderWidget = mock(Widget.class);
		when(client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_HEADER)).thenReturn(logEntryHeaderWidget);

		Widget[] logEntryHeaderItemsWidget = new Widget[1];
		when(logEntryHeaderWidget.getChildren()).thenReturn(logEntryHeaderItemsWidget);

		Widget logEntryHeaderTitleWidget = mock(Widget.class);
		when(logEntryHeaderWidget.getChild(ChatCommandsPlugin.COL_LOG_ENTRY_HEADER_TITLE_INDEX))
			.thenReturn(logEntryHeaderTitleWidget);
		when(logEntryHeaderTitleWidget.getText()).thenReturn("All Pets");

		Widget logEntryItemsWidget = mock(Widget.class);
		when(client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_ITEMS)).thenReturn(logEntryItemsWidget);

		Widget[] logPetEntriesWidget = new Widget[3];
		for (int i = 0; i < 3; i++)
		{
			logPetEntriesWidget[i] = mock(Widget.class);
			when(logPetEntriesWidget[i].getOpacity()).thenReturn(175);
		}

		when(logPetEntriesWidget[1].getName()).thenReturn("<col=ff9040>Ikkle hydra</col>");
		when(logPetEntriesWidget[1].getOpacity()).thenReturn(0);

		when(logEntryItemsWidget.getChildren()).thenReturn(logPetEntriesWidget);

		ScriptPostFired scriptPostFired = new ScriptPostFired(ScriptID.COLLECTION_DRAW_LIST);
		chatCommandsPlugin.onScriptPostFired(scriptPostFired);

		chatCommandsPlugin.onGameTick(new GameTick());

		Pet[] playerPetList = new Pet[1];
		playerPetList[0] = Pet.IKKLE_HYDRA;

		verify(configManager).setRSProfileConfiguration("chatcommands", "pets", gson.toJson(playerPetList));

		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setMessage("New item added to your collection log: Chompy chick");
		chatMessage.setType(GAMEMESSAGE);
		when(configManager.getRSProfileConfiguration("chatcommands", "pets",
			String.class)).thenReturn(gson.toJson(playerPetList));
		chatCommandsPlugin.onChatMessage(chatMessage);

		playerPetList = new Pet[2];
		playerPetList[0] = Pet.IKKLE_HYDRA;
		playerPetList[1] = Pet.CHOMPY_CHICK;
		verify(configManager).setRSProfileConfiguration("chatcommands", "pets", gson.toJson(playerPetList));
	}

	@Test
	public void testTimeStringToSeconds()
	{
		final double DELTA = 0.0001;

		// ss
		assertEquals(55.0, ChatCommandsPlugin.timeStringToSeconds("55.00"), DELTA);
		// mm:ss
		assertEquals(6 * 60 + 55.4, ChatCommandsPlugin.timeStringToSeconds("6:55.40"), DELTA);
		// h:mm:ss
		assertEquals(2 * 3600 + 50 * 60 + 30.2, ChatCommandsPlugin.timeStringToSeconds("2:50:30.20"), DELTA);
	}
}
