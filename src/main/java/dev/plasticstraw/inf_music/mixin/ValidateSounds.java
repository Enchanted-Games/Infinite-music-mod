package dev.plasticstraw.inf_music.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.plasticstraw.inf_music.InfiniteMusic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;

@Mixin(SoundManager.class)
public class ValidateSounds {

    @Inject(method = "Lnet/minecraft/client/sound/SoundManager;play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void validateSounds(SoundInstance soundInstance, CallbackInfo ci) {
        if (soundInstance.getCategory() == SoundCategory.MUSIC) {
            InfiniteMusic.musicInstanceList.add(soundInstance);
        } else if (soundInstance.getId().getPath().startsWith("music_disc.")) {
            InfiniteMusic.musicDiscInstanceList.add(soundInstance);

            if (InfiniteMusic.CONFIG.pauseForDiscMusic) {
                MinecraftClient.getInstance().getSoundManager().stop(InfiniteMusic.soundInstance);
            }
        }
    }

}
