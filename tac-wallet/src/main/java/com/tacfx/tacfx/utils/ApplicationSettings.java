package com.tacfx.tacfx.utils;

import com.tacplatform.tacj.AssetDetails;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class ApplicationSettings {
    public final static AssetDetails MAIN_TOKEN = new AssetDetails("TAC", 0L, 0L, "", "Tac", "", 8, false, 10000000000000000L, false, 100L);
    public final static char CUSTOM_ID = 'C';
    public final static char MAINNET_ID = '%';
    public final static char STAGENET_ID = 'S';
    public final static char TESTNET_ID = 'T';
    public final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public final static int INPUT_REQUEST_DELAY = 1000;
    public final static int MAX_MASS_TX_SIZE = 100;
    public final static int NET_REQUEST_DELAY = 100;
    public final static int REQUEST_DELAY = 5;
    public final static int TX_LIST_REQUEST_DELAY = 15; // Do not set the value too low!
    public final static long TOKEN_MAX_AMOUNT = Long.MAX_VALUE;
    public final static String APPLICATION_NAME = "wallet";
    public final static String CUSTOM_NODE = "http://127.0.0.1:6863";
    public final static String MAINNET_NODE = "https://nodes.tactoken.io";
    public final static String RESOURCE_BUNDLE_FILEPATH = "messages";
    public final static String STAGENET_NODE = "https://nodes-stagenet.tacnodes.com/";
    public final static String TESTNET_NODE = "https://testnode1.tacnodes.com";
    public final static String VENDOR_NAME = "tacfx";
    public final static String[] LANGUAGES = {"de", "en", "fr", "it", "nl", "pt", "ru"};

    public static Path getAppPath(final OperatingSystem os) {
        final var childPath = Paths.get(VENDOR_NAME, APPLICATION_NAME);
        return Path.of(os.getAppDataFolder().toString(), childPath.toString());
    }

    public static String loadAppVersion() {
        final var appSettingsClass = MethodHandles.lookup().lookupClass();
        final var file = appSettingsClass.getResourceAsStream("/version.properties");
        final var properties = new Properties();
        try {
            properties.load(file);
            return properties.getProperty("version");
        } catch (IOException e) {
            e.printStackTrace();
            return "0.0.0";
        }
    }

}
