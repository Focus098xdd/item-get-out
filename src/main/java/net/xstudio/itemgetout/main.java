package net.xstudio.itemgetout;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class main extends JavaPlugin implements Listener, CommandExecutor
{
  private static final Logger LOGGER = Logger.getLogger("itemgetout");
  private Map<UUID, List<Material>> playerBlacklists;
  private FileConfiguration config;

  public void onEnable()
  {
    LOGGER.info("ItemGetOut enabled");
    
    // Initialize blacklist
    playerBlacklists = new java.util.HashMap<>();
    
    // Load configuration
    saveDefaultConfig();
    config = getConfig();
    loadBlacklistedItems();
    
    // Register events and commands
    getServer().getPluginManager().registerEvents(this, this);
    getCommand("itemgetout").setExecutor(this);
    getCommand("blacklist").setExecutor(this);
    
    int total = playerBlacklists.values().stream().mapToInt(List::size).sum();
    LOGGER.info("ItemGetOut loaded with " + total + " total blacklisted items across all players");
  }

  public void onDisable()
  {
    LOGGER.info("ItemGetOut disabled");
    saveBlacklistedItems();
  }
  
  // Load per-player blacklists
  private void loadBlacklistedItems() {
    playerBlacklists.clear();
    if (config.isConfigurationSection("blacklisted-items")) {
        for (String uuidStr : config.getConfigurationSection("blacklisted-items").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            List<String> items = config.getStringList("blacklisted-items." + uuidStr);
            List<Material> mats = new ArrayList<>();
            for (String itemName : items) {
                try {
                    mats.add(Material.valueOf(itemName));
                } catch (IllegalArgumentException ignored) {}
            }
            playerBlacklists.put(uuid, mats);
        }
    }
  }
  
  // Save per-player blacklists
  private void saveBlacklistedItems() {
    for (Map.Entry<UUID, List<Material>> entry : playerBlacklists.entrySet()) {
        List<String> items = new ArrayList<>();
        for (Material mat : entry.getValue()) {
            items.add(mat.name());
        }
        config.set("blacklisted-items." + entry.getKey().toString(), items);
    }
    saveConfig();
  }
  
  private List<Material> getBlacklist(Player player) {
    return playerBlacklists.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
  }
  
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
      return true;
    }
    
    Player player = (Player) sender;
    
    if (command.getName().equalsIgnoreCase("itemgetout")) {
      openBlacklistGUI(player);
      return true;
    }
    
    if (command.getName().equalsIgnoreCase("blacklist")) {
      openBlacklistGUI(player);
      return true;
    }
    
    return false;
  }
  
  private void openBlacklistGUI(Player player) {
    Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Item Blacklist Manager");
    
    // Add instructions at the top
    ItemStack instructionItem = new ItemStack(Material.BOOK);
    ItemMeta instructionMeta = instructionItem.getItemMeta();
    instructionMeta.setDisplayName(ChatColor.YELLOW + "Instructions");
    List<String> instructionLore = new ArrayList<>();
    instructionLore.add(ChatColor.GRAY + "Place items in the slots below");
    instructionLore.add(ChatColor.GRAY + "to add them to the blacklist");
    instructionLore.add(ChatColor.GRAY + "Click on blacklisted items to remove them");
    instructionMeta.setLore(instructionLore);
    instructionItem.setItemMeta(instructionMeta);
    gui.setItem(4, instructionItem);
    
    // Add current blacklisted items starting from slot 9
    int slot = 9;
    for (Material material : getBlacklist(player)) {
      if (slot < 54) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
          meta.setDisplayName(ChatColor.RED + material.name());
          List<String> lore = new ArrayList<>();
          lore.add(ChatColor.GRAY + "Click to remove from blacklist");
          meta.setLore(lore);
          item.setItemMeta(meta);
        }
        
        gui.setItem(slot, item);
        slot++;
      }
    }
    
    player.openInventory(gui);
  }
  
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;

    Player player = (Player) event.getWhoClicked();
    String title = event.getView().getTitle();

    if (!title.equals(ChatColor.DARK_RED + "Item Blacklist Manager")) return;

    // Only handle clicks in the GUI slots (9-53)
    if (event.getSlot() >= 9 && event.getSlot() < 54) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
            List<Material> blacklist = getBlacklist(player);
            Material material = clickedItem.getType();
            if (blacklist.contains(material)) {
                blacklist.remove(material);
                player.sendMessage(ChatColor.GREEN + material.name() + " removed from blacklist!");
                event.getInventory().setItem(event.getSlot(), null);
                event.setCancelled(true); // Prevent item from going to inventory
            }
        }
        return;
    }

    // Cancel clicks on instruction item (slot 4)
    if (event.getSlot() == 4) {
        event.setCancelled(true);
        return;
    }
  }
  
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player)) return;
    
    Player player = (Player) event.getPlayer();
    String title = event.getView().getTitle();
    
    if (!title.equals(ChatColor.DARK_RED + "Item Blacklist Manager")) return;
    
    // Check for new items placed in the GUI
    Inventory inventory = event.getInventory();
    for (int slot = 9; slot < 54; slot++) {
      ItemStack item = inventory.getItem(slot);
      if (item != null && item.getType() != Material.AIR) {
        Material material = item.getType();
        List<Material> blacklist = getBlacklist(player);
        if (!blacklist.contains(material)) {
          blacklist.add(material);
          player.sendMessage(ChatColor.RED + material.name() + " added to blacklist!");
        }
      }
    }
  }
  
  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    
    Player player = (Player) event.getWhoClicked();
    String title = event.getView().getTitle();
    
    if (!title.equals(ChatColor.DARK_RED + "Item Blacklist Manager")) return;
    
    // Allow dragging to blacklist slots (9-53)
    for (int slot : event.getRawSlots()) {
      if (slot >= 9 && slot < 54) {
        // Allow dragging to these slots
        continue;
      } else if (slot == 4) {
        // Cancel dragging to instruction slot
        event.setCancelled(true);
        return;
      }
    }
  }
  
  @EventHandler
  public void onPlayerPickupItem(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem().getItemStack();
    
    List<Material> blacklist = getBlacklist(player);
    if (blacklist.contains(item.getType())) {
      // Cancel the pickup and destroy the item silently
      event.setCancelled(true);
      event.getItem().remove();
      
      LOGGER.info("Player " + player.getName() + " attempted to pick up blacklisted item: " + item.getType().name());
    }
  }
}
