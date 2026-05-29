package io.github.steamwork.guide;

import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage;
import io.github.steamwork.SteamworkItems;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public class CalibratorSupportedMachinesPage extends SimpleStaticGuidePage {

    public CalibratorSupportedMachinesPage() {
        super(steamworkKey("calibrator_supported_machines"));

        // 蒸汽加工机器（AbstractSteamProcessor 子类）
        addItem(SteamworkItems.STEAM_STERILIZER);
        addItem(SteamworkItems.STEAM_STEEPING_VAT);
        addItem(SteamworkItems.STEAM_WASHING_TROUGH);
        addItem(SteamworkItems.STEAM_PRESS);
        addItem(SteamworkItems.STEAM_GRINDER);
        addItem(SteamworkItems.STEAM_PRESSURIZED_FURNACE);
        addItem(SteamworkItems.STEAM_PRECISION_MILL);
        addItem(SteamworkItems.PRECISION_FOUNDRY);
        addItem(SteamworkItems.PRECISION_CATALYTIC_REACTOR);
        addItem(SteamworkItems.HEAVY_IMPACT_CRUSHER);
        addItem(SteamworkItems.HYDRAULIC_FORGE);
        addItem(SteamworkItems.PRECISION_CRYSTALLIZER);
        addItem(SteamworkItems.PRECISION_CENTRIFUGE);

        // 蒸汽涡轮（AbstractSteamBooster 子类）
        addItem(SteamworkItems.SIMPLE_STEAM_TURBINE);
        addItem(SteamworkItems.ADVANCED_STEAM_TURBINE);

        // 产线入口
        addItem(SteamworkItems.PRODUCTION_LINE_INLET);
    }
}
