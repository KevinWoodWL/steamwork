package io.github.steamwork.guide;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage;
import io.github.steamwork.SteamworkItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public class ProductionLineSupportedMachinesPage extends SimpleStaticGuidePage {

    public ProductionLineSupportedMachinesPage() {
        super(steamworkKey("production_line_supported_machines"));

        addItem(new ItemStack(Material.FURNACE));
        addItem(new ItemStack(Material.BLAST_FURNACE));
        addItem(new ItemStack(Material.SMOKER));
        addItem(new ItemStack(Material.CRAFTER));

        addItem(SteamworkItems.STEAM_STERILIZER);
        addItem(SteamworkItems.STEAM_STEEPING_VAT);
        addItem(SteamworkItems.STEAM_WASHING_TROUGH);
        addItem(SteamworkItems.STEAM_PRESS);
        addItem(SteamworkItems.STEAM_GRINDER);
        addItem(SteamworkItems.STEAM_PRESSURIZED_FURNACE);
        addItem(SteamworkItems.STEAM_ASSEMBLY_BENCH);
        addItem(SteamworkItems.STEAM_PRECISION_MILL);
        addItem(SteamworkItems.PRECISION_FOUNDRY);
        addItem(SteamworkItems.PRECISION_CATALYTIC_REACTOR);
        addItem(SteamworkItems.HEAVY_IMPACT_CRUSHER);
        addItem(SteamworkItems.HYDRAULIC_FORGE);
        addItem(SteamworkItems.PRECISION_CRYSTALLIZER);
        addItem(SteamworkItems.PRECISION_CENTRIFUGE);
        addItem(SteamworkItems.PRODUCTION_LINE_BUFFER_CHEST);
        addItem(SteamworkItems.PRODUCTION_LINE_OUTLET);

        addOptionalPylonItem("GRINDSTONE");
        addOptionalPylonItem("MIXING_POT");
        addOptionalPylonItem("HYDRAULIC_GRINDSTONE_TURNER");
        addOptionalPylonItem("HYDRAULIC_MIXING_ATTACHMENT");
        addOptionalPylonItem("HYDRAULIC_PRESS_PISTON");
        addOptionalPylonItem("HYDRAULIC_HAMMER_HEAD");
        addOptionalPylonItem("HYDRAULIC_PIPE_BENDER");
        addOptionalPylonItem("HYDRAULIC_TABLE_SAW");
        addOptionalPylonItem("HYDRAULIC_BREAKER");
        addOptionalPylonItem("DIESEL_GRINDSTONE");
        addOptionalPylonItem("DIESEL_MIXING_ATTACHMENT");
        addOptionalPylonItem("DIESEL_PRESS");
        addOptionalPylonItem("DIESEL_HAMMER_HEAD");
        addOptionalPylonItem("DIESEL_PIPE_BENDER");
        addOptionalPylonItem("DIESEL_TABLE_SAW");
        addOptionalPylonItem("DIESEL_BREAKER");
        addOptionalPylonItem("DIESEL_FURNACE");
    }

    private void addOptionalPylonItem(String fieldName) {
        try {
            Field field = PylonItems.class.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof ItemStack itemStack) {
                addItem(itemStack);
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep the guide page compatible with nearby Pylon versions.
        }
    }
}
