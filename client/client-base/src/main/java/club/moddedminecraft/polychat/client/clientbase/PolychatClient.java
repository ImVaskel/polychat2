package club.moddedminecraft.polychat.client.clientbase;

import club.moddedminecraft.polychat.client.clientbase.handlers.ChatMessageHandler;
import club.moddedminecraft.polychat.client.clientbase.util.YamlConfig;
import club.moddedminecraft.polychat.core.messagelibrary.PolychatProtobufMessageDispatcher;
import club.moddedminecraft.polychat.core.messagelibrary.ServerProtos;
import club.moddedminecraft.polychat.core.networklibrary.Client;
import club.moddedminecraft.polychat.core.networklibrary.Message;
import com.google.protobuf.Any;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PolychatClient {

    private final ClientApiBase clientApi;
    private final Client client;
    private final PolychatProtobufMessageDispatcher polychatProtobufMessageDispatcher;
    private final YamlConfig config;
    private final String serverId;
    private final ClientCallbacks clientCallbacks;
    private long lastUpdate = 0;

    /**
     * Connects to the Polychat server based on config values
     *
     * @param clientImpl the implementation of the client protocol
     */
    public PolychatClient(ClientApiBase clientImpl) {
        clientCallbacks = new ClientCallbacks(this);
        clientApi = clientImpl;
        config = getConfig();
        client = new Client(
                config.getOrDefault("server.address", "localhost"),
                config.getOrDefault("server.port", 5005),
                config.getOrDefault("server.buffersize", 32768)
        );
        polychatProtobufMessageDispatcher = new PolychatProtobufMessageDispatcher();
        OnlinePlayerThread playerThread = new OnlinePlayerThread(this);
        serverId = config.getOrDefault("serverId", "ID");

        polychatProtobufMessageDispatcher.addEventHandler(new ChatMessageHandler(clientApi));
        setupInfoMessage();
        playerThread.start();
    }

    /**
     * Gets config for client
     *
     * @return client config
     */
    private YamlConfig getConfig() {
        try {
            Path directory = clientApi.getConfigDirectory();
            Path configPath = directory.resolve("polychat.yml");
            directory.toFile().mkdir();

            if (configPath.toFile().createNewFile()) {
                return getDefaultConfig(configPath);
            }

            return YamlConfig.fromFilesystem(configPath);
        } catch (IOException e) {
            System.err.println("Failed to load config!");
            e.printStackTrace();
        }
        return YamlConfig.fromInMemoryString("");
    }

    /**
     * Sets up a default config
     *
     * @param path path for new config
     * @return default config
     * @throws IOException if unable to save file
     */
    private YamlConfig getDefaultConfig(Path path) throws IOException {
        YamlConfig def = YamlConfig.fromInMemoryString("");
        def.set("name", "A Minecraft Server");
        def.set("address", "example.com");
        def.set("color", 14);
        def.set("serverId", "ID");
        def.set("server.address", "localhost");
        def.set("server.port", 5005);
        def.saveToFile(path);
        return def;
    }

    /**
     * Prepares server info message for (re)connects
     */
    private void setupInfoMessage() {
        ServerProtos.ServerInfo info = ServerProtos.ServerInfo.newBuilder()
                .setServerId(serverId)
                .setServerName(config.getOrDefault("name", "DEFAULT_NAME"))
                .setServerAddress(config.getOrDefault("address", "DEFAULT_ADDRESS"))
                .setMaxPlayers(clientApi.getMaxPlayers())
                .build();
        Any packed = Any.pack(info);
        client.getReconnectMessageSet().add(packed.toByteArray());
    }

    /**
     * This method should be called at a consistent interval in order to process messages from the server.
     */
    public void update() {
        // only run every 250 ms
        if (lastUpdate + 250 > System.currentTimeMillis()) {
            return;
        }

        List<Message> messages = new ArrayList<>();
        try {
            messages = client.poll();
        } catch (IOException e) {
            System.err.println("Failed to reconnect to Polychat server");
        }

        for (Message message : messages) {
            polychatProtobufMessageDispatcher.handlePolychatMessage(message);
        }

        lastUpdate = System.currentTimeMillis();
    }

    /**
     * Sends a message to the server.
     *
     * @param message the protobuf message to be sent
     */
    public void sendMessage(com.google.protobuf.Message message) {
        Any packedMessage = Any.pack(message);
        byte[] messageBytes = packedMessage.toByteArray();
        try {
            client.sendMessage(messageBytes);
        } catch (IOException e) {
            System.err.println("Failed to send message!");
            e.printStackTrace();
        }
    }

    /**
     * Gets the formatted server ID with colors ex. §4[A5]
     *
     * @return formatted server id
     */
    public String getFormattedServerId() {
        int color = config.getOrDefault("color", 14);
        return String.format("§%01x", color) + "[" + serverId + "]" + "§r";
    }

    /**
     * Gets the formatted server ID ex. A5
     *
     * @return server id
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Gets the implementation of ClientApiBase
     *
     * @return the client API
     */
    public ClientApiBase getClientApi() {
        return clientApi;
    }

    /**
     * Gets the instance of ClientCallbacks
     * @return client callbacks
     */
    public ClientCallbacks getCallbacks() {
        return clientCallbacks;
    }
}