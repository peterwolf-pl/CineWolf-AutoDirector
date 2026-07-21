package pl.peterwolf.cinewolf.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class CineWolfConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Logger logger;
    private final Path path;
    private CineWolfConfig config;

    public CineWolfConfigManager(Logger logger) {
        this(logger, FabricLoader.getInstance().getConfigDir().resolve("cinewolf-autodirector.json"));
    }

    CineWolfConfigManager(Logger logger, Path path) {
        this.logger = logger;
        this.path = path;
    }

    public CineWolfConfig load() {
        if (!Files.exists(path)) {
            config = new CineWolfConfig();
            save();
            return config;
        }
        try {
            config = GSON.fromJson(Files.readString(path), CineWolfConfig.class);
            if (config == null) throw new IllegalArgumentException("Configuration root is null");
            config.normalize();
            save();
        } catch (Exception exception) {
            logger.error("Unable to load {}; using safe defaults", path, exception);
            backupMalformedFile();
            config = new CineWolfConfig();
            save();
        }
        return config;
    }

    public CineWolfConfig get() {
        return config == null ? load() : config;
    }

    public void save() {
        if (config == null) return;
        config.normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
        } catch (IOException exception) {
            logger.error("Unable to save {}", path, exception);
        }
    }

    private void backupMalformedFile() {
        try {
            Path backup = path.resolveSibling(path.getFileName() + ".malformed");
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.error("Unable to preserve malformed configuration {}", path, exception);
        }
    }
}
