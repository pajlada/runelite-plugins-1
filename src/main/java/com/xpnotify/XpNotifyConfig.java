package com.xpnotify;

import net.runelite.api.SoundEffectID;
import net.runelite.api.SoundEffectVolume;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("XpNotify")
public interface XpNotifyConfig extends Config {
    @ConfigItem(
            keyName = "greeting",
            name = "Welcome Greeting",
            description = "The message to show to the user when they login"
    )
    default String greeting() {
        return "Hello";
    }

    @ConfigItem(
            keyName = "skillsToFilter",
            name = "Skills to filter",
            description = "Names of the skills for which a XP drop should not be shown, each name separated by a comma. Must be the full name of the skill as shown when hovered in the skills tab."
    )
    default String skillsToFilter() {
        return "";
    }

    @Range(
            max = 5322 // Manually have to increase over time to let people use newer sounds
    )
    @ConfigItem(
            keyName = "sound id",
            name = "Sound ID",
            description = "The sound ID to play"
    )
    default int soundId() {
        return SoundEffectID.TOWN_CRIER_BELL_DING;
    }

    @Range(
            max = SoundEffectVolume.HIGH
    )
    @ConfigItem(
            keyName = "volume",
            name = "Volume",
            description = "The volume of the xp drop sound effect"
    )
    default int volume() {
        return SoundEffectVolume.MEDIUM_LOW;
    }
}
