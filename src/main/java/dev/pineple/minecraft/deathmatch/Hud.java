package dev.pineple.minecraft.deathmatch;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.scoreboard.Sidebar;

/** Everything the match shows to players: kill sidebar, round timer, kill feed. */
final class Hud {
    private static final int SIDEBAR_LINES = 15; // vanilla limit; extra players still play and get a line when one frees up

    private final Audience everyone;
    private final Sidebar board = new Sidebar(Component.text("DEATHMATCH", NamedTextColor.GOLD));
    private final BossBar timer = BossBar.bossBar(Component.empty(), 1, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    Hud(Audience everyone) { this.everyone = everyone; }

    void add(Combatant c) {
        list(c);
        board.addViewer(c.player());
        c.player().showBossBar(timer);
        c.player().sendMessage(Component.text("Left-click: fire/throw · F: reload · Right-click: sniper scope"
                + " · 1-5 guns · 6 knife · 7-9 grenades", NamedTextColor.YELLOW));
    }

    /** Give the combatant a sidebar line if they have none and there is room. */
    void list(Combatant c) {
        if (board.getLine(c.name()) == null && board.getLines().size() < SIDEBAR_LINES)
            board.createLine(new Sidebar.ScoreboardLine(c.name(), Component.text(c.name()), c.kills()));
    }

    void remove(Combatant c) {
        board.removeViewer(c.player());
        c.player().hideBossBar(timer);
        if (board.getLine(c.name()) != null) board.removeLine(c.name());
    }

    void score(Combatant c) {
        if (board.getLine(c.name()) != null) board.updateLineScore(c.name(), c.kills());
    }

    void time(int secondsLeft, int total) {
        timer.name(Component.text(String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60)));
        timer.progress((float) secondsLeft / total);
    }

    void kill(Combatant killer, Combatant victim, String cause, boolean headshot) {
        everyone.sendMessage(Component.text(killer.name(), NamedTextColor.GREEN)
                .append(Component.text(" [" + cause + (headshot ? " ✦HS" : "") + "] ", NamedTextColor.GRAY))
                .append(Component.text(victim.name(), NamedTextColor.RED)));
    }

    void suicide(Combatant victim) {
        everyone.sendMessage(Component.text(victim.name() + " blew themselves up", NamedTextColor.GRAY));
    }

    void winner(Combatant c) {
        everyone.sendMessage(Component.text("Round over! Winner: " + c.name() + " (" + c.kills() + " kills)",
                NamedTextColor.GOLD));
    }
}
