package com.eerussianguy.betterfoliage;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

import com.eerussianguy.betterfoliage.model.GrassLoader;
import com.eerussianguy.betterfoliage.model.LeavesBakedModel;
import com.eerussianguy.betterfoliage.model.LeavesLoader;
import com.eerussianguy.betterfoliage.model.ResourcePackLeavesBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForgeConfig;

public class EventHandler
{
    private static final Supplier<Boolean> OPTIFINE_LOADED = Suppliers.memoize(() ->
    {
        try
        {
            Class.forName("net.optifine.Config");
            return true;
        }
        catch (ClassNotFoundException ignored)
        {
            return false;
        }
    });

    public static void init(IEventBus bus)
    {
        bus.addListener(EventHandler::clientSetup);
        bus.addListener(EventHandler::onModelRegister);
        bus.addListener(EventHandler::onLoaderRegister);
        bus.addListener(EventHandler::afterTextureStitch);
        bus.addListener(EventPriority.LOWEST, EventHandler::onModifyBakingResult);
    }

    private static void clientSetup(final FMLClientSetupEvent event)
    {
        if (BFConfig.CLIENT.forceForgeLighting.get() && !OPTIFINE_LOADED.get() && !ModList.get().isLoaded("oculus"))
        {
            NeoForgeConfig.CLIENT.experimentalForgeLightPipelineEnabled.set(true);
        }

        if (ModList.get().isLoaded("tfc"))
        {
            BetterFoliage.LEAVES_DISABLED_BY_MOD = true;
        }
    }

    private static void afterTextureStitch(final TextureAtlasStitchedEvent event)
    {
        ForgeEventHandler.clearCache();
        LeavesBakedModel.clearSnowOverlayCache();
    }

    private static void onLoaderRegister(final ModelEvent.RegisterGeometryLoaders event)
    {
        event.register(Helpers.identifier("leaves"), new LeavesLoader());
        event.register(Helpers.identifier("grass"), new GrassLoader());
    }

    private static void onModelRegister(final ModelEvent.RegisterAdditional event)
    {
        event.register(Helpers.standalone("block/better_grass"));
        event.register(Helpers.standalone("block/better_grass_snowed"));
        event.register(Helpers.standalone("block/better_mycelium"));
        for (int i = 0; i < 4; i++)
        {
            event.register(Helpers.standalone("block/better_reed_" + i));
        }
    }

    /**
     * Resource packs have higher priority than mod resources, so a pack can replace Better Foliage's custom leaf
     * loader with a normal baked model containing its own fixed bushy planes. Wrap those block-state models after
     * baking: the wrapper keeps every pack-provided core quad, but takes ownership of quads whose texture is marked
     * as bushy so BF can position and cull them consistently.
     */
    private static void onModifyBakingResult(final ModelEvent.ModifyBakingResult event)
    {
        final Map<BakedModel, ResourcePackLeavesBakedModel> wrappers = new IdentityHashMap<>();
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet())
        {
            final ModelResourceLocation location = entry.getKey();
            if (ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())
                || ModelResourceLocation.STANDALONE_VARIANT.equals(location.getVariant())
                || !(BuiltInRegistries.BLOCK.get(location.id()) instanceof LeavesBlock))
            {
                continue;
            }

            final BakedModel model = entry.getValue();
            if (model instanceof LeavesBakedModel || model instanceof ResourcePackLeavesBakedModel)
            {
                continue;
            }
            entry.setValue(wrappers.computeIfAbsent(
                model,
                original -> new ResourcePackLeavesBakedModel(original, event.getTextureGetter())
            ));
        }
    }

}
