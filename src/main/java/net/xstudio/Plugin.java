package net.xstudio;

import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/*
 * itemgetout java plugin
 */
public class Plugin extends JavaPlugin
{
  private static final Logger LOGGER=Logger.getLogger("itemgetout");

  public void onEnable()
  {
    LOGGER.info("itemgetout enabled");
  }

  public void onDisable()
  {
    LOGGER.info("itemgetout disabled");
  }
}
