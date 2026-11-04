package com.reiko.trains.gametest;

import com.mojang.authlib.GameProfile;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.guide.Handbook;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the Railway Handbook (when Patchouli is installed, as it is in the dev runs). */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class HandbookGameTests {
    private static final String LINE = "line";

    private HandbookGameTests() {
    }

    /** A player joining the server (for real, so their persisted data works as it would). */
    private static ServerPlayer join(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "rail_" + id.toString().substring(0, 8)), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
        } catch (UnsupportedOperationException e) {
            // A mod sending a packet on login that a fake connection can't take: the player's in by then.
        }
        return player;
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        MinecraftServer server = helper.getLevel().getServer();
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
            server.getPlayerList().remove(player);
        }
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void handbookGivenOnceAndCraftable(GameTestHelper helper) {
        ServerPlayer player = join(helper);
        try {
            if (!Handbook.available()) {
                // Without Patchouli (./gradlew runGameTestServer -PnoOptionalMods) there's no handbook, and no recipe for one.
                helper.assertTrue(Handbook.book().isEmpty(), "no handbook without Patchouli");
                helper.assertTrue(!Handbook.giveFirstTime(player), "so none to give");
                helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(Handbook.id()).isEmpty(), "and no recipe for one");
                helper.succeed();
                return;
            }
            ItemStack handbook = Handbook.book();
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(handbook.getItem()).toString().equals("patchouli:guide_book"),
                    "the handbook is a Patchouli book");
            // Joining gives one (unless the setting says not to); either way there's one after giveFirstTime.
            Handbook.giveFirstTime(player);
            Supplier<Integer> handbooks = () -> player.getInventory().items.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, handbook)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(handbooks.get() == 1, "one handbook on first joining, not " + handbooks.get());
            helper.assertTrue(!Handbook.giveFirstTime(player), "and only the once");
            player.getInventory().clearContent();
            helper.assertTrue(!Handbook.giveFirstTime(player), "not even after losing it");
            helper.assertTrue(handbooks.get() == 0, "so no second copy");
            // Lost it? Craft another: a book and Train Track.
            var recipe = helper.getLevel().getServer().getRecipeManager().byKey(Handbook.id()).orElse(null);
            helper.assertTrue(recipe != null, "the handbook has a recipe");
            ItemStack made = recipe.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(ItemStack.isSameItemSameComponents(made, handbook), "which makes the Railway Handbook");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    /** The pages the screens' handbook buttons open, and the book itself, are all there. */
    @GameTest(template = LINE, timeoutTicks = 20)
    public static void handbookPagesExist(GameTestHelper helper) {
        String book = "/assets/reikotrains/patchouli_books/railway_handbook/en_us/entries/";
        for (String page : Handbook.SCREEN_PAGES) {
            helper.assertTrue(Handbook.class.getResource(book + page + ".json") != null, "the handbook has a page for " + page);
        }
        helper.assertTrue(Handbook.class.getResource("/data/reikotrains/patchouli_books/railway_handbook/book.json") != null, "and its book.json");
        helper.succeed();
    }
}
