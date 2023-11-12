package marsh.town.brb.config;

import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "newRecipes")
public class NewRecipes {
    @ConfigEntry.Gui.Tooltip()
    public boolean unlockAll = true;
    @ConfigEntry.Gui.Tooltip()
    public boolean enableBounce = false;
}
