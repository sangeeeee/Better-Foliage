package com.eerussianguy.betterfoliage.model;


import net.neoforged.neoforge.client.model.data.ModelProperty;

public class GrassConnectionData
{
    public static final ModelProperty<GrassConnectionData> PROPERTY = new ModelProperty<>();

    private final int meta;
    private final boolean up;
    private final boolean reedEligible;
    private final float reedPopulationRoll;
    private final int reedModel;
    private final int reedLight;

    public GrassConnectionData(boolean north, boolean east, boolean south, boolean west, boolean up, boolean reedEligible, float reedPopulationRoll, int reedModel, int reedLight)
    {
        int i = 0;
        if (north)
            i |= 1;
        if (east)
            i |= 2;
        if (south)
            i |= 4;
        if (west)
            i |= 8;

        meta = i;
        this.up = up;
        this.reedEligible = reedEligible;
        this.reedPopulationRoll = reedPopulationRoll;
        this.reedModel = reedModel;
        this.reedLight = reedLight;
    }

    public int get()
    {
        return meta;
    }

    public boolean hasUp()
    {
        return up;
    }

    public boolean hasReed(double population)
    {
        return reedEligible && reedPopulationRoll < population;
    }

    public int getReedModel()
    {
        return reedModel;
    }

    public int getReedLight()
    {
        return reedLight;
    }

}
