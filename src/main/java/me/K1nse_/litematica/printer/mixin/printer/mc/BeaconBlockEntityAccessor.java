package me.K1nse_.litematica.printer.mixin.printer.mc;

//#if MC > 12004
import net.minecraft.core.Holder;
//#endif
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

//#if MC > 12004
import java.util.List;
//#endif

@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
    @Accessor("BEACON_EFFECTS")
    //#if MC > 12004
    static List<List<Holder<MobEffect>>> litematica_printer$getBeaconEffects() {
    //#else
    //$$ static MobEffect[][] litematica_printer$getBeaconEffects() {
    //#endif
        throw new AssertionError();
    }

    @Mutable
    @Accessor("BEACON_EFFECTS")
    static void litematica_printer$setBeaconEffects(
            //#if MC > 12004
            List<List<Holder<MobEffect>>> beaconEffects
            //#else
            //$$ MobEffect[][] beaconEffects
            //#endif
    ) {
        throw new AssertionError();
    }
}
