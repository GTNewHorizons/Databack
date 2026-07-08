package databack.common.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.GTNHLib;
import databack.common.loader.Datapack;
import databack.common.loader.DatapackLoader;
import databack.common.loader.DatapackWorldInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class DatapackCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "datapack";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "Usage: /datapack [subcommand]. Valid subcommands: enable, disable, list, reload.";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, getCommandName());
    }

    public void printHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(getCommandUsage(null)));
        sender.addChatMessage(new ChatComponentText("  enable <datapack name>"));
        sender.addChatMessage(new ChatComponentText("    - Enables a datapack without altering its position."));
        sender.addChatMessage(new ChatComponentText("  enable <datapack name> (first|last)"));
        sender.addChatMessage(new ChatComponentText("    - Enables a datapack and moves it to the first or last position."));
        sender.addChatMessage(new ChatComponentText("  enable <datapack name> (before|after) <existing>"));
        sender.addChatMessage(new ChatComponentText("    - Enables a datapack and moves it before or after another pack."));
        sender.addChatMessage(new ChatComponentText("  disable <datapack name>"));
        sender.addChatMessage(new ChatComponentText("    - Disables a datapack."));
        sender.addChatMessage(new ChatComponentText("  list"));
        sender.addChatMessage(new ChatComponentText("    - Lists available datapacks."));
        sender.addChatMessage(new ChatComponentText("  reload"));
        sender.addChatMessage(new ChatComponentText("    - Reloads datapacks."));
        sender.addChatMessage(new ChatComponentText(""));
        sender.addChatMessage(new ChatComponentText("Packs must be reloaded by restarting the world or running /datapack reload after making modifications."));
        sender.addChatMessage(new ChatComponentText("Packs at the end/bottom of the list have priority over earlier packs."));
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ObjectArrayList<String> argStack = new ObjectArrayList<>();

        for (String arg : args) argStack.add(0, arg);

        if (argStack.isEmpty()) {
            printHelp(sender);
            return;
        }

        World world = DimensionManager.getWorld(0);
        File saveDir = world.getSaveHandler().getWorldDirectory();

        DatapackWorldInfo worldInfo = (DatapackWorldInfo) world.getWorldInfo();
        List<Datapack> packs = DatapackLoader.loadDatapacks(DatapackLoader.discoverCandidates(saveDir));

        worldInfo.syncPackDeltas(packs);

        try {
            switch (argStack.pop()) {
                case "enable" -> {
                    if (argStack.isEmpty()) {
                        printHelp(sender);
                        return;
                    }

                    String packName = argStack.pop();

                    if (argStack.isEmpty()) {
                        if (worldInfo.getDatapackOrder().contains(packName)) {
                            worldInfo.enable(packName);
                            sender.addChatMessage(new ChatComponentText("Enabled " + packName));
                        } else {
                            sender.addChatMessage(new ChatComponentText("Pack " + packName + " does not exist"));
                        }
                    } else {
                        switch (argStack.pop()) {
                            case "first" -> {
                                if (worldInfo.getDatapackOrder().contains(packName)) {
                                    worldInfo.getDatapackOrder().remove(packName);
                                    worldInfo.getDatapackOrder().add(0, packName);
                                    sender.addChatMessage(new ChatComponentText("Moved " + packName + " to the front of the list."));
                                } else {
                                    sender.addChatMessage(new ChatComponentText("Pack " + packName + " does not exist"));
                                }
                            }
                            case "last" -> {
                                if (worldInfo.getDatapackOrder().contains(packName)) {
                                    worldInfo.getDatapackOrder().remove(packName);
                                    worldInfo.getDatapackOrder().add(packName);
                                    sender.addChatMessage(new ChatComponentText("Moved " + packName + " to the end of the list."));
                                } else {
                                    sender.addChatMessage(new ChatComponentText("Pack " + packName + " does not exist"));
                                }
                            }
                            case "before" -> {
                                if (argStack.isEmpty()) {
                                    printHelp(sender);
                                    return;
                                }

                                String relativeTo = argStack.pop();

                                int idx = worldInfo.getDatapackOrder().indexOf(relativeTo);

                                if (idx == -1) {
                                    sender.addChatMessage(new ChatComponentText("Pack " + relativeTo + " does not exist"));
                                    return;
                                }

                                worldInfo.getDatapackOrder().remove(packName);
                                worldInfo.getDatapackOrder().add(idx, packName);
                                sender.addChatMessage(new ChatComponentText("Moved " + packName + " before " + relativeTo));
                            }
                            case "after" -> {
                                if (argStack.isEmpty()) {
                                    printHelp(sender);
                                    return;
                                }

                                String relativeTo = argStack.pop();

                                int idx = worldInfo.getDatapackOrder().indexOf(relativeTo);

                                if (idx == -1) {
                                    sender.addChatMessage(new ChatComponentText("Pack " + relativeTo + " does not exist"));
                                    return;
                                }

                                worldInfo.getDatapackOrder().remove(packName);
                                worldInfo.getDatapackOrder().add(idx + 1, packName);
                                sender.addChatMessage(new ChatComponentText("Moved " + packName + " after " + relativeTo));
                            }
                        }
                    }
                }
                case "disable" -> {
                    if (argStack.isEmpty()) {
                        printHelp(sender);
                        return;
                    }

                    String packName = argStack.pop();

                    if (worldInfo.getDatapackOrder().contains(packName)) {
                        worldInfo.disable(packName);
                        sender.addChatMessage(new ChatComponentText("Disabled " + packName));
                    } else {
                        sender.addChatMessage(new ChatComponentText("Pack " + packName + " does not exist"));
                    }
                }
                case "list" -> {
                    sender.addChatMessage(new ChatComponentText("Available data packs:"));

                    if (packs.isEmpty()) {
                        sender.addChatMessage(new ChatComponentText("None"));
                    }

                    for (var pack : worldInfo.order(packs)) {
                        sender.addChatMessage(new ChatComponentText(" - " + pack.getPackId() + (!pack.isEnabled() ? " (disabled)" : "")));

                        try {
                            pack.getSource().close();
                        } catch (IOException e) {
                            GTNHLib.LOG.error("Could not close datapack {}", pack.getPackId(), e);
                        }
                    }
                }
                case "reload" -> {
                    sender.addChatMessage(new ChatComponentText("Reloading data packs."));

                    DatapackLoader.load(saveDir, world);
                }
            }
        } finally {
            for (var pack : packs) {
                try {
                    pack.getSource().close();
                } catch (IOException e) {
                    GTNHLib.LOG.error("Could not close datapack {}", pack.getPackId(), e);
                }
            }
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        ObjectArrayList<String> argStack = new ObjectArrayList<>();

        for (String arg : args) argStack.add(0, arg);

        if (argStack.isEmpty()) {
            return ImmutableList.of("enable", "disable", "list");
        }

        World world = DimensionManager.getWorld(0);
        File saveDir = world.getSaveHandler().getWorldDirectory();

        DatapackWorldInfo worldInfo = (DatapackWorldInfo) world.getWorldInfo();
        List<Datapack> packs = DatapackLoader.loadDatapacks(DatapackLoader.discoverCandidates(saveDir));

        worldInfo.syncPackDeltas(packs);
        packs = worldInfo.order(packs);

        try {
            String subcommand = argStack.pop();
            switch (subcommand) {
                case "enable" -> {
                    if (argStack.isEmpty()) {
                        return packs.stream().map(Datapack::getPackId).collect(Collectors.toList());
                    }

                    String packName = argStack.pop();

                    boolean exactMatch = packs.stream().anyMatch(pack -> pack.getPackId().equals(packName));

                    if (exactMatch) {
                        if (argStack.isEmpty()) {
                            return ImmutableList.of("first", "last", "before", "after");
                        }

                        String positionKeyword = argStack.pop();

                        if (argStack.isEmpty()) {
                            return Stream.of("first", "last", "before", "after")
                                .filter(s -> s.startsWith(positionKeyword))
                                .collect(Collectors.toList());
                        }

                        switch (positionKeyword) {
                            case "before", "after" -> {
                                String relativeTo = argStack.pop();
                                return packs.stream()
                                    .map(Datapack::getPackId)
                                    .filter(id -> id.startsWith(relativeTo))
                                    .collect(Collectors.toList());
                            }
                            default -> {
                                return new ArrayList<>();
                            }
                        }
                    }

                    return packs.stream()
                        .map(Datapack::getPackId)
                        .filter(id -> id.startsWith(packName))
                        .collect(Collectors.toList());
                }
                case "disable" -> {
                    if (argStack.isEmpty()) {
                        return packs.stream().map(Datapack::getPackId).collect(Collectors.toList());
                    }

                    String packName = argStack.pop();

                    boolean exactMatch = packs.stream().anyMatch(pack -> pack.getPackId().equals(packName));

                    if (exactMatch) {
                        return new ArrayList<>();
                    } else {
                        return packs.stream().map(Datapack::getPackId).filter(id -> id.startsWith(packName)).collect(Collectors.toList());
                    }
                }
                case "list" -> {
                    return new ArrayList<>();
                }
                default -> {
                    return Stream.of("enable", "disable", "list").filter(s -> s.startsWith(subcommand)).collect(Collectors.toList());
                }
            }
        } finally {
            for (var pack : packs) {
                try {
                    pack.getSource().close();
                } catch (IOException e) {
                    GTNHLib.LOG.error("Could not close datapack {}", pack.getPackId(), e);
                }
            }
        }
    }
}
