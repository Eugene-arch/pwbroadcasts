package com.eugenearch.broadcasts.server.commands;

import com.eugenearch.broadcasts.common.helpers.EasyJsonHelper;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import joptsimple.internal.Strings;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.HoverEvent;
import scala.actors.threadpool.Arrays;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CommandTournament extends CommandBase {

    public static final CommandTournament INSTANCE = new CommandTournament();
    public final ITextComponent WRONG_USAGE_COMPONENT = new TextComponentString("Invalid arguments!");

    private String configFile = "";
    public int DELAY_BETWEEN_MESSAGES_MINUTES = 15;
    public String DEFAULT_PREFIX = "§4[§bTOURNAMENT§4]";
    private int LAST_MESSAGE_INDEX = 0;
    private final HashMap<String, Tournament> tournaments = new HashMap<>();

    private CommandTournament() {
    }

    @Override
    public String getName() {
        return "pwtournament";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAliases() {
        return Arrays.asList(new String[]{"pwt", "pwtour"});
    }

    @Override
    public String getUsage(ICommandSender iCommandSender) {
        return getUsage();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(getUsage()));
            return;
        }

        EnumTournamentActions action = EnumTournamentActions.getAction(args[0]);
        if (action == null) {
            sender.sendMessage(WRONG_USAGE_COMPONENT);
            sender.sendMessage(new TextComponentString(getUsage()));
            return;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        action.perform(server, sender, subArgs);
        try {
            saveConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUsage() {
        return "§6Usage of §e/pwtournament§6 (§e/pwt§6, §e/pwtour§6):\n"
                + "§e/pwt list§b - get list of created tournaments\n"
                + "§e/pwt create <name>§b - create a new tournament\n"
                + "§e/pwt delete <name>§b - delete selected tournament\n"
                + "§e/pwt on <name|--all>§b - enable tournament message\n"
                + "§e/pwt off <name|--all>§b - disable tournament message\n"
                + "§e/pwt edit <tournament> <args>§b - edit selected tournament. Run without args to get help\n";
    }

    public void loadConfig(String configFile) throws IOException {
        this.configFile = configFile;

        try {
            try (Reader reader = Files.newBufferedReader(Paths.get(configFile))) {
                Gson config = new Gson();
                Map<?, ?> jsonValues = config.fromJson(reader, Map.class);

                EasyJsonHelper.JsonObject jsonObject = EasyJsonHelper.readAbstract(jsonValues);
                if (jsonObject == null) return;

                Double delay = jsonObject.getDouble("delay_minutes");
                if (delay != null && delay > 0) {
                    double delayF = delay;
                    this.DELAY_BETWEEN_MESSAGES_MINUTES = (int) delayF;
                }

                String default_prefix = jsonObject.getString("default_prefix");
                if (default_prefix != null) {
                    this.DEFAULT_PREFIX = default_prefix;
                }

                EasyJsonHelper.JsonObject messages = jsonObject.getSubObject("messages");
                if (messages == null) return;

                ArrayList<String> keys = messages.getSubKeys();
                if (keys == null) return;

                for (String msgName : keys) {
                    EasyJsonHelper.JsonObject msg = messages.getSubObject(msgName);
                    if (msg == null) continue;

                    Tournament tour = new Tournament(msgName);
                    String prefix = msg.getString("prefix");
                    String message = msg.getString("message");
                    Boolean isEnabled = msg.getBoolean("enabled");

                    if (prefix != null) tour.prefix = prefix;
                    if (message != null) tour.message = message;
                    if (isEnabled == Boolean.TRUE) tour.isEnabled = true;

                    tournaments.put(msgName, tour);
                }
            }
        } catch (IOException e) {
            saveConfig();
        }
    }

    public void saveConfig() throws IOException {
        try (Writer writer = Files.newBufferedWriter(Paths.get(configFile))) {

            Map<String, Object> values = new LinkedTreeMap<>();
            values.put("delay_minutes", this.DELAY_BETWEEN_MESSAGES_MINUTES);
            values.put("default_prefix", this.DEFAULT_PREFIX);

            HashMap<String, Object> messages = new HashMap<>();
            values.put("messages", messages);

            for (Map.Entry<String, Tournament> e : tournaments.entrySet()) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("prefix", e.getValue().prefix);
                params.put("message", e.getValue().message);
                params.put("enabled", e.getValue().isEnabled);

                messages.put(e.getKey(), params);
            }

            Gson config = new Gson();
            writer.write(config.toJson(values));
        }
    }

    public String getNextMessage() {
        ArrayList<String> names = new ArrayList<>(tournaments.keySet());
        names.sort(String::compareTo);

        ArrayList<String> messages = new ArrayList<>();
        for (String name : names) {
            Tournament tour = tournaments.get(name);

            if (tour == null) continue;
            if (!tour.isEnabled) continue;
            if (tour.getChatMessage().isEmpty()) continue;

            messages.add(tour.getChatMessage());
        }

        if (messages.isEmpty()) return "";

        LAST_MESSAGE_INDEX %= messages.size();
        return messages.get(LAST_MESSAGE_INDEX++);
    }

    public enum EnumTournamentActions {
        LIST("list") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length > 0) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (INSTANCE.tournaments.isEmpty()) {
                    sender.sendMessage(new TextComponentString("§bTournament set is empty! You may create a new tournament using §e/pwt create"));
                    return;
                }

                sender.sendMessage(new TextComponentString("§bCreated tournaments:"));
                ArrayList<String> names = new ArrayList<>(INSTANCE.tournaments.keySet());
                names.sort(String::compareTo);

                for (String key : names) {
                    Tournament tour = INSTANCE.tournaments.get(key);
                    TextComponentString component = new TextComponentString(
                            "§b - §e{name}§b (prefix §e{prefix}§b, status §e{status}§b)"
                                    .replace("{name}", key)
                                    .replace("{prefix}", tour.prefix.isEmpty() ? "\"\"" : tour.prefix)
                                    .replace("{status}", tour.isEnabled ? "§2Enabled" : "§cDisabled")
                    );
                    component.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(tour.getChatMessage())));
                    sender.sendMessage(component);
                }
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt list§b - get list of created tournaments";
            }
        },
        DELAY("delay") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length > 1) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (subArgs.length == 0) {
                    sender.sendMessage(new TextComponentString("§a[§dDELAY§a] current delay is §d{delay}§a minutes".replace("{delay}", Integer.toString(INSTANCE.DELAY_BETWEEN_MESSAGES_MINUTES))));
                    return;
                }

                try {
                    int delay = Integer.parseInt(subArgs[0]);
                    if (delay < 0) {
                        sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                        sender.sendMessage(new TextComponentString("§bDelay between messages §ccan't be less§b then 1 minute"));
                        sender.sendMessage(new TextComponentString(getUsage()));
                    }
                    INSTANCE.DELAY_BETWEEN_MESSAGES_MINUTES = delay;
                    sender.sendMessage(new TextComponentString("§a[§dDELAY§a] delay between messages §2set§a to §b{delay}§a minutes".replace("{delay}", Integer.toString(INSTANCE.DELAY_BETWEEN_MESSAGES_MINUTES))));

                } catch (Exception e) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt delay [new_delay]§b - get or set delay between messages (in minutes)";
            }
        },
        DEFAULT_PREFIX("default_prefix") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length == 0) {
                    sender.sendMessage(new TextComponentString("§a[§cDEFAULT PREFIX§a] current default prefix is §d{PREFIX}".replace("{PREFIX}", INSTANCE.DEFAULT_PREFIX.isEmpty() ? "\"\"" : INSTANCE.DEFAULT_PREFIX)));
                    return;
                }

                String[] words = new String[subArgs.length - 1];
                System.arraycopy(subArgs, 1, words, 0, subArgs.length - 1);

                if (words.length == 1 && words[0].equals("\"\"")) {
                    INSTANCE.DEFAULT_PREFIX = "";
                } else {
                    INSTANCE.DEFAULT_PREFIX = Strings.join(words, " ").replace("&", "§");
                }

                sender.sendMessage(new TextComponentString("§a[§cDEFAULT PREFIX§a] default prefix §2set§a to §b{prefix}§a".replace("{prefix}", INSTANCE.DEFAULT_PREFIX.isEmpty() ? "\"\"" : INSTANCE.DEFAULT_PREFIX)));
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt default_prefix [new_prefix]§b - get or set default prefix";
            }
        },
        CREATE("create") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length != 1) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                String name = subArgs[0];

                if (!name.matches("[0-9a-zA-Z_]+")) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name may §conly§b contain EN latters, digits and symbol \"_\""));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (name.length() > 12) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name §ccan't be longer§b then 12 symbols"));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (INSTANCE.tournaments.get(name) != null) {
                    sender.sendMessage(new TextComponentString("§bTournament with name §e{name}§b is §calready created§b! You may choose other name for new tournament or edit tournament §e{name}.".replace("{name}", name)));
                    return;
                }

                Tournament tour = new Tournament(name);
                INSTANCE.tournaments.put(name, tour);

                sender.sendMessage(new TextComponentString("§a[§dCREATE§a] Tournament §d{name}§a created!".replace("{name}", name)));
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt create <name>§b - create a new tournament";
            }
        },
        DELETE("delete") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length != 1) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                String name = subArgs[0];

                if (!name.matches("[0-9a-zA-Z_]+")) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name may §conly§b contain EN latters, digits and symbol \"_\""));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (name.length() > 12) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name §ccan't be longer§b then 12 symbols"));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (INSTANCE.tournaments.get(name) == null) {
                    sender.sendMessage(new TextComponentString("§bTournament with name §e{name}§b does §cnot found§b!".replace("{name}", name)));
                    return;
                }

                INSTANCE.tournaments.remove(name);
                sender.sendMessage(new TextComponentString("§a[§4DELETE§a] Tournament §d{name}§a deleted!".replace("{name}", name)));
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt delete <name>§b - delete selected tournament";
            }
        },
        ON("on") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length != 1) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                String name = subArgs[0];

                if (!name.matches("[0-9a-zA-Z_]+")) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name may §conly§b contain EN latters, digits and symbol \"_\""));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (name.length() > 12) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name §ccan't be longer§b then 12 symbols"));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (INSTANCE.tournaments.isEmpty()) {
                    sender.sendMessage(new TextComponentString("§bTournament set is empty! You may create a new tournament using §e/pwt create"));
                    return;
                }

                if (name.equalsIgnoreCase("--all")) {

                    for (Tournament tour : INSTANCE.tournaments.values()) {
                        tour.isEnabled = true;
                    }
                    sender.sendMessage(new TextComponentString("§a[§2ENABLE§a] §d{count}§a tournaments enabled!".replace("{count}", Integer.toString(INSTANCE.tournaments.size()))));

                } else {

                    if (INSTANCE.tournaments.get(name) == null) {
                        sender.sendMessage(new TextComponentString("§bTournament with name §e{name}§b does §cnot found§b!".replace("{name}", name)));
                        return;
                    }

                    INSTANCE.tournaments.get(name).isEnabled = true;
                    sender.sendMessage(new TextComponentString("§a[§2ENABLE§a] Tournament §d{name}§a enabled!".replace("{name}", name)));

                }
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt on <name|--all>§b - enable tournament message";
            }
        },
        OFF("off") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length != 1) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                String name = subArgs[0];

                if (!name.matches("[0-9a-zA-Z_]+")) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name may §conly§b contain EN latters, digits and symbol \"_\""));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (name.length() > 12) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name §ccan't be longer§b then 12 symbols"));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (INSTANCE.tournaments.isEmpty()) {
                    sender.sendMessage(new TextComponentString("§bTournament set is empty! You may create a new tournament using §e/pwt create"));
                    return;
                }

                if (name.equalsIgnoreCase("--all")) {

                    for (Tournament tour : INSTANCE.tournaments.values()) {
                        tour.isEnabled = false;
                    }
                    sender.sendMessage(new TextComponentString("§a[§cDISABLE§a] §d{count}§a tournaments disabled!".replace("{count}", Integer.toString(INSTANCE.tournaments.size()))));

                } else {

                    if (INSTANCE.tournaments.get(name) == null) {
                        sender.sendMessage(new TextComponentString("§bTournament with name §e{name}§b does §cnot found§b!".replace("{name}", name)));
                        return;
                    }

                    INSTANCE.tournaments.get(name).isEnabled = true;
                    sender.sendMessage(new TextComponentString("§a[§cDISABLE§a] Tournament §d{name}§a disabled!".replace("{name}", name)));

                }
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt off <name|--all>§b - disable tournament message";
            }
        },
        EDIT("edit") {
            @Override
            public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
                super.perform(server, sender, subArgs);
                if (subArgs.length < 2) {
                    sender.sendMessage(new TextComponentString(getExtendedUsage()));
                    return;
                }

                String name = subArgs[0];

                if (!name.matches("[0-9a-zA-Z_]+")) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name may §conly§b contain EN latters, digits and symbol \"_\""));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                if (name.length() > 12) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString("§bTournament name §ccan't be longer§b then 12 symbols"));
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                Tournament tournament = INSTANCE.tournaments.get(name);
                if (tournament == null) {
                    sender.sendMessage(new TextComponentString("§bTournament with name §e{name}§b does §cnot found§b!".replace("{name}", name)));
                    return;
                }

                EnumEditActions action = EnumEditActions.getAction(subArgs[1]);
                if (action == null) {
                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(getUsage()));
                    return;
                }

                String[] actionArgs = new String[subArgs.length - 2];
                System.arraycopy(subArgs, 2, actionArgs, 0, subArgs.length - 2);

                action.perform(server, sender, tournament, actionArgs);
            }

            @Override
            public String getUsage() {
                return "§bUsage: §e/pwt edit <tournament> <args>§b - edit selected tournament. Run without args to get help";
            }

            @Override
            public String getExtendedUsage() {
                return getUsage() + "\n"
                        + "§6Valid args for pwt edit:\n"
                        + "§eprefix <get|set <prefix>|reset>§b - get or change prefix of message, default is {prefix}\n".replace("{prefix}", INSTANCE.DEFAULT_PREFIX)
                        + "§emessage <get|set <message>>§b - get or set tournament broadcast message";
            }
        };

        public final String name;

        EnumTournamentActions(String name) {
            this.name = name;
        }

        public static EnumTournamentActions getAction(String name) {
            for (EnumTournamentActions action : values()) {
                if (action.name.equalsIgnoreCase(name)) {
                    return action;
                }
            }
            return null;
        }

        public void perform(MinecraftServer server, ICommandSender sender, String... subArgs) {
            // overrided for actions
        }

        public String getUsage() {
            // overrided for actions
            return INSTANCE.getUsage();
        }

        public String getExtendedUsage() {
            return getUsage();
        }

        private enum EnumEditActions {
            PREFIX("prefix") {
                @Override
                public void perform(MinecraftServer server, ICommandSender sender, Tournament tour, String... subArgs) {
                    super.perform(server, sender, tour, subArgs);
                    if (subArgs.length == 0) {
                        sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                        return;
                    }

                    String mode = subArgs[0];
                    if (mode.equalsIgnoreCase("get")) {
                        if (subArgs.length > 1) {
                            sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                            sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                            return;
                        }
                        sender.sendMessage(new TextComponentString("§a[§9EDIT§a][§9PREFIX§a] §b{name}§a prefix is §b{prefix}§a".replace("{name}", tour.name).replace("{prefix}", tour.prefix.isEmpty() ? "\"\"" : tour.prefix)));
                        return;
                    }

                    if (mode.equalsIgnoreCase("set")) {
                        if (subArgs.length < 2) {
                            sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                            sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                            return;
                        }

                        String[] words = new String[subArgs.length - 1];
                        System.arraycopy(subArgs, 1, words, 0, subArgs.length - 1);

                        if (words.length == 1 && words[0].equals("\"\"")) {
                            tour.prefix = "";
                        } else {
                            tour.prefix = Strings.join(words, " ").replace("&", "§");
                        }
                        sender.sendMessage(new TextComponentString("§a[§9EDIT§a][§9PREFIX§a] §b{name}§a prefix §2set§a to §b{prefix}§a".replace("{name}", tour.name).replace("{prefix}", tour.prefix.isEmpty() ? "\"\"" : tour.prefix)));
                        return;
                    }

                    if (mode.equalsIgnoreCase("reset")) {
                        if (subArgs.length > 1) {
                            sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                            sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                            return;
                        }
                        tour.prefix = INSTANCE.DEFAULT_PREFIX;
                        sender.sendMessage(new TextComponentString("§a[§9EDIT§a][§9PREFIX§a] §b{name}§a prefix §creset§a to §b{prefix}§a".replace("{name}", tour.name).replace("{prefix}", tour.prefix.isEmpty() ? "\"\"" : tour.prefix)));
                        return;
                    }

                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                }
            },
            MESSAGE("message") {
                @Override
                public void perform(MinecraftServer server, ICommandSender sender, Tournament tour, String... subArgs) {
                    super.perform(server, sender, tour, subArgs);
                    if (subArgs.length == 0) {
                        sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                        return;
                    }

                    String mode = subArgs[0];
                    if (mode.equalsIgnoreCase("get")) {
                        if (subArgs.length > 1) {
                            sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                            sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                            return;
                        }
                        sender.sendMessage(new TextComponentString("§a[§9EDIT§a][§9MESSAGE§a] §b{name}§a message is §b{message}§a".replace("{name}", tour.name).replace("{message}", tour.message.isEmpty() ? "\"\"" : tour.message)));
                        return;
                    }

                    if (mode.equalsIgnoreCase("set")) {
                        if (subArgs.length < 2) {
                            sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                            sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                            return;
                        }

                        String[] words = new String[subArgs.length - 1];
                        System.arraycopy(subArgs, 1, words, 0, subArgs.length - 1);

                        if (words.length == 1 && words[0].equals("\"\"")) {
                            tour.message = "";
                        } else {
                            tour.message = Strings.join(words, " ").replace("&", "§");
                        }

                        sender.sendMessage(new TextComponentString("§a[§9EDIT§a][§9MESSAGE§a] §b{name}§a message §2set§a to §b{message}§a".replace("{name}", tour.name).replace("{message}", tour.message.isEmpty() ? "\"\"" : tour.message)));
                        return;
                    }

                    sender.sendMessage(INSTANCE.WRONG_USAGE_COMPONENT);
                    sender.sendMessage(new TextComponentString(EDIT.getExtendedUsage()));
                }
            };

            public final String name;

            EnumEditActions(String name) {
                this.name = name;
            }

            public static EnumEditActions getAction(String name) {
                for (EnumEditActions action : values()) {
                    if (action.name.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
                return null;
            }

            public void perform(MinecraftServer server, ICommandSender sender, Tournament tour, String... subArgs) {
                // overrided for actions
            }
        }
    }

    public static class Tournament {
        public String name;
        public String prefix;
        public String message;
        public boolean isEnabled;

        public Tournament(String name) {
            this.name = name.toLowerCase();
            this.prefix = INSTANCE.DEFAULT_PREFIX;
            this.message = "";
            this.isEnabled = false;
        }

        public String getChatMessage() {
            if (prefix.isEmpty()) return message;
            return prefix + "§a " + message;
        }
    }
}
