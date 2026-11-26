package org.blake;

// How a bot decides who to fight. Tune each personality right here.
//   fightChance: chance, each time the bot acts, that it goes after its target instead of
//                just expanding peacefully
//   memoryMs:    Tit-for-tat and Grudge: how long they remember being attacked.
//                Opportunist: how long an attack on someone else stays an opening to join in.
public enum Personality {
    // Picks on the weakest bordering player, often.
    AGGRESSOR("Aggressor", 0.6, 0L),
    // Piles onto anyone currently being attacked by someone else (the memoryMs window is how
    // long an attack on a player stays an opening), and also preys on players clearly smaller than itself.
    OPPORTUNIST("Opportunist", 0.7, 15_000L),
    // Strikes back at whoever hit it recently, then forgives once they stop.
    TIT_FOR_TAT("Tit-for-tat", 0.9, 30_000L),
    // Never forgives: hunts anyone who has ever hit it, even across the sea.
    GRUDGE("Grudge", 0.9, Long.MAX_VALUE),
    // Never attacks anyone. Just grows.
    PACIFIST("Pacifist", 0.0, 0L);

    final String label;
    final double fightChance;
    final long memoryMs;

    Personality(String label, double fightChance, long memoryMs) {
        this.label = label;
        this.fightChance = fightChance;
        this.memoryMs = memoryMs;
    }
}