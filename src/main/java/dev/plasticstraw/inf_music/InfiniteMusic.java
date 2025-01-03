package dev.plasticstraw.inf_music;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.plasticstraw.inf_music.config.InfiniteMusicConfig;
import dev.plasticstraw.inf_music.config.InfiniteMusicConfig.MusicOptions;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.MusicType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class InfiniteMusic implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("inf-music");
    public static final InfiniteMusicConfig CONFIG = InfiniteMusicConfig.load();
    public static final Tracker TRACKER = new Tracker();

    private static final List<MusicSound> gameplayMusic = new ArrayList<MusicSound>();
    public static final List<SoundInstance> musicDiscInstanceList = new ArrayList<SoundInstance>();
    public static final List<SoundInstance> musicInstanceList = new ArrayList<SoundInstance>();
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static SoundInstance soundInstance;

    @Override
    public void onInitializeClient() {
        client = MinecraftClient.getInstance();
        updateMusicDelay(MusicType.MENU, CONFIG.mainMenuMusic);
    }

    public static void addGameplayMusic(MusicSound musicSound) {
        gameplayMusic.add(musicSound);
    }

    public static void updateMusicDelays() {
        updateMusicDelay(MusicType.MENU, CONFIG.mainMenuMusic);
        updateMusicDelay(MusicType.CREATIVE, CONFIG.creativeMusic);
        updateMusicDelay(MusicType.UNDERWATER, CONFIG.underwaterMusic);
        updateMusicDelay(MusicType.END, CONFIG.endMusic);

        for (MusicSound musicSound : gameplayMusic) {
            if (!(musicSound == MusicType.MENU
                    || musicSound == MusicType.CREATIVE
                    || musicSound == MusicType.UNDERWATER
                    || musicSound == MusicType.END
                    || musicSound == MusicType.DRAGON
                    || musicSound == MusicType.CREDITS)) {
                updateMusicDelay(musicSound, CONFIG.gameplayMusic);
            }
        }

        TRACKER.shouldDoFullTick();
    }

    public static boolean isMusicDiscMusicPlaying() {
        for (SoundInstance soundInstance : new ArrayList<SoundInstance>(musicDiscInstanceList)) {
            if (MinecraftClient.getInstance().getSoundManager().isPlaying(soundInstance)) {
                return true;
            }

            musicDiscInstanceList.remove(soundInstance);
        }

        if (musicDiscInstanceList.size() == 0) {
            return false;
        }

        return true;
    }

    private static void updateMusicDelay(MusicSound musicSound, MusicOptions config) {
        musicSound.updateMusicDelays(config.getMinTicks(), config.getMaxTicks(), config.enabled);
    }

    public static class Tracker {
        @Nullable
        private SoundInstance currentMusicPlaying;
        private final Random random = Random.create();
        private float randomFloat = random.nextFloat();
        private int timeSinceLastSong = -1;
        private boolean hasJoinedWorld = true;
        private boolean readyToPlay = false;
        private MusicSound musicSound;
        private float volume = 1.0f;
        @Nullable
        private TickCondition evaluatedTickCondition;

        public void tick() {
            musicSound = client.getMusicInstance().music();

            if (evaluatedTickCondition == null || evaluatedTickCondition.musicSoundType != musicSound) {
                evaluatedTickCondition = fullTick();
            }

            if (evaluatedTickCondition.tick()) {
                evaluatedTickCondition = null;
            }

            float instanceVolume = client.getMusicInstance().volume();
            tryToFadeVolume(instanceVolume);
        }

        public TickCondition fullTick() {
            MusicInstance musicInstance = client.getMusicInstance();
            MusicSound musicSound = musicInstance.music();

            if (fixForMcBug() || musicSound == null) {
                return new TickCondition(null);
            }

            if (!musicSound.enabled()) { // is music disabled?
                stop(musicSound);
                return new TickCondition(musicSound);
            }


            if (musicSound.shouldReplaceCurrentMusic() && !isPlayingType(musicSound)) {
                // should music start immediately and is not playing self?
                // the skip next condition
            }

            else if (currentMusicPlaying != null) { // is waiting for music to finish?
                return new TickCondition(musicSound) {

                    public boolean tick() {
                        if (isPlayingMusic()) {
                            return false;
                        }

                        currentMusicPlaying = null;
                        return true;
                    }

                };
            }

            if (hasJoinedWorld && !musicSound.equals(MusicType.MENU)) {
                hasJoinedWorld = false;

                if (CONFIG.playMusicImmediately) {
                    readyToPlay = true;
                }
            }

            if (musicSound.getMinDelay() == 0 && musicSound.getMaxDelay() == 0) {
                readyToPlay = true;
            }

            if (readyToPlay) {
                return new TickCondition(musicSound) {
                    public boolean tick() {
                        if (isDiscMusicBlocking()) {
                            return false;
                        }

                        stop();
                        play(musicInstance);
                        return true;
                    }
                };
            }

            return new TickCondition(musicSound) {

                private final int musicDelay;

                {
                    musicDelay = Math.round(randomFloat * (musicSound.getMaxDelay() - musicSound.getMinDelay()))
                            + musicSound.getMinDelay();
                }

                public boolean tick() {
                    timeSinceLastSong++;

                    if (timeSinceLastSong < musicDelay) {
                        return false;
                    }

                    if (isDiscMusicBlocking()) {
                        readyToPlay = true;
                    } else {
                        stop();
                        play(musicInstance);
                    }

                    return true;
                }

            };

        }

        private void tryToFadeVolume(float volume) {
            if (this.currentMusicPlaying == null) {
                return;
            }
            if (this.volume == volume) {
                return;
            }
            if (this.volume < volume) {
                this.volume += MathHelper.clamp(this.volume, 5.0E-4F, 0.005F);
                if (this.volume > volume) {
                    this.volume = volume;
                }
            } else {
                this.volume = 0.03F * volume + 0.97F * this.volume;
                if (Math.abs(this.volume - volume) < 1.0E-4F || this.volume < volume) {
                    this.volume = volume;
                }
            }

            this.volume = MathHelper.clamp(this.volume, 0.0F, 1.0F);
            if (this.volume <= 1.0E-4F) {
                this.stop();
            } else {
                client.getSoundManager().setVolume(this.currentMusicPlaying, this.volume);
            }
        }

        private class TickCondition {

            @Nullable
            public final MusicSound musicSoundType;

            TickCondition(@Nullable MusicSound musicSoundType) {
                this.musicSoundType = musicSoundType;
            }

            public boolean tick() {
                return false;
            }

        }

        public void play(MusicInstance musicInstance) {
            if (isDiscMusicBlocking()) {
                return;
            }

            MusicSound musicSound = musicInstance.music();

            if (musicSound != null && musicSound.getSound() == SoundManager.MISSING_SOUND) {
                timeSinceLastSong = Integer.MIN_VALUE;
                return;
            }

            readyToPlay = false;
            timeSinceLastSong = -1;
            randomFloat = random.nextFloat();

            for (SoundInstance soundInstance : new ArrayList<SoundInstance>(musicInstanceList)) {
                if (!MinecraftClient.getInstance().getSoundManager().isPlaying(soundInstance)) {
                    musicInstanceList.remove(soundInstance);
                }
            }

            if (musicInstanceList.size() != 0) {
                return;
            }

            currentMusicPlaying = PositionedSoundInstance.music(musicSound.getSound().value());

            if (this.currentMusicPlaying.getSound() != SoundManager.MISSING_SOUND) {
                client.getSoundManager().play(this.currentMusicPlaying);
                client.getSoundManager().setVolume(this.currentMusicPlaying, musicInstance.volume());
            }

            this.volume = musicInstance.volume();
        }

        public void stop(MusicSound type) {
            if (this.isPlayingType(type)) {
                this.stop();
            }
        }

        public void stop() {
            if (currentMusicPlaying == null) {
                return;
            }
            client.getSoundManager().stop(currentMusicPlaying);
        }

        public boolean isPlayingType(MusicSound musicSound) {
            if (currentMusicPlaying == null || currentMusicPlaying.getSound() == null) {
                return false;
            }

            return musicSound.getSound().matchesId(currentMusicPlaying.getId());
        }

        public boolean isPlayingMusic() {
            return client.getSoundManager().isPlaying(currentMusicPlaying);
        }

        public void hasJoinedWorld() {
            hasJoinedWorld = true;
        }

        public void shouldDoFullTick() {
            evaluatedTickCondition = null;
        }

        private boolean isDiscMusicBlocking() {
            return CONFIG.pauseForDiscMusic && isMusicDiscMusicPlaying();
        }

        // Fix for World.getBiome() returning "minecraft:plains" when still in the
        // loading screen. At the moment this functions just checks to see if the player
        // is not in the overworld and in a plains biome. Should probably come up with a
        // better solution.
        private boolean fixForMcBug() {
            if (client.player != null) {
                return client.player.getWorld().getRegistryKey() != World.OVERWORLD
                        && client.player.getWorld().getBiome(client.player.getBlockPos())
                                .getIdAsString().equals("minecraft:plains");
            }

            return false;
        }

    }

}
