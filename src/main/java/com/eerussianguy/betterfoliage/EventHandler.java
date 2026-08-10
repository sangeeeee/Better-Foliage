package com.eerussianguy.betterfoliage;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

import com.eerussianguy.betterfoliage.model.GrassBakedModel;
import com.eerussianguy.betterfoliage.model.GrassLoader;
import com.eerussianguy.betterfoliage.model.LeavesBakedModel;
import com.eerussianguy.betterfoliage.model.LeavesLoader;
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
        bus.addListener(EventHandler::onModelBake);
        bus.addListener(EventHandler::onModelRegister);
        bus.addListener(EventHandler::onLoaderRegister);
        bus.addListener(EventHandler::afterTextureStitch);
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

    private static void onModelBake(final ModelEvent.BakingCompleted event)
    {
        LeavesBakedModel.INSTANCES.forEach(LeavesBakedModel::init);
        GrassBakedModel.INSTANCES.forEach(GrassBakedModel::init);
    }

    private static void afterTextureStitch(final TextureAtlasStitchedEvent event)
    {
        ForgeEventHandler.clearCache();
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
}
